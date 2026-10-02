package com.kregosh.mtglifetracker.domain

import com.kregosh.mtglifetracker.shared.StatType
import com.kregosh.mtglifetracker.shared.UserState

/** How a player's seat in a session is created, reset and cleaned up. */
object SeatRules {

    /** Rejoining keeps the life total; only a seat without one starts at [startLife]. */
    fun lifeOnJoin(existingLife: Long?, startLife: UInt): Long = existingLife ?: startLife.toLong()

    /**
     * Whether the local seat must be reset because the host started a new game.
     * [lastResetGame] guards against resetting twice while the first write is in flight.
     */
    fun needsReset(mySeat: UserState?, sessionGame: Long, lastResetGame: Long): Boolean =
        mySeat != null && sessionGame > mySeat.game && sessionGame > lastResetGame

    /** A seat as it looks at the start of [game]: name and chosen counters kept, values fresh. */
    fun newGameSeat(myId: String, displayName: String, startLife: UInt, game: Long,
                    stats: Map<String, StatType> = emptyMap()): UserState =
        UserState(id = myId, displayName = displayName, life = startLife, online = true, game = game, stats = stats)

    /**
     * After [myId] leaves: the seats of offline players to clear before the session is
     * deleted, or null if anyone else is still there (online, or [othersWatching]) and
     * the session must stay.
     */
    fun ghostsToClearAfterLeaving(users: List<UserState>, myId: String, othersWatching: Boolean = false): List<String>? {
        val others = users.filter { it.id != myId }
        return if (othersWatching || others.any { it.online }) null else others.map { it.id }
    }
}
