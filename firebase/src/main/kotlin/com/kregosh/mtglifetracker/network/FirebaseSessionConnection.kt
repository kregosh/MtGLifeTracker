package com.kregosh.mtglifetracker.network

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.*
import com.kregosh.mtglifetracker.domain.SeatRules
import com.kregosh.mtglifetracker.network.schema.SessionSchema
import com.kregosh.mtglifetracker.shared.ServerMessage
import com.kregosh.mtglifetracker.shared.SessionSettings
import com.kregosh.mtglifetracker.shared.StatType
import com.kregosh.mtglifetracker.shared.applyDelta
import com.kregosh.mtglifetracker.shared.statTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Firebase Realtime Database adapter for [SessionConnection]. It only moves data:
 * decisions come from core (SeatRules, statTarget, applyDelta) and the data layout
 * from [SessionSchema].
 */
class FirebaseSessionConnection(
    private val sessionId   : String,
    private val userId      : String,
    private var displayName : String,
    private val startLife   : UInt,
) : SessionConnection {

    private val db           = FirebaseDatabase.getInstance()
    private val sessionRef   = db.getReference(SessionSchema.sessionPath(sessionId))
    private val myUserRef    = sessionRef.child(SessionSchema.seatPath(userId))
    private val onlineRef    = myUserRef.child("online")
    private val observerRef  = sessionRef.child(SessionSchema.observerPath(userId))
    private val presenceRef  = db.getReference(SessionSchema.presencePath(userId))
    private val connectedRef = db.getReference(".info/connected")

    private val inboundRequestRef  = sessionRef.child("friendRequests/$userId")
    private val inboundAcceptedRef = sessionRef.child("friendAccepted/$userId")

    private val _messages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)
    private val _state    = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    private val scope     = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val messages        : SharedFlow<ServerMessage>   = _messages
    override val connectionState : StateFlow<ConnectionState>  = _state

    private var connectedListener     : ValueEventListener? = null
    private var sessionListener       : ValueEventListener? = null
    private var requestChildListener  : ChildEventListener? = null
    private var acceptedChildListener : ChildEventListener? = null

    private var resetForGame = -1L

    // Watching without a seat: nothing is written to users/, only to observers/.
    @Volatile private var observing = false

    override fun connect(asObserver: Boolean) {
        observing = asObserver
        if (!observing) claimSeat()

        connectedListener = connectedRef.addValueEventListener(valueListener(
            onData  = { snapshot ->
                if (snapshot.getValue(Boolean::class.java) == true) {
                    _state.value = ConnectionState.Connected
                    markOnline()
                } else {
                    _state.value = ConnectionState.Reconnecting
                }
            },
            onError = { _state.value = ConnectionState.Failed(it.message) },
        ))

        sessionListener = sessionRef.addValueEventListener(valueListener(
            onData  = { snapshot -> onSession(SessionSchema.parseSession(snapshot.value, startLife)) },
            onError = { emit(ServerMessage.Error(it.message)) },
        ))

        requestChildListener = inboundRequestRef.addChildEventListener(childAddedListener { fromUserId, name ->
            emit(ServerMessage.FriendRequest(fromUserId, name))
        })
        acceptedChildListener = inboundAcceptedRef.addChildEventListener(childAddedListener { fromUserId, name ->
            emit(ServerMessage.FriendAccepted(fromUserId, name))
        })
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
            myUserRef.setValue(SessionSchema.seatToMap(
                SeatRules.newGameSeat(userId, displayName, life, state.game, stats = mySeat?.stats.orEmpty())))
        }
        emit(state)
    }

    private fun claimSeat(startLife: UInt = this.startLife) {
        myUserRef.runTransaction(transaction(
            update     = { data ->
                data.child("displayName").value = displayName
                data.child("life").value = SeatRules.lifeOnJoin(data.child("life").getValue(Long::class.java), startLife)
            },
            onDone = { error -> if (error != null) emit(ServerMessage.Error(error.message)) },
        ))
    }

    // onDisconnect handlers are consumed when they fire, so they are re-armed on every reconnect.
    private fun markOnline() {
        if (observing) {
            // An observer who drops off simply stops watching.
            observerRef.onDisconnect().removeValue()
            observerRef.setValue(displayName)
        } else {
            onlineRef.onDisconnect().setValue(false)
            onlineRef.setValue(true)
        }
        presenceRef.onDisconnect().removeValue()
        presenceRef.setValue(sessionId)
    }

    override fun watch() {
        if (observing) return
        observing = true
        // A pending onDisconnect write would recreate a nameless stub of the seat.
        onlineRef.onDisconnect().cancel()
        myUserRef.removeValue()
        if (_state.value == ConnectionState.Connected) markOnline()
    }

    override fun play(startLife: UInt) {
        if (!observing) return
        observing = false
        observerRef.onDisconnect().cancel()
        observerRef.removeValue()
        claimSeat(startLife)
        if (_state.value == ConnectionState.Connected) markOnline()
    }

    override fun adjust(stat: String, delta: Int) {
        myUserRef.child(SessionSchema.statField(statTarget(stat))).runTransaction(transaction(
            update = { data -> data.value = applyDelta(data.getValue(Long::class.java), delta) },
            onDone = { error -> if (error != null) emit(ServerMessage.Error(error.message)) },
        ))
    }

    override fun addCustomStat(name: String, type: StatType) {
        myUserRef.child(SessionSchema.seatStatPath(name)).setValue(type.name).reportFailure()
    }

    override fun removeCustomStat(name: String) {
        myUserRef.child(SessionSchema.seatStatPath(name)).removeValue().reportFailure()
    }

    override fun setMonarch(userId: String?) {
        val ref = sessionRef.child(SessionSchema.MONARCH)
        if (userId == null) ref.removeValue() else ref.setValue(userId)
    }

    override fun setGlobal(stat: String, value: UInt) {
        sessionRef.child("globalStats/$stat").setValue(value.toLong())
    }

    override fun removeGlobal(stat: String) {
        sessionRef.child("globalStats/$stat").removeValue()
    }

    override fun setConceded(conceded: Boolean) {
        myUserRef.child("conceded").setValue(conceded)
    }

    override fun setDisplayName(name: String) {
        displayName = name
        if (observing) observerRef.setValue(name) else myUserRef.child("displayName").setValue(name)
    }

    // ── Host controls (the rules only accept these from the host) ─────────

    override fun updateSettings(settings: SessionSettings) {
        sessionRef.child("settings").setValue(SessionSchema.settingsToMap(settings))
    }

    override fun startNewGame() {
        sessionRef.child("game").runTransaction(transaction(
            update = { data -> data.value = (data.getValue(Long::class.java) ?: 0L) + 1 },
        ))
    }

    override fun removePlayer(userId: String) {
        sessionRef.child(SessionSchema.seatPath(userId)).removeValue()
    }

    // ── Friend requests ───────────────────────────────────────────────────

    override fun sendFriendRequest(toUserId: String) {
        sessionRef.child("friendRequests/$toUserId/$userId").setValue(mapOf("displayName" to displayName))
    }

    override fun acceptFriendRequest(fromUserId: String) {
        inboundRequestRef.child(fromUserId).removeValue()
        sessionRef.child("friendAccepted/$fromUserId/$userId").setValue(mapOf("displayName" to displayName))
    }

    override fun declineFriendRequest(fromUserId: String) {
        inboundRequestRef.child(fromUserId).removeValue()
    }

    override fun acknowledgeAccepted(fromUserId: String) {
        inboundAcceptedRef.child(fromUserId).removeValue()
    }

    override fun close(removePlayer: Boolean) {
        _state.value = ConnectionState.Closed
        connectedListener?.let     { connectedRef.removeEventListener(it) }
        sessionListener?.let       { sessionRef.removeEventListener(it) }
        requestChildListener?.let  { inboundRequestRef.removeEventListener(it) }
        acceptedChildListener?.let { inboundAcceptedRef.removeEventListener(it) }
        presenceRef.onDisconnect().cancel()
        presenceRef.removeValue()
        if (observing) {
            // Observers keep nothing behind; resuming adds them back.
            observerRef.onDisconnect().cancel()
            observerRef.removeValue().addOnSuccessListener { if (removePlayer) closeSessionIfEmpty() }
            scope.cancel()
            return
        }
        // A pending onDisconnect write would recreate a nameless stub of a removed player.
        onlineRef.onDisconnect().cancel()
        if (removePlayer) {
            myUserRef.removeValue().addOnSuccessListener { closeSessionIfEmpty() }
        } else {
            onlineRef.setValue(false)
        }
        scope.cancel()
    }

    private fun closeSessionIfEmpty() {
        sessionRef.get().addOnSuccessListener { snapshot ->
            val parsed = SessionSchema.parseSession(snapshot.value, startLife) ?: return@addOnSuccessListener
            val othersWatching = (parsed.state.observers.keys - userId).isNotEmpty()
            val ghosts = SeatRules.ghostsToClearAfterLeaving(parsed.state.users, userId, othersWatching)
                ?: return@addOnSuccessListener
            val cleared = if (ghosts.isEmpty()) Tasks.forResult<Void>(null)
                          else sessionRef.updateChildren(SessionSchema.clearSeats(ghosts))
            cleared
                .onSuccessTask { sessionRef.removeValue() }
                .addOnSuccessListener { parsed.code?.let { db.getReference(SessionSchema.codePath(it)).removeValue() } }
        }
    }

    // ── Firebase plumbing ─────────────────────────────────────────────────

    // A write the rules refuse (for example after a schema change the rules haven't caught
    // up with) is shown to the player instead of failing silently.
    private fun Task<*>.reportFailure() {
        addOnFailureListener { emit(ServerMessage.Error(it.message ?: it.toString())) }
    }

    private fun emit(message: ServerMessage) {
        scope.launch { _messages.emit(message) }
    }

    private fun valueListener(onData: (DataSnapshot) -> Unit, onError: (DatabaseError) -> Unit) =
        object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) = onData(snapshot)
            override fun onCancelled(error: DatabaseError) = onError(error)
        }

    // Only additions matter, so life-total changes elsewhere in the session don't fire these.
    private fun childAddedListener(onAdded: (key: String, displayName: String) -> Unit) =
        object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val key  = snapshot.key ?: return
                val name = snapshot.child("displayName").getValue(String::class.java) ?: return
                onAdded(key, name)
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }

    private fun transaction(update: (MutableData) -> Unit, onDone: (DatabaseError?) -> Unit = {}) =
        object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                update(data)
                return Transaction.success(data)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) =
                onDone(error)
        }
}
