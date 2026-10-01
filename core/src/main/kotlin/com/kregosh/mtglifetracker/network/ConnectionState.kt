package com.kregosh.mtglifetracker.network

/** State of the realtime connection to the session backend. */
sealed interface ConnectionState {
    object Connecting   : ConnectionState
    object Connected    : ConnectionState
    object Reconnecting : ConnectionState
    object Closed       : ConnectionState
    data class Failed(val reason: String) : ConnectionState
}
