package com.kregosh.mtglifetracker.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kregosh.mtglifetracker.data.UserPreferences
import com.kregosh.mtglifetracker.data.UserPrefs
import com.kregosh.mtglifetracker.network.ApiClient
import com.kregosh.mtglifetracker.network.SessionApi
import com.kregosh.mtglifetracker.network.SessionConnection
import com.kregosh.mtglifetracker.network.SessionWebSocket
import com.kregosh.mtglifetracker.network.WsState
import com.kregosh.mtglifetracker.shared.ServerMessage
import com.kregosh.mtglifetracker.shared.UserState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Navigation state
// ─────────────────────────────────────────────────────────────────────────────

sealed interface Screen {
    object Home : Screen
    data class Session(val sessionId: String) : Screen
}

// ─────────────────────────────────────────────────────────────────────────────
// UI state for a live session
// ─────────────────────────────────────────────────────────────────────────────

data class SessionUiState(
    val sessionCode      : String          = "",
    val myUserId         : String          = "",
    val users            : List<UserState> = emptyList(),
    val customStatNames  : List<String>    = emptyList(),
    val wsState          : WsState         = WsState.Connecting,
    val error            : String?         = null,
    /** Players are eliminated at or above this commander damage total. */
    val commanderDeathThreshold: UInt = 21u,
    /** Players are eliminated at or above this poison counter total. */
    val poisonDeathThreshold   : UInt = 10u,
)

fun UserState.isDead(state: SessionUiState): Boolean =
    life == 0u
        || commanderDamage >= state.commanderDeathThreshold
        || poisonDamage    >= state.poisonDeathThreshold

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class SessionViewModel(
    private val prefs: UserPrefs,
    private val api: SessionApi,
    private val wsFactory: (sessionId: String, userId: String, displayName: String) -> SessionConnection,
) : ViewModel() {

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _sessionUi = MutableStateFlow(SessionUiState())
    val sessionUi: StateFlow<SessionUiState> = _sessionUi.asStateFlow()

    private val _homeLoading = MutableStateFlow(false)
    val homeLoading: StateFlow<Boolean> = _homeLoading.asStateFlow()

    private val _homeError = MutableStateFlow<String?>(null)
    val homeError: StateFlow<String?> = _homeError.asStateFlow()

    private val _backgroundImageUri = MutableStateFlow(prefs.backgroundImageUri)
    val backgroundImageUri: StateFlow<String?> = _backgroundImageUri.asStateFlow()

    private var webSocket: SessionConnection? = null
    private var wsCollectorJob: Job? = null

    // ── display name ─────────────────────────────────────────────────

    val displayName: String get() = prefs.displayName

    fun setDisplayName(name: String) { prefs.displayName = name.trim() }

    // ── background image ─────────────────────────────────────────────

    fun setBackgroundImage(uri: String?) {
        prefs.backgroundImageUri = uri
        _backgroundImageUri.value = uri
    }

    // ── home screen actions ──────────────────────────────────────────

    fun createSession() {
        viewModelScope.launch {
            _homeLoading.value = true
            _homeError.value   = null
            runCatching { api.createSession() }
                .onSuccess { resp -> joinSession(resp.sessionId, resp.sessionCode) }
                .onFailure { _homeError.value = it.message ?: "Failed to create session" }
            _homeLoading.value = false
        }
    }

    fun joinByCode(code: String) {
        viewModelScope.launch {
            _homeLoading.value = true
            _homeError.value   = null
            runCatching { api.getSessionByCode(code.trim().uppercase()) }
                .onSuccess { info -> joinSession(info.sessionId, info.sessionCode) }
                .onFailure { _homeError.value = "Session not found" }
            _homeLoading.value = false
        }
    }

    fun handleInviteLink(code: String) {
        if (code.isNotBlank()) joinByCode(code)
    }

    // ── session screen actions ───────────────────────────────────────

    fun adjust(stat: String, delta: Int) = webSocket?.adjust(stat, delta)

    fun addCustomStat(name: String) = webSocket?.addCustomStat(name)

    fun leaveSession() {
        tearDownWebSocket()
        _screen.value = Screen.Home
    }

    // ── internal ─────────────────────────────────────────────────────

    private fun joinSession(sessionId: String, sessionCode: String) {
        tearDownWebSocket()

        val myId = prefs.userId
        val name = prefs.displayName.ifBlank { "Player" }

        _sessionUi.value = SessionUiState(sessionCode = sessionCode, myUserId = myId)
        _screen.value    = Screen.Session(sessionId)

        val ws = wsFactory(sessionId, myId, name)
        webSocket = ws

        wsCollectorJob = viewModelScope.launch {
            launch {
                ws.connectionState.collect { state ->
                    _sessionUi.update { it.copy(wsState = state) }
                }
            }
            launch {
                ws.messages.collect { msg ->
                    when (msg) {
                        is ServerMessage.State  -> _sessionUi.update {
                            it.copy(users = msg.users, customStatNames = msg.customStatNames)
                        }
                        is ServerMessage.Joined -> _sessionUi.update {
                            it.copy(sessionCode = msg.sessionCode)
                        }
                        is ServerMessage.Error  -> _sessionUi.update { it.copy(error = msg.message) }
                    }
                }
            }
        }

        ws.connect()
    }

    private fun tearDownWebSocket() {
        wsCollectorJob?.cancel()
        wsCollectorJob = null
        webSocket?.close()
        webSocket = null
    }

    override fun onCleared() {
        super.onCleared()
        tearDownWebSocket()
        api.close()
    }

    // ── production factory ────────────────────────────────────────────

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SessionViewModel(
                        prefs     = UserPreferences(app),
                        api       = ApiClient(),
                        wsFactory = { id, uid, name -> SessionWebSocket(id, uid, name) },
                    ) as T
            }
    }
}
