package com.kregosh.mtglifetracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kregosh.mtglifetracker.data.UserPrefs
import com.kregosh.mtglifetracker.network.SessionApi
import com.kregosh.mtglifetracker.network.SessionConnection
import com.kregosh.mtglifetracker.network.WsState
import com.kregosh.mtglifetracker.shared.ServerMessage
import com.kregosh.mtglifetracker.shared.StatType
import com.kregosh.mtglifetracker.shared.UserState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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
    val sessionCode              : String               = "",
    val myUserId                 : String               = "",
    val users                    : List<UserState>      = emptyList(),
    val statDefs                 : Map<String, StatType> = emptyMap(),
    val globalStats              : Map<String, UInt>    = emptyMap(),
    val wsState                  : WsState              = WsState.Connecting,
    val error                    : String?              = null,
    val commanderDeathThreshold  : UInt                 = 21u,
    val infectDeathThreshold     : UInt                 = 10u,
)

fun UserState.isDead(state: SessionUiState): Boolean =
    life == 0u
        || (customStats["commander"] ?: 0u) >= state.commanderDeathThreshold
        || (customStats["poison"]    ?: 0u) >= state.infectDeathThreshold

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
    val isStormPreset: StateFlow<Boolean> = _backgroundImageUri
        .map { it?.contains("bg_arcane_storm") == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, prefs.backgroundImageUri?.contains("bg_arcane_storm") == true)

    private val _cardBackgroundImageUri = MutableStateFlow(prefs.cardBackgroundImageUri)
    val cardBackgroundImageUri: StateFlow<String?> = _cardBackgroundImageUri.asStateFlow()

    private val _colorScheme = MutableStateFlow(prefs.colorScheme)
    val colorScheme: StateFlow<String> = _colorScheme.asStateFlow()

    private var webSocket: SessionConnection? = null
    private var wsCollectorJob: Job? = null
    private var settingsReturnTo: Screen = Screen.Home

    // ── timer settings ────────────────────────────────────────────────

    private val _timerVisible    = MutableStateFlow(prefs.timerVisible)
    val timerVisible: StateFlow<Boolean> = _timerVisible.asStateFlow()

    private val _timerCountDown  = MutableStateFlow(prefs.timerCountDown)
    val timerCountDown: StateFlow<Boolean> = _timerCountDown.asStateFlow()

    private val _timerLimitMinutes = MutableStateFlow(prefs.timerLimitMinutes)
    val timerLimitMinutes: StateFlow<UInt> = _timerLimitMinutes.asStateFlow()

    fun setTimerVisible(v: Boolean)       { prefs.timerVisible = v;      _timerVisible.value = v }
    fun setTimerCountDown(v: Boolean)     { prefs.timerCountDown = v;    _timerCountDown.value = v; resetTimer() }
    fun setTimerLimitMinutes(v: UInt)     { prefs.timerLimitMinutes = v; _timerLimitMinutes.value = v; resetTimer() }

    // ── game timer ────────────────────────────────────────────────────

    private val _timerRunning = MutableStateFlow(false)
    val timerRunning: StateFlow<Boolean> = _timerRunning.asStateFlow()

    private val _timerElapsed = MutableStateFlow(Duration.ZERO)
    val timerElapsed: StateFlow<Duration> = _timerElapsed.asStateFlow()

    private var timerJob: Job? = null
    private var timerMark: TimeMark? = null
    private var timerAccumulated: Duration = Duration.ZERO

    fun startPauseTimer() {
        if (_timerRunning.value) pauseTimer() else startTimer()
    }

    private fun startTimer() {
        timerMark = TimeSource.Monotonic.markNow()
        _timerRunning.value = true
        timerJob = viewModelScope.launch {
            val limit = if (prefs.timerCountDown) prefs.timerLimitMinutes.toLong().minutes else null
            while (isActive) {
                delay(500)
                val now = timerAccumulated + (timerMark?.elapsedNow() ?: Duration.ZERO)
                if (limit != null && now >= limit) {
                    _timerElapsed.value = limit
                    pauseTimer()
                    break
                }
                _timerElapsed.value = now
            }
        }
    }

    private fun pauseTimer() {
        timerAccumulated += timerMark?.elapsedNow() ?: Duration.ZERO
        timerMark = null
        _timerRunning.value = false
        timerJob?.cancel()
        timerJob = null
    }

    fun resetTimer() {
        timerJob?.cancel()
        timerJob = null
        timerMark = null
        timerAccumulated = Duration.ZERO
        _timerRunning.value = false
        _timerElapsed.value = Duration.ZERO
    }

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
    val commanderDefaultEnabled: Boolean get() = prefs.commanderDefaultEnabled

    fun setStartLife(v: UInt) { prefs.startLife = v }
    fun setCommanderThreshold(v: UInt) { prefs.commanderDeathThreshold = v }
    fun setInfectThreshold(v: UInt) { prefs.infectDeathThreshold = v }
    fun setCommanderDefaultEnabled(v: Boolean) { prefs.commanderDefaultEnabled = v }

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
            delay(400)
            val accumulated = pendingDeltas.remove(stat) ?: return@launch
            debounceJobs.remove(stat)
            webSocket?.adjust(stat, accumulated)
        }
    }

    fun addCustomStat(name: String, type: StatType = StatType.NUMERIC) =
        webSocket?.addCustomStat(name, type)

    fun removeCustomStat(name: String) = webSocket?.removeCustomStat(name)

    fun setGlobal(stat: String, value: UInt) = webSocket?.setGlobal(stat, value)

    fun toggleGlobal(stat: String) {
        val current = _sessionUi.value.globalStats[stat] ?: 0u
        setGlobal(stat, if (current == 0u) 1u else 0u)
    }

    fun leaveSession() {
        tearDownWebSocket()
        _screen.value = Screen.Home
    }

    // ── internal ─────────────────────────────────────────────────────

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
            "life" -> copy(life = clamp(life.toLong() + delta))
            else   -> copy(customStats = customStats.toMutableMap().also { map ->
                val current = map[stat] ?: 0u
                map[stat] = clamp(current.toLong() + delta)
            })
        }
    }

    private fun joinSession(sessionId: String, sessionCode: String) {
        val current = _screen.value
        if (current is Screen.Session && current.sessionId == sessionId) return

        tearDownWebSocket()

        val myId = prefs.userId
        val name = prefs.displayName.ifBlank { "Player" }

        _sessionUi.value = SessionUiState(
            sessionCode             = sessionCode,
            myUserId                = myId,
            commanderDeathThreshold = prefs.commanderDeathThreshold,
            infectDeathThreshold    = prefs.infectDeathThreshold,
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
                                    users       = applyPendingDeltas(msg.users),
                                    statDefs    = msg.statDefs,
                                    globalStats = msg.globalStats,
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

        if (prefs.commanderDefaultEnabled) {
            ws.addCustomStat("commander", StatType.NUMERIC)
        }
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
        resetTimer()
    }

    override fun onCleared() {
        super.onCleared()
        tearDownWebSocket()
        api.close()
    }
}
