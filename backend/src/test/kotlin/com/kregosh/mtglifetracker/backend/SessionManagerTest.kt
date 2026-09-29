package com.kregosh.mtglifetracker.backend

import com.kregosh.mtglifetracker.backend.session.ConnectedUser
import com.kregosh.mtglifetracker.backend.session.SessionManager
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SessionManagerTest {

    @Test
    fun `create returns a room with non-blank id and code`() {
        val mgr  = SessionManager()
        val room = mgr.create()
        assert(room.sessionId.isNotBlank())
        assert(room.code.isNotBlank())
    }

    @Test
    fun `getById returns the room that was created`() {
        val mgr  = SessionManager()
        val room = mgr.create()
        assertEquals(room, mgr.getById(room.sessionId))
    }

    @Test
    fun `getByCode returns the room by uppercase code`() {
        val mgr  = SessionManager()
        val room = mgr.create()
        assertEquals(room, mgr.getByCode(room.code))
        assertEquals(room, mgr.getByCode(room.code.lowercase()))
    }

    @Test
    fun `getById returns null for unknown id`() {
        assertNull(SessionManager().getById("nonexistent"))
    }

    @Test
    fun `getByCode returns null for unknown code`() {
        assertNull(SessionManager().getByCode("ZZZZZZ"))
    }

    @Test
    fun `pruneIfEmpty removes empty room`() {
        val mgr  = SessionManager()
        val room = mgr.create()
        mgr.pruneIfEmpty(room)
        assertNull(mgr.getById(room.sessionId))
        assertNull(mgr.getByCode(room.code))
    }

    @Test
    fun `pruneIfEmpty keeps non-empty room`() = kotlinx.coroutines.test.runTest {
        val mgr  = SessionManager()
        val room = mgr.create()
        room.addUser(ConnectedUser("u1", "Alice", mockk(relaxed = true)))
        mgr.pruneIfEmpty(room)
        assertNotNull(mgr.getById(room.sessionId))
    }

    @Test
    fun `each created session has a unique code`() {
        val mgr   = SessionManager()
        val codes = (1..20).map { mgr.create().code }.toSet()
        assertEquals(20, codes.size)
    }
}
