package com.kregosh.mtglifetracker.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InviteLinksTest {

    @Test
    fun `invite url round-trips`() {
        assertEquals("ABCD2345", parseInviteCode(inviteUrl("ABCD2345")))
    }

    @Test
    fun `app scheme links are understood`() {
        assertEquals("ABC123", parseInviteCode("mtgtracker://join/ABC123"))
        assertEquals("ABCD2345", parseInviteCode("mtgtracker://join/abcd2345"))
    }

    @Test
    fun `invite page links with extra parameters are understood`() {
        assertEquals("ABCD2345", parseInviteCode("${INVITE_PAGE_URL}?utm=chat&code=ABCD2345"))
        assertEquals("ABCD2345", parseInviteCode("https://kregosh.github.io/MtGLifeTracker/join?code=ABCD2345"))
    }

    @Test
    fun `other links and malformed codes are rejected`() {
        assertNull(parseInviteCode("https://evil.example/MtGLifeTracker/join/?code=ABCD2345"))
        assertNull(parseInviteCode("https://kregosh.github.io/MtGLifeTracker/joinus?code=ABCD2345"))
        assertNull(parseInviteCode("mtgtracker://join/"))
        assertNull(parseInviteCode("mtgtracker://join/AB"))
        assertNull(parseInviteCode("mtgtracker://join/ABC-123"))
        assertNull(parseInviteCode("${INVITE_PAGE_URL}?code=<script>"))
        assertNull(parseInviteCode("not a url"))
        // Lookalike hosts: only the real host counts, whatever comes before an @.
        assertNull(parseInviteCode("https://kregosh.github.io@evil.example/MtGLifeTracker/join/?code=ABCD2345"))
        assertNull(parseInviteCode("https://kregosh.github.io.evil.example/MtGLifeTracker/join/?code=ABCD2345"))
        assertNull(parseInviteCode("http://kregosh.github.io/MtGLifeTracker/join/?code=ABCD2345"))
    }
}
