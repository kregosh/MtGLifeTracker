package com.kregosh.mtglifetracker.web.firebase

import com.kregosh.mtglifetracker.domain.SeatRules
import com.kregosh.mtglifetracker.network.ConnectionState
import com.kregosh.mtglifetracker.network.SessionConnection
import com.kregosh.mtglifetracker.network.schema.SessionSchema
import com.kregosh.mtglifetracker.shared.ServerMessage
import com.kregosh.mtglifetracker.shared.SessionSettings
import com.kregosh.mtglifetracker.shared.StatType
import com.kregosh.mtglifetracker.shared.applyDelta
import com.kregosh.mtglifetracker.shared.statTarget
import com.kregosh.mtglifetracker.web.firebase.js.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.await
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.js.Promise

// Outlives a closed connection, so the clean-up after leaving still finishes.
private val cleanupScope = MainScope()

/**
 * The browser's [SessionConnection], a port of the Android FirebaseSessionConnection on the
 * Firebase JS SDK. As there, it only moves data: decisions come from core and the layout
 * from [SessionSchema].
 */
class WebSessionConnection(
    private val sessionId   : String,
    private val userId      : String,
    private var displayName : String,
    private val startLife   : UInt,
) : SessionConnection {

    private val db           = WebFirebase.db
    private val sessionRef   = ref(db, SessionSchema.sessionPath(sessionId))
    private val myUserRef    = child(sessionRef, SessionSchema.seatPath(userId))
    private val onlineRef    = child(myUserRef, "online")
    private val observerRef  = child(sessionRef, SessionSchema.observerPath(userId))
    private val presenceRef  = ref(db, SessionSchema.presencePath(userId))
    private val connectedRef = ref(db, ".info/connected")

    private val inboundRequestRef  = child(sessionRef, "friendRequests/$userId")
    private val inboundAcceptedRef = child(sessionRef, "friendAccepted/$userId")

    private val _messages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)
    private val _state    = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    private val scope     = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val messages        : SharedFlow<ServerMessage>  = _messages
    override val connectionState : StateFlow<ConnectionState> = _state

    // Each listener registration hands back the JS function that removes it.
    private val unsubscribers = mutableListOf<JsAny>()

    private var resetForGame = -1L

    // Watching without a seat: nothing is written to users/, only to observers/.
    private var observing = false

    override fun connect(asObserver: Boolean) {
        observing = asObserver
        if (!observing) claimSeat()

        unsubscribers += onValue(connectedRef, { snapshot ->
            if (snapshot.`val`().toKotlin() == true) {
                _state.value = ConnectionState.Connected
                markOnline()
            } else {
                _state.value = ConnectionState.Reconnecting
            }
        }, { error -> _state.value = ConnectionState.Failed(errorMessage(error)) })

        unsubscribers += onValue(sessionRef, { snapshot ->
            onSession(SessionSchema.parseSession(snapshot.`val`().toKotlin(), startLife))
        }, { error -> emit(ServerMessage.Error(errorMessage(error))) })

        unsubscribers += onChildAdded(inboundRequestRef) { snapshot ->
            snapshot.named { from, name -> emit(ServerMessage.FriendRequest(from, name)) }
        }
        unsubscribers += onChildAdded(inboundAcceptedRef) { snapshot ->
            snapshot.named { from, name -> emit(ServerMessage.FriendAccepted(from, name)) }
        }
    }

    private fun onSession(parsed: SessionSchema.ParsedSession?) {
        if (parsed == null) {
            emit(ServerMessage.SessionGone)
            return
        }
        val state = parsed.state
        val mySeat = state.users.find { it.id == userId }
        if (SeatRules.needsReset(mySeat, state.game, resetForGame)) {
            resetForGame = state.game
            val life = state.settings?.startLife ?: startLife
            set(myUserRef, SessionSchema.seatToMap(
                SeatRules.newGameSeat(userId, displayName, life, state.game, stats = mySeat?.stats.orEmpty())).toJs()
            ).reportFailure()
        }
        emit(state)
    }

    private fun claimSeat(startLife: UInt = this.startLife) {
        transact(myUserRef) { current ->
            val seat = (current as? Map<*, *>).orEmpty()
            seat + mapOf(
                "displayName" to displayName,
                "life" to SeatRules.lifeOnJoin((seat["life"] as? Number)?.toLong(), startLife),
            )
        }
    }

    // onDisconnect handlers are consumed when they fire, so they are re-armed on every reconnect.
    private fun markOnline() {
        if (observing) {
            // An observer who drops off simply stops watching.
            onDisconnect(observerRef).remove().reportFailure()
            set(observerRef, displayName.toJs()).reportFailure()
        } else {
            onDisconnect(onlineRef).set(false.toJs()).reportFailure()
            set(onlineRef, true.toJs()).reportFailure()
        }
        onDisconnect(presenceRef).remove().reportFailure()
        set(presenceRef, sessionId.toJs()).reportFailure()
    }

    override fun watch() {
        if (observing) return
        observing = true
        // A pending onDisconnect write would recreate a nameless stub of the seat.
        onDisconnect(onlineRef).cancel()
        remove(myUserRef).reportFailure()
        if (_state.value == ConnectionState.Connected) markOnline()
    }

    override fun play(startLife: UInt) {
        if (!observing) return
        observing = false
        onDisconnect(observerRef).cancel()
        remove(observerRef).reportFailure()
        claimSeat(startLife)
        if (_state.value == ConnectionState.Connected) markOnline()
    }

    override fun adjust(stat: String, delta: Int) {
        transact(child(myUserRef, SessionSchema.statField(statTarget(stat)))) { current ->
            applyDelta((current as? Number)?.toLong(), delta)
        }
    }

    override fun addCustomStat(name: String, type: StatType) {
        set(child(myUserRef, SessionSchema.seatStatPath(name)), type.name.toJs()).reportFailure()
    }

    override fun removeCustomStat(name: String) {
        remove(child(myUserRef, SessionSchema.seatStatPath(name))).reportFailure()
    }

    override fun setMonarch(userId: String?) {
        val monarch = child(sessionRef, SessionSchema.MONARCH)
        (if (userId == null) remove(monarch) else set(monarch, userId.toJs())).reportFailure()
    }

    override fun setGlobal(stat: String, value: UInt) {
        set(child(sessionRef, "globalStats/$stat"), value.toLong().toJs()).reportFailure()
    }

    override fun removeGlobal(stat: String) {
        remove(child(sessionRef, "globalStats/$stat")).reportFailure()
    }

    override fun setConceded(conceded: Boolean) {
        set(child(myUserRef, "conceded"), conceded.toJs()).reportFailure()
    }

    override fun setDisplayName(name: String) {
        displayName = name
        set(if (observing) observerRef else child(myUserRef, "displayName"), name.toJs()).reportFailure()
    }

    // ── Host controls (the rules only accept these from the host) ─────────

    override fun updateSettings(settings: SessionSettings) {
        set(child(sessionRef, "settings"), SessionSchema.settingsToMap(settings).toJs()).reportFailure()
    }

    override fun startNewGame() {
        transact(child(sessionRef, "game")) { current -> ((current as? Number)?.toLong() ?: 0L) + 1 }
    }

    override fun removePlayer(userId: String) {
        remove(child(sessionRef, SessionSchema.seatPath(userId))).reportFailure()
    }

    // ── Friend requests ───────────────────────────────────────────────────

    override fun sendFriendRequest(toUserId: String) {
        set(child(sessionRef, "friendRequests/$toUserId/$userId"), mapOf("displayName" to displayName).toJs())
            .reportFailure()
    }

    override fun acceptFriendRequest(fromUserId: String) {
        remove(child(inboundRequestRef, fromUserId)).reportFailure()
        set(child(sessionRef, "friendAccepted/$fromUserId/$userId"), mapOf("displayName" to displayName).toJs())
            .reportFailure()
    }

    override fun declineFriendRequest(fromUserId: String) {
        remove(child(inboundRequestRef, fromUserId)).reportFailure()
    }

    override fun acknowledgeAccepted(fromUserId: String) {
        remove(child(inboundAcceptedRef, fromUserId)).reportFailure()
    }

    override fun close(removePlayer: Boolean) {
        detach()
        val wasObserving = observing
        scope.cancel()
        cleanupScope.launch {
            runCatching {
                if (wasObserving) {
                    // Observers keep nothing behind; resuming adds them back.
                    onDisconnect(observerRef).cancel().await<JsAny?>()
                    remove(observerRef).await<JsAny?>()
                } else {
                    // A pending onDisconnect write would recreate a nameless stub of a removed player.
                    onDisconnect(onlineRef).cancel().await<JsAny?>()
                    // Leaving removes the seat; closing the page keeps it, offline, for a later resume.
                    if (removePlayer) remove(myUserRef).await<JsAny?>()
                    else set(onlineRef, false.toJs()).await<JsAny?>()
                }
                closeSessionIfEmpty()
            }
        }
    }

    override fun handOverHost(toUserId: String) {
        set(child(sessionRef, SessionSchema.HOST), toUserId.toJs()).reportFailure()
    }

    private fun detach() {
        _state.value = ConnectionState.Closed
        unsubscribers.forEach(::callFunction)
        unsubscribers.clear()
        onDisconnect(presenceRef).cancel()
        remove(presenceRef)
    }

    // If nobody else is still here, the session and its code are cleaned up.
    private suspend fun closeSessionIfEmpty() {
        val value  = get(sessionRef).await<DataSnapshot>().`val`().toKotlin()
        val parsed = SessionSchema.parseSession(value, startLife) ?: return
        val othersWatching = (parsed.state.observers.keys - userId).isNotEmpty()
        val ghosts = SeatRules.seatsToClearBeforeDeleting(parsed.state.users, userId, othersWatching) ?: return
        if (ghosts.isNotEmpty()) update(sessionRef, SessionSchema.clearSeats(ghosts).toJs()!!).await<JsAny?>()
        remove(sessionRef).await<JsAny?>()
        parsed.code?.let { remove(ref(db, SessionSchema.codePath(it))).await<JsAny?>() }
    }

    // ── Firebase plumbing ─────────────────────────────────────────────────

    /** Read-modify-write of [target]; [change] gets and returns plain Kotlin values. */
    private fun transact(target: DatabaseReference, change: (Any?) -> Any?) {
        runTransaction(target) { current -> change(current.toKotlin()).toJs() }.reportFailure()
    }

    // A write the rules refuse is shown to the player instead of failing silently.
    private fun Promise<JsAny?>.reportFailure() {
        cleanupScope.launch {
            try { await<JsAny?>() } catch (e: Throwable) { emit(ServerMessage.Error(e.message ?: e.toString())) }
        }
    }

    private fun emit(message: ServerMessage) {
        scope.launch { _messages.emit(message) }
    }

    private fun DataSnapshot.named(onAdded: (key: String, displayName: String) -> Unit) {
        val from = key ?: return
        val name = ((`val`().toKotlin() as? Map<*, *>)?.get("displayName") as? String) ?: return
        onAdded(from, name)
    }
}
