package com.kregosh.mtglifetracker.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kregosh.mtglifetracker.data.UserPreferences
import com.kregosh.mtglifetracker.data.UserPrefs
import com.kregosh.mtglifetracker.network.FirebaseSessionApi
import com.kregosh.mtglifetracker.network.FirebaseSessionConnection
import com.kregosh.mtglifetracker.network.SessionApi
import com.kregosh.mtglifetracker.network.SessionConnection
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
    object Home     : Screen
    data class Session(val sessionId: String) : Screen
    object Settings : Screen
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
    val commanderDeathThreshold: UInt = 21u,
    val infectDeathThreshold   : UInt = 10u,
)

fun UserState.isDead(state: SessionUiState): Boolean =
    life == 0u
        || commanderDamage >= state.commanderDeathThreshold
        || poisonDamage    >= state.infectDeathThreshold

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class SessionViewModel(
    private val prefs: UserPrefs,
    private val api: SessionApi,
    private val wsFactory: (sessionId: String, userId: String, displayName: String, startLife: UInt) -> SessionConnection,
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

    private val _cardBackgroundImageUri = MutableStateFlow(prefs.cardBackgroundImageUri)
    val cardBackgroundImageUri: StateFlow<String?> = _cardBackgroundImageUri.asStateFlow()

    private val _colorScheme = MutableStateFlow(prefs.colorScheme)
    val colorScheme: StateFlow<String> = _colorScheme.asStateFlow()

    private var webSocket: SessionConnection? = null
    private var wsCollectorJob: Job? = null
    private var settingsReturnTo: Screen = Screen.Home

    // Debounce state: last server-confirmed snapshot + per-stat pending deltas
    private var serverUsers   = emptyList<UserState>()
    private val pendingDeltas = mutableMapOf<String, Int>()
    private val debounceJobs  = mutableMapOf<String, Job>()

    // ── display name ─────────────────────────────────────────────────

    val displayName: String get() = prefs.displayName

    fun setDisplayName(name: String) { prefs.displayName = name.trim() }

    // ── background images ─────────────────────────────────────────────

    fun setBackgroundImage(uri: String?) {
        prefs.backgroundImageUri = uri
        _backgroundImageUri.value = uri
    }

    fun setCardBackgroundImage(uri: String?) {
        prefs.cardBackgroundImageUri = uri
        _cardBackgroundImageUri.value = uri
    }

    // ── game settings ─────────────────────────────────────────────────

    val startLife: UInt get() = prefs.startLife
    val commanderThreshold: UInt get() = prefs.commanderDeathThreshold
    val infectThreshold: UInt get() = prefs.infectDeathThreshold

    fun setStartLife(v: UInt) { prefs.startLife = v }
    fun setCommanderThreshold(v: UInt) { prefs.commanderDeathThreshold = v }
    fun setInfectThreshold(v: UInt) { prefs.infectDeathThreshold = v }

    fun setColorScheme(scheme: String) {
        prefs.colorScheme = scheme
        _colorScheme.value = scheme
    }

    // ── navigation ────────────────────────────────────────────────────

    fun openSettings() {
        settingsReturnTo = _screen.value
        _screen.value = Screen.Settings
    }

    fun closeSettings() {
        _screen.value = settingsReturnTo
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

    fun adjust(stat: String, delta: Int) {
        webSocket ?: return
        pendingDeltas[stat] = (pendingDeltas[stat] ?: 0) + delta
        _sessionUi.update { it.copy(users = applyPendingDeltas(serverUsers)) }
        debounceJobs[stat]?.cancel()
        debounceJobs[stat] = viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            val accumulated = pendingDeltas.remove(stat) ?: return@launch
            debounceJobs.remove(stat)
            webSocket?.adjust(stat, accumulated)
        }
    }

    private fun applyPendingDeltas(users: List<UserState>): List<UserState> {
        if (pendingDeltas.isEmpty()) return users
        val myId = _sessionUi.value.myUserId
        return users.map { user ->
            if (user.id != myId) user
            else pendingDeltas.entries.fold(user) { u, (stat, delta) -> u.withDelta(stat, delta) }
        }
    }

    private fun UserState.withDelta(stat: String, delta: Int): UserState {
        fun clamp(v: Long) = v.coerceIn(0L, UInt.MAX_VALUE.toLong()).toUInt()
        return when (stat) {
            "life"      -> copy(life = clamp(life.toLong() + delta))
            "commander" -> copy(commanderDamage = clamp(commanderDamage.toLong() + delta))
            "poison"    -> copy(poisonDamage = clamp(poisonDamage.toLong() + delta))
            else        -> copy(customStats = customStats.toMutableMap().also { map ->
                map[stat]?.let { map[stat] = clamp(it.toLong() + delta) }
            })
        }
    }

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

        _sessionUi.value = SessionUiState(
            sessionCode              = sessionCode,
            myUserId                 = myId,
            commanderDeathThreshold  = prefs.commanderDeathThreshold,
            infectDeathThreshold     = prefs.infectDeathThreshold,
        )
        _screen.value = Screen.Session(sessionId)

        val ws = wsFactory(sessionId, myId, name, prefs.startLife)
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
                        is ServerMessage.State  -> {
                            serverUsers = msg.users
                            _sessionUi.update {
                                it.copy(
                                    users           = applyPendingDeltas(msg.users),
                                    customStatNames = msg.customStatNames,
                                )
                            }
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
        debounceJobs.values.forEach { it.cancel() }
        debounceJobs.clear()
        pendingDeltas.clear()
        serverUsers = emptyList()
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
                        api       = FirebaseSessionApi(),
                        wsFactory = { id, uid, name, startLife -> FirebaseSessionConnection(id, uid, name, startLife) },
                    ) as T
            }
    }
}
