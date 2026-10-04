package com.kregosh.mtglifetracker

import android.app.Notification
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SessionServiceTest {

    private fun started(code: String?): SessionService {
        val intent = Intent(ApplicationProvider.getApplicationContext(), SessionService::class.java)
        code?.let { intent.putExtra(SessionService.EXTRA_CODE, it) }
        return Robolectric.buildService(SessionService::class.java, intent).create().startCommand(0, 1).get()
    }

    private fun SessionService.notification(): Notification =
        assertNotNull(shadowOf(this).lastForegroundNotification)

    @Test
    fun `it runs in the foreground with the session code in its notification`() {
        val service = started("ABCD2345")
        assertEquals(SessionService.NOTIFICATION_ID, shadowOf(service).lastForegroundNotificationId)
        val text = service.notification().extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue("ABCD2345" in text, text)
    }

    @Test
    fun `without a code yet it just offers to return`() {
        val text = started(null).notification().extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertEquals("Tap to return to the game", text)
    }

    @Test
    fun `swiping the app away stops it`() {
        val service = started("ABCD2345")
        service.onTaskRemoved(null)
        assertTrue(shadowOf(service).isStoppedBySelf)
    }
}
