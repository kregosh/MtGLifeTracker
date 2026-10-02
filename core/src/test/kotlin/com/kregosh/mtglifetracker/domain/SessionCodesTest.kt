package com.kregosh.mtglifetracker.domain

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionCodesTest {

    private val day = SessionCodes.ABANDONED_AFTER_MS
    private val now = 10 * day

    /** In-memory backend: codes → session IDs, session IDs → creation times. */
    private class FakeRegistry(
        val codes   : MutableMap<String, String> = mutableMapOf(),
        val sessions: MutableMap<String, Long>   = mutableMapOf(),
        val failDeletes: Boolean = false,
        val claimFails : Int     = 0,
    ) : CodeRegistry {
        var claimAttempts = 0
        override suspend fun sessionFor(code: String) = codes[code]
        override suspend fun sessionCreatedAt(sessionId: String) = sessions[sessionId]
        override suspend fun deleteSession(sessionId: String) = !failDeletes && sessions.remove(sessionId) != null
        override suspend fun releaseCode(code: String) = codes.remove(code) != null
        override suspend fun claimCode(code: String, sessionId: String): Boolean {
            claimAttempts++
            if (claimAttempts <= claimFails || code in codes) return false
            codes[code] = sessionId
            return true
        }
    }

    /** The codes [SessionCodes.generate] will draw, in order, for a given seed. */
    private fun codesFor(seed: Int, count: Int): List<String> {
        val random = Random(seed)
        return List(count) { SessionCodes.generate(random) }
    }

    @Test
    fun `codes have the right length and only unambiguous characters`() {
        val random = Random(1)
        repeat(500) {
            val code = SessionCodes.generate(random)
            assertEquals(SessionCodes.LENGTH, code.length)
            assertTrue(code.all { it in SessionCodes.ALPHABET }, code)
        }
        listOf('0', 'O', '1', 'I').forEach { assertFalse(it in SessionCodes.ALPHABET) }
    }

    @Test
    fun `a session is abandoned after exactly one day`() {
        assertFalse(SessionCodes.isAbandoned(createdAt = now - day + 1, now = now))
        assertTrue(SessionCodes.isAbandoned(createdAt = now - day, now = now))
    }

    @Test
    fun `a free code is claimed on the first try`() = runTest {
        val registry = FakeRegistry()
        val code = SessionCodes.allocate(registry, "new", now, Random(7))

        assertEquals(codesFor(7, 1).single(), code)
        assertEquals("new", registry.codes[code])
    }

    @Test
    fun `a code held by a live session is skipped`() = runTest {
        val (taken, next) = codesFor(7, 2)
        val registry = FakeRegistry(codes = mutableMapOf(taken to "live"), sessions = mutableMapOf("live" to now))

        assertEquals(next, SessionCodes.allocate(registry, "new", now, Random(7)))
        assertEquals("live", registry.codes[taken])
        assertTrue("live" in registry.sessions)
    }

    @Test
    fun `a code held by an abandoned session is reclaimed`() = runTest {
        val taken = codesFor(7, 1).single()
        val registry = FakeRegistry(codes = mutableMapOf(taken to "old"), sessions = mutableMapOf("old" to now - 2 * day))

        assertEquals(taken, SessionCodes.allocate(registry, "new", now, Random(7)))
        assertEquals("new", registry.codes[taken])
        assertFalse("old" in registry.sessions)
    }

    @Test
    fun `a code pointing at a session that no longer exists is reclaimed`() = runTest {
        val taken = codesFor(7, 1).single()
        val registry = FakeRegistry(codes = mutableMapOf(taken to "gone"))

        assertEquals(taken, SessionCodes.allocate(registry, "new", now, Random(7)))
    }

    @Test
    fun `an abandoned session that can't be deleted keeps its code`() = runTest {
        val (taken, next) = codesFor(7, 2)
        val registry = FakeRegistry(
            codes = mutableMapOf(taken to "old"), sessions = mutableMapOf("old" to 0L), failDeletes = true,
        )

        assertEquals(next, SessionCodes.allocate(registry, "new", now, Random(7)))
        assertEquals("old", registry.codes[taken])
    }

    @Test
    fun `losing a claim race moves on to another code`() = runTest {
        val registry = FakeRegistry(claimFails = 2)
        val code = SessionCodes.allocate(registry, "new", now, Random(7))

        assertEquals(codesFor(7, 3).last(), code)
        assertEquals(3, registry.claimAttempts)
    }

    @Test
    fun `gives up after the allowed attempts`() = runTest {
        val taken = codesFor(7, SessionCodes.ATTEMPTS)
        val registry = FakeRegistry(
            codes    = taken.associateWith { "live" }.toMutableMap(),
            sessions = mutableMapOf("live" to now),
        )

        assertNull(SessionCodes.allocate(registry, "new", now, Random(7)))
        assertEquals(0, registry.claimAttempts)
    }

    @Test
    fun `default randomness still allocates`() = runTest {
        assertNotNull(SessionCodes.allocate(FakeRegistry(), "new", now))
    }
}
