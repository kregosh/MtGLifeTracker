package com.kregosh.mtglifetracker.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kregosh.mtglifetracker.viewmodel.HomeError
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class HomeErrorsTest {

    private val res = ApplicationProvider.getApplicationContext<Application>().resources

    @Test
    fun `each error explains itself`() {
        assertEquals(
            "No session with that code. Check the code and try again.",
            HomeError.SessionNotFound.message(res),
        )
        assertEquals("You are no longer in that session.", HomeError.RemovedFromSession.message(res))
        assertEquals("This session is full (4 players).", HomeError.SessionFull(4).message(res))
    }

    @Test
    fun `failures include what went wrong`() {
        assertEquals("Couldn't create a session: offline", HomeError.CreateFailed("offline").message(res))
        assertEquals("Couldn't join the session: timeout", HomeError.JoinFailed("timeout").message(res))
    }

    @Test
    fun `failures without a reason say so`() {
        assertEquals("Couldn't join the session: unknown error", HomeError.JoinFailed(null).message(res))
        assertEquals("Couldn't create a session: unknown error", HomeError.CreateFailed(null).message(res))
    }
}
