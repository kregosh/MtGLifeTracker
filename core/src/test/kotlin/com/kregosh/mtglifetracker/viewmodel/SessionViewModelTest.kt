package com.kregosh.mtglifetracker.viewmodel

import com.kregosh.mtglifetracker.data.AppColorScheme
import com.kregosh.mtglifetracker.data.Friend
import com.kregosh.mtglifetracker.data.KnownPlayer
import com.kregosh.mtglifetracker.data.UserPrefs
import com.kregosh.mtglifetracker.network.SessionApi
import com.kregosh.mtglifetracker.network.SessionConnection
import com.kregosh.mtglifetracker.network.SessionNotFoundException
import com.kregosh.mtglifetracker.network.ConnectionState
import io.mockk.coVerify
import com.kregosh.mtglifetracker.shared.CreateSessionResponse
import com.kregosh.mtglifetracker.shared.ServerMessage
import com.kregosh.mtglifetracker.shared.SessionInfoResponse
import com.kregosh.mtglifetracker.shared.COMMANDER_STAT
import com.kregosh.mtglifetracker.shared.DAY_NIGHT_GLOBAL
import com.kregosh.mtglifetracker.shared.POISON_STAT
import com.kregosh.mtglifetracker.shared.SessionSettings
import com.kregosh.mtglifetracker.shared.StatType
import com.kregosh.mtglifetracker.shared.UserState
import com.kregosh.mtglifetracker.viewmodel.RESERVED_STAT_NAMES
import io.mockk.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val prefs      = mockk<UserPrefs>(relaxed = true)
    private val api        = mockk<SessionApi>(relaxed = true)
    private val serverMessages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 16)
    private val connectionStateFlow    = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    private val ws         = mockk<SessionConnection>(relaxed = true) {
        every { messages }        returns serverMessages
        every { connectionState } returns connectionStateFlow
    }
    private val connectionFactory  = mockk<(String, String, String, UInt) -> SessionConnection>()

    private fun makeVm() = SessionViewModel(prefs, api, connectionFactory, testDispatcher.scheduler.timeSource)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { api.signIn() }    returns "test-user-id"
        every { prefs.displayName } returns "Test Player"
        every { prefs.backgroundImageUri }      returns null
        every { prefs.cardBackgroundImageUri }  returns null
        every { prefs.startLife }               returns 20u
        every { prefs.commanderDeathThreshold } returns 21u
        every { prefs.infectDeathThreshold }    returns 10u
        every { prefs.colorScheme }             returns AppColorScheme.DARK
        every { prefs.commanderDefaultEnabled } returns false
        every { prefs.timerVisible }            returns true
        every { prefs.timerCountDown }          returns false
        every { prefs.timerLimitMinutes }       returns 60u
        every { prefs.knownPlayers }            returns emptyList()
        every { prefs.friendList }              returns emptyList()
        every { prefs.lastSessionId }           returns null
        every { connectionFactory(any(), any(), any(), any()) } returns ws
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial screen is Home`() {
        assertEquals(Screen.Home, makeVm().screen.value)
    }

    @Test
    fun `createSession on success navigates to Session screen`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "ABC123")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        assertIs<Screen.Session>(vm.screen.value)
        assertEquals("sid-1", (vm.screen.value as Screen.Session).sessionId)
        verify { ws.connect() }
    }

    @Test
    fun `createSession on failure sets homeError`() = runTest {
        coEvery { api.createSession(any(), any()) } throws RuntimeException("network error")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        assertEquals(Screen.Home, vm.screen.value)
        assertEquals(HomeError.CreateFailed("network error"), vm.homeError.value)
    }

    @Test
    fun `joinByCode on success navigates to Session screen`() = runTest {
        coEvery { api.getSessionByCode("XYZ999") } returns SessionInfoResponse("sid-2", "XYZ999", 0)

        val vm = makeVm()
        vm.joinByCode("xyz999")
        advanceUntilIdle()

        assertIs<Screen.Session>(vm.screen.value)
    }

    @Test
    fun `joinByCode reports network failures as such, not as a missing session`() = runTest {
        coEvery { api.getSessionByCode(any()) } throws RuntimeException("timeout")

        val vm = makeVm()
        vm.joinByCode("ABCDEFGH")
        advanceUntilIdle()

        assertEquals(HomeError.JoinFailed("timeout"), vm.homeError.value)
    }

    @Test
    fun `joinByCode on failure sets homeError`() = runTest {
        coEvery { api.getSessionByCode(any()) } throws SessionNotFoundException("not found")

        val vm = makeVm()
        vm.joinByCode("BADCOD")
        advanceUntilIdle()

        assertEquals(HomeError.SessionNotFound, vm.homeError.value)
    }

    @Test
    fun `State message updates sessionUi users and their counters`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        val users = listOf(UserState("test-user-id", "Test Player", life = 20u, stats = mapOf("energy" to StatType.NUMERIC)))
        serverMessages.emit(ServerMessage.State(users))
        advanceUntilIdle()

        assertEquals(users, vm.sessionUi.value.users)
        assertEquals(mapOf("energy" to StatType.NUMERIC), vm.sessionUi.value.myStats)
    }

    @Test
    fun `leaveSession navigates back to Home`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        vm.leaveSession()

        assertEquals(Screen.Home, vm.screen.value)
        verify { ws.close() }
    }

    @Test
    fun `adjust delegates to websocket after debounce`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        vm.adjust("life", 1)
        advanceTimeBy(500)
        verify { ws.adjust("life", 1) }

        vm.adjust("commander", -1)
        advanceTimeBy(500)
        verify { ws.adjust("commander", -1) }
    }

    @Test
    fun `adjust accumulates rapid taps into a single websocket call`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        repeat(5) { vm.adjust("life", 1) }
        advanceTimeBy(500)
        verify(exactly = 1) { ws.adjust("life", 5) }
    }

    @Test
    fun `adjust shows optimistic state immediately without waiting for server`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        val base = listOf(UserState("test-user-id", "Test Player", life = 20u))
        serverMessages.emit(ServerMessage.State(base))
        advanceUntilIdle()

        vm.adjust("life", -3)
        assertEquals(17u, vm.sessionUi.value.users.first().life)
    }

    @Test
    fun `adjust reapplies pending deltas when server State arrives mid-debounce`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        val base = listOf(UserState("test-user-id", "Test Player", life = 20u))
        serverMessages.emit(ServerMessage.State(base))
        advanceUntilIdle()

        vm.adjust("life", -3)

        serverMessages.emit(ServerMessage.State(base))
        advanceUntilIdle()

        assertEquals(17u, vm.sessionUi.value.users.first().life)
    }

    @Test
    fun `addCustomStat delegates to websocket`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        vm.addCustomStat("Energy", StatType.NUMERIC)
        verify { ws.addCustomStat("Energy", StatType.NUMERIC) }
    }

    @Test
    fun `removeCustomStat delegates to websocket`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        serverMessages.emit(ServerMessage.State(listOf(UserState("test-user-id", "Test Player", stats = mapOf("Energy" to StatType.NUMERIC)))))
        advanceUntilIdle()

        vm.removeCustomStat("Energy")
        verify { ws.removeCustomStat("Energy") }
    }

    @Test
    fun `displayName comes from prefs`() {
        assertEquals("Test Player", makeVm().displayName.value)
    }

    @Test
    fun `stale collectors do not fire after leaveSession`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        vm.leaveSession()
        advanceUntilIdle()

        val staleUsers = listOf(UserState("test-user-id", "Test Player", life = 99u))
        serverMessages.emit(ServerMessage.State(staleUsers))
        advanceUntilIdle()

        assertNotEquals(staleUsers, vm.sessionUi.value.users)
    }

    @Test
    fun `homeLoading is false after createSession completes`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()
        assertFalse(vm.homeLoading.value)
    }

    @Test
    fun `Error message updates error field in sessionUi`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        serverMessages.emit(ServerMessage.Error("Join first"))
        advanceUntilIdle()

        assertEquals("Join first", vm.sessionUi.value.error)
    }

    @Test
    fun `connection state change propagates to sessionUi`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        connectionStateFlow.value = ConnectionState.Connected
        advanceUntilIdle()

        assertEquals(ConnectionState.Connected, vm.sessionUi.value.connectionState)
    }

    @Test
    fun `setDisplayName trims whitespace and persists to prefs`() {
        val vm = makeVm()
        vm.setDisplayName("  Alice  ")
        verify { prefs.displayName = "Alice" }
    }

    @Test
    fun `setBackgroundImage persists uri to prefs and updates flow`() {
        val vm = makeVm()
        vm.setBackgroundImage("content://image/1")
        verify { prefs.backgroundImageUri = "content://image/1" }
        assertEquals("content://image/1", vm.backgroundImageUri.value)
    }

    @Test
    fun `setBackgroundImage null clears prefs and flow`() {
        val vm = makeVm()
        vm.setBackgroundImage(null)
        verify { prefs.backgroundImageUri = null }
        assertNull(vm.backgroundImageUri.value)
    }

    @Test
    fun `handleInviteLink with blank code is a no-op`() = runTest {
        val vm = makeVm()
        vm.handleInviteLink("   ")
        advanceUntilIdle()
        assertEquals(Screen.Home, vm.screen.value)
        coVerify(exactly = 0) { api.getSessionByCode(any()) }
    }

    @Test
    fun `handleInviteLink with non-blank code joins session`() = runTest {
        coEvery { api.getSessionByCode("XYZABC") } returns SessionInfoResponse("sid-3", "XYZABC", 0)
        val vm = makeVm()
        vm.handleInviteLink("xyzabc")
        advanceUntilIdle()
        assertIs<Screen.Session>(vm.screen.value)
    }

    @Test
    fun `a first-time player following an invite names themselves before joining`() = runTest {
        every { prefs.displayName } returns ""
        coEvery { api.getSessionByCode("XYZABC") } returns SessionInfoResponse("sid-3", "XYZABC", 0)
        val vm = makeVm()

        vm.handleInviteLink("xyzabc")
        advanceUntilIdle()
        assertEquals(Screen.Home, vm.screen.value)
        assertEquals("XYZABC", vm.pendingInviteCode.value)
        coVerify(exactly = 0) { api.getSessionByCode(any()) }

        vm.setDisplayName("Dana")
        advanceUntilIdle()
        assertIs<Screen.Session>(vm.screen.value)
        assertNull(vm.pendingInviteCode.value)
        verify { connectionFactory("sid-3", any(), "Dana", any()) }
    }

    @Test
    fun `adjust when no session active is silent no-op`() {
        val vm = makeVm()
        vm.adjust("life", 1)
    }

    @Test
    fun `addCustomStat when no session active is silent no-op`() {
        val vm = makeVm()
        vm.addCustomStat("Energy", StatType.NUMERIC)
    }

    @Test
    fun `rejoin cancels collectors from previous session`() = runTest {
        val ws2Messages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 16)
        val ws2State    = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
        val ws2         = mockk<SessionConnection>(relaxed = true) {
            every { messages }        returns ws2Messages
            every { connectionState } returns ws2State
        }

        coEvery { api.createSession(any(), any()) } returnsMany listOf(
            CreateSessionResponse("sid-1", "CODE01"),
            CreateSessionResponse("sid-2", "CODE02"),
        )
        var callCount = 0
        every { connectionFactory(any(), any(), any(), any()) } answers {
            if (callCount++ == 0) ws else ws2
        }

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()
        vm.createSession()
        advanceUntilIdle()

        val staleUsers = listOf(UserState("test-user-id", "Old Session", life = 5u))
        serverMessages.emit(ServerMessage.State(staleUsers))
        advanceUntilIdle()

        assertNotEquals(staleUsers, vm.sessionUi.value.users)
        verify { ws.close() }
    }

    @Test
    fun `commanderDefaultEnabled auto-adds commander stat when joining`() = runTest {
        every { prefs.commanderDefaultEnabled } returns true
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()
        verify(exactly = 0) { ws.addCustomStat(any(), any()) }  // not before the seat exists

        serverMessages.emit(ServerMessage.State(listOf(UserState("test-user-id", "Test Player"))))
        advanceUntilIdle()
        serverMessages.emit(ServerMessage.State(listOf(UserState("test-user-id", "Test Player"))))
        advanceUntilIdle()

        verify(exactly = 1) { ws.addCustomStat("commander", StatType.NUMERIC) }
        assertTrue(vm.history.value.isEmpty())
    }

    @Test
    fun `commanderDefaultEnabled leaves a seat that already tracks commander damage alone`() = runTest {
        every { prefs.commanderDefaultEnabled } returns true
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()
        serverMessages.emit(ServerMessage.State(listOf(
            UserState("test-user-id", "Test Player", stats = mapOf(COMMANDER_STAT to StatType.NUMERIC)))))
        advanceUntilIdle()

        verify(exactly = 0) { ws.addCustomStat(any(), any()) }
    }

    @Test
    fun `globalStats are updated from State message`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        serverMessages.emit(ServerMessage.State(emptyList(), globalStats = mapOf("daynight" to 1u)))
        advanceUntilIdle()

        assertEquals(1u, vm.sessionUi.value.globalStats["daynight"])
    }

    @Test
    fun `toggleGlobal flips global stat value`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        // seed daynight = 0 (day)
        serverMessages.emit(ServerMessage.State(emptyList(), globalStats = mapOf("daynight" to 0u)))
        advanceUntilIdle()

        vm.toggleGlobal("daynight")
        verify { ws.setGlobal("daynight", 1u) }
    }

    @Test
    fun `isDead uses commander in customStats`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        val deadUser = UserState(
            id          = "test-user-id",
            displayName = "Test Player",
            life        = 20u,
            customStats = mapOf(COMMANDER_STAT to 21u),
            stats       = mapOf(COMMANDER_STAT to StatType.NUMERIC),
        )
        serverMessages.emit(ServerMessage.State(listOf(deadUser)))
        advanceUntilIdle()

        val uiState = vm.sessionUi.value
        assertTrue(deadUser.isDead(uiState))
    }

    // ── timer tests ──────────────────────────────────────────────────────────

    @Test
    fun `timer starts not running with zero elapsed`() {
        val vm = makeVm()
        assertFalse(vm.timerRunning.value)
        assertEquals(kotlin.time.Duration.ZERO, vm.timerElapsed.value)
    }

    @Test
    fun `startPauseTimer toggles running state`() = runTest {
        val vm = makeVm()
        vm.startPauseTimer()
        assertTrue(vm.timerRunning.value)
        vm.startPauseTimer()
        assertFalse(vm.timerRunning.value)
    }

    @Test
    fun `elapsed increases while timer is running`() = runTest {
        val vm = makeVm()
        vm.startPauseTimer()
        advanceTimeBy(2_000)
        assertTrue(vm.timerElapsed.value.inWholeSeconds >= 1)
        vm.resetTimer()   // a running stopwatch would keep runTest draining forever
    }

    @Test
    fun `pausing preserves accumulated elapsed`() = runTest {
        val vm = makeVm()
        vm.startPauseTimer()
        advanceTimeBy(3_000)
        vm.startPauseTimer()          // pause
        val snapshot = vm.timerElapsed.value
        advanceTimeBy(2_000)          // time passes while paused
        assertEquals(snapshot, vm.timerElapsed.value)
    }

    @Test
    fun `resetTimer zeroes elapsed and stops running`() = runTest {
        val vm = makeVm()
        vm.startPauseTimer()
        advanceTimeBy(2_000)
        vm.resetTimer()
        assertFalse(vm.timerRunning.value)
        assertEquals(kotlin.time.Duration.ZERO, vm.timerElapsed.value)
    }

    @Test
    fun `leaveSession resets the timer`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()
        vm.startPauseTimer()
        advanceTimeBy(1_500)
        vm.leaveSession()
        assertFalse(vm.timerRunning.value)
        assertEquals(kotlin.time.Duration.ZERO, vm.timerElapsed.value)
    }

    @Test
    fun `setTimerVisible persists and updates flow`() {
        val vm = makeVm()
        vm.setTimerVisible(false)
        verify { prefs.timerVisible = false }
        assertFalse(vm.timerVisible.value)
    }

    @Test
    fun `setTimerCountDown persists, updates flow, and resets timer`() = runTest {
        val vm = makeVm()
        vm.startPauseTimer()
        advanceTimeBy(1_000)
        vm.setTimerCountDown(true)
        verify { prefs.timerCountDown = true }
        assertTrue(vm.timerCountDown.value)
        assertFalse(vm.timerRunning.value)
        assertEquals(kotlin.time.Duration.ZERO, vm.timerElapsed.value)
    }

    @Test
    fun `setTimerLimitMinutes persists, updates flow, and resets timer`() = runTest {
        val vm = makeVm()
        vm.startPauseTimer()
        advanceTimeBy(1_000)
        vm.setTimerLimitMinutes(45u)
        verify { prefs.timerLimitMinutes = 45u }
        assertEquals(45u, vm.timerLimitMinutes.value)
        assertFalse(vm.timerRunning.value)
        assertEquals(kotlin.time.Duration.ZERO, vm.timerElapsed.value)
    }

    @Test
    fun `countdown timer stops at exactly the limit without overshoot`() = runTest {
        every { prefs.timerCountDown }     returns true
        every { prefs.timerLimitMinutes }  returns 1u   // 1-minute limit

        val vm = makeVm()
        vm.startPauseTimer()
        advanceTimeBy(90_000)   // advance well past the 1-minute limit
        advanceUntilIdle()

        assertEquals(1.minutes, vm.timerElapsed.value)
    }

    @Test
    fun `countdown timer is not running after hitting the limit`() = runTest {
        every { prefs.timerCountDown }     returns true
        every { prefs.timerLimitMinutes }  returns 1u

        val vm = makeVm()
        vm.startPauseTimer()
        advanceTimeBy(90_000)
        advanceUntilIdle()

        assertFalse(vm.timerRunning.value)
    }

    @Test
    fun `elapsed is preserved correctly across multiple pause-resume cycles`() = runTest {
        val vm = makeVm()

        vm.startPauseTimer()
        advanceTimeBy(3_000)
        vm.startPauseTimer()   // pause — ~3 s accumulated
        val after1 = vm.timerElapsed.value

        advanceTimeBy(2_000)   // time passes while paused — should not add
        assertEquals(after1, vm.timerElapsed.value)

        vm.startPauseTimer()   // resume
        advanceTimeBy(2_000)
        vm.startPauseTimer()   // pause — ~5 s accumulated

        assertTrue(vm.timerElapsed.value.inWholeSeconds >= 4)
        assertFalse(vm.timerRunning.value)
    }

    @Test
    fun `countdown timer does not advance after hitting limit`() = runTest {
        every { prefs.timerCountDown }     returns true
        every { prefs.timerLimitMinutes }  returns 1u

        val vm = makeVm()
        vm.startPauseTimer()
        advanceTimeBy(90_000)
        advanceUntilIdle()

        val snapshot = vm.timerElapsed.value
        advanceTimeBy(5_000)
        assertEquals(snapshot, vm.timerElapsed.value)   // frozen at limit
    }

    @Test
    fun `rapid start-pause toggles preserve accumulated time correctly`() = runTest {
        val vm = makeVm()

        vm.startPauseTimer()
        advanceTimeBy(1_000)
        vm.startPauseTimer()   // pause

        vm.startPauseTimer()   // resume
        advanceTimeBy(1_000)
        vm.startPauseTimer()   // pause

        vm.startPauseTimer()   // resume
        advanceTimeBy(1_000)
        vm.startPauseTimer()   // pause

        assertTrue(vm.timerElapsed.value.inWholeSeconds >= 2)
        assertFalse(vm.timerRunning.value)
    }

    // ── display name live propagation ────────────────────────────────────────

    @Test
    fun `setDisplayName propagates to live websocket when session is active`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        vm.setDisplayName("Bob")
        verify { ws.setDisplayName("Bob") }
    }

    @Test
    fun `setDisplayName does not call websocket when no session is active`() {
        val vm = makeVm()
        vm.setDisplayName("Bob")
        verify(exactly = 0) { ws.setDisplayName(any()) }
    }

    @Test
    fun `setDisplayName trims before propagating to websocket`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        vm.setDisplayName("  Carol  ")
        verify { ws.setDisplayName("Carol") }
        verify { prefs.displayName = "Carol" }
    }

    // ── concede / unconcede ──────────────────────────────────────────────────

    @Test
    fun `concede delegates setConceded(true) to websocket`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        vm.concede()
        verify { ws.setConceded(true) }
    }

    @Test
    fun `unconcede delegates setConceded(false) to websocket`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        vm.unconcede()
        verify { ws.setConceded(false) }
    }

    @Test
    fun `concede updates local user state optimistically`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        val users = listOf(UserState("test-user-id", "Test Player", life = 20u, conceded = false))
        serverMessages.emit(ServerMessage.State(users))
        advanceUntilIdle()

        vm.concede()
        assertTrue(vm.sessionUi.value.users.first { it.id == "test-user-id" }.conceded)
    }

    @Test
    fun `unconcede updates local user state optimistically`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        val users = listOf(UserState("test-user-id", "Test Player", life = 20u, conceded = true))
        serverMessages.emit(ServerMessage.State(users))
        advanceUntilIdle()

        vm.unconcede()
        assertFalse(vm.sessionUi.value.users.first { it.id == "test-user-id" }.conceded)
    }

    // ── friend requests ──────────────────────────────────────────────────────

    @Test
    fun `sendFriendRequest delegates to websocket`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        vm.sendFriendRequest("other-user-id")
        verify { ws.sendFriendRequest("other-user-id") }
    }

    @Test
    fun `FriendRequest message adds entry to pendingFriendRequests`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        serverMessages.emit(ServerMessage.FriendRequest("user-2", "Alice"))
        advanceUntilIdle()

        val pending = vm.pendingFriendRequests.value
        assertEquals(1, pending.size)
        assertEquals("user-2", pending.first().fromUserId)
        assertEquals("Alice", pending.first().fromDisplayName)
    }

    @Test
    fun `duplicate FriendRequest message is not added twice`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        serverMessages.emit(ServerMessage.FriendRequest("user-2", "Alice"))
        serverMessages.emit(ServerMessage.FriendRequest("user-2", "Alice"))
        advanceUntilIdle()

        assertEquals(1, vm.pendingFriendRequests.value.size)
    }

    @Test
    fun `acceptFriendRequest adds friend, removes pending entry, and delegates to websocket`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        every { prefs.friendList } returns listOf(Friend("user-2", "Alice"))
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        serverMessages.emit(ServerMessage.FriendRequest("user-2", "Alice"))
        advanceUntilIdle()

        vm.acceptFriendRequest("user-2", "Alice")

        verify { prefs.addFriend("user-2", "Alice") }
        verify { ws.acceptFriendRequest("user-2") }
        assertTrue(vm.pendingFriendRequests.value.none { it.fromUserId == "user-2" })
    }

    @Test
    fun `declineFriendRequest removes pending entry and delegates to websocket`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        serverMessages.emit(ServerMessage.FriendRequest("user-2", "Alice"))
        advanceUntilIdle()

        vm.declineFriendRequest("user-2")

        verify { ws.declineFriendRequest("user-2") }
        assertTrue(vm.pendingFriendRequests.value.none { it.fromUserId == "user-2" })
    }

    @Test
    fun `FriendAccepted message persists friend and acknowledges`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        every { prefs.friendList } returns listOf(Friend("user-3", "Bob"))
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        vm.sendFriendRequest("user-3")
        serverMessages.emit(ServerMessage.FriendAccepted("user-3", "Bob"))
        advanceUntilIdle()

        verify { prefs.addFriend("user-3", "Bob") }
        verify { ws.acknowledgeAccepted("user-3") }
        assertTrue(vm.friendList.value.any { it.userId == "user-3" })
    }

    @Test
    fun `unsolicited FriendAccepted is acknowledged but not added as a friend`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        serverMessages.emit(ServerMessage.FriendAccepted("stalker", "Mallory"))
        advanceUntilIdle()

        verify(exactly = 0) { prefs.addFriend("stalker", any()) }
        verify { ws.acknowledgeAccepted("stalker") }
    }

    @Test
    fun `pendingFriendRequests cleared on leaveSession`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        serverMessages.emit(ServerMessage.FriendRequest("user-2", "Alice"))
        advanceUntilIdle()
        assertEquals(1, vm.pendingFriendRequests.value.size)

        vm.leaveSession()
        assertEquals(0, vm.pendingFriendRequests.value.size)
    }

    // ── known players ────────────────────────────────────────────────────────

    @Test
    fun `State message with other players triggers touchKnownPlayer`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        every { prefs.knownPlayers } returns listOf(KnownPlayer("other-id", "Alice", 0L))
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        val users = listOf(
            UserState("test-user-id", "Test Player", life = 20u),
            UserState("other-id", "Alice", life = 20u),
        )
        serverMessages.emit(ServerMessage.State(users))
        advanceUntilIdle()

        verify { prefs.touchKnownPlayer("other-id", "Alice") }
        assertTrue(vm.knownPlayers.value.any { it.userId == "other-id" })
    }

    @Test
    fun `State message does not call touchKnownPlayer for own user`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        serverMessages.emit(ServerMessage.State(listOf(UserState("test-user-id", "Test Player", life = 20u))))
        advanceUntilIdle()

        verify(exactly = 0) { prefs.touchKnownPlayer("test-user-id", any()) }
    }

    @Test
    fun `State message updates friend display name when friend is in session`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        every { prefs.friendList } returns listOf(Friend("friend-id", "OldName"))
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        val users = listOf(
            UserState("test-user-id", "Test Player", life = 20u),
            UserState("friend-id", "NewName", life = 20u),
        )
        serverMessages.emit(ServerMessage.State(users))
        advanceUntilIdle()

        verify { prefs.addFriend("friend-id", "NewName") }
    }

    @Test
    fun `repeated State with the same players does not rewrite prefs`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        val me    = UserState("test-user-id", "Test Player", life = 20u)
        val alice = UserState("other-id", "Alice", life = 20u)
        serverMessages.emit(ServerMessage.State(listOf(me, alice)))
        serverMessages.emit(ServerMessage.State(listOf(me, alice.copy(life = 19u))))
        serverMessages.emit(ServerMessage.State(listOf(me.copy(life = 18u), alice.copy(life = 17u))))
        advanceUntilIdle()

        verify(exactly = 1) { prefs.touchKnownPlayer("other-id", "Alice") }
    }

    @Test
    fun `State touches prefs again when a player is renamed`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        val me = UserState("test-user-id", "Test Player", life = 20u)
        serverMessages.emit(ServerMessage.State(listOf(me, UserState("other-id", "Alice"))))
        serverMessages.emit(ServerMessage.State(listOf(me, UserState("other-id", "Alicia"))))
        advanceUntilIdle()

        verify(exactly = 1) { prefs.touchKnownPlayer("other-id", "Alice") }
        verify(exactly = 1) { prefs.touchKnownPlayer("other-id", "Alicia") }
    }

    // ── player identity comes from the backend sign-in ───────────────────────

    @Test
    fun `the signed-in player ID is used for the session and as host`() = runTest {
        coEvery { api.signIn() } returns "auth-uid-42"
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        coVerify { api.createSession("auth-uid-42", any()) }
        verify { connectionFactory("sid-1", "auth-uid-42", any(), any()) }
        assertEquals("auth-uid-42", vm.sessionUi.value.myUserId)
    }

    @Test
    fun `sign-in happens once and is reused`() = runTest {
        coEvery { api.getSessionByCode(any()) } returns SessionInfoResponse("sid-2", "ABCDEFGH", 0)
        coEvery { api.getSessionById(any()) } returns SessionInfoResponse("sid-3", "ABCDEFGH", 0)
        val vm = makeVm()
        vm.joinByCode("ABCDEFGH")
        advanceUntilIdle()
        vm.joinFriendSession("sid-3")
        advanceUntilIdle()

        coVerify(exactly = 1) { api.signIn() }
    }

    @Test
    fun `a failed sign-in is reported and nothing is created`() = runTest {
        coEvery { api.signIn() } throws RuntimeException("offline")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        assertEquals(HomeError.CreateFailed("offline"), vm.homeError.value)
        coVerify(exactly = 0) { api.createSession(any(), any()) }
        assertEquals(Screen.Home, vm.screen.value)
    }

    // ── resume after restart ─────────────────────────────────────────────────

    @Test
    fun `joining a session remembers it for resume`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        verify { prefs.lastSessionId = "sid-1" }
    }

    @Test
    fun `leaveSession forgets the session and removes the player`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        vm.leaveSession()

        verify { prefs.lastSessionId = null }
        verify { ws.close(true) }
    }

    @Test
    fun `resumeLastSession rejoins the remembered session`() = runTest {
        every { prefs.lastSessionId } returns "sid-9"
        coEvery { api.getSessionById("sid-9") } returns SessionInfoResponse("sid-9", "RESUME", 2)
        val vm = makeVm()

        vm.resumeLastSession()
        advanceUntilIdle()

        assertEquals(Screen.Session("sid-9"), vm.screen.value)
        assertEquals("RESUME", vm.sessionUi.value.sessionCode)
        verify { ws.connect() }
    }

    @Test
    fun `resumeLastSession forgets a session that no longer exists`() = runTest {
        every { prefs.lastSessionId } returns "gone"
        coEvery { api.getSessionById("gone") } throws RuntimeException("Session no longer exists")
        val vm = makeVm()

        vm.resumeLastSession()
        advanceUntilIdle()

        assertEquals(Screen.Home, vm.screen.value)
        assertNull(vm.homeError.value)
        verify { prefs.lastSessionId = null }
    }

    @Test
    fun `resumeLastSession without a remembered session does nothing`() = runTest {
        val vm = makeVm()
        vm.resumeLastSession()
        advanceUntilIdle()

        assertEquals(Screen.Home, vm.screen.value)
        coVerify(exactly = 0) { api.getSessionById(any()) }
    }

    // ── local friend list management ─────────────────────────────────────────

    @Test
    fun `addFriend persists and updates friendList flow`() {
        every { prefs.friendList } returns listOf(Friend("u1", "Alice"))
        val vm = makeVm()
        vm.addFriend("u1", "Alice")
        verify { prefs.addFriend("u1", "Alice") }
        assertTrue(vm.friendList.value.any { it.userId == "u1" })
    }

    @Test
    fun `removeFriend persists and updates friendList flow`() {
        every { prefs.friendList } returns emptyList()
        val vm = makeVm()
        vm.removeFriend("u1")
        verify { prefs.removeFriend("u1") }
        assertTrue(vm.friendList.value.isEmpty())
    }

    // ── friend presence ──────────────────────────────────────────────────────

    @Test
    fun `friendPresence starts empty`() {
        assertEquals(emptyMap<String, String?>(), makeVm().friendPresence.value)
    }

    @Test
    fun `friendPresence reflects values emitted by observeFriendPresence`() = runTest {
        val presenceFlow = MutableSharedFlow<Map<String, String?>>(replay = 1)
        every { prefs.friendList }                       returns listOf(Friend("uid1", "Alice"))
        every { api.observeFriendPresence(listOf("uid1")) } returns presenceFlow

        val vm = makeVm()
        advanceUntilIdle()

        presenceFlow.emit(mapOf("uid1" to "session-abc"))
        advanceUntilIdle()

        assertEquals(mapOf("uid1" to "session-abc"), vm.friendPresence.value)
    }

    @Test
    fun `friendPresence stores null when friend is offline`() = runTest {
        val presenceFlow = MutableSharedFlow<Map<String, String?>>(replay = 1)
        every { prefs.friendList }                           returns listOf(Friend("uid1", "Alice"))
        every { api.observeFriendPresence(listOf("uid1")) }  returns presenceFlow

        val vm = makeVm()
        advanceUntilIdle()

        presenceFlow.emit(mapOf("uid1" to null))
        advanceUntilIdle()

        assertNull(vm.friendPresence.value["uid1"])
    }

    @Test
    fun `observeFriendPresence is not called when friend list is empty`() = runTest {
        every { prefs.friendList } returns emptyList()

        val vm = makeVm()
        advanceUntilIdle()

        verify(exactly = 0) { api.observeFriendPresence(any()) }
        assertEquals(emptyMap<String, String?>(), vm.friendPresence.value)
    }

    @Test
    fun `friendPresence resubscribes via flatMapLatest when friend list grows`() = runTest {
        val presenceFlow = MutableSharedFlow<Map<String, String?>>(replay = 1)
        every { prefs.friendList }                           returnsMany listOf(
            emptyList(),
            listOf(Friend("uid1", "Alice")),
        )
        every { api.observeFriendPresence(listOf("uid1")) }  returns presenceFlow

        val vm = makeVm()
        advanceUntilIdle()
        verify(exactly = 0) { api.observeFriendPresence(any()) }

        vm.addFriend("uid1", "Alice")
        advanceUntilIdle()

        presenceFlow.emit(mapOf("uid1" to "session-xyz"))
        advanceUntilIdle()

        verify { api.observeFriendPresence(listOf("uid1")) }
        assertEquals("session-xyz", vm.friendPresence.value["uid1"])
    }

    @Test
    fun `friendPresence tracks multiple friends simultaneously`() = runTest {
        val presenceFlow = MutableSharedFlow<Map<String, String?>>(replay = 1)
        val friends = listOf(Friend("uid1", "Alice"), Friend("uid2", "Bob"))
        every { prefs.friendList }                                        returns friends
        every { api.observeFriendPresence(listOf("uid1", "uid2")) }       returns presenceFlow

        val vm = makeVm()
        advanceUntilIdle()

        presenceFlow.emit(mapOf("uid1" to "session-1", "uid2" to null))
        advanceUntilIdle()

        assertEquals("session-1", vm.friendPresence.value["uid1"])
        assertNull(vm.friendPresence.value["uid2"])
    }

    // ── joinFriendSession ────────────────────────────────────────────────────

    @Test
    fun `joinFriendSession on success navigates to Session screen`() = runTest {
        coEvery { api.getSessionById("sid-5") } returns SessionInfoResponse("sid-5", "CODE05", 1)

        val vm = makeVm()
        vm.joinFriendSession("sid-5")
        advanceUntilIdle()

        assertIs<Screen.Session>(vm.screen.value)
        assertEquals("sid-5", (vm.screen.value as Screen.Session).sessionId)
        verify { ws.connect() }
    }

    @Test
    fun `joinFriendSession on failure sets homeError`() = runTest {
        coEvery { api.getSessionById(any()) } throws RuntimeException("session gone")

        val vm = makeVm()
        vm.joinFriendSession("bad-id")
        advanceUntilIdle()

        assertEquals(Screen.Home, vm.screen.value)
        assertEquals(HomeError.JoinFailed("session gone"), vm.homeError.value)
    }

    @Test
    fun `joinFriendSession homeLoading is false after completion`() = runTest {
        coEvery { api.getSessionById("sid-5") } returns SessionInfoResponse("sid-5", "CODE05", 1)

        val vm = makeVm()
        vm.joinFriendSession("sid-5")
        advanceUntilIdle()

        assertFalse(vm.homeLoading.value)
        assertNull(vm.homeError.value)
    }

    @Test
    fun `joinFriendSession uses session code from getSessionById response`() = runTest {
        coEvery { api.getSessionById("sid-5") } returns SessionInfoResponse("sid-5", "FRND01", 1)

        val vm = makeVm()
        vm.joinFriendSession("sid-5")
        advanceUntilIdle()

        assertEquals("FRND01", vm.sessionUi.value.sessionCode)
    }

    // ── presence cleanup when friend is removed ──────────────────────────────

    @Test
    fun `removeFriend for last friend clears friendPresence`() = runTest {
        val presenceFlow = MutableSharedFlow<Map<String, String?>>(replay = 1)
        every { prefs.friendList } returnsMany listOf(
            listOf(Friend("uid1", "Alice")),
            emptyList(),
        )
        every { api.observeFriendPresence(listOf("uid1")) } returns presenceFlow

        val vm = makeVm()
        advanceUntilIdle()

        presenceFlow.emit(mapOf("uid1" to "session-abc"))
        advanceUntilIdle()
        assertEquals("session-abc", vm.friendPresence.value["uid1"])

        // Removing the friend updates the list to empty; flatMapLatest should
        // switch to an empty-list branch that emits no presence entries.
        vm.removeFriend("uid1")
        advanceUntilIdle()

        assertNull(vm.friendPresence.value["uid1"])
    }

    @Test
    fun `removeFriend for one of two friends resubscribes with the remaining friend`() = runTest {
        val flow1 = MutableSharedFlow<Map<String, String?>>(replay = 1)
        val flow2 = MutableSharedFlow<Map<String, String?>>(replay = 1)
        val both  = listOf(Friend("uid1", "Alice"), Friend("uid2", "Bob"))
        val bobOnly = listOf(Friend("uid2", "Bob"))

        every { prefs.friendList } returnsMany listOf(both, bobOnly)
        every { api.observeFriendPresence(both.map { it.userId }) }   returns flow1
        every { api.observeFriendPresence(listOf("uid2")) }            returns flow2

        val vm = makeVm()
        advanceUntilIdle()

        flow1.emit(mapOf("uid1" to "session-1", "uid2" to "session-2"))
        advanceUntilIdle()

        // Remove Alice — flatMapLatest must resubscribe with only Bob's id
        vm.removeFriend("uid1")
        advanceUntilIdle()

        flow2.emit(mapOf("uid2" to "session-2"))
        advanceUntilIdle()

        verify { api.observeFriendPresence(listOf("uid2")) }
        assertEquals("session-2", vm.friendPresence.value["uid2"])
    }

    @Test
    fun `joinFriendSession homeError contains the api exception message`() = runTest {
        coEvery { api.getSessionById(any()) } throws RuntimeException("session expired")

        val vm = makeVm()
        vm.joinFriendSession("stale-id")
        advanceUntilIdle()

        assertEquals(HomeError.JoinFailed("session expired"), vm.homeError.value)
        assertEquals(Screen.Home, vm.screen.value)
    }

    @Test
    fun `joinFriendSession homeError is null on success`() = runTest {
        coEvery { api.getSessionById("sid-ok") } returns SessionInfoResponse("sid-ok", "CODEX1", 1)

        val vm = makeVm()
        // Seed a prior error to confirm it gets cleared
        vm.joinFriendSession("bad-id")
        advanceUntilIdle()

        vm.joinFriendSession("sid-ok")
        advanceUntilIdle()

        assertNull(vm.homeError.value)
    }

    // ── Reserved stat name guard ──────────────────────────────────────────

    @Test
    fun `addCustomStat silently ignores reserved names`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid", "CODE01")
        every { connectionFactory("sid", any(), any(), any()) } returns ws

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        RESERVED_STAT_NAMES.forEach { reserved ->
            // Use the original casing variant to ensure the lowercase comparison fires
            vm.addCustomStat(reserved)
            vm.addCustomStat(reserved.uppercase())
            vm.addCustomStat(reserved.replaceFirstChar { it.uppercase() })
        }
        advanceUntilIdle()

        verify(exactly = 0) { ws.addCustomStat(any(), any()) }
    }

    @Test
    fun `addCustomStat accepts the commander and poison presets`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid", "CODE01")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        vm.addCustomStat("commander")
        vm.addCustomStat("poison")
        advanceUntilIdle()

        verify { ws.addCustomStat("commander", StatType.NUMERIC) }
        verify { ws.addCustomStat("poison", StatType.NUMERIC) }
    }

    @Test
    fun `setDisplayName caps the name at the rules limit`() {
        val vm = makeVm()
        vm.setDisplayName("x".repeat(100))
        verify { prefs.displayName = "x".repeat(MAX_DISPLAY_NAME_LENGTH) }
        assertEquals(MAX_DISPLAY_NAME_LENGTH, vm.displayName.value.length)
    }

    @Test
    fun `setDisplayName ignores a blank name`() {
        val vm = makeVm()
        vm.setDisplayName("   ")
        verify(exactly = 0) { prefs.displayName = any() }
        assertEquals("Test Player", vm.displayName.value)
    }

    @Test
    fun `addCustomStat ignores names Firebase cannot store`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        listOf("a.b", "a#b", "a\$b", "a[b]", "a/b", "x".repeat(33), "   ").forEach(vm::addCustomStat)
        advanceUntilIdle()

        verify(exactly = 0) { ws.addCustomStat(any(), any()) }
    }

    @Test
    fun `isValidStatName accepts presets and ordinary names`() {
        listOf("commander", "poison", "Lore Counters", "Gold", "city's-blessing_2").forEach {
            assertTrue(isValidStatName(it), it)
        }
        listOf("", "life", "LIFE", "gold!", "a.b").forEach {
            assertFalse(isValidStatName(it), it)
        }
    }

    @Test
    fun `addCustomStat forwards non-reserved names`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid", "CODE01")
        every { connectionFactory("sid", any(), any(), any()) } returns ws

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        vm.addCustomStat("gold")
        advanceUntilIdle()

        verify(exactly = 1) { ws.addCustomStat("gold", StatType.NUMERIC) }
    }

    @Test
    fun `friendPresence shows online friend before join is triggered`() = runTest {
        val presenceFlow = MutableSharedFlow<Map<String, String?>>(replay = 1)
        every { prefs.friendList }                                  returns listOf(Friend("uid1", "Alice"))
        every { api.observeFriendPresence(listOf("uid1")) }         returns presenceFlow
        coEvery { api.getSessionById("live-sid") }                  returns SessionInfoResponse("live-sid", "LIVE01", 1)

        val vm = makeVm()
        advanceUntilIdle()

        presenceFlow.emit(mapOf("uid1" to "live-sid"))
        advanceUntilIdle()

        // HomeScreen reads presence and calls joinFriendSession with the discovered id
        val sessionId = vm.friendPresence.value["uid1"]
        assertNotNull(sessionId)

        vm.joinFriendSession(sessionId)
        advanceUntilIdle()

        assertIs<Screen.Session>(vm.screen.value)
        assertEquals("live-sid", (vm.screen.value as Screen.Session).sessionId)
    }

    // ── game features: commander damage, settings, undo, host controls ───────

    private suspend fun TestScope.inSession(
        users: List<UserState> = listOf(UserState("test-user-id", "Test Player", life = 20u)),
        hostUserId: String? = null,
    ): SessionViewModel {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()
        serverMessages.emit(ServerMessage.State(users, hostUserId = hostUserId))
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `commander damage is one counter`() = runTest {
        val vm = inSession()

        vm.adjust(COMMANDER_STAT, 3)
        vm.adjust(COMMANDER_STAT, 5)

        val me = vm.sessionUi.value.users.single()
        assertEquals(8u, me.customStats[COMMANDER_STAT])
        advanceUntilIdle()
        verify { ws.adjust(COMMANDER_STAT, 8) }
    }

    @Test
    fun `createSession makes us host with our default game rules`() = runTest {
        every { prefs.startLife } returns 40u
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        coVerify { api.createSession("test-user-id", SessionSettings(startLife = 40u)) }
        verify { connectionFactory("sid-1", "test-user-id", any(), 40u) }
    }

    @Test
    fun `joining uses the session's rules instead of local ones`() = runTest {
        val rules = SessionSettings(startLife = 30u, commanderDeathThreshold = 15u)
        coEvery { api.getSessionByCode("ABCDEFGH") } returns
            SessionInfoResponse("sid-2", "ABCDEFGH", 1, rules, setOf("host"))
        val vm = makeVm()
        vm.joinByCode("ABCDEFGH")
        advanceUntilIdle()

        verify { connectionFactory("sid-2", any(), any(), 30u) }
        assertEquals(rules, vm.sessionUi.value.settings)
    }

    @Test
    fun `a full session cannot be joined`() = runTest {
        coEvery { api.getSessionByCode(any()) } returns
            SessionInfoResponse("sid-2", "ABCDEFGH", 2, SessionSettings(maxPlayers = 2), setOf("a", "b"))
        val vm = makeVm()
        vm.joinByCode("ABCDEFGH")
        advanceUntilIdle()

        assertEquals(Screen.Home, vm.screen.value)
        assertEquals(HomeError.SessionFull(2), vm.homeError.value)
    }

    @Test
    fun `a member can rejoin a full session`() = runTest {
        coEvery { api.getSessionByCode(any()) } returns
            SessionInfoResponse("sid-2", "ABCDEFGH", 2, SessionSettings(maxPlayers = 2), setOf("test-user-id", "b"))
        val vm = makeVm()
        vm.joinByCode("ABCDEFGH")
        advanceUntilIdle()

        assertIs<Screen.Session>(vm.screen.value)
    }

    @Test
    fun `settings and host come from the session state`() = runTest {
        val vm = inSession()
        val rules = SessionSettings(startLife = 40u, infectDeathThreshold = 7u)
        serverMessages.emit(ServerMessage.State(
            listOf(UserState("test-user-id", "Test Player")), hostUserId = "test-user-id", settings = rules,
        ))
        advanceUntilIdle()

        assertEquals(rules, vm.sessionUi.value.settings)
        assertTrue(vm.sessionUi.value.isHost)
    }

    @Test
    fun `host controls are ignored for non-hosts`() = runTest {
        val vm = inSession(hostUserId = "someone-else")

        vm.startNewGame()
        vm.removePlayer("other")
        vm.updateSessionSettings(SessionSettings(startLife = 40u))

        verify(exactly = 0) { ws.startNewGame() }
        verify(exactly = 0) { ws.removePlayer(any()) }
        verify(exactly = 0) { ws.updateSettings(any()) }
    }

    @Test
    fun `host controls reach the connection for the host`() = runTest {
        val vm = inSession(hostUserId = "test-user-id")

        vm.startNewGame()
        vm.removePlayer("other")
        vm.removePlayer("test-user-id")   // not yourself
        vm.updateSessionSettings(SessionSettings(startLife = 40u))

        verify { ws.startNewGame() }
        verify(exactly = 1) { ws.removePlayer("other") }
        verify { ws.updateSettings(SessionSettings(startLife = 40u)) }
    }

    @Test
    fun `losing our seat returns to Home with a message`() = runTest {
        val vm = inSession()
        serverMessages.emit(ServerMessage.State(listOf(UserState("other", "Bob"))))
        advanceUntilIdle()

        assertEquals(Screen.Home, vm.screen.value)
        assertEquals(HomeError.RemovedFromSession, vm.homeError.value)
        verify { prefs.lastSessionId = null }
    }

    @Test
    fun `a state without our seat before we sat down is not a removal`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()
        serverMessages.emit(ServerMessage.State(listOf(UserState("other", "Bob"))))
        advanceUntilIdle()

        assertIs<Screen.Session>(vm.screen.value)
    }

    @Test
    fun `life changes are recorded once per burst of taps`() = runTest {
        val vm = inSession()
        vm.adjust("life", -1)
        vm.adjust("life", -1)
        vm.adjust("life", -1)
        advanceUntilIdle()

        val history = vm.statChanges
        assertEquals(1, history.size)
        assertEquals(-3, history.single().delta)
        assertEquals(17u, history.single().valueAfter)
    }

    private val MY_STATS = mapOf("poison" to StatType.NUMERIC, "initiative" to StatType.TOGGLE, COMMANDER_STAT to StatType.NUMERIC)

    private val SessionViewModel.statChanges: List<StatChange>
        get() = history.value.filterIsInstance<StatChange>()

    private suspend fun TestScope.inSessionWithStats(): SessionViewModel {
        val vm = inSession()
        serverMessages.emit(ServerMessage.State(
            users = listOf(UserState("test-user-id", "Test Player", life = 20u, stats = MY_STATS), UserState("bob", "Bob")),
        ))
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `counter changes are recorded and undoable`() = runTest {
        val vm = inSessionWithStats()
        vm.adjust("poison", 1)
        vm.adjust("poison", 1)
        advanceUntilIdle()

        assertEquals(listOf(StatChange(0, "poison", 2, 2u)), vm.statChanges)
        vm.undoLastChange()
        assertEquals(0u, vm.sessionUi.value.users.first().customStats["poison"])
        advanceUntilIdle()
        verify { ws.adjust("poison", 2) }
        verify { ws.adjust("poison", -2) }
    }

    @Test
    fun `toggles and commander damage are recorded too`() = runTest {
        val vm = inSessionWithStats()
        vm.adjust("initiative", 1)
        advanceUntilIdle()
        vm.adjust(COMMANDER_STAT, 4)
        advanceUntilIdle()

        assertEquals(
            listOf(COMMANDER_STAT to 4, "initiative" to 1),
            vm.statChanges.map { it.stat to it.delta },
        )
        vm.undoLastChange()
        advanceUntilIdle()
        verify { ws.adjust(COMMANDER_STAT, -4) }
    }

    @Test
    fun `changes to different stats are undone newest first`() = runTest {
        val vm = inSessionWithStats()
        vm.adjust("life", -3)
        advanceUntilIdle()
        vm.adjust("poison", 2)
        advanceUntilIdle()

        vm.undoLastChange()
        advanceUntilIdle()
        verify { ws.adjust("poison", -2) }
        verify(exactly = 0) { ws.adjust("life", 3) }
        assertEquals(listOf("life"), vm.statChanges.map { it.stat })
    }

    @Test
    fun `a change stopped at zero records only what really changed`() = runTest {
        val vm = inSession(users = listOf(UserState("test-user-id", "Test Player", life = 2u)))
        vm.adjust("life", -5)
        advanceUntilIdle()

        assertEquals(-2, vm.statChanges.single().delta)
        assertEquals(0u, vm.statChanges.single().valueAfter)
    }

    @Test
    fun `turning a counter off hides its changes until it is back on`() = runTest {
        val vm = inSessionWithStats()
        vm.adjust("poison", 1)
        vm.adjust("life", -1)
        advanceUntilIdle()
        val me = UserState("test-user-id", "Test Player", life = 19u, customStats = mapOf("poison" to 1u))

        serverMessages.emit(ServerMessage.State(users = listOf(me.copy(stats = mapOf("initiative" to StatType.TOGGLE)))))
        advanceUntilIdle()
        assertEquals(listOf("life"), vm.statChanges.map { it.stat })

        serverMessages.emit(ServerMessage.State(users = listOf(me.copy(stats = mapOf("poison" to StatType.NUMERIC)))))
        advanceUntilIdle()
        assertEquals(listOf("life", "poison"), vm.statChanges.map { it.stat })
    }

    @Test
    fun `turning a counter on is undone by turning it off`() = runTest {
        val vm = inSessionWithStats()
        vm.addCustomStat("Gold", StatType.NUMERIC)
        verify { ws.addCustomStat("Gold", StatType.NUMERIC) }
        serverMessages.emit(ServerMessage.State(
            users = listOf(UserState("test-user-id", "Test Player", stats = MY_STATS + ("Gold" to StatType.NUMERIC))),
        ))
        advanceUntilIdle()

        assertEquals(listOf(StatToggled(0, "Gold", StatType.NUMERIC, enabled = true)), vm.history.value)
        vm.undoLastChange()
        verify { ws.removeCustomStat("Gold") }
        assertTrue(vm.history.value.isEmpty())
    }

    @Test
    fun `turning a counter off is undone by turning it back on with its type`() = runTest {
        val vm = inSessionWithStats()
        vm.removeCustomStat("initiative")
        verify { ws.removeCustomStat("initiative") }
        serverMessages.emit(ServerMessage.State(
            users = listOf(UserState("test-user-id", "Test Player", stats = mapOf("poison" to StatType.NUMERIC))),
        ))
        advanceUntilIdle()

        assertEquals(listOf(StatToggled(0, "initiative", StatType.TOGGLE, enabled = false)), vm.history.value)
        vm.undoLastChange()
        verify { ws.addCustomStat("initiative", StatType.TOGGLE) }
    }

    @Test
    fun `turning on a counter that is already on is not recorded`() = runTest {
        val vm = inSessionWithStats()
        vm.addCustomStat("poison")
        verify(exactly = 0) { ws.addCustomStat("poison", any()) }
        assertTrue(vm.history.value.isEmpty())
    }

    @Test
    fun `a counter turned back on elsewhere is no longer offered for undo`() = runTest {
        val vm = inSessionWithStats()
        vm.removeCustomStat("poison")
        serverMessages.emit(ServerMessage.State(users = listOf(UserState("test-user-id", "Test Player"))))
        advanceUntilIdle()
        assertEquals(1, vm.history.value.size)

        serverMessages.emit(ServerMessage.State(
            users = listOf(UserState("test-user-id", "Test Player", stats = mapOf("poison" to StatType.NUMERIC))),
        ))
        advanceUntilIdle()
        assertTrue(vm.history.value.isEmpty())
    }

    @Test
    fun `turning Day and Night on is undone by turning it off`() = runTest {
        val vm = inSession()
        vm.setGlobal(DAY_NIGHT_GLOBAL, 0u)
        verify { ws.setGlobal(DAY_NIGHT_GLOBAL, 0u) }
        serverMessages.emit(ServerMessage.State(
            users       = listOf(UserState("test-user-id", "Test Player")),
            globalStats = mapOf(DAY_NIGHT_GLOBAL to 0u),
        ))
        advanceUntilIdle()

        assertEquals(listOf(GlobalChange(0, DAY_NIGHT_GLOBAL, before = null, after = 0u)), vm.history.value)
        vm.undoLastChange()
        verify { ws.removeGlobal(DAY_NIGHT_GLOBAL) }
    }

    @Test
    fun `flipping day to night is undone, unless someone flipped it again`() = runTest {
        val vm = inSession()
        val me = UserState("test-user-id", "Test Player")
        serverMessages.emit(ServerMessage.State(users = listOf(me), globalStats = mapOf(DAY_NIGHT_GLOBAL to 0u)))
        advanceUntilIdle()

        vm.toggleGlobal(DAY_NIGHT_GLOBAL)
        serverMessages.emit(ServerMessage.State(users = listOf(me), globalStats = mapOf(DAY_NIGHT_GLOBAL to 1u)))
        advanceUntilIdle()
        assertEquals(listOf(GlobalChange(0, DAY_NIGHT_GLOBAL, before = 0u, after = 1u)), vm.history.value)

        serverMessages.emit(ServerMessage.State(users = listOf(me), globalStats = mapOf(DAY_NIGHT_GLOBAL to 0u)))
        advanceUntilIdle()
        assertTrue(vm.history.value.isEmpty())

        serverMessages.emit(ServerMessage.State(users = listOf(me), globalStats = mapOf(DAY_NIGHT_GLOBAL to 1u)))
        advanceUntilIdle()
        vm.undoLastChange()
        verify { ws.setGlobal(DAY_NIGHT_GLOBAL, 0u) }
    }

    @Test
    fun `poison and commander damage only kill while the player tracks them`() {
        val user = UserState("u", "U", customStats = mapOf(POISON_STAT to 10u, COMMANDER_STAT to 21u))
        assertFalse(user.isDead(SessionUiState()))
        assertTrue(user.copy(stats = mapOf(POISON_STAT to StatType.NUMERIC)).isDead(SessionUiState()))
        assertTrue(user.copy(stats = mapOf(COMMANDER_STAT to StatType.NUMERIC)).isDead(SessionUiState()))
    }

    @Test
    fun `counters are per player`() = runTest {
        val vm = inSessionWithStats()
        vm.addCustomStat("Gold")
        verify { ws.addCustomStat("Gold", StatType.NUMERIC) }

        // Bob tracking Gold doesn't turn it on for us, and doesn't stop us adding it.
        serverMessages.emit(ServerMessage.State(users = listOf(
            UserState("test-user-id", "Test Player", stats = MY_STATS),
            UserState("bob", "Bob", stats = mapOf("Energy" to StatType.NUMERIC)),
        )))
        advanceUntilIdle()
        vm.addCustomStat("Energy")
        verify { ws.addCustomStat("Energy", StatType.NUMERIC) }
        assertEquals(MY_STATS, vm.sessionUi.value.myStats)
    }

    @Test
    fun `taking the monarch is recorded and undone by passing it back`() = runTest {
        val vm = inSessionWithStats()
        val users = listOf(UserState("test-user-id", "Test Player", stats = MY_STATS), UserState("bob", "Bob"))
        serverMessages.emit(ServerMessage.State(users = users, monarch = "bob"))
        advanceUntilIdle()

        vm.setMonarch("test-user-id")
        verify { ws.setMonarch("test-user-id") }
        serverMessages.emit(ServerMessage.State(users = users, monarch = "test-user-id"))
        advanceUntilIdle()
        assertEquals(listOf(MonarchChange(0, before = "bob", after = "test-user-id")), vm.history.value)

        vm.undoLastChange()
        verify { ws.setMonarch("bob") }
    }

    @Test
    fun `a monarch passed on by someone else is no longer offered for undo`() = runTest {
        val vm = inSessionWithStats()
        vm.setMonarch("test-user-id")
        val users = listOf(UserState("test-user-id", "Test Player", stats = MY_STATS), UserState("bob", "Bob"))
        serverMessages.emit(ServerMessage.State(users = users, monarch = "test-user-id"))
        advanceUntilIdle()
        assertEquals(1, vm.history.value.size)

        serverMessages.emit(ServerMessage.State(users = users, monarch = "bob"))
        advanceUntilIdle()
        assertTrue(vm.history.value.isEmpty())
    }

    @Test
    fun `setting the monarch to who already holds it does nothing`() = runTest {
        val vm = inSession()
        vm.setMonarch(null)
        verify(exactly = 0) { ws.setMonarch(any()) }
    }

    @Test
    fun `a new game ends the monarch`() = runTest {
        val vm = inSession(hostUserId = "test-user-id")
        serverMessages.emit(ServerMessage.State(
            users = listOf(UserState("test-user-id", "Test Player")), hostUserId = "test-user-id", monarch = "test-user-id"))
        advanceUntilIdle()

        vm.startNewGame()
        verify { ws.startNewGame() }
        verify { ws.setMonarch(null) }
    }

    @Test
    fun `undo reverts the last life change without recording itself`() = runTest {
        val vm = inSession()
        vm.adjust("life", -5)
        advanceUntilIdle()
        serverMessages.emit(ServerMessage.State(listOf(UserState("test-user-id", "Test Player", life = 15u))))
        advanceUntilIdle()

        vm.undoLastChange()
        assertEquals(20u, vm.sessionUi.value.users.single().life)
        advanceUntilIdle()

        verify { ws.adjust("life", -5) }
        verify { ws.adjust("life", 5) }
        assertTrue(vm.history.value.isEmpty())
    }

    @Test
    fun `undo merged with new taps records only the taps`() = runTest {
        val vm = inSession()
        vm.adjust("life", -5)
        advanceUntilIdle()

        vm.undoLastChange()
        vm.adjust("life", -2)
        advanceUntilIdle()

        verify { ws.adjust("life", 3) }
        assertEquals(listOf(-2), vm.statChanges.map { it.delta })
    }

    @Test
    fun `a new game clears the life history`() = runTest {
        val vm = inSession()
        vm.adjust("life", -5)
        advanceUntilIdle()
        assertEquals(1, vm.history.value.size)

        serverMessages.emit(ServerMessage.State(listOf(UserState("test-user-id", "Test Player")), game = 1))
        advanceUntilIdle()

        assertTrue(vm.history.value.isEmpty())
        assertEquals(1L, vm.sessionUi.value.game)
    }

    // ── settings and navigation ──────────────────────────────────────────────

    @Test
    fun `settings returns to the screen it was opened from`() = runTest {
        val vm = inSession()
        vm.openSettings()
        assertEquals(Screen.Settings, vm.screen.value)
        vm.closeSettings()
        assertEquals(Screen.Session("sid-1"), vm.screen.value)
    }

    @Test
    fun `settings opened from home returns home`() {
        val vm = makeVm()
        vm.openSettings()
        vm.closeSettings()
        assertEquals(Screen.Home, vm.screen.value)
    }

    @Test
    fun `game rule defaults persist and update their flows`() {
        val vm = makeVm()
        vm.setStartLife(40u)
        vm.setCommanderThreshold(15u)
        vm.setInfectThreshold(7u)
        vm.setCommanderDefaultEnabled(true)

        verify { prefs.startLife = 40u }
        verify { prefs.commanderDeathThreshold = 15u }
        verify { prefs.infectDeathThreshold = 7u }
        verify { prefs.commanderDefaultEnabled = true }
        assertEquals(40u, vm.startLife.value)
        assertEquals(15u, vm.commanderThreshold.value)
        assertEquals(7u, vm.infectThreshold.value)
        every { prefs.commanderDefaultEnabled } returns true
        assertTrue(vm.commanderDefaultEnabled)
    }

    @Test
    fun `appearance settings persist and update their flows`() {
        val vm = makeVm()
        vm.setColorScheme(AppColorScheme.LIGHT)
        vm.setCardBackgroundImage("content://card")

        verify { prefs.colorScheme = AppColorScheme.LIGHT }
        verify { prefs.cardBackgroundImageUri = "content://card" }
        assertEquals(AppColorScheme.LIGHT, vm.colorScheme.value)
        assertEquals("content://card", vm.cardBackgroundImageUri.value)
    }

    @Test
    fun `the storm preset is recognised by name`() = runTest {
        val vm = makeVm()
        assertFalse(vm.isStormPreset.value)
        vm.setBackgroundImage("android.resource://pkg/drawable/bg_arcane_storm")
        advanceUntilIdle()
        assertTrue(vm.isStormPreset.value)
        assertFalse(vm.isManaOrbsPreset.value)
    }

    @Test
    fun `the mana orbs preset is recognised by name`() = runTest {
        val vm = makeVm()
        assertFalse(vm.isManaOrbsPreset.value)
        vm.setBackgroundImage("android.resource://pkg/drawable/bg_mana_orbs")
        advanceUntilIdle()
        assertTrue(vm.isManaOrbsPreset.value)
        assertFalse(vm.isStormPreset.value)
    }

    @Test
    fun `new sessions start with the saved game rules`() = runTest {
        every { prefs.startLife }               returns 30u
        every { prefs.commanderDeathThreshold } returns 15u
        every { prefs.infectDeathThreshold }    returns 7u
        val vm = inSession()
        assertEquals(SessionSettings(30u, 15u, 7u, 0), vm.sessionUi.value.settings)
    }

    // ── closing the app keeps the seat (resume, #42) ─────────────────────────

    @Test
    fun `clearing the view model marks the player offline instead of removing them`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val store = ViewModelStore()
        val vm = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = makeVm() as T
        })[SessionViewModel::class.java]
        vm.createSession()
        advanceUntilIdle()

        store.clear()

        verify { ws.close(false) }
        verify(exactly = 0) { ws.close(true) }
        verify(exactly = 0) { prefs.lastSessionId = null }
        verify { api.close() }
    }

    // ── guards ────────────────────────────────────────────────────────────────

    @Test
    fun `session actions without a session do nothing`() = runTest {
        val vm = makeVm()
        vm.sendFriendRequest("bob")
        vm.undoLastChange()
        vm.startNewGame()
        vm.removePlayer("bob")
        vm.updateSessionSettings(SessionSettings())
        advanceUntilIdle()

        verify(exactly = 0) { connectionFactory(any(), any(), any(), any()) }
        assertTrue(vm.history.value.isEmpty())
    }

    @Test
    fun `undo with an empty history does nothing`() = runTest {
        val vm = inSession()
        vm.undoLastChange()
        advanceUntilIdle()
        verify(exactly = 0) { ws.adjust(any(), any()) }
    }

    @Test
    fun `taps that cancel out send nothing and record nothing`() = runTest {
        val vm = inSession()
        vm.adjust("life", -1)
        vm.adjust("life", 1)
        advanceUntilIdle()

        verify(exactly = 0) { ws.adjust(any(), any()) }
        assertTrue(vm.history.value.isEmpty())
    }

    @Test
    fun `sessions without a host give nobody host controls`() = runTest {
        val vm = inSession(hostUserId = null)
        assertFalse(vm.sessionUi.value.isHost)
        vm.startNewGame()
        verify(exactly = 0) { ws.startNewGame() }
    }

    @Test
    fun `joining the session you are already in is a no-op`() = runTest {
        coEvery { api.getSessionById("sid-1") } returns SessionInfoResponse("sid-1", "CODE01", 1)
        val vm = inSession()
        vm.joinFriendSession("sid-1")
        advanceUntilIdle()

        verify(exactly = 1) { connectionFactory(any(), any(), any(), any()) }
        verify(exactly = 0) { ws.close(any()) }
    }

    @Test
    fun `a blank display name joins as Player`() = runTest {
        every { prefs.displayName } returns ""
        inSession()
        verify { connectionFactory("sid-1", any(), "Player", any()) }
    }

    @Test
    fun `resume is skipped when already in a session`() = runTest {
        val vm = inSession()
        every { prefs.lastSessionId } returns "other"
        vm.resumeLastSession()
        advanceUntilIdle()
        coVerify(exactly = 0) { api.getSessionById(any()) }
    }

    @Test
    fun `resuming into a session that filled up forgets it quietly`() = runTest {
        every { prefs.lastSessionId } returns "sid-full"
        coEvery { api.getSessionById("sid-full") } returns
            SessionInfoResponse("sid-full", "CODE01", 2, SessionSettings(maxPlayers = 2), setOf("a", "b"))
        val vm = makeVm()
        vm.resumeLastSession()
        advanceUntilIdle()

        assertEquals(Screen.Home, vm.screen.value)
        assertNull(vm.homeError.value)
        verify { prefs.lastSessionId = null }
    }

    @Test
    fun `a friend request is only remembered when it was actually sent`() = runTest {
        val vm = makeVm()
        vm.sendFriendRequest("bob")   // no session: nothing sent
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        vm.createSession()
        advanceUntilIdle()
        serverMessages.emit(ServerMessage.FriendAccepted("bob", "Bob"))
        advanceUntilIdle()

        verify(exactly = 0) { prefs.addFriend("bob", any()) }
    }

    // ── observer mode (#6) ───────────────────────────────────────────────────

    private val me  = UserState("test-user-id", "Test Player")
    private val bob = UserState("bob", "Bob")

    private suspend fun TestScope.watchingFriend(settings: SessionSettings = SessionSettings()): SessionViewModel {
        coEvery { api.getSessionById("sid-w") } returns
            SessionInfoResponse("sid-w", "WATCH123", 1, settings, userIds = setOf("bob"))
        val vm = makeVm()
        vm.joinFriendSession("sid-w", watch = true)
        advanceUntilIdle()
        serverMessages.emit(ServerMessage.State(users = listOf(bob), settings = settings,
                                                observers = mapOf("test-user-id" to "Test Player")))
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `watching a friend's game connects without a seat`() = runTest {
        val vm = watchingFriend()
        verify { ws.connect(asObserver = true) }
        verify { prefs.lastSessionObserving = true }
        assertIs<Screen.Session>(vm.screen.value)
        assertTrue(vm.sessionUi.value.observing)
        assertEquals(mapOf("test-user-id" to "Test Player"), vm.sessionUi.value.observers)
    }

    @Test
    fun `a full game can still be watched`() = runTest {
        val vm = watchingFriend(SessionSettings(maxPlayers = 1))
        assertIs<Screen.Session>(vm.screen.value)
        assertNull(vm.homeError.value)
        assertFalse(vm.sessionUi.value.hasFreeSeat)
    }

    @Test
    fun `playing a friend's game still takes a seat`() = runTest {
        coEvery { api.getSessionById("sid-w") } returns SessionInfoResponse("sid-w", "WATCH123", 1)
        val vm = makeVm()
        vm.joinFriendSession("sid-w")
        advanceUntilIdle()
        verify { ws.connect(asObserver = false) }
        assertFalse(vm.sessionUi.value.observing)
    }

    @Test
    fun `observers cannot change anything`() = runTest {
        val vm = watchingFriend()
        vm.adjust("life", -1)
        vm.addCustomStat("Gold")
        vm.setMonarch("bob")
        vm.toggleGlobal(DAY_NIGHT_GLOBAL)
        vm.concede()
        advanceUntilIdle()

        verify(exactly = 0) { ws.adjust(any(), any()) }
        verify(exactly = 0) { ws.addCustomStat(any(), any()) }
        verify(exactly = 0) { ws.setMonarch(any()) }
        verify(exactly = 0) { ws.setGlobal(any(), any()) }
        verify(exactly = 0) { ws.setConceded(any()) }
    }

    @Test
    fun `an observer without a seat is not treated as removed`() = runTest {
        val vm = watchingFriend()
        serverMessages.emit(ServerMessage.State(users = listOf(bob)))
        advanceUntilIdle()
        assertIs<Screen.Session>(vm.screen.value)
    }

    @Test
    fun `when the watched session ends the observer goes home and is told`() = runTest {
        val vm = watchingFriend()
        serverMessages.emit(ServerMessage.SessionGone)
        advanceUntilIdle()
        assertEquals(Screen.Home, vm.screen.value)
        assertEquals(HomeError.SessionEnded, vm.homeError.value)
        verify { prefs.lastSessionId = null }
    }

    @Test
    fun `players are sent home when the session ends`() = runTest {
        val vm = inSession()
        serverMessages.emit(ServerMessage.SessionGone)
        advanceUntilIdle()
        assertEquals(Screen.Home, vm.screen.value)
        assertEquals(HomeError.SessionEnded, vm.homeError.value)
        verify { prefs.lastSessionId = null }
    }

    @Test
    fun `a leaving host hands hosting to the first other player who is online`() = runTest {
        val vm = inSession(
            users = listOf(
                UserState("test-user-id", "Test Player"),
                UserState("ghost", "Ghost", online = false),
                UserState("bob", "Bob"),
                UserState("carol", "Carol"),
            ),
            hostUserId = "test-user-id",
        )
        assertEquals("bob", vm.nextHost?.id)
        vm.leaveSession()

        verifyOrder {
            ws.handOverHost("bob")
            ws.close(true)
        }
        assertEquals(Screen.Home, vm.screen.value)
    }

    @Test
    fun `a host alone in the session just leaves`() = runTest {
        val vm = inSession(hostUserId = "test-user-id")
        assertNull(vm.nextHost)
        vm.leaveSession()

        verify(exactly = 0) { ws.handOverHost(any()) }
        verify { ws.close(true) }
    }

    @Test
    fun `another player leaving hands nothing over`() = runTest {
        val vm = inSession(
            users = listOf(UserState("test-user-id", "Test Player"), UserState("bob", "Bob")),
            hostUserId = "bob",
        )
        vm.leaveSession()

        verify(exactly = 0) { ws.handOverHost(any()) }
        verify { ws.close(true) }
    }


    @Test
    fun `switching to watch sends unsent taps, gives up the seat and clears the history`() = runTest {
        val vm = inSession()
        vm.adjust("life", -2)
        advanceUntilIdle()
        vm.adjust("life", -1)

        vm.watchInstead()
        verify { ws.adjust("life", -1) }
        verify { ws.watch() }
        verify { prefs.lastSessionObserving = true }
        assertTrue(vm.sessionUi.value.observing)
        assertTrue(vm.history.value.isEmpty())

        // The seat going away is our own doing, not a removal.
        serverMessages.emit(ServerMessage.State(users = emptyList(), observers = mapOf("test-user-id" to "Test Player")))
        advanceUntilIdle()
        assertIs<Screen.Session>(vm.screen.value)
        verify(exactly = 1) { ws.adjust("life", -1) }
    }

    @Test
    fun `an observer takes a seat again while one is free`() = runTest {
        val vm = watchingFriend(SessionSettings(maxPlayers = 4))
        vm.playInstead()
        verify { ws.play(20u) }
        verify { prefs.lastSessionObserving = false }
        assertFalse(vm.sessionUi.value.observing)

        serverMessages.emit(ServerMessage.State(users = listOf(bob, me), settings = SessionSettings(maxPlayers = 4)))
        advanceUntilIdle()
        vm.adjust("life", -1)
        advanceUntilIdle()
        verify { ws.adjust("life", -1) }
    }

    @Test
    fun `taking a seat uses the start life the host set since we started watching`() = runTest {
        val vm = watchingFriend(SessionSettings(startLife = 20u, maxPlayers = 4))
        serverMessages.emit(ServerMessage.State(users = listOf(bob), settings = SessionSettings(startLife = 40u, maxPlayers = 4)))
        advanceUntilIdle()

        vm.playInstead()
        verify { ws.play(40u) }
        verify(exactly = 0) { ws.play(20u) }
    }

    @Test
    fun `an observer cannot take a seat in a full game`() = runTest {
        val vm = watchingFriend(SessionSettings(maxPlayers = 1))
        vm.playInstead()
        verify(exactly = 0) { ws.play(any()) }
        assertTrue(vm.sessionUi.value.observing)
    }

    @Test
    fun `resuming rejoins as an observer when we were watching`() = runTest {
        every { prefs.lastSessionId } returns "sid-w"
        every { prefs.lastSessionObserving } returns true
        coEvery { api.getSessionById("sid-w") } returns
            SessionInfoResponse("sid-w", "WATCH123", 1, SessionSettings(maxPlayers = 1), userIds = setOf("bob"))
        val vm = makeVm()
        vm.resumeLastSession()
        advanceUntilIdle()

        verify { ws.connect(asObserver = true) }
        assertTrue(vm.sessionUi.value.observing)
    }
}
