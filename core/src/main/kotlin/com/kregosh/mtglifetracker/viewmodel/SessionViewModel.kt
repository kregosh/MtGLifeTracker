package com.kregosh.mtglifetracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kregosh.mtglifetracker.data.Friend
import com.kregosh.mtglifetracker.data.KnownPlayer
import com.kregosh.mtglifetracker.data.UserPrefs
import com.kregosh.mtglifetracker.network.SessionApi
import com.kregosh.mtglifetracker.network.SessionConnection
import com.kregosh.mtglifetracker.network.WsState
import com.kregosh.mtglifetracker.shared.COMMANDER_STAT
import com.kregosh.mtglifetracker.shared.LIFE_STAT
import com.kregosh.mtglifetracker.shared.POISON_STAT
import com.kregosh.mtglifetracker.shared.ServerMessage
import com.kregosh.mtglifetracker.shared.SessionInfoResponse
import com.kregosh.mtglifetracker.shared.SessionSettings
import com.kregosh.mtglifetracker.shared.StatType
import com.kregosh.mtglifetracker.shared.UserState
import com.kregosh.mtglifetracker.shared.commanderDamageSource
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

// Stat names that would shadow Firebase data-model fields.
val RESERVED_STAT_NAMES = setOf("life", "displayname", "conceded", "customstats")

// Limits below mirror database.rules.json.
const val MAX_DISPLAY_NAME_LENGTH = 64
const val MAX_STAT_NAME_LENGTH    = 32

private val STAT_NAME_PATTERN = Regex("^[A-Za-z0-9 _'-]{1,$MAX_STAT_NAME_LENGTH}$")

/** Whether [name] can be stored as a stat: Firebase keys can't contain . # $ [ ] or /. */
fun isValidStatName(name: String): Boolean =
    STAT_NAME_PATTERN.matches(name) && name.lowercase() !in RESERVED_STAT_NAMES

// ─────────────────────────────────────────────────────────────────────────────
// Friend-request pending entry (local to ViewModel, not shared over network)
// ─────────────────────────────────────────────────────────────────────────────

data class FriendRequestInfo(val fromUserId: String, val fromDisplayName: String)

// ─────────────────────────────────────────────────────────────────────────────
// UI state for a live session
// ─────────────────────────────────────────────────────────────────────────────

data class SessionUiState(
    val sessionCode : String                = "",
    val myUserId    : String                = "",
    val users       : List<UserState>       = emptyList(),
    val statDefs    : Map<String, StatType> = emptyMap(),
    val globalStats : Map<String, UInt>     = emptyMap(),
    val wsState     : WsState               = WsState.Connecting,
    val error       : String?               = null,
    val settings    : SessionSettings       = SessionSettings(),
    val hostUserId  : String?               = null,
    val game        : Long                  = 0,
) {
    val isHost: Boolean get() = hostUserId != null && hostUserId == myUserId
}

/** Dead at 0 life, or at the threshold of commander damage from any single commander, or of poison. */
fun UserState.isDead(state: SessionUiState): Boolean =
    life == 0u
        || (commanderDamage.values.maxOrNull() ?: 0u) >= state.settings.commanderDeathThreshold
        || (customStats[POISON_STAT] ?: 0u) >= state.settings.infectDeathThreshold

/** One committed change to the local player's life total, newest first in the history. */
data class LifeChange(val id: Long, val delta: Int, val lifeAfter: UInt)

private const val MAX_LIFE_HISTORY = 50

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class SessionViewModel(
    private val prefs: UserPrefs,
    private val api: SessionApi,
    private val wsFactory: (sessionId: String, userId: String, displayName: String, startLife: UInt) -> SessionConnection,
    private val timeSource: TimeSource = TimeSource.Monotonic,
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
        timerMark = timeSource.markNow()
        _timerRunning.value = true
        timerJob = viewModelScope.launch {
            val limit = if (prefs.timerCountDown) prefs.timerLimitMinutes.toLong().minutes else null
            while (isActive) {
                delay(500)
                val now = timerAccumulated + (timerMark?.elapsedNow() ?: Duration.ZERO)
                if (limit != null && now >= limit) {
                    _timerElapsed.value = limit
                    timerAccumulated = limit  // cap before pauseTimer re-reads the clock
                    timerMark = null
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
    private var lastRoster    = emptyMap<String, String>()
    private var hadSeat       = false

    // ── life history / undo ───────────────────────────────────────────

    private val _lifeHistory = MutableStateFlow<List<LifeChange>>(emptyList())
    val lifeHistory: StateFlow<List<LifeChange>> = _lifeHistory.asStateFlow()

    private var nextLifeChangeId = 0L
    // Part of the pending life delta that comes from undo and must not be recorded again.
    private var unrecordedLifeDelta = 0
    private val pendingDeltas = mutableMapOf<String, Int>()
    private val debounceJobs  = mutableMapOf<String, Job>()

    // ── display name ─────────────────────────────────────────────────

    private val _displayName = MutableStateFlow(prefs.displayName)
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    fun setDisplayName(name: String) {
        val trimmed = name.trim().take(MAX_DISPLAY_NAME_LENGTH)
        if (trimmed.isEmpty()) return
        prefs.displayName = trimmed
        _displayName.value = trimmed
        webSocket?.setDisplayName(trimmed)
    }

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

    private val _startLife           = MutableStateFlow(prefs.startLife)
    val startLife: StateFlow<UInt>   = _startLife.asStateFlow()

    private val _commanderThreshold          = MutableStateFlow(prefs.commanderDeathThreshold)
    val commanderThreshold: StateFlow<UInt>  = _commanderThreshold.asStateFlow()

    private val _infectThreshold             = MutableStateFlow(prefs.infectDeathThreshold)
    val infectThreshold: StateFlow<UInt>     = _infectThreshold.asStateFlow()

    val commanderDefaultEnabled: Boolean get() = prefs.commanderDefaultEnabled

    private val _knownPlayers = MutableStateFlow(prefs.knownPlayers)
    val knownPlayers: StateFlow<List<KnownPlayer>> = _knownPlayers.asStateFlow()

    private val _friendList = MutableStateFlow(prefs.friendList)
    val friendList: StateFlow<List<Friend>> = _friendList.asStateFlow()

    private val _pendingFriendRequests = MutableStateFlow<List<FriendRequestInfo>>(emptyList())
    val pendingFriendRequests: StateFlow<List<FriendRequestInfo>> = _pendingFriendRequests.asStateFlow()

    private val _friendPresence = MutableStateFlow<Map<String, String?>>(emptyMap())
    val friendPresence: StateFlow<Map<String, String?>> = _friendPresence.asStateFlow()

    init {
        viewModelScope.launch {
            _friendList.flatMapLatest { friends ->
                if (friends.isEmpty()) flowOf(emptyMap())
                else api.observeFriendPresence(friends.map { it.userId })
            }.collect { _friendPresence.value = it }
        }
    }

    fun forgetPlayer(userId: String) {
        prefs.forgetKnownPlayer(userId)
        _knownPlayers.value = prefs.knownPlayers
    }

    fun addFriend(userId: String, displayName: String) {
        prefs.addFriend(userId, displayName)
        _friendList.value = prefs.friendList
    }

    fun removeFriend(userId: String) {
        prefs.removeFriend(userId)
        _friendList.value = prefs.friendList
    }

    // Acceptances are only honoured for requests we sent, so nobody can add themselves
    // to our friends list (and see where we play) without our consent.
    private val sentFriendRequests = mutableSetOf<String>()

    fun sendFriendRequest(toUserId: String) {
        val ws = webSocket ?: return
        sentFriendRequests += toUserId
        ws.sendFriendRequest(toUserId)
    }

    fun acceptFriendRequest(fromUserId: String, fromDisplayName: String) {
        prefs.addFriend(fromUserId, fromDisplayName)
        _friendList.value = prefs.friendList
        _pendingFriendRequests.update { it.filterNot { req -> req.fromUserId == fromUserId } }
        webSocket?.acceptFriendRequest(fromUserId)
    }

    fun declineFriendRequest(fromUserId: String) {
        _pendingFriendRequests.update { it.filterNot { req -> req.fromUserId == fromUserId } }
        webSocket?.declineFriendRequest(fromUserId)
    }

    fun setStartLife(v: UInt)          { prefs.startLife = v;                _startLife.value = v }
    fun setCommanderThreshold(v: UInt) { prefs.commanderDeathThreshold = v;  _commanderThreshold.value = v }
    fun setInfectThreshold(v: UInt)    { prefs.infectDeathThreshold = v;     _infectThreshold.value = v }
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

    private fun defaultSettings() = SessionSettings(
        startLife               = prefs.startLife,
        commanderDeathThreshold = prefs.commanderDeathThreshold,
        infectDeathThreshold    = prefs.infectDeathThreshold,
    )

    fun createSession() {
        viewModelScope.launch {
            _homeLoading.value = true
            _homeError.value   = null
            val settings = defaultSettings()
            runCatching { api.createSession(prefs.userId, settings) }
                .onSuccess { resp -> joinSession(resp.sessionId, resp.sessionCode, settings) }
                .onFailure { _homeError.value = it.message ?: "Failed to create session" }
            _homeLoading.value = false
        }
    }

    fun joinByCode(code: String) {
        viewModelScope.launch {
            _homeLoading.value = true
            _homeError.value   = null
            runCatching { api.getSessionByCode(code.trim().uppercase()) }
                .onSuccess { info -> joinIfRoom(info) }
                .onFailure { _homeError.value = "Session not found" }
            _homeLoading.value = false
        }
    }

    fun joinFriendSession(sessionId: String) {
        viewModelScope.launch {
            _homeLoading.value = true
            _homeError.value   = null
            runCatching { api.getSessionById(sessionId) }
                .onSuccess { info -> joinIfRoom(info) }
                .onFailure { _homeError.value = it.message ?: "Failed to join session" }
            _homeLoading.value = false
        }
    }

    fun handleInviteLink(code: String) {
        if (code.isNotBlank()) joinByCode(code)
    }

    /** Rejoins the session the app was in when it was last closed or killed. */
    fun resumeLastSession() {
        val sessionId = prefs.lastSessionId ?: return
        if (_screen.value !is Screen.Home) return
        viewModelScope.launch {
            _homeLoading.value = true
            runCatching { api.getSessionById(sessionId) }
                .onSuccess { info -> if (!joinIfRoom(info, quiet = true)) prefs.lastSessionId = null }
                .onFailure { prefs.lastSessionId = null }
            _homeLoading.value = false
        }
    }

    private fun joinIfRoom(info: SessionInfoResponse, quiet: Boolean = false): Boolean {
        val max = info.settings?.maxPlayers ?: 0
        if (max > 0 && prefs.userId !in info.userIds && info.userIds.size >= max) {
            if (!quiet) _homeError.value = "This session is full ($max players)"
            return false
        }
        joinSession(info.sessionId, info.sessionCode, info.settings)
        return true
    }

    // ── session screen actions ───────────────────────────────────────

    fun adjust(stat: String, delta: Int) {
        webSocket ?: return
        queueDelta(stat, delta)
    }

    /** Reverts the most recent recorded change to the local player's life. */
    fun undoLastLifeChange() {
        webSocket ?: return
        val last = _lifeHistory.value.firstOrNull() ?: return
        _lifeHistory.update { it.drop(1) }
        unrecordedLifeDelta -= last.delta
        queueDelta(LIFE_STAT, -last.delta)
    }

    // Rapid taps are coalesced into one network write per stat.
    private fun queueDelta(stat: String, delta: Int) {
        pendingDeltas[stat] = (pendingDeltas[stat] ?: 0) + delta
        _sessionUi.update { it.copy(users = applyPendingDeltas(serverUsers)) }
        debounceJobs[stat]?.cancel()
        debounceJobs[stat] = viewModelScope.launch {
            delay(400)
            val accumulated = pendingDeltas.remove(stat) ?: return@launch
            debounceJobs.remove(stat)
            if (stat == LIFE_STAT) recordLifeChange(accumulated - unrecordedLifeDelta)
            if (stat == LIFE_STAT) unrecordedLifeDelta = 0
            if (accumulated != 0) webSocket?.adjust(stat, accumulated)
        }
    }

    private fun recordLifeChange(delta: Int) {
        if (delta == 0) return
        val me = _sessionUi.value.users.find { it.id == _sessionUi.value.myUserId } ?: return
        val change = LifeChange(nextLifeChangeId++, delta, me.life)
        _lifeHistory.update { (listOf(change) + it).take(MAX_LIFE_HISTORY) }
    }

    // ── host controls ─────────────────────────────────────────────────

    fun updateSessionSettings(settings: SessionSettings) {
        if (!_sessionUi.value.isHost) return
        webSocket?.updateSettings(settings)
    }

    fun startNewGame() {
        if (!_sessionUi.value.isHost) return
        webSocket?.startNewGame()
    }

    fun removePlayer(userId: String) {
        val ui = _sessionUi.value
        if (!ui.isHost || userId == ui.myUserId) return
        webSocket?.removePlayer(userId)
    }

    fun addCustomStat(name: String, type: StatType = StatType.NUMERIC) {
        val trimmed = name.trim()
        if (!isValidStatName(trimmed)) return
        webSocket?.addCustomStat(trimmed, type)
    }

    fun removeCustomStat(name: String) = webSocket?.removeCustomStat(name)

    fun setGlobal(stat: String, value: UInt) = webSocket?.setGlobal(stat, value)

    fun concede() {
        val myId = _sessionUi.value.myUserId
        serverUsers = serverUsers.map { if (it.id == myId) it.copy(conceded = true) else it }
        _sessionUi.update { it.copy(users = applyPendingDeltas(serverUsers)) }
        webSocket?.setConceded(true)
    }

    fun unconcede() {
        val myId = _sessionUi.value.myUserId
        serverUsers = serverUsers.map { if (it.id == myId) it.copy(conceded = false) else it }
        _sessionUi.update { it.copy(users = applyPendingDeltas(serverUsers)) }
        webSocket?.setConceded(false)
    }

    fun toggleGlobal(stat: String) {
        val current = _sessionUi.value.globalStats[stat] ?: 0u
        setGlobal(stat, if (current == 0u) 1u else 0u)
    }

    fun leaveSession() {
        prefs.lastSessionId = null
        tearDownWebSocket(removePlayer = true)
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
        val opponent = commanderDamageSource(stat)
        return when {
            stat == LIFE_STAT -> copy(life = clamp(life.toLong() + delta))
            opponent != null  -> copy(commanderDamage = commanderDamage +
                (opponent to clamp((commanderDamage[opponent] ?: 0u).toLong() + delta)))
            else -> copy(customStats = customStats +
                (stat to clamp((customStats[stat] ?: 0u).toLong() + delta)))
        }
    }

    // State arrives on every life change; only touch prefs when someone joins or is renamed.
    private fun rememberPlayers(users: List<UserState>) {
        val myId   = _sessionUi.value.myUserId
        val roster = users
            .filter { it.id != myId && it.displayName.isNotBlank() }
            .associate { it.id to it.displayName }
        val changed = roster.filter { (id, name) -> lastRoster[id] != name }
        lastRoster = roster
        if (changed.isEmpty()) return

        val friendIds = _friendList.value.map { it.userId }.toSet()
        var friendDirty = false
        changed.forEach { (id, name) ->
            prefs.touchKnownPlayer(id, name)
            if (id in friendIds) {
                prefs.addFriend(id, name)
                friendDirty = true
            }
        }
        _knownPlayers.value = prefs.knownPlayers
        if (friendDirty) _friendList.value = prefs.friendList
    }

    private fun joinSession(sessionId: String, sessionCode: String, settings: SessionSettings?) {
        val current = _screen.value
        if (current is Screen.Session && current.sessionId == sessionId) return

        tearDownWebSocket()

        val myId = prefs.userId
        val name = prefs.displayName.ifBlank { "Player" }

        val rules = settings ?: defaultSettings()
        _sessionUi.value = SessionUiState(
            sessionCode = sessionCode,
            myUserId    = myId,
            settings    = rules,
        )
        _screen.value = Screen.Session(sessionId)
        prefs.lastSessionId = sessionId

        val ws = wsFactory(sessionId, myId, name, rules.startLife)
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
                            if (msg.users.any { it.id == myId }) {
                                hadSeat = true
                            } else if (hadSeat) {
                                removedFromSession()
                                return@collect
                            }
                            if (msg.game > _sessionUi.value.game) {
                                resetTimer()
                                _lifeHistory.value = emptyList()
                            }
                            serverUsers = msg.users
                            _sessionUi.update {
                                it.copy(
                                    users       = applyPendingDeltas(msg.users),
                                    statDefs    = msg.statDefs,
                                    globalStats = msg.globalStats,
                                    settings    = msg.settings ?: it.settings,
                                    hostUserId  = msg.hostUserId,
                                    game        = msg.game,
                                )
                            }
                            rememberPlayers(msg.users)
                        }
                        is ServerMessage.Joined -> _sessionUi.update {
                            it.copy(sessionCode = msg.sessionCode)
                        }
                        is ServerMessage.Error  -> _sessionUi.update { it.copy(error = msg.message) }
                        is ServerMessage.FriendRequest -> {
                            _pendingFriendRequests.update {
                                if (it.any { r -> r.fromUserId == msg.fromUserId }) it
                                else it + FriendRequestInfo(msg.fromUserId, msg.fromDisplayName)
                            }
                        }
                        is ServerMessage.FriendAccepted -> {
                            if (sentFriendRequests.remove(msg.fromUserId)) {
                                prefs.addFriend(msg.fromUserId, msg.fromDisplayName)
                                _friendList.value = prefs.friendList
                            }
                            webSocket?.acknowledgeAccepted(msg.fromUserId)
                        }
                    }
                }
            }
        }

        ws.connect()

        if (prefs.commanderDefaultEnabled) {
            ws.addCustomStat(COMMANDER_STAT, StatType.NUMERIC)
        }
    }

    // Our seat disappeared: the host removed us, or the game ended while we were offline.
    private fun removedFromSession() {
        prefs.lastSessionId = null
        tearDownWebSocket(removePlayer = false)
        _screen.value    = Screen.Home
        _homeError.value = "You are no longer in that session"
    }

    private fun tearDownWebSocket(removePlayer: Boolean = true) {
        debounceJobs.values.forEach { it.cancel() }
        debounceJobs.clear()
        pendingDeltas.clear()
        serverUsers = emptyList()
        lastRoster  = emptyMap()
        hadSeat     = false
        sentFriendRequests.clear()
        _lifeHistory.value  = emptyList()
        unrecordedLifeDelta = 0
        _pendingFriendRequests.value = emptyList()
        wsCollectorJob?.cancel()
        wsCollectorJob = null
        webSocket?.close(removePlayer)
        webSocket = null
        resetTimer()
    }

    override fun onCleared() {
        super.onCleared()
        // The activity is going away, not necessarily the player: keep their seat for resume.
        tearDownWebSocket(removePlayer = false)
        api.close()
    }
}
