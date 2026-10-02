package com.kregosh.mtglifetracker.domain

import kotlin.random.Random

/**
 * The storage operations session-code allocation needs. Each backend implements
 * these primitives; the allocation policy itself lives in [SessionCodes].
 */
interface CodeRegistry {
    /** The session holding [code], or null if the code is free. */
    suspend fun sessionFor(code: String): String?

    /** When [sessionId] was created, or null if that session no longer exists. */
    suspend fun sessionCreatedAt(sessionId: String): Long?

    suspend fun deleteSession(sessionId: String): Boolean
    suspend fun releaseCode(code: String): Boolean

    /** Claims [code] for [sessionId]; false if someone else got there first. */
    suspend fun claimCode(code: String, sessionId: String): Boolean
}

object SessionCodes {
    // 32^8 ≈ 10^12 codes: guessing a live one is impractical.
    const val LENGTH   = 8
    const val ATTEMPTS = 5
    const val ABANDONED_AFTER_MS = 24 * 60 * 60 * 1000L

    // No 0/O or 1/I, so codes survive being read aloud or typed from a screen.
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generate(random: Random = Random.Default): String =
        (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    fun isAbandoned(createdAt: Long, now: Long): Boolean = now - createdAt >= ABANDONED_AFTER_MS

    /**
     * Finds a free code and claims it for [sessionId]. A code still held by a session
     * that everyone abandoned more than a day ago is reclaimed. Null if every attempt
     * collided with a live session.
     */
    suspend fun allocate(
        registry : CodeRegistry,
        sessionId: String,
        now      : Long,
        random   : Random = Random.Default,
    ): String? {
        repeat(ATTEMPTS) {
            val code   = generate(random)
            val holder = registry.sessionFor(code)
            val free   = holder == null || releaseIfAbandoned(registry, code, holder, now)
            if (free && registry.claimCode(code, sessionId)) return code
        }
        return null
    }

    private suspend fun releaseIfAbandoned(registry: CodeRegistry, code: String, holder: String, now: Long): Boolean {
        val createdAt = registry.sessionCreatedAt(holder)
        if (createdAt != null) {
            if (!isAbandoned(createdAt, now)) return false
            if (!registry.deleteSession(holder)) return false
        }
        return registry.releaseCode(code)
    }
}
