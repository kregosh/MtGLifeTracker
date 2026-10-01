package com.kregosh.mtglifetracker.viewmodel

import com.kregosh.mtglifetracker.data.Friend
import com.kregosh.mtglifetracker.data.KnownPlayer
import com.kregosh.mtglifetracker.data.UserPrefs
import com.kregosh.mtglifetracker.network.SessionApi
import com.kregosh.mtglifetracker.network.SessionConnection
import com.kregosh.mtglifetracker.network.WsState
import io.mockk.coVerify
import com.kregosh.mtglifetracker.shared.CreateSessionResponse
import com.kregosh.mtglifetracker.shared.ServerMessage
import com.kregosh.mtglifetracker.shared.SessionInfoResponse
import com.kregosh.mtglifetracker.shared.SessionSettings
import com.kregosh.mtglifetracker.shared.commanderDamageStat
import com.kregosh.mtglifetracker.shared.StatType
import com.kregosh.mtglifetracker.shared.UserState
import com.kregosh.mtglifetracker.viewmodel.RESERVED_STAT_NAMES
import io.mockk.*
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
    private val wsMessages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 16)
    private val wsState    = MutableStateFlow<WsState>(WsState.Connecting)
    private val ws         = mockk<SessionConnection>(relaxed = true) {
        every { messages }        returns wsMessages
        every { connectionState } returns wsState
    }
    private val wsFactory  = mockk<(String, String, String, UInt) -> SessionConnection>()

    private fun makeVm() = SessionViewModel(prefs, api, wsFactory, testDispatcher.scheduler.timeSource)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { prefs.userId }      returns "test-user-id"
        every { prefs.displayName } returns "Test Player"
        every { prefs.backgroundImageUri }      returns null
        every { prefs.cardBackgroundImageUri }  returns null
        every { prefs.startLife }               returns 20u
        every { prefs.commanderDeathThreshold } returns 21u
        every { prefs.infectDeathThreshold }    returns 10u
        every { prefs.colorScheme }             returns "dark"
        every { prefs.commanderDefaultEnabled } returns false
        every { prefs.timerVisible }            returns true
        every { prefs.timerCountDown }          returns false
        every { prefs.timerLimitMinutes }       returns 60u
        every { prefs.knownPlayers }            returns emptyList()
        every { prefs.friendList }              returns emptyList()
        every { prefs.lastSessionId }           returns null
        every { wsFactory(any(), any(), any(), any()) } returns ws
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
        assertEquals("network error", vm.homeError.value)
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
    fun `joinByCode on failure sets homeError`() = runTest {
        coEvery { api.getSessionByCode(any()) } throws RuntimeException("not found")

        val vm = makeVm()
        vm.joinByCode("BADCOD")
        advanceUntilIdle()

        assertEquals("Session not found", vm.homeError.value)
    }

    @Test
    fun `State message updates sessionUi users and statDefs`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        val users = listOf(UserState("test-user-id", "Test Player", life = 20u))
        wsMessages.emit(ServerMessage.State(users, statDefs = mapOf("energy" to StatType.NUMERIC)))
        advanceUntilIdle()

        assertEquals(users, vm.sessionUi.value.users)
        assertEquals(mapOf("energy" to StatType.NUMERIC), vm.sessionUi.value.statDefs)
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
        wsMessages.emit(ServerMessage.State(base))
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
        wsMessages.emit(ServerMessage.State(base))
        advanceUntilIdle()

        vm.adjust("life", -3)

        wsMessages.emit(ServerMessage.State(base))
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
        wsMessages.emit(ServerMessage.State(staleUsers))
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
    fun `Joined message updates sessionCode in sessionUi`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        wsMessages.emit(ServerMessage.Joined("test-user-id", "NEWCOD"))
        advanceUntilIdle()

        assertEquals("NEWCOD", vm.sessionUi.value.sessionCode)
    }

    @Test
    fun `Error message updates error field in sessionUi`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        wsMessages.emit(ServerMessage.Error("Join first"))
        advanceUntilIdle()

        assertEquals("Join first", vm.sessionUi.value.error)
    }

    @Test
    fun `wsState change propagates to sessionUi`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        wsState.value = WsState.Connected
        advanceUntilIdle()

        assertEquals(WsState.Connected, vm.sessionUi.value.wsState)
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
        val ws2State    = MutableStateFlow<WsState>(WsState.Connecting)
        val ws2         = mockk<SessionConnection>(relaxed = true) {
            every { messages }        returns ws2Messages
            every { connectionState } returns ws2State
        }

        coEvery { api.createSession(any(), any()) } returnsMany listOf(
            CreateSessionResponse("sid-1", "CODE01"),
            CreateSessionResponse("sid-2", "CODE02"),
        )
        var callCount = 0
        every { wsFactory(any(), any(), any(), any()) } answers {
            if (callCount++ == 0) ws else ws2
        }

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()
        vm.createSession()
        advanceUntilIdle()

        val staleUsers = listOf(UserState("test-user-id", "Old Session", life = 5u))
        wsMessages.emit(ServerMessage.State(staleUsers))
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

        verify { ws.addCustomStat("commander", StatType.NUMERIC) }
    }

    @Test
    fun `globalStats are updated from State message`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        wsMessages.emit(ServerMessage.State(emptyList(), globalStats = mapOf("daynight" to 1u)))
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
        wsMessages.emit(ServerMessage.State(emptyList(), globalStats = mapOf("daynight" to 0u)))
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
            commanderDamage = mapOf("opponent" to 21u),
        )
        wsMessages.emit(ServerMessage.State(listOf(deadUser)))
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
        wsMessages.emit(ServerMessage.State(users))
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
        wsMessages.emit(ServerMessage.State(users))
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

        wsMessages.emit(ServerMessage.FriendRequest("user-2", "Alice"))
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

        wsMessages.emit(ServerMessage.FriendRequest("user-2", "Alice"))
        wsMessages.emit(ServerMessage.FriendRequest("user-2", "Alice"))
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

        wsMessages.emit(ServerMessage.FriendRequest("user-2", "Alice"))
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

        wsMessages.emit(ServerMessage.FriendRequest("user-2", "Alice"))
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
        wsMessages.emit(ServerMessage.FriendAccepted("user-3", "Bob"))
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

        wsMessages.emit(ServerMessage.FriendAccepted("stalker", "Mallory"))
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

        wsMessages.emit(ServerMessage.FriendRequest("user-2", "Alice"))
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
        wsMessages.emit(ServerMessage.State(users))
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

        wsMessages.emit(ServerMessage.State(listOf(UserState("test-user-id", "Test Player", life = 20u))))
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
        wsMessages.emit(ServerMessage.State(users))
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
        wsMessages.emit(ServerMessage.State(listOf(me, alice)))
        wsMessages.emit(ServerMessage.State(listOf(me, alice.copy(life = 19u))))
        wsMessages.emit(ServerMessage.State(listOf(me.copy(life = 18u), alice.copy(life = 17u))))
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
        wsMessages.emit(ServerMessage.State(listOf(me, UserState("other-id", "Alice"))))
        wsMessages.emit(ServerMessage.State(listOf(me, UserState("other-id", "Alicia"))))
        advanceUntilIdle()

        verify(exactly = 1) { prefs.touchKnownPlayer("other-id", "Alice") }
        verify(exactly = 1) { prefs.touchKnownPlayer("other-id", "Alicia") }
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

    @Test
    fun `forgetPlayer persists and updates knownPlayers flow`() {
        every { prefs.knownPlayers } returns emptyList()
        val vm = makeVm()
        vm.forgetPlayer("u1")
        verify { prefs.forgetKnownPlayer("u1") }
        assertTrue(vm.knownPlayers.value.isEmpty())
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
        assertEquals("session gone", vm.homeError.value)
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

        assertEquals("session expired", vm.homeError.value)
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
        every { wsFactory("sid", any(), any(), any()) } returns ws

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
        every { wsFactory("sid", any(), any(), any()) } returns ws

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
        wsMessages.emit(ServerMessage.State(users, hostUserId = hostUserId))
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `commander damage is tracked per opponent`() = runTest {
        val vm = inSession()

        vm.adjust(commanderDamageStat("bob"), 3)
        vm.adjust(commanderDamageStat("carol"), 5)

        val me = vm.sessionUi.value.users.single()
        assertEquals(mapOf("bob" to 3u, "carol" to 5u), me.commanderDamage)
        advanceUntilIdle()
        verify { ws.adjust(commanderDamageStat("bob"), 3) }
        verify { ws.adjust(commanderDamageStat("carol"), 5) }
    }

    @Test
    fun `createSession makes us host with our default game rules`() = runTest {
        every { prefs.startLife } returns 40u
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        coVerify { api.createSession("test-user-id", SessionSettings(startLife = 40u)) }
        verify { wsFactory("sid-1", "test-user-id", any(), 40u) }
    }

    @Test
    fun `joining uses the session's rules instead of local ones`() = runTest {
        val rules = SessionSettings(startLife = 30u, commanderDeathThreshold = 15u)
        coEvery { api.getSessionByCode("ABCDEFGH") } returns
            SessionInfoResponse("sid-2", "ABCDEFGH", 1, rules, setOf("host"))
        val vm = makeVm()
        vm.joinByCode("ABCDEFGH")
        advanceUntilIdle()

        verify { wsFactory("sid-2", any(), any(), 30u) }
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
        assertEquals("This session is full (2 players)", vm.homeError.value)
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
        wsMessages.emit(ServerMessage.State(
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
        wsMessages.emit(ServerMessage.State(listOf(UserState("other", "Bob"))))
        advanceUntilIdle()

        assertEquals(Screen.Home, vm.screen.value)
        assertEquals("You are no longer in that session", vm.homeError.value)
        verify { prefs.lastSessionId = null }
    }

    @Test
    fun `a state without our seat before we sat down is not a removal`() = runTest {
        coEvery { api.createSession(any(), any()) } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()
        wsMessages.emit(ServerMessage.State(listOf(UserState("other", "Bob"))))
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

        val history = vm.lifeHistory.value
        assertEquals(1, history.size)
        assertEquals(-3, history.single().delta)
        assertEquals(17u, history.single().lifeAfter)
    }

    @Test
    fun `other stats are not recorded in the life history`() = runTest {
        val vm = inSession()
        vm.adjust("poison", 1)
        advanceUntilIdle()
        assertTrue(vm.lifeHistory.value.isEmpty())
    }

    @Test
    fun `undo reverts the last life change without recording itself`() = runTest {
        val vm = inSession()
        vm.adjust("life", -5)
        advanceUntilIdle()
        wsMessages.emit(ServerMessage.State(listOf(UserState("test-user-id", "Test Player", life = 15u))))
        advanceUntilIdle()

        vm.undoLastLifeChange()
        assertEquals(20u, vm.sessionUi.value.users.single().life)
        advanceUntilIdle()

        verify { ws.adjust("life", -5) }
        verify { ws.adjust("life", 5) }
        assertTrue(vm.lifeHistory.value.isEmpty())
    }

    @Test
    fun `undo merged with new taps records only the taps`() = runTest {
        val vm = inSession()
        vm.adjust("life", -5)
        advanceUntilIdle()

        vm.undoLastLifeChange()
        vm.adjust("life", -2)
        advanceUntilIdle()

        verify { ws.adjust("life", 3) }
        assertEquals(listOf(-2), vm.lifeHistory.value.map { it.delta })
    }

    @Test
    fun `a new game clears the life history`() = runTest {
        val vm = inSession()
        vm.adjust("life", -5)
        advanceUntilIdle()
        assertEquals(1, vm.lifeHistory.value.size)

        wsMessages.emit(ServerMessage.State(listOf(UserState("test-user-id", "Test Player")), game = 1))
        advanceUntilIdle()

        assertTrue(vm.lifeHistory.value.isEmpty())
        assertEquals(1L, vm.sessionUi.value.game)
    }
}
