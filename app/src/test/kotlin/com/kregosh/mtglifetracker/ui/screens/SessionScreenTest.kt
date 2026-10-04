package com.kregosh.mtglifetracker.ui.screens

import com.kregosh.mtglifetracker.ui.setPlatformContent
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kregosh.mtglifetracker.data.AppColorScheme
import androidx.compose.ui.test.assertIsNotEnabled
import com.kregosh.mtglifetracker.data.UserPrefs
import com.kregosh.mtglifetracker.network.ConnectionState
import com.kregosh.mtglifetracker.network.SessionApi
import com.kregosh.mtglifetracker.network.SessionConnection
import com.kregosh.mtglifetracker.shared.CreateSessionResponse
import com.kregosh.mtglifetracker.shared.ServerMessage
import com.kregosh.mtglifetracker.shared.SessionSettings
import com.kregosh.mtglifetracker.shared.UserState
import com.kregosh.mtglifetracker.viewmodel.Screen
import com.kregosh.mtglifetracker.viewmodel.SessionViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class SessionScreenTest {

    @get:Rule val compose = createComposeRule()

    private val messages   = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 16)
    private val connection = mockk<SessionConnection>(relaxed = true) {
        every { this@mockk.messages }        returns this@SessionScreenTest.messages
        every { connectionState }            returns MutableStateFlow(ConnectionState.Connected)
    }
    private val prefs = mockk<UserPrefs>(relaxed = true) {
        every { displayName }             returns "Alice"
        every { backgroundImageUri }      returns null
        every { cardBackgroundImageUri }  returns null
        every { startLife }               returns 20u
        every { commanderDeathThreshold } returns 21u
        every { infectDeathThreshold }    returns 10u
        every { colorScheme }             returns AppColorScheme.DARK
        every { commanderDefaultEnabled } returns false
        every { timerVisible }            returns false
        every { timerCountDown }          returns false
        every { timerLimitMinutes }       returns 60u
        every { knownPlayers }            returns emptyList()
        every { friendList }              returns emptyList()
        every { lastSessionId }           returns null
    }
    private val api = mockk<SessionApi>(relaxed = true) {
        coEvery { signIn() }                    returns "me"
        coEvery { createSession(any(), any()) } returns CreateSessionResponse("sid", "ABCD2345")
    }

    private lateinit var vm: SessionViewModel

    @Before
    fun setUp() {
        vm = SessionViewModel(prefs, api, { _, _, _, _ -> connection })
        vm.createSession()
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun showSession(hostUserId: String?) {
        messages.tryEmit(ServerMessage.State(
            users      = listOf(UserState("me", "Alice"), UserState("bob", "Bob")),
            hostUserId = hostUserId,
        ))
        idle()
        compose.setPlatformContent { SessionScreen(vm) }
    }

    @Test
    fun `the host can start a new game from the menu`() {
        showSession(hostUserId = "me")
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("New game").performClick()
        compose.onNodeWithText("Start a new game?").assertExists()
        compose.onNodeWithText("New game").performClick()

        verify { connection.startNewGame() }
    }

    @Test
    fun `other players have no new-game option`() {
        showSession(hostUserId = "bob")
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Game rules").assertExists()
        compose.onNodeWithText("New game").assertDoesNotExist()
    }

    @Test
    fun `only the host sees remove buttons on other players`() {
        showSession(hostUserId = "me")
        compose.onNodeWithContentDescription("Remove Bob from the session").assertExists()
    }

    @Test
    fun `leaving asks first and staying keeps the session`() {
        showSession(hostUserId = "me")
        compose.onNodeWithContentDescription("Leave session").performClick()
        compose.onNodeWithText("Leave session?").assertExists()
        compose.onNodeWithText("Stay").performClick()

        compose.onNodeWithText("Leave session?").assertDoesNotExist()
        assertEquals(Screen.Session("sid"), vm.screen.value)
        verify(exactly = 0) { connection.close(any()) }
    }

    @Test
    fun `confirming leave removes the player and returns home`() {
        showSession(hostUserId = "bob")
        compose.onNodeWithContentDescription("Leave session").performClick()
        compose.onNodeWithText("Your life total and stats will be removed from this game.").assertExists()
        compose.onNodeWithText("Leave").performClick()

        assertEquals(Screen.Home, vm.screen.value)
        verify { connection.close(true) }
        verify(exactly = 0) { connection.handOverHost(any()) }
    }

    @Test
    fun `a leaving host is told who takes over, and hands hosting on`() {
        showSession(hostUserId = "me")
        compose.onNodeWithContentDescription("Leave session").performClick()
        compose.onNodeWithText("Your life total and stats will be removed from this game, and Bob becomes the host.").assertExists()
        compose.onNodeWithText("Leave").performClick()

        assertEquals(Screen.Home, vm.screen.value)
        verify { connection.handOverHost("bob") }
        verify { connection.close(true) }
    }

    @Test
    fun `a player can switch to watching from the menu after confirming`() {
        showSession(hostUserId = "bob")
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Watch instead").performClick()
        compose.onNodeWithText("Watch instead of playing?").assertExists()
        compose.onNodeWithText("Watch instead").performClick()

        verify { connection.watch() }
        compose.onNodeWithText("You are watching").assertExists()
        compose.onNodeWithText("Stats").assertDoesNotExist()
    }

    @Test
    fun `an observer can join again unless the game is full`() {
        showSession(hostUserId = "bob")
        vm.watchInstead()
        messages.tryEmit(ServerMessage.State(
            users    = listOf(UserState("bob", "Bob")),
            settings = SessionSettings(maxPlayers = 1),
        ))
        idle()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Join as a player (game full)").assertIsNotEnabled()

        messages.tryEmit(ServerMessage.State(
            users    = listOf(UserState("bob", "Bob")),
            settings = SessionSettings(maxPlayers = 2),
        ))
        idle()
        compose.onNodeWithText("Join as a player").performClick()
        verify { connection.play(any()) }
    }
}
