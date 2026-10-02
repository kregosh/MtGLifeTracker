package com.kregosh.mtglifetracker.network

import com.kregosh.mtglifetracker.shared.ServerMessage
import com.kregosh.mtglifetracker.shared.SessionSettings
import com.kregosh.mtglifetracker.shared.StatType
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface SessionConnection {
    val messages: SharedFlow<ServerMessage>
    val connectionState: StateFlow<ConnectionState>
    /** Joins with a seat, or with [asObserver] only to watch. */
    fun connect(asObserver: Boolean = false)
    /** Gives up the seat to watch, or takes a seat again. */
    fun setObserving(observing: Boolean)
    fun adjust(stat: String, delta: Int)
    /** Turns a counter on for the local player only. */
    fun addCustomStat(name: String, type: StatType = StatType.NUMERIC)
    /** Turns a counter off for the local player. Its value is kept. */
    fun removeCustomStat(name: String)
    /** Makes [userId] the monarch; null takes the monarch out of the game. */
    fun setMonarch(userId: String?)
    fun setGlobal(stat: String, value: UInt)
    fun removeGlobal(stat: String)
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
