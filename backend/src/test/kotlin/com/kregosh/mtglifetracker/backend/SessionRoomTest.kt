package com.kregosh.mtglifetracker.backend

import com.kregosh.mtglifetracker.backend.session.ConnectedUser
import com.kregosh.mtglifetracker.backend.session.SessionRoom
import io.mockk.mockk
import io.ktor.websocket.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionRoomTest {

    private fun makeRoom() = SessionRoom("session-1", "ABCDEF")

    private fun makeUser(id: String, name: String = "Player") =
        ConnectedUser(
            userId      = id,
            displayName = name,
            socket      = mockk(relaxed = true),
            value       = 0u,
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
    fun `increment increases value`() = runTest {
        val room = makeRoom()
        room.addUser(makeUser("u1"))
        room.increment("u1")
        assertEquals(1u, room.currentState().first { it.id == "u1" }.value)
    }

    @Test
    fun `decrement on zero stays at zero`() = runTest {
        val room = makeRoom()
        room.addUser(makeUser("u1"))
        room.decrement("u1")
        assertEquals(0u, room.currentState().first { it.id == "u1" }.value)
    }

    @Test
    fun `increment at MAX_VALUE stays at MAX_VALUE`() = runTest {
        val room = makeRoom()
        val user = makeUser("u1").also { it.value = UInt.MAX_VALUE }
        room.addUser(user)
        room.increment("u1")
        assertEquals(UInt.MAX_VALUE, room.currentState().first { it.id == "u1" }.value)
    }

    @Test
    fun `increment and decrement on unknown userId is no-op`() = runTest {
        val room = makeRoom()
        room.increment("nobody")
        room.decrement("nobody")
        assertEquals(0, room.userCount())
    }

    @Test
    fun `currentState reflects multiple users`() = runTest {
        val room = makeRoom()
        room.addUser(makeUser("u1", "Alice"))
        room.addUser(makeUser("u2", "Bob"))
        room.increment("u1")
        room.increment("u1")
        room.increment("u2")
        val state = room.currentState()
        assertEquals(2u, state.first { it.id == "u1" }.value)
        assertEquals(1u, state.first { it.id == "u2" }.value)
    }
}
