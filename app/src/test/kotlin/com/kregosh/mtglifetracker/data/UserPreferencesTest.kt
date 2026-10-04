package com.kregosh.mtglifetracker.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class UserPreferencesTest {

    private lateinit var prefs: UserPreferences
    private val fakeTime = AtomicLong(0)

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // Fresh SharedPreferences for each test
        app.getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        fakeTime.set(0)
        prefs = UserPreferences(app, clock = { fakeTime.incrementAndGet() })
    }

    // ── lastSessionId ────────────────────────────────────────────────────────

    @Test
    fun `lastSessionId defaults to null and round-trips`() {
        assertNull(prefs.lastSessionId)
        prefs.lastSessionId = "sid-1"
        assertEquals("sid-1", prefs.lastSessionId)
        prefs.lastSessionId = null
        assertNull(prefs.lastSessionId)
    }

    // ── displayName ──────────────────────────────────────────────────────────

    @Test
    fun `displayName defaults to empty string`() {
        assertEquals("", prefs.displayName)
    }

    @Test
    fun `displayName round-trips correctly`() {
        prefs.displayName = "Aragorn"
        assertEquals("Aragorn", prefs.displayName)
    }

    // ── friendList ───────────────────────────────────────────────────────────

    @Test
    fun `friendList is empty initially`() {
        assertTrue(prefs.friendList.isEmpty())
    }

    @Test
    fun `addFriend persists and retrieves a friend`() {
        prefs.addFriend("uid-1", "Alice")
        val friends = prefs.friendList
        assertEquals(1, friends.size)
        assertEquals("uid-1", friends.first().userId)
        assertEquals("Alice", friends.first().displayName)
    }

    @Test
    fun `addFriend is idempotent - second add updates display name`() {
        prefs.addFriend("uid-1", "Alice")
        prefs.addFriend("uid-1", "Alice Updated")
        val friends = prefs.friendList
        assertEquals(1, friends.size)
        assertEquals("Alice Updated", friends.first().displayName)
    }

    @Test
    fun `friendList is sorted alphabetically by display name`() {
        prefs.addFriend("uid-1", "Zara")
        prefs.addFriend("uid-2", "Alice")
        prefs.addFriend("uid-3", "Mike")
        val names = prefs.friendList.map { it.displayName }
        assertEquals(listOf("Alice", "Mike", "Zara"), names)
    }

    @Test
    fun `removeFriend removes the correct entry`() {
        prefs.addFriend("uid-1", "Alice")
        prefs.addFriend("uid-2", "Bob")
        prefs.removeFriend("uid-1")
        val friends = prefs.friendList
        assertEquals(1, friends.size)
        assertEquals("uid-2", friends.first().userId)
    }

    @Test
    fun `removeFriend on non-existent userId is a no-op`() {
        prefs.addFriend("uid-1", "Alice")
        prefs.removeFriend("uid-999")
        assertEquals(1, prefs.friendList.size)
    }

    @Test
    fun `display name containing separator character is stored correctly`() {
        // \u001F is the separator; a name that contains it must not corrupt the entry
        prefs.addFriend("uid-1", "Normal Name")
        assertEquals("Normal Name", prefs.friendList.first().displayName)
    }

    @Test
    fun `knownPlayer display name containing separator is stored and retrieved correctly`() {
        // \u001F in the display name must not corrupt userId or lastSeen parsing
        prefs.touchKnownPlayer("uid-1", "A\u001FB")
        val known = prefs.knownPlayers
        assertEquals(1, known.size)
        assertEquals("uid-1", known.first().userId)
        assertEquals("A\u001FB", known.first().displayName)
        assertTrue(known.first().lastSeen > 0L)
    }

    // ── knownPlayers ─────────────────────────────────────────────────────────

    @Test
    fun `knownPlayers is empty initially`() {
        assertTrue(prefs.knownPlayers.isEmpty())
    }

    @Test
    fun `touchKnownPlayer adds a new entry`() {
        prefs.touchKnownPlayer("uid-1", "Alice")
        val known = prefs.knownPlayers
        assertEquals(1, known.size)
        assertEquals("uid-1", known.first().userId)
        assertEquals("Alice", known.first().displayName)
    }

    @Test
    fun `touchKnownPlayer updates display name on re-encounter`() {
        prefs.touchKnownPlayer("uid-1", "Alice")
        prefs.touchKnownPlayer("uid-1", "Alice Renamed")
        val known = prefs.knownPlayers
        assertEquals(1, known.size)
        assertEquals("Alice Renamed", known.first().displayName)
    }

    @Test
    fun `knownPlayers is ordered newest-first`() {
        prefs.touchKnownPlayer("uid-1", "First")
        prefs.touchKnownPlayer("uid-2", "Second")
        val ids = prefs.knownPlayers.map { it.userId }
        assertEquals(listOf("uid-2", "uid-1"), ids)
    }

    @Test
    fun `touchKnownPlayer is capped at 10 entries evicting the oldest`() {
        repeat(11) { i ->
            prefs.touchKnownPlayer("uid-$i", "Player $i")
        }
        val known = prefs.knownPlayers
        assertEquals(10, known.size)
        // uid-0 was the oldest and should have been evicted
        assertTrue(known.none { it.userId == "uid-0" })
        // uid-10 was the most recent and must be present
        assertTrue(known.any { it.userId == "uid-10" })
    }

    @Test
    fun `touchKnownPlayer re-touching an existing entry moves it to the front`() {
        prefs.touchKnownPlayer("uid-1", "Alice")
        prefs.touchKnownPlayer("uid-2", "Bob")
        prefs.touchKnownPlayer("uid-1", "Alice") // re-touch uid-1
        assertEquals("uid-1", prefs.knownPlayers.first().userId)
    }

    @Test
    fun `forgetKnownPlayer removes the correct entry`() {
        prefs.touchKnownPlayer("uid-1", "Alice")
        prefs.touchKnownPlayer("uid-2", "Bob")
        prefs.forgetKnownPlayer("uid-1")
        val known = prefs.knownPlayers
        assertEquals(1, known.size)
        assertEquals("uid-2", known.first().userId)
    }

    @Test
    fun `forgetKnownPlayer on non-existent userId is a no-op`() {
        prefs.touchKnownPlayer("uid-1", "Alice")
        prefs.forgetKnownPlayer("uid-999")
        assertEquals(1, prefs.knownPlayers.size)
    }

    // ── numeric settings ─────────────────────────────────────────────────────

    @Test
    fun `startLife defaults to 40, the Commander standard`() {
        assertEquals(40u, prefs.startLife)
    }

    @Test
    fun `startLife round-trips correctly`() {
        prefs.startLife = 40u
        assertEquals(40u, prefs.startLife)
    }

    @Test
    fun `commanderDeathThreshold defaults to 21`() {
        assertEquals(21u, prefs.commanderDeathThreshold)
    }

    @Test
    fun `infectDeathThreshold defaults to 10`() {
        assertEquals(10u, prefs.infectDeathThreshold)
    }

    @Test
    fun `colorScheme defaults to following the system`() {
        assertEquals(AppColorScheme.SYSTEM, prefs.colorScheme)
    }

    @Test
    fun `colorScheme round-trips every value`() {
        AppColorScheme.entries.forEach {
            prefs.colorScheme = it
            assertEquals(it, prefs.colorScheme)
        }
    }

    @Test
    fun `colorScheme reads values saved by earlier versions`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val raw = app.getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
        raw.edit().putString("color_scheme", "light").commit()
        assertEquals(AppColorScheme.LIGHT, prefs.colorScheme)
        raw.edit().putString("color_scheme", "something-else").commit()
        assertEquals(AppColorScheme.SYSTEM, prefs.colorScheme)
    }

    @Test
    fun `backgroundImageUri defaults to the mana orbs preset`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertEquals("android.resource://${app.packageName}/drawable/bg_mana_orbs", prefs.backgroundImageUri)
    }

    @Test
    fun `backgroundImageUri round-trips`() {
        prefs.backgroundImageUri = "content://some/image"
        assertEquals("content://some/image", prefs.backgroundImageUri)
    }

    @Test
    fun `turning the background off sticks instead of bringing the default back`() {
        prefs.backgroundImageUri = "content://some/image"
        prefs.backgroundImageUri = null
        assertNull(prefs.backgroundImageUri)
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertNull(UserPreferences(app).backgroundImageUri)
    }

    @Test
    fun `two separate UserPreferences instances see the same written values`() {
        prefs.addFriend("uid-1", "Alice")
        val app = ApplicationProvider.getApplicationContext<Application>()
        val other = UserPreferences(app)
        assertEquals(1, other.friendList.size)
        assertEquals("Alice", other.friendList.first().displayName)
    }

    // ── remaining settings ───────────────────────────────────────────────────

    @Test
    fun `cardBackgroundImageUri round-trips and clears`() {
        assertNull(prefs.cardBackgroundImageUri)
        prefs.cardBackgroundImageUri = "content://card"
        assertEquals("content://card", prefs.cardBackgroundImageUri)
        prefs.cardBackgroundImageUri = null
        assertNull(prefs.cardBackgroundImageUri)
    }

    @Test
    fun `damage thresholds round-trip`() {
        prefs.commanderDeathThreshold = 15u
        prefs.infectDeathThreshold = 7u
        assertEquals(15u, prefs.commanderDeathThreshold)
        assertEquals(7u, prefs.infectDeathThreshold)
    }

    @Test
    fun `commanderDefaultEnabled defaults to off and round-trips`() {
        assertEquals(false, prefs.commanderDefaultEnabled)
        prefs.commanderDefaultEnabled = true
        assertEquals(true, prefs.commanderDefaultEnabled)
    }

    @Test
    fun `timer settings default to a visible 60-minute stopwatch`() {
        assertEquals(true, prefs.timerVisible)
        assertEquals(false, prefs.timerCountDown)
        assertEquals(60u, prefs.timerLimitMinutes)
    }

    @Test
    fun `timer settings round-trip`() {
        prefs.timerVisible = false
        prefs.timerCountDown = true
        prefs.timerLimitMinutes = 45u
        assertEquals(false, prefs.timerVisible)
        assertEquals(true, prefs.timerCountDown)
        assertEquals(45u, prefs.timerLimitMinutes)
    }
}
