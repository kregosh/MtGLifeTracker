package com.kregosh.mtglifetracker.network

import com.kregosh.mtglifetracker.shared.ServerMessage
import com.kregosh.mtglifetracker.shared.StatType
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface SessionConnection {
    val messages: SharedFlow<ServerMessage>
    val connectionState: StateFlow<WsState>
    fun connect()
    fun adjust(stat: String, delta: Int)
    fun addCustomStat(name: String, type: StatType = StatType.NUMERIC)
    fun removeCustomStat(name: String)
    fun setGlobal(stat: String, value: UInt)
    fun close()
}
