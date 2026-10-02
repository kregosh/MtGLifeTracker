package com.kregosh.mtglifetracker.domain

import com.kregosh.mtglifetracker.shared.StatType
import com.kregosh.mtglifetracker.shared.UserState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeatRulesTest {

    private fun seat(id: String, online: Boolean = true, game: Long = 0) =
        UserState(id = id, displayName = id, online = online, game = game)

    @Test
    fun `rejoining keeps the life total`() {
        assertEquals(7L, SeatRules.lifeOnJoin(existingLife = 7L, startLife = 40u))
        assertEquals(0L, SeatRules.lifeOnJoin(existingLife = 0L, startLife = 40u))
    }

    @Test
    fun `a new seat starts at the starting life`() {
        assertEquals(40L, SeatRules.lifeOnJoin(existingLife = null, startLife = 40u))
    }

    @Test
    fun `a seat from an earlier game is reset`() {
        assertTrue(SeatRules.needsReset(seat("me", game = 1), sessionGame = 2, lastResetGame = -1))
    }

    @Test
    fun `a seat already in the current game is left alone`() {
        assertFalse(SeatRules.needsReset(seat("me", game = 2), sessionGame = 2, lastResetGame = -1))
        assertFalse(SeatRules.needsReset(seat("me", game = 3), sessionGame = 2, lastResetGame = -1))
    }

    @Test
    fun `a reset already in flight is not repeated`() {
        assertFalse(SeatRules.needsReset(seat("me", game = 1), sessionGame = 2, lastResetGame = 2))
    }

    @Test
    fun `without a seat there is nothing to reset`() {
        assertFalse(SeatRules.needsReset(null, sessionGame = 5, lastResetGame = -1))
    }

    @Test
    fun `a new-game seat keeps only the name`() {
        val fresh = SeatRules.newGameSeat("me", "Alice", startLife = 30u, game = 4)
        assertEquals(UserState(id = "me", displayName = "Alice", life = 30u, online = true, game = 4), fresh)
    }

    @Test
    fun `a new game keeps the counters the player tracks, with fresh values`() {
        val stats = mapOf("poison" to StatType.NUMERIC)
        val fresh = SeatRules.newGameSeat("me", "Alice", startLife = 30u, game = 4, stats = stats)
        assertEquals(stats, fresh.stats)
        assertTrue(fresh.customStats.isEmpty())
    }

    @Test
    fun `the last player out clears every offline seat`() {
        val users = listOf(seat("me"), seat("ghost1", online = false), seat("ghost2", online = false))
        assertEquals(listOf("ghost1", "ghost2"), SeatRules.ghostsToClearAfterLeaving(users, "me"))
    }

    @Test
    fun `leaving alone leaves nothing to clear but still ends the session`() {
        assertEquals(emptyList(), SeatRules.ghostsToClearAfterLeaving(listOf(seat("me")), "me"))
        assertEquals(emptyList(), SeatRules.ghostsToClearAfterLeaving(emptyList(), "me"))
    }

    @Test
    fun `the session stays while anyone else is online`() {
        val users = listOf(seat("me"), seat("ghost", online = false), seat("bob"))
        assertNull(SeatRules.ghostsToClearAfterLeaving(users, "me"))
    }
}
