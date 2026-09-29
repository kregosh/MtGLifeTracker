package com.kregosh.mtglifetracker.network

sealed interface WsState {
    object Connecting   : WsState
    object Connected    : WsState
    object Reconnecting : WsState
    object Closed       : WsState
    data class Failed(val reason: String) : WsState
}
