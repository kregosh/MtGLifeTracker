package com.kregosh.mtglifetracker.network

import com.google.firebase.database.*
import com.kregosh.mtglifetracker.shared.ServerMessage
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
    private val displayName : String,
    private val startLife   : UInt,
) : SessionConnection {

    private val db         = FirebaseDatabase.getInstance()
    private val sessionRef = db.getReference("sessions/$sessionId")
    private val myUserRef  = sessionRef.child("users/$userId")

    // Friend-request subtrees scoped to me
    private val inboundRequestRef  = sessionRef.child("friendRequests/$userId")
    private val inboundAcceptedRef = sessionRef.child("friendAccepted/$userId")

    private val _messages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)
    private val _state    = MutableStateFlow<WsState>(WsState.Connecting)
    private val scope     = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val messages        : SharedFlow<ServerMessage> = _messages
    override val connectionState : StateFlow<WsState>        = _state

    private var connectedListener     : ValueEventListener?  = null
    private var sessionListener       : ValueEventListener?  = null
    private var requestChildListener  : ChildEventListener?  = null
    private var acceptedChildListener : ChildEventListener?  = null

    override fun connect() {
        myUserRef.setValue(
            mapOf(
                "displayName" to displayName,
                "life"        to startLife.toLong(),
                "customStats" to emptyMap<String, Any>(),
            )
        )

        // ── Connection state ──────────────────────────────────────────────
        val connListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _state.value = if (snapshot.getValue(Boolean::class.java) == true)
                    WsState.Connected
                else
                    WsState.Reconnecting
            }
            override fun onCancelled(error: DatabaseError) {
                _state.value = WsState.Failed(error.message)
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
                        customStats = userSnap.child("customStats").children.associate { stat ->
                            (stat.key ?: "") to (stat.getValue(Long::class.java) ?: 0L).toUInt()
                        },
                        conceded    = userSnap.child("conceded").getValue(Boolean::class.java) ?: false,
                    )
                }
                val statDefs = snapshot.child("customStatNames").children.associate { child ->
                    (child.key ?: "") to child.getValue(String::class.java).toStatType()
                }
                val globalStats = snapshot.child("globalStats").children.associate { child ->
                    (child.key ?: "") to (child.getValue(Long::class.java) ?: 0L).toUInt()
                }
                scope.launch {
                    _messages.emit(ServerMessage.State(users, statDefs, globalStats))
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

    override fun adjust(stat: String, delta: Int) {
        val fieldRef = when (stat) {
            "life" -> myUserRef.child("life")
            else   -> myUserRef.child("customStats/$stat")
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
        myUserRef.child("displayName").setValue(name)
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

    override fun close() {
        _state.value = WsState.Closed
        connectedListener?.let     { db.getReference(".info/connected").removeEventListener(it) }
        sessionListener?.let       { sessionRef.removeEventListener(it) }
        requestChildListener?.let  { inboundRequestRef.removeEventListener(it) }
        acceptedChildListener?.let { inboundAcceptedRef.removeEventListener(it) }
        myUserRef.removeValue()
        scope.cancel()
    }
}

private fun DataSnapshot.longOrZero(default: Long = 0L): Long =
    getValue(Long::class.java) ?: default

private fun String?.toStatType(): StatType = when (this) {
    "TOGGLE"     -> StatType.TOGGLE
    "RING_STAGE" -> StatType.RING_STAGE
    else         -> StatType.NUMERIC
}
