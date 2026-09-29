package com.kregosh.mtglifetracker.backend

import com.kregosh.mtglifetracker.backend.session.ConnectedUser
import com.kregosh.mtglifetracker.backend.session.SessionRoom
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SessionRoomTest {

    private fun makeRoom() = SessionRoom("session-1", "ABCDEF")

    private fun makeUser(id: String, name: String = "Player", life: UInt = 20u) =
        ConnectedUser(
            userId      = id,
            displayName = name,
            socket      = mockk(relaxed = true),
            life        = life,
        )

    @Test
    fun `room starts empty`() {
        assertTrue(makeRoom().isEmpty())
    }

    @Test
    fun `addUser increments count`() = runTest {
        val room = makeRoom()
        room.addUser(makeUser("u1"))
        assertEquals(1, room.userCount())
        assertFalse(room.isEmpty())
    }

    @Test
    fun `removeUser decrements count`() = runTest {
        val room = makeRoom()
        room.addUser(makeUser("u1"))
        room.removeUser("u1")
        assertEquals(0, room.userCount())
        assertTrue(room.isEmpty())
    }

    @Test
    fun `adjust life increments correctly`() = runTest {
        val room = makeRoom()
        room.addUser(makeUser("u1", life = 20u))
        room.adjust("u1", "life", 1)
        assertEquals(21u, room.currentState().first { it.id == "u1" }.life)
    }

    @Test
    fun `adjust life decrements correctly`() = runTest {
        val room = makeRoom()
        room.addUser(makeUser("u1", life = 20u))
        room.adjust("u1", "life", -5)
        assertEquals(15u, room.currentState().first { it.id == "u1" }.life)
    }

    @Test
    fun `adjust life clamped at zero`() = runTest {
        val room = makeRoom()
        room.addUser(makeUser("u1", life = 1u))
        room.adjust("u1", "life", -100)
        assertEquals(0u, room.currentState().first { it.id == "u1" }.life)
    }

    @Test
    fun `adjust life clamped at MAX_VALUE`() = runTest {
        val room = makeRoom()
        room.addUser(makeUser("u1", life = UInt.MAX_VALUE))
        room.adjust("u1", "life", 1)
        assertEquals(UInt.MAX_VALUE, room.currentState().first { it.id == "u1" }.life)
    }

    @Test
    fun `adjust commander damage`() = runTest {
        val room = makeRoom()
        room.addUser(makeUser("u1"))
        room.adjust("u1", "commander", 7)
        assertEquals(7u, room.currentState().first { it.id == "u1" }.commanderDamage)
    }

    @Test
    fun `adjust poison damage`() = runTest {
        val room = makeRoom()
        room.addUser(makeUser("u1"))
        room.adjust("u1", "poison", 3)
        assertEquals(3u, room.currentState().first { it.id == "u1" }.poisonDamage)
    }

    @Test
    fun `addCustomStat and adjust custom stat`() = runTest {
        val room = makeRoom()
        room.addUser(makeUser("u1"))
        room.addCustomStat("Energy")
        room.adjust("u1", "Energy", 4)
        assertEquals(4u, room.currentState().first { it.id == "u1" }.customStats["Energy"])
    }

    @Test
    fun `addCustomStat seeds existing users`() = runTest {
        val room = makeRoom()
        room.addUser(makeUser("u1"))
        room.addCustomStat("Infect")
        val stats = room.currentState().first { it.id == "u1" }.customStats
        assertEquals(0u, stats["Infect"])
    }

    @Test
    fun `addCustomStat seeds new users joining after stat exists`() = runTest {
        val room = makeRoom()
        room.addCustomStat("Rad")
        room.addUser(makeUser("u1"))
        val stats = room.currentState().first { it.id == "u1" }.customStats
        assertEquals(0u, stats["Rad"])
    }

    @Test
    fun `addCustomStat returns false for duplicate`() = runTest {
        val room = makeRoom()
        assertTrue(room.addCustomStat("Energy"))
        assertFalse(room.addCustomStat("Energy"))
    }

    @Test
    fun `adjust on unknown userId is no-op`() = runTest {
        val room = makeRoom()
        room.adjust("nobody", "life", -1)
        assertEquals(0, room.userCount())
    }

    @Test
    fun `adjust on unknown custom stat is no-op`() = runTest {
        val room = makeRoom()
        room.addUser(makeUser("u1"))
        room.adjust("u1", "nonexistent", 5)  // not registered — silently ignored
        assertTrue(room.currentState().first { it.id == "u1" }.customStats.isEmpty())
    }

    @Test
    fun `currentState reflects multiple users`() = runTest {
        val room = makeRoom()
        room.addUser(makeUser("u1", "Alice", life = 20u))
        room.addUser(makeUser("u2", "Bob",   life = 20u))
        room.adjust("u1", "life", 2)
        room.adjust("u2", "life", 1)
        val state = room.currentState()
        assertEquals(22u, state.first { it.id == "u1" }.life)
        assertEquals(21u, state.first { it.id == "u2" }.life)
    }
}
