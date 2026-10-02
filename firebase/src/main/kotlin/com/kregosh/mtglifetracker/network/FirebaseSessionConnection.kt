package com.kregosh.mtglifetracker.network

import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.*
import com.kregosh.mtglifetracker.shared.LIFE_STAT
import com.kregosh.mtglifetracker.shared.ServerMessage
import com.kregosh.mtglifetracker.shared.SessionSettings
import com.kregosh.mtglifetracker.shared.commanderDamageSource
import com.kregosh.mtglifetracker.shared.StatType
import com.kregosh.mtglifetracker.shared.UserState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FirebaseSessionConnection(
    private val sessionId   : String,
    private val userId      : String,
    private var displayName : String,
    private val startLife   : UInt,
) : SessionConnection {

    private val db         = FirebaseDatabase.getInstance()
    private val sessionRef = db.getReference("sessions/$sessionId")
    private val myUserRef  = sessionRef.child("users/$userId")

    // Friend-request subtrees scoped to me
    private val inboundRequestRef  = sessionRef.child("friendRequests/$userId")
    private val inboundAcceptedRef = sessionRef.child("friendAccepted/$userId")

    private val _messages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)
    private val _state    = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    private val scope     = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val messages        : SharedFlow<ServerMessage> = _messages
    override val connectionState : StateFlow<ConnectionState>        = _state

    private var connectedListener     : ValueEventListener?  = null
    private var sessionListener       : ValueEventListener?  = null
    private var requestChildListener  : ChildEventListener?  = null
    private var acceptedChildListener : ChildEventListener?  = null

    private val presenceRef = db.getReference("presence/$userId")

    private val onlineRef = myUserRef.child("online")

    override fun connect() {
        claimSeat()

        // ── Connection state ──────────────────────────────────────────────
        val connListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.getValue(Boolean::class.java) == true) {
                    _state.value = ConnectionState.Connected
                    markOnline()
                } else {
                    _state.value = ConnectionState.Reconnecting
                }
            }
            override fun onCancelled(error: DatabaseError) {
                _state.value = ConnectionState.Failed(error.message)
            }
        }
        connectedListener = connListener
        db.getReference(".info/connected").addValueEventListener(connListener)

        // ── Session state (users, stats, globals) ─────────────────────────
        val sessListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = snapshot.child("users").children.map { userSnap ->
                    UserState(
                        id          = userSnap.key ?: "",
                        displayName = userSnap.child("displayName").getValue(String::class.java) ?: "",
                        life        = userSnap.child("life").longOrZero(startLife.toLong()).toUInt(),
                        customStats = userSnap.child("customStats").toUIntMap(),
                        conceded    = userSnap.child("conceded").getValue(Boolean::class.java) ?: false,
                        online      = userSnap.child("online").getValue(Boolean::class.java) ?: true,
                        commanderDamage = userSnap.child("commanderDamage").toUIntMap(),
                    )
                }
                val settings = snapshot.child("settings").toSessionSettings()
                val game     = snapshot.child("game").getValue(Long::class.java) ?: 0L
                resetSeatIfNewGame(snapshot.child("users/$userId"), game, settings)
                val statDefs = snapshot.child("customStatNames").children.associate { child ->
                    (child.key ?: "") to child.getValue(String::class.java).toStatType()
                }
                val globalStats = snapshot.child("globalStats").toUIntMap()
                val hostUserId  = snapshot.child("hostUserId").getValue(String::class.java)
                scope.launch {
                    _messages.emit(ServerMessage.State(users, statDefs, globalStats, hostUserId, settings, game))
                }
            }
            override fun onCancelled(error: DatabaseError) {
                scope.launch { _messages.emit(ServerMessage.Error(error.message)) }
            }
        }
        sessionListener = sessListener
        sessionRef.addValueEventListener(sessListener)

        // ── Incoming friend requests ──────────────────────────────────────
        // Use ChildEventListener so this only fires when a request arrives,
        // not on every life-total change.
        val reqListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val fromUserId = snapshot.key ?: return
                val fromName   = snapshot.child("displayName").getValue(String::class.java) ?: return
                scope.launch {
                    _messages.emit(ServerMessage.FriendRequest(fromUserId, fromName))
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        requestChildListener = reqListener
        inboundRequestRef.addChildEventListener(reqListener)

        // ── Incoming friend acceptances ───────────────────────────────────
        val accListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val fromUserId = snapshot.key ?: return
                val fromName   = snapshot.child("displayName").getValue(String::class.java) ?: return
                scope.launch {
                    _messages.emit(ServerMessage.FriendAccepted(fromUserId, fromName))
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        acceptedChildListener = accListener
        inboundAcceptedRef.addChildEventListener(accListener)
    }

    // Rejoining (after a crash, a restart or a dropped connection) must not reset the
    // player's life and stats, so only a missing seat is initialised.
    private fun claimSeat() {
        myUserRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                data.child("displayName").value = displayName
                if (!data.hasChild("life")) data.child("life").value = startLife.toLong()
                return Transaction.success(data)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (error != null) scope.launch { _messages.emit(ServerMessage.Error(error.message)) }
            }
        })
    }

    // onDisconnect handlers are consumed when they fire, so they are re-armed on every reconnect.
    private fun markOnline() {
        onlineRef.onDisconnect().setValue(false)
        onlineRef.setValue(true)
        presenceRef.onDisconnect().removeValue()
        presenceRef.setValue(sessionId)
    }

    // Each player resets their own seat when the host starts a new game, so the
    // rules never have to let one player write another player's stats.
    private var resetForGame = -1L

    private fun resetSeatIfNewGame(mySeat: DataSnapshot, game: Long, settings: SessionSettings?) {
        val seatGame = mySeat.child("game").getValue(Long::class.java) ?: 0L
        if (!mySeat.exists() || game <= seatGame || game <= resetForGame) return
        resetForGame = game
        myUserRef.setValue(
            mapOf(
                "displayName" to displayName,
                "life"        to (settings?.startLife ?: startLife).toLong(),
                "online"      to true,
                "game"        to game,
            )
        )
    }

    override fun adjust(stat: String, delta: Int) {
        val opponent = commanderDamageSource(stat)
        val fieldRef = when {
            stat == LIFE_STAT -> myUserRef.child("life")
            opponent != null  -> myUserRef.child("commanderDamage/$opponent")
            else              -> myUserRef.child("customStats/$stat")
        }
        fieldRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                val current = data.getValue(Long::class.java) ?: 0L
                data.value  = (current + delta).coerceAtLeast(0L)
                return Transaction.success(data)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
        })
    }

    override fun addCustomStat(name: String, type: StatType) {
        sessionRef.child("customStatNames/$name").setValue(type.name)
    }

    override fun removeCustomStat(name: String) {
        sessionRef.child("customStatNames/$name").removeValue()
        sessionRef.child("users").get().addOnSuccessListener { snapshot ->
            val updates = snapshot.children
                .mapNotNull { it.key }
                .associate { uid -> "users/$uid/customStats/$name" to null as Any? }
            if (updates.isNotEmpty()) sessionRef.updateChildren(updates)
        }
    }

    override fun setGlobal(stat: String, value: UInt) {
        sessionRef.child("globalStats/$stat").setValue(value.toLong())
    }

    override fun setConceded(conceded: Boolean) {
        myUserRef.child("conceded").setValue(conceded)
    }

    override fun setDisplayName(name: String) {
        displayName = name
        myUserRef.child("displayName").setValue(name)
    }

    // ── Host controls (the rules only accept these from the host) ─────────

    override fun updateSettings(settings: SessionSettings) {
        sessionRef.child("settings").setValue(settings.toMap())
    }

    override fun startNewGame() {
        sessionRef.child("game").runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                data.value = (data.getValue(Long::class.java) ?: 0L) + 1
                return Transaction.success(data)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
        })
    }

    override fun removePlayer(userId: String) {
        sessionRef.child("users/$userId").removeValue()
    }

    // ── Friend requests ───────────────────────────────────────────────────

    override fun sendFriendRequest(toUserId: String) {
        sessionRef.child("friendRequests/$toUserId/$userId").setValue(
            mapOf("displayName" to displayName)
        )
    }

    override fun acceptFriendRequest(fromUserId: String) {
        // Remove the inbound request
        inboundRequestRef.child(fromUserId).removeValue()
        // Notify the sender that we accepted, carrying our own display name
        sessionRef.child("friendAccepted/$fromUserId/$userId").setValue(
            mapOf("displayName" to displayName)
        )
    }

    override fun declineFriendRequest(fromUserId: String) {
        inboundRequestRef.child(fromUserId).removeValue()
    }

    override fun acknowledgeAccepted(fromUserId: String) {
        // Clean up the acceptance notification once the app has processed it
        inboundAcceptedRef.child(fromUserId).removeValue()
    }

    override fun close(removePlayer: Boolean) {
        _state.value = ConnectionState.Closed
        connectedListener?.let     { db.getReference(".info/connected").removeEventListener(it) }
        sessionListener?.let       { sessionRef.removeEventListener(it) }
        requestChildListener?.let  { inboundRequestRef.removeEventListener(it) }
        acceptedChildListener?.let { inboundAcceptedRef.removeEventListener(it) }
        presenceRef.onDisconnect().cancel()
        presenceRef.removeValue()
        // A pending onDisconnect write would recreate a nameless stub of a removed player.
        onlineRef.onDisconnect().cancel()
        if (removePlayer) {
            myUserRef.removeValue().addOnSuccessListener { closeSessionIfEmpty() }
        } else {
            onlineRef.setValue(false)
        }
        scope.cancel()
    }

    // The last player out deletes the session (and any offline ghosts) so game data
    // doesn't pile up in the database.
    private fun closeSessionIfEmpty() {
        sessionRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) return@addOnSuccessListener
            val others = snapshot.child("users").children.filter { it.key != userId }
            if (others.any { it.child("online").getValue(Boolean::class.java) != false }) return@addOnSuccessListener

            val code   = snapshot.child("code").getValue(String::class.java)
            val ghosts = others.mapNotNull { it.key }.associate { "users/$it" to null as Any? }
            val cleared = if (ghosts.isEmpty()) Tasks.forResult<Void>(null) else sessionRef.updateChildren(ghosts)
            cleared
                .onSuccessTask { sessionRef.removeValue() }
                .addOnSuccessListener { code?.let { db.getReference("sessionCodes/$it").removeValue() } }
        }
    }
}

private fun DataSnapshot.longOrZero(default: Long = 0L): Long =
    getValue(Long::class.java) ?: default

private fun String?.toStatType(): StatType = when (this) {
    "TOGGLE"     -> StatType.TOGGLE
    "RING_STAGE" -> StatType.RING_STAGE
    else         -> StatType.NUMERIC
}
