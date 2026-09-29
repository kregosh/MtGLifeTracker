package com.kregosh.mtglifetracker.viewmodel

import com.kregosh.mtglifetracker.data.UserPrefs
import com.kregosh.mtglifetracker.network.SessionApi
import com.kregosh.mtglifetracker.network.SessionConnection
import com.kregosh.mtglifetracker.network.WsState
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
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val prefs      = mockk<UserPrefs>(relaxed = true)
    private val api        = mockk<SessionApi>(relaxed = true)
    private val wsMessages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 16)
    private val wsState    = MutableStateFlow<WsState>(WsState.Connecting)
    private val ws         = mockk<SessionConnection>(relaxed = true) {
        every { messages }         returns wsMessages
        every { connectionState }  returns wsState
    }
    private val wsFactory  = mockk<(String, String, String) -> SessionConnection>()

    private fun makeVm() = SessionViewModel(prefs, api, wsFactory)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { prefs.userId }      returns "test-user-id"
        every { prefs.displayName } returns "Test Player"
        every { wsFactory(any(), any(), any()) } returns ws
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
    fun `State message updates sessionUi users`() = runTest {
        coEvery { api.createSession() } returns CreateSessionResponse("sid-1", "CODE01")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        val users = listOf(UserState("test-user-id", "Test Player", 20u))
        wsMessages.emit(ServerMessage.State(users))
        advanceUntilIdle()

        assertEquals(users, vm.sessionUi.value.users)
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
    fun `increment delegates to websocket`() = runTest {
        coEvery { api.createSession() } returns CreateSessionResponse("sid-1", "CODE01")

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()

        vm.increment()
        verify { ws.increment() }
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

        // Any update from the old ws should not reach sessionUi after teardown
        val staleUsers = listOf(UserState("test-user-id", "Test Player", 99u))
        wsMessages.emit(ServerMessage.State(staleUsers))
        advanceUntilIdle()

        // sessionUi.users should NOT be updated with the stale emission
        assertNotEquals(staleUsers, vm.sessionUi.value.users)
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
        every { wsFactory(any(), any(), any()) } answers {
            if (callCount++ == 0) ws else ws2
        }

        val vm = makeVm()
        vm.createSession()
        advanceUntilIdle()
        vm.createSession()      // rejoin: tears down old ws, sets up ws2
        advanceUntilIdle()

        // Stale emission from first ws should not reach current sessionUi
        val staleUsers = listOf(UserState("test-user-id", "Old Session", 5u))
        wsMessages.emit(ServerMessage.State(staleUsers))
        advanceUntilIdle()

        assertNotEquals(staleUsers, vm.sessionUi.value.users)
        verify { ws.close() }   // first ws was torn down
    }
}
