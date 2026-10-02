package com.kregosh.mtglifetracker.network.schema

import com.kregosh.mtglifetracker.shared.SessionSettings
import com.kregosh.mtglifetracker.shared.StatTarget
import com.kregosh.mtglifetracker.shared.StatType
import com.kregosh.mtglifetracker.shared.UserState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionSchemaTest {

    // What DataSnapshot.getValue() returns: nested maps, numbers as Long.
    private val session = mapOf(
        "code"       to "ABCD2345",
        "createdAt"  to 1_700_000_000_000L,
        "hostUserId" to "alice",
        "game"       to 2L,
        "settings"   to mapOf(
            "startLife" to 40L, "commanderDeathThreshold" to 21L,
            "infectDeathThreshold" to 10L, "maxPlayers" to 4L,
        ),
        "users" to mapOf(
            "alice" to mapOf(
                "displayName" to "Alice", "life" to 33L, "online" to true, "game" to 2L,
                "conceded" to true,
                "customStats" to mapOf("poison" to 3L),
                "commanderDamage" to mapOf("bob" to 7L),
                "stats" to mapOf("poison" to "NUMERIC", "initiative" to "TOGGLE", "ring" to "RING_STAGE"),
            ),
            "bob" to mapOf("displayName" to "Bob", "life" to 40L, "online" to false, "game" to 1L),
        ),
        "globalStats"     to mapOf("daynight" to 1L),
        "monarch"         to "bob",
        "observers"       to mapOf("sam" to "Sam", "bad" to 3L),
    )

    @Test
    fun `a full session is parsed into core state`() {
        val parsed = SessionSchema.parseSession(session, defaultLife = 20u)!!

        assertEquals("ABCD2345", parsed.code)
        assertEquals(1_700_000_000_000L, parsed.createdAt)
        with(parsed.state) {
            assertEquals("alice", hostUserId)
            assertEquals(2L, game)
            assertEquals(SessionSettings(40u, 21u, 10u, 4), settings)
            assertEquals(mapOf("daynight" to 1u), globalStats)
            assertEquals("bob", monarch)
            assertEquals(mapOf("sam" to "Sam"), observers)
            assertEquals(
                listOf(
                    UserState(
                        id = "alice", displayName = "Alice", life = 33u, conceded = true, online = true, game = 2,
                        customStats = mapOf("poison" to 3u), commanderDamage = mapOf("bob" to 7u),
                        stats = mapOf("poison" to StatType.NUMERIC, "initiative" to StatType.TOGGLE, "ring" to StatType.RING_STAGE),
                    ),
                    UserState(id = "bob", displayName = "Bob", life = 40u, online = false, game = 1),
                ),
                users,
            )
        }
    }

    @Test
    fun `a missing session parses to null`() {
        assertNull(SessionSchema.parseSession(null, 20u))
        assertNull(SessionSchema.parseInfo("sid", null))
    }

    @Test
    fun `missing fields get safe defaults`() {
        val parsed = SessionSchema.parseSession(
            mapOf("code" to "ABC123", "users" to mapOf("x" to mapOf<String, Any>())), defaultLife = 20u,
        )!!
        assertNull(parsed.createdAt)
        assertNull(parsed.state.hostUserId)
        assertNull(parsed.state.settings)
        assertEquals(0L, parsed.state.game)
        assertEquals(UserState(id = "x", displayName = "", life = 20u, online = true), parsed.state.users.single())
    }

    @Test
    fun `numbers stored as doubles and negative counters are tolerated`() {
        val parsed = SessionSchema.parseSession(
            mapOf("users" to mapOf("x" to mapOf("life" to 12.0, "customStats" to mapOf("storm" to -3L)))), 20u,
        )!!
        val seat = parsed.state.users.single()
        assertEquals(12u, seat.life)
        assertEquals(mapOf("storm" to 0u), seat.customStats)
    }

    @Test
    fun `maps Firebase returned as lists are read by index`() {
        val parsed = SessionSchema.parseSession(mapOf("globalStats" to listOf(5L, null, 7L)), 20u)!!
        assertEquals(mapOf("0" to 5u, "2" to 7u), parsed.state.globalStats)
    }

    @Test
    fun `unknown stat types fall back to counters`() {
        assertEquals(StatType.NUMERIC, SessionSchema.parseStatType("SOMETHING_NEW"))
        assertEquals(StatType.NUMERIC, SessionSchema.parseStatType(null))
    }

    @Test
    fun `settings round-trip`() {
        val settings = SessionSettings(startLife = 30u, commanderDeathThreshold = 15u, infectDeathThreshold = 7u, maxPlayers = 6)
        assertEquals(settings, SessionSchema.parseSettings(SessionSchema.settingsToMap(settings)))
    }

    @Test
    fun `partial settings use the defaults for what is missing`() {
        assertEquals(SessionSettings(startLife = 40u), SessionSchema.parseSettings(mapOf("startLife" to 40L)))
    }

    @Test
    fun `session info lists the seated players and the rules`() {
        val info = SessionSchema.parseInfo("sid", session)!!
        assertEquals("sid", info.sessionId)
        assertEquals("ABCD2345", info.sessionCode)
        assertEquals(2, info.connectedUsers)
        assertEquals(setOf("alice", "bob"), info.userIds)
        assertEquals(4, info.settings?.maxPlayers)
    }

    @Test
    fun `a new session document has everything but the server timestamp`() {
        val doc = SessionSchema.newSession("ABCD2345", "alice", SessionSettings(startLife = 40u))
        assertEquals(setOf("code", "hostUserId", "game", "settings"), doc.keys)
        assertEquals(0L, doc["game"])
        val parsed = SessionSchema.parseSession(doc, 20u)!!
        assertEquals("alice", parsed.state.hostUserId)
        assertEquals(40u, parsed.state.settings?.startLife)
    }

    @Test
    fun `a seat written for a new game reads back the same`() {
        val seat = UserState(id = "alice", displayName = "Alice", life = 40u, online = true, game = 3,
                             stats = mapOf("poison" to StatType.NUMERIC, "ring" to StatType.RING_STAGE))
        val parsed = SessionSchema.parseSession(mapOf("users" to mapOf("alice" to SessionSchema.seatToMap(seat))), 20u)!!
        assertEquals(seat, parsed.state.users.single())
    }

    @Test
    fun `a fresh seat writes no empty stat maps`() {
        val map = SessionSchema.seatToMap(UserState(id = "a", displayName = "A", life = 20u))
        assertEquals(setOf("displayName", "life", "online", "game"), map.keys)
    }

    @Test
    fun `stats are stored at the right field of the seat`() {
        assertEquals("life", SessionSchema.statField(StatTarget.Life))
        assertEquals("commanderDamage/bob", SessionSchema.statField(StatTarget.CommanderDamage("bob")))
        assertEquals("customStats/poison", SessionSchema.statField(StatTarget.Custom("poison")))
    }

    @Test
    fun `cleanup writes target the right paths`() {
        assertEquals(mapOf("users/a" to null, "users/b" to null), SessionSchema.clearSeats(listOf("a", "b")))
        assertEquals("sessions/s1", SessionSchema.sessionPath("s1"))
        assertEquals("sessionCodes/ABC", SessionSchema.codePath("ABC"))
        assertEquals("presence/u1", SessionSchema.presencePath("u1"))
    }
}
