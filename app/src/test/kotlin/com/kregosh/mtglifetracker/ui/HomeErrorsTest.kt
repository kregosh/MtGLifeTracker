package com.kregosh.mtglifetracker.ui

import com.kregosh.mtglifetracker.viewmodel.HomeError
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeErrorsTest {

    @Test
    fun `each error explains itself`() {
        assertEquals(
            "No session with that code. Check the code and try again.",
            HomeError.SessionNotFound.message(),
        )
        assertEquals("You are no longer in that session.", HomeError.RemovedFromSession.message())
        assertEquals("This session is full (4 players).", HomeError.SessionFull(4).message())
    }

    @Test
    fun `failures include what went wrong`() {
        assertEquals("Couldn't create a session: offline", HomeError.CreateFailed("offline").message())
        assertEquals("Couldn't join the session: timeout", HomeError.JoinFailed("timeout").message())
    }

    @Test
    fun `failures without a reason say so`() {
        assertEquals("Couldn't join the session: unknown error", HomeError.JoinFailed(null).message())
        assertEquals("Couldn't create a session: unknown error", HomeError.CreateFailed(null).message())
    }
}
