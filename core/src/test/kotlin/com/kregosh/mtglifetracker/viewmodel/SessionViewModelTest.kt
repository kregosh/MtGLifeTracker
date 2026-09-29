package com.kregosh.mtglifetracker.viewmodel

import com.kregosh.mtglifetracker.data.UserPrefs
import com.kregosh.mtglifetracker.network.SessionApi
import com.kregosh.mtglifetracker.network.SessionConnection
import com.kregosh.mtglifetracker.network.WsState
import io.mockk.coVerify
import com.kregosh.mtglifetracker.shared.CreateSessionResponse
import com.kregosh.mtglifetracker.shared.ServerMessage
import com.kregosh.mtglifetracker.shared.SessionInfoResponse
import com.kregosh.mtglifetracker.shared.UserState
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.test.*

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

    private fun makeVm() = SessionViewModel(prefs, api, wsFactory)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { prefs.userId }      returns "test-user-id"
        every { prefs.displayName } returns "Test Player"
        every { prefs.backgroundImageUri }    returns null
        every { prefs.cardBackgroundImageUri } returns null
        every { prefs.startLife }             returns 20u
        every { prefs.commanderDeathThreshold } returns 21u
        every { prefs.infectDeathThreshold }  returns 10u
        every { prefs.colorScheme }           returns "dark"
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
        coEvery { api.createSession() } returns CreateSessionResponse("sid-1", "ABC123")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        assertIs<Screen.Session>(vm.screen.value)
        assertEquals("sid-1", (vm.screen.value as Screen.Session).sessionId)
        verify { ws.connect() }
    }

    @Test
    fun `createSession on failure sets homeError`() = runTest {
        coEvery { api.createSession() } throws RuntimeException("network error")

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
    fun `State message updates sessionUi users and customStatNames`() = runTest {
        coEvery { api.createSession() } returns CreateSessionResponse("sid-1", "CODE01")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        val users = listOf(UserState("test-user-id", "Test Player", life = 20u))
        wsMessages.emit(ServerMessage.State(users, customStatNames = listOf("Energy")))
        advanceUntilIdle()

        assertEquals(users, vm.sessionUi.value.users)
        assertEquals(listOf("Energy"), vm.sessionUi.value.customStatNames)
    }

    @Test
    fun `leaveSession navigates back to Home`() = runTest {
        coEvery { api.createSession() } returns CreateSessionResponse("sid-1", "CODE01")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        vm.leaveSession()

        assertEquals(Screen.Home, vm.screen.value)
        verify { ws.close() }
    }

    @Test
    fun `adjust delegates to websocket after debounce`() = runTest {
        coEvery { api.createSession() } returns CreateSessionResponse("sid-1", "CODE01")

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
        coEvery { api.createSession() } returns CreateSessionResponse("sid-1", "CODE01")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        // Five rapid taps on life — should collapse to one send with delta=5
        repeat(5) { vm.adjust("life", 1) }
        advanceTimeBy(500)
        verify(exactly = 1) { ws.adjust("life", 5) }
    }

    @Test
    fun `adjust shows optimistic state immediately without waiting for server`() = runTest {
        coEvery { api.createSession() } returns CreateSessionResponse("sid-1", "CODE01")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        // Seed a server state: life=20
        val base = listOf(UserState("test-user-id", "Test Player", life = 20u))
        wsMessages.emit(ServerMessage.State(base))
        advanceUntilIdle()

        // Tap -3 — optimistic update must appear before debounce fires
        vm.adjust("life", -3)
        assertEquals(17u, vm.sessionUi.value.users.first().life)
    }

    @Test
    fun `adjust reapplies pending deltas when server State arrives mid-debounce`() = runTest {
        coEvery { api.createSession() } returns CreateSessionResponse("sid-1", "CODE01")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        val base = listOf(UserState("test-user-id", "Test Player", life = 20u))
        wsMessages.emit(ServerMessage.State(base))
        advanceUntilIdle()

        vm.adjust("life", -3)  // pending: -3 (debounce not yet fired)

        // Server broadcasts a concurrent update (another player triggered a State snapshot)
        // reflecting the old life=20 because our delta hasn't been sent yet
        wsMessages.emit(ServerMessage.State(base))
        advanceUntilIdle()

        // Pending delta must be reapplied on top → still shows 17
        assertEquals(17u, vm.sessionUi.value.users.first().life)
    }

    @Test
    fun `addCustomStat delegates to websocket`() = runTest {
        coEvery { api.createSession() } returns CreateSessionResponse("sid-1", "CODE01")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        vm.addCustomStat("Energy")
        verify { ws.addCustomStat("Energy") }
    }

    @Test
    fun `displayName comes from prefs`() {
        assertEquals("Test Player", makeVm().displayName)
    }

    @Test
    fun `stale collectors do not fire after leaveSession`() = runTest {
        coEvery { api.createSession() } returns CreateSessionResponse("sid-1", "CODE01")

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
        coEvery { api.createSession() } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()
        assertFalse(vm.homeLoading.value)
    }

    @Test
    fun `Joined message updates sessionCode in sessionUi`() = runTest {
        coEvery { api.createSession() } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        wsMessages.emit(ServerMessage.Joined("test-user-id", "NEWCOD"))
        advanceUntilIdle()

        assertEquals("NEWCOD", vm.sessionUi.value.sessionCode)
    }

    @Test
    fun `Error message updates error field in sessionUi`() = runTest {
        coEvery { api.createSession() } returns CreateSessionResponse("sid-1", "CODE01")
        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        wsMessages.emit(ServerMessage.Error("Join first"))
        advanceUntilIdle()

        assertEquals("Join first", vm.sessionUi.value.error)
    }

    @Test
    fun `wsState change propagates to sessionUi`() = runTest {
        coEvery { api.createSession() } returns CreateSessionResponse("sid-1", "CODE01")
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
        vm.addCustomStat("Energy")
    }

    @Test
    fun `rejoin cancels collectors from previous session`() = runTest {
        val ws2Messages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 16)
        val ws2State    = MutableStateFlow<WsState>(WsState.Connecting)
        val ws2         = mockk<SessionConnection>(relaxed = true) {
            every { messages }        returns ws2Messages
            every { connectionState } returns ws2State
        }

        coEvery { api.createSession() } returnsMany listOf(
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
}
