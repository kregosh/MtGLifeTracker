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

    @Test
    fun `someone still watching keeps the session`() {
        val users = listOf(seat("me"), seat("ghost", online = false))
        assertNull(SeatRules.ghostsToClearAfterLeaving(users, "me", othersWatching = true))
    }

    @Test
    fun `the last observer out of a session without players closes it`() {
        assertEquals(emptyList(), SeatRules.ghostsToClearAfterLeaving(emptyList(), "me"))
    }

    @Test
    fun `the next host is the first other player, preferring one who is online`() {
        val users = listOf(seat("host"), seat("off", online = false), seat("bob"), seat("carol"))
        assertEquals("bob", SeatRules.nextHost(users, "host"))
    }

    @Test
    fun `with only offline players left the first of them becomes host`() {
        val users = listOf(seat("host"), seat("off1", online = false), seat("off2", online = false))
        assertEquals("off1", SeatRules.nextHost(users, "host"))
    }

    @Test
    fun `nobody becomes host when the host was the only player`() {
        assertNull(SeatRules.nextHost(listOf(seat("host")), "host"))
        assertNull(SeatRules.nextHost(emptyList(), "host"))
    }

    @Test
    fun `the last one online closing the app clears every seat, their own included`() {
        val users = listOf(seat("me", online = false), seat("ghost", online = false))
        assertEquals(listOf("ghost", "me"), SeatRules.seatsToClearBeforeDeleting(users, "me"))
    }

    @Test
    fun `closing the app while someone else is online or watching keeps the session`() {
        val users = listOf(seat("me"), seat("bob"))
        assertNull(SeatRules.seatsToClearBeforeDeleting(users, "me"))
        assertNull(SeatRules.seatsToClearBeforeDeleting(listOf(seat("me")), "me", othersWatching = true))
    }

    @Test
    fun `a player who already left has no seat of their own to clear`() {
        val users = listOf(seat("ghost", online = false))
        assertEquals(listOf("ghost"), SeatRules.seatsToClearBeforeDeleting(users, "me"))
    }
}
