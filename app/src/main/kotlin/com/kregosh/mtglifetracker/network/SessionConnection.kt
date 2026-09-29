package com.kregosh.mtglifetracker.network

import com.kregosh.mtglifetracker.shared.ServerMessage
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface SessionConnection {
    val messages: SharedFlow<ServerMessage>
    val connectionState: StateFlow<WsState>
    fun connect()
    fun increment()
    fun decrement()
    fun close()
}
