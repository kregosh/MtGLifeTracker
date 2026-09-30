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

    private val db         = FirebaseDatabase.getInstance(
        "https://mtg-lifetracker-7866f-default-rtdb.europe-west1.firebasedatabase.app"
    )
    private val sessionRef = db.getReference("sessions/$sessionId")
    private val myUserRef  = sessionRef.child("users/$userId")

    private val _messages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)
    private val _state    = MutableStateFlow<WsState>(WsState.Connecting)
    private val scope     = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val messages        : SharedFlow<ServerMessage> = _messages
    override val connectionState : StateFlow<WsState>        = _state

    private var connectedListener : ValueEventListener? = null
    private var sessionListener   : ValueEventListener? = null

    override fun connect() {
        myUserRef.setValue(
            mapOf(
                "displayName" to displayName,
                "life"        to startLife.toLong(),
                "customStats" to emptyMap<String, Any>(),
            )
        )
        myUserRef.onDisconnect().removeValue()

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

    override fun close() {
        _state.value = WsState.Closed
        connectedListener?.let { db.getReference(".info/connected").removeEventListener(it) }
        sessionListener?.let { sessionRef.removeEventListener(it) }
        myUserRef.onDisconnect().cancel()
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
