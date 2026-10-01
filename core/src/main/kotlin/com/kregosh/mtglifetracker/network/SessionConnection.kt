package com.kregosh.mtglifetracker.network

import com.kregosh.mtglifetracker.shared.ServerMessage
import com.kregosh.mtglifetracker.shared.SessionSettings
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
    fun setConceded(conceded: Boolean)
    fun setDisplayName(name: String)

    // ── Host controls ─────────────────────────────────────────────────────
    fun updateSettings(settings: SessionSettings)
    fun startNewGame()
    fun removePlayer(userId: String)

    // ── Friend requests ───────────────────────────────────────────────────
    fun sendFriendRequest(toUserId: String)
    fun acceptFriendRequest(fromUserId: String)
    fun declineFriendRequest(fromUserId: String)
    fun acknowledgeAccepted(fromUserId: String)

    /**
     * Detaches from the session. With [removePlayer] the player leaves for good;
     * without it they stay in the session as offline so they can resume later.
     */
    fun close(removePlayer: Boolean = true)
}
