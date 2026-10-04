package com.kregosh.mtglifetracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kregosh.mtglifetracker.data.AppColorScheme
import com.kregosh.mtglifetracker.data.Friend
import com.kregosh.mtglifetracker.data.KnownPlayer
import com.kregosh.mtglifetracker.data.UserPrefs
import com.kregosh.mtglifetracker.network.SessionApi
import com.kregosh.mtglifetracker.network.SessionConnection
import com.kregosh.mtglifetracker.network.SessionNotFoundException
import com.kregosh.mtglifetracker.network.ConnectionState
import com.kregosh.mtglifetracker.shared.COMMANDER_STAT
import com.kregosh.mtglifetracker.shared.LIFE_STAT
import com.kregosh.mtglifetracker.shared.POISON_STAT
import com.kregosh.mtglifetracker.shared.ServerMessage
import com.kregosh.mtglifetracker.shared.SessionInfoResponse
import com.kregosh.mtglifetracker.shared.SessionSettings
import com.kregosh.mtglifetracker.shared.StatType
import com.kregosh.mtglifetracker.shared.UserState
import com.kregosh.mtglifetracker.shared.StatTarget
import com.kregosh.mtglifetracker.shared.statTarget
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

/** Why the home screen couldn't start or join a session. The app turns these into text. */
sealed interface HomeError {
    data object SessionNotFound : HomeError
    data class SessionFull(val maxPlayers: Int) : HomeError
    data object RemovedFromSession : HomeError
    /** The session we were watching closed. */
    data object SessionEnded : HomeError
    data class CreateFailed(val detail: String?) : HomeError
    data class JoinFailed(val detail: String?) : HomeError
}

// ─────────────────────────────────────────────────────────────────────────────
// UI state for a live session
// ─────────────────────────────────────────────────────────────────────────────

data class SessionUiState(
    val sessionCode : String                = "",
    val myUserId    : String                = "",
    val users       : List<UserState>       = emptyList(),
    val globalStats : Map<String, UInt>     = emptyMap(),
    val monarch     : String?               = null,
    /** Watching without a seat: everything is read-only. */
    val observing   : Boolean               = false,
    val observers   : Map<String, String>   = emptyMap(),
    val connectionState     : ConnectionState               = ConnectionState.Connecting,
    val error       : String?               = null,
    val settings    : SessionSettings       = SessionSettings(),
    val hostUserId  : String?               = null,
    val game        : Long                  = 0,
) {
    val isHost: Boolean get() = hostUserId != null && hostUserId == myUserId

    /** The local player's seat. */
    val me: UserState? get() = users.find { it.id == myUserId }

    /** The counters the local player tracks. */
    val myStats: Map<String, StatType> get() = me?.stats.orEmpty()

    /** Whether there is a free seat for an observer to take. */
    val hasFreeSeat: Boolean get() = settings.maxPlayers <= 0 || users.size < settings.maxPlayers
}

/** Dead at 0 life, or at the threshold of commander damage (in total) or of poison. */
// Only counters the player tracks count; turned-off ones keep their values (so turning
// them back on restores them) but are ignored.
fun UserState.isDead(state: SessionUiState): Boolean =
    life == 0u
        || (COMMANDER_STAT in stats &&
            (customStats[COMMANDER_STAT] ?: 0u) >= state.settings.commanderDeathThreshold)
        || (POISON_STAT in stats &&
            (customStats[POISON_STAT] ?: 0u) >= state.settings.infectDeathThreshold)

/** Something the local player did that can be undone. The history lists them newest first. */
sealed interface Change { val id: Long }

/** A committed change to one of the player's stats: life or a counter. */
data class StatChange(override val id: Long, val stat: String, val delta: Int, val valueAfter: UInt) : Change

/** A counter turned on ([enabled]) or off for the local player. */
data class StatToggled(override val id: Long, val stat: String, val type: StatType, val enabled: Boolean) : Change

/** A session-wide value (Day/Night) set to [after]; [before] is null when it was turned on. */
data class GlobalChange(override val id: Long, val stat: String, val before: UInt?, val after: UInt) : Change

/** The monarch passed from [before] to [after]; null means nobody (the monarch not in play). */
data class MonarchChange(override val id: Long, val before: String?, val after: String?) : Change

private const val MAX_HISTORY = 50

/** The value of [stat] on this seat. */
fun UserState.valueOf(stat: String): UInt = when (val target = statTarget(stat)) {
    StatTarget.Life      -> life
    is StatTarget.Custom -> customStats[target.name] ?: 0u
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class SessionViewModel(
    private val prefs: UserPrefs,
    private val api: SessionApi,
    private val connectionFactory: (sessionId: String, userId: String, displayName: String, startLife: UInt) -> SessionConnection,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : ViewModel() {

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _sessionUi = MutableStateFlow(SessionUiState())
    val sessionUi: StateFlow<SessionUiState> = _sessionUi.asStateFlow()

    private val _homeLoading = MutableStateFlow(false)
    val homeLoading: StateFlow<Boolean> = _homeLoading.asStateFlow()

    private val _homeError = MutableStateFlow<HomeError?>(null)
    val homeError: StateFlow<HomeError?> = _homeError.asStateFlow()

    private val _backgroundImageUri = MutableStateFlow(prefs.backgroundImageUri)
    val backgroundImageUri: StateFlow<String?> = _backgroundImageUri.asStateFlow()
    val isStormPreset: StateFlow<Boolean> = _backgroundImageUri
        .map { it?.contains("bg_arcane_storm") == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, prefs.backgroundImageUri?.contains("bg_arcane_storm") == true)
    val isManaOrbsPreset: StateFlow<Boolean> = _backgroundImageUri
        .map { it?.contains("bg_mana_orbs") == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, prefs.backgroundImageUri?.contains("bg_mana_orbs") == true)

    private val _cardBackgroundImageUri = MutableStateFlow(prefs.cardBackgroundImageUri)
    val cardBackgroundImageUri: StateFlow<String?> = _cardBackgroundImageUri.asStateFlow()

    private val _colorScheme = MutableStateFlow(prefs.colorScheme)
    val colorScheme: StateFlow<AppColorScheme> = _colorScheme.asStateFlow()

    private var connection: SessionConnection? = null
    private var collectorJob: Job? = null
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

    // ── change history / undo ─────────────────────────────────────────

    private val _history = MutableStateFlow<List<Change>>(emptyList())
    /** The changes that can be undone right now, newest first. */
    val history: StateFlow<List<Change>> = _history.asStateFlow()

    // Everything recorded this game, including changes hidden while their counter is
    // turned off or someone else has changed the same thing since.
    private var changes = emptyList<Change>()

    private var nextChangeId = 0L
    // Per stat: the part of the pending delta that comes from undo and must not be recorded again.
    private val unrecordedDeltas = mutableMapOf<String, Int>()
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
        connection?.setDisplayName(trimmed)
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
        val conn = connection ?: return
        sentFriendRequests += toUserId
        conn.sendFriendRequest(toUserId)
    }

    fun acceptFriendRequest(fromUserId: String, fromDisplayName: String) {
        prefs.addFriend(fromUserId, fromDisplayName)
        _friendList.value = prefs.friendList
        _pendingFriendRequests.update { it.filterNot { req -> req.fromUserId == fromUserId } }
        connection?.acceptFriendRequest(fromUserId)
    }

    fun declineFriendRequest(fromUserId: String) {
        _pendingFriendRequests.update { it.filterNot { req -> req.fromUserId == fromUserId } }
        connection?.declineFriendRequest(fromUserId)
    }

    fun setStartLife(v: UInt)          { prefs.startLife = v;                _startLife.value = v }
    fun setCommanderThreshold(v: UInt) { prefs.commanderDeathThreshold = v;  _commanderThreshold.value = v }
    fun setInfectThreshold(v: UInt)    { prefs.infectDeathThreshold = v;     _infectThreshold.value = v }
    fun setCommanderDefaultEnabled(v: Boolean) { prefs.commanderDefaultEnabled = v }

    fun setColorScheme(scheme: AppColorScheme) {
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

    private var myUserId: String? = null

    private suspend fun signedInUserId(): String =
        myUserId ?: api.signIn().also { myUserId = it }

    fun createSession() {
        viewModelScope.launch {
            _homeLoading.value = true
            _homeError.value   = null
            val settings = defaultSettings()
            runCatching {
                val me = signedInUserId()
                me to api.createSession(me, settings)
            }
                .onSuccess { (me, resp) -> joinSession(me, resp.sessionId, resp.sessionCode, settings) }
                .onFailure { _homeError.value = HomeError.CreateFailed(it.message) }
            _homeLoading.value = false
        }
    }

    fun joinByCode(code: String) {
        viewModelScope.launch {
            _homeLoading.value = true
            _homeError.value   = null
            runCatching { signedInUserId() to api.getSessionByCode(code.trim().uppercase()) }
                .onSuccess { (me, info) -> joinIfRoom(me, info) }
                .onFailure { _homeError.value = joinError(it) }
            _homeLoading.value = false
        }
    }

    /** Joins a friend's session, to play or with [watch] only to watch. */
    fun joinFriendSession(sessionId: String, watch: Boolean = false) {
        viewModelScope.launch {
            _homeLoading.value = true
            _homeError.value   = null
            runCatching { signedInUserId() to api.getSessionById(sessionId) }
                .onSuccess { (me, info) -> joinIfRoom(me, info, watch = watch) }
                .onFailure { _homeError.value = joinError(it) }
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
        val watch = prefs.lastSessionObserving
        viewModelScope.launch {
            _homeLoading.value = true
            runCatching { signedInUserId() to api.getSessionById(sessionId) }
                .onSuccess { (me, info) -> if (!joinIfRoom(me, info, quiet = true, watch = watch)) prefs.lastSessionId = null }
                .onFailure { prefs.lastSessionId = null }
            _homeLoading.value = false
        }
    }

    private fun joinError(e: Throwable): HomeError =
        if (e is SessionNotFoundException) HomeError.SessionNotFound else HomeError.JoinFailed(e.message)

    // Watching never takes a seat, so a full session can always be watched.
    private fun joinIfRoom(me: String, info: SessionInfoResponse, quiet: Boolean = false, watch: Boolean = false): Boolean {
        val max = info.settings?.maxPlayers ?: 0
        if (!watch && max > 0 && me !in info.userIds && info.userIds.size >= max) {
            if (!quiet) _homeError.value = HomeError.SessionFull(max)
            return false
        }
        joinSession(me, info.sessionId, info.sessionCode, info.settings, watch)
        return true
    }

    // ── session screen actions ───────────────────────────────────────

    // Observers watch: none of the game actions apply to them.
    private val playing: Boolean get() = connection != null && !_sessionUi.value.observing

    fun adjust(stat: String, delta: Int) {
        if (!playing) return
        queueDelta(stat, delta)
    }

    /** Reverts the most recent recorded change to any of the local player's stats. */
    fun undoLastChange() {
        if (!playing) return
        val last = _history.value.firstOrNull() ?: return
        changes = changes - last
        publishHistory()
        when (last) {
            is StatChange   -> {
                unrecordedDeltas[last.stat] = (unrecordedDeltas[last.stat] ?: 0) - last.delta
                queueDelta(last.stat, -last.delta)
            }
            is StatToggled  ->
                if (last.enabled) connection?.removeCustomStat(last.stat)
                else              connection?.addCustomStat(last.stat, last.type)
            is GlobalChange ->
                if (last.before == null) connection?.removeGlobal(last.stat)
                else                     connection?.setGlobal(last.stat, last.before)
            is MonarchChange -> connection?.setMonarch(last.before)
        }
    }

    private fun record(change: (id: Long) -> Change) {
        changes = (listOf(change(nextChangeId++)) + changes).take(MAX_HISTORY)
        publishHistory()
    }

    private fun publishHistory() {
        val ui = _sessionUi.value
        _history.value = changes.filter { it.isUndoable(ui) }
    }

    // A change is offered for undo while undoing it still means something: its counter is
    // on, and nobody has since flipped Day/Night or passed the monarch on.
    private fun Change.isUndoable(ui: SessionUiState): Boolean = when (this) {
        is StatChange    -> isTracked(stat, ui.myStats)
        is StatToggled   -> (stat in ui.myStats) == enabled
        is GlobalChange  -> ui.globalStats[stat] == after
        is MonarchChange -> ui.monarch == after
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
            val undone = unrecordedDeltas.remove(stat) ?: 0
            recordChange(stat, taps = accumulated - undone, mixedWithUndo = undone != 0)
            if (accumulated != 0) connection?.adjust(stat, accumulated)
        }
    }

    // Only the player's own taps are recorded, never an undo. Counters stop at zero, so
    // when nothing else is pending the change recorded is what really applied
    // (1 life minus 3 taps is −1).
    private fun recordChange(stat: String, taps: Int, mixedWithUndo: Boolean) {
        if (taps == 0) return
        val myId  = _sessionUi.value.myUserId
        val after = _sessionUi.value.users.find { it.id == myId }?.valueOf(stat) ?: return
        val delta = if (mixedWithUndo) taps else {
            val before = serverUsers.find { it.id == myId }?.valueOf(stat) ?: return
            (after.toLong() - before.toLong()).toInt()
        }
        if (delta == 0) return
        record { id -> StatChange(id, stat, delta, after) }
    }

    private fun isTracked(stat: String, statDefs: Map<String, StatType>): Boolean =
        when (val target = statTarget(stat)) {
            StatTarget.Life      -> true
            is StatTarget.Custom -> target.name in statDefs
        }

    // ── host controls ─────────────────────────────────────────────────

    fun updateSessionSettings(settings: SessionSettings) {
        if (!_sessionUi.value.isHost) return
        connection?.updateSettings(settings)
    }

    fun startNewGame() {
        val ui = _sessionUi.value
        if (!ui.isHost) return
        val conn = connection ?: return
        conn.startNewGame()
        // A new game starts without a monarch.
        if (ui.monarch != null) conn.setMonarch(null)
    }

    fun removePlayer(userId: String) {
        val ui = _sessionUi.value
        if (!ui.isHost || userId == ui.myUserId) return
        connection?.removePlayer(userId)
    }

    fun addCustomStat(name: String, type: StatType = StatType.NUMERIC) {
        val trimmed = name.trim()
        if (!isValidStatName(trimmed) || !playing) return
        val conn = connection ?: return
        if (trimmed in _sessionUi.value.myStats) return
        conn.addCustomStat(trimmed, type)
        record { id -> StatToggled(id, trimmed, type, enabled = true) }
    }

    /** Hides the counter on the local player's card; its value stays, so turning it back on restores it. */
    fun removeCustomStat(name: String) {
        if (!playing) return
        val conn = connection ?: return
        val type = _sessionUi.value.myStats[name] ?: return
        conn.removeCustomStat(name)
        record { id -> StatToggled(id, name, type, enabled = false) }
    }

    fun setGlobal(stat: String, value: UInt) {
        if (!playing) return
        val conn   = connection ?: return
        val before = _sessionUi.value.globalStats[stat]
        if (before == value) return
        conn.setGlobal(stat, value)
        record { id -> GlobalChange(id, stat, before, value) }
    }

    /** Makes [userId] the monarch, or takes the monarch out of the game with null. */
    fun setMonarch(userId: String?) {
        if (!playing) return
        val conn   = connection ?: return
        val before = _sessionUi.value.monarch
        if (before == userId) return
        conn.setMonarch(userId)
        record { id -> MonarchChange(id, before, userId) }
    }

    fun concede() {
        if (!playing) return
        val myId = _sessionUi.value.myUserId
        serverUsers = serverUsers.map { if (it.id == myId) it.copy(conceded = true) else it }
        _sessionUi.update { it.copy(users = applyPendingDeltas(serverUsers)) }
        connection?.setConceded(true)
    }

    fun unconcede() {
        if (!playing) return
        val myId = _sessionUi.value.myUserId
        serverUsers = serverUsers.map { if (it.id == myId) it.copy(conceded = false) else it }
        _sessionUi.update { it.copy(users = applyPendingDeltas(serverUsers)) }
        connection?.setConceded(false)
    }

    fun toggleGlobal(stat: String) {
        val current = _sessionUi.value.globalStats[stat] ?: 0u
        setGlobal(stat, if (current == 0u) 1u else 0u)
    }

    /** Gives up the seat and keeps watching. Unsent taps are sent first. */
    fun watchInstead() {
        if (!playing) return
        val conn = connection ?: return
        debounceJobs.values.forEach { it.cancel() }
        debounceJobs.clear()
        pendingDeltas.forEach { (stat, delta) -> if (delta != 0) conn.adjust(stat, delta) }
        pendingDeltas.clear()
        unrecordedDeltas.clear()
        hadSeat = false
        changes = emptyList()
        _sessionUi.update { it.copy(observing = true) }
        publishHistory()
        prefs.lastSessionObserving = true
        conn.watch()
    }

    /** Takes a seat again after watching, if there is one free. */
    fun playInstead() {
        val ui   = _sessionUi.value
        val conn = connection ?: return
        if (!ui.observing || !ui.hasFreeSeat) return
        _sessionUi.update { it.copy(observing = false) }
        prefs.lastSessionObserving = false
        defaultStatsAdded = false
        // The rules may have changed since we connected, so the seat uses today's start life.
        conn.play(ui.settings.startLife)
    }

    fun leaveSession() {
        prefs.lastSessionId = null
        tearDownConnection(removePlayer = true)
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
            LIFE_STAT -> copy(life = clamp(life.toLong() + delta))
            else      -> copy(customStats = customStats +
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

    private fun joinSession(myId: String, sessionId: String, sessionCode: String, settings: SessionSettings?, watch: Boolean = false) {
        val current = _screen.value
        if (current is Screen.Session && current.sessionId == sessionId) return

        tearDownConnection()

        val name = prefs.displayName.ifBlank { "Player" }

        val rules = settings ?: defaultSettings()
        _sessionUi.value = SessionUiState(
            sessionCode = sessionCode,
            myUserId    = myId,
            settings    = rules,
            observing   = watch,
        )
        _screen.value = Screen.Session(sessionId)
        prefs.lastSessionId = sessionId
        prefs.lastSessionObserving = watch

        val conn = connectionFactory(sessionId, myId, name, rules.startLife)
        connection = conn

        collectorJob = viewModelScope.launch {
            launch {
                conn.connectionState.collect { state ->
                    _sessionUi.update { it.copy(connectionState = state) }
                }
            }
            launch {
                conn.messages.collect { msg ->
                    when (msg) {
                        is ServerMessage.State  -> {
                            if (msg.users.any { it.id == myId }) {
                                hadSeat = true
                            } else if (hadSeat && !_sessionUi.value.observing) {
                                removedFromSession()
                                return@collect
                            }
                            if (msg.game > _sessionUi.value.game) {
                                resetTimer()
                                changes = emptyList()
                            }
                            serverUsers = msg.users
                            _sessionUi.update {
                                it.copy(
                                    users       = applyPendingDeltas(msg.users),
                                    error       = null,
                                    globalStats = msg.globalStats,
                                    monarch     = msg.monarch,
                                    observers   = msg.observers,
                                    settings    = msg.settings ?: it.settings,
                                    hostUserId  = msg.hostUserId,
                                    game        = msg.game,
                                )
                            }
                            publishHistory()
                            rememberPlayers(msg.users)
                            addDefaultStats(conn, msg.users.find { it.id == myId })
                        }
                        is ServerMessage.Error  -> _sessionUi.update { it.copy(error = msg.message) }
                        is ServerMessage.SessionGone -> when {
                            _sessionUi.value.observing -> leftSession(HomeError.SessionEnded)
                            hadSeat                    -> removedFromSession()
                        }
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
                            connection?.acknowledgeAccepted(msg.fromUserId)
                        }
                    }
                }
            }
        }

        defaultStatsAdded = false
        conn.connect(asObserver = watch)
    }

    private var defaultStatsAdded = false

    // Counters go on the seat, so the default ones are added once the seat exists.
    private fun addDefaultStats(conn: SessionConnection, mySeat: UserState?) {
        if (defaultStatsAdded || mySeat == null) return
        defaultStatsAdded = true
        if (prefs.commanderDefaultEnabled && COMMANDER_STAT !in mySeat.stats) {
            conn.addCustomStat(COMMANDER_STAT, StatType.NUMERIC)
        }
    }

    // Our seat disappeared: the host removed us, or the game ended while we were offline.
    private fun removedFromSession() = leftSession(HomeError.RemovedFromSession)

    private fun leftSession(reason: HomeError) {
        prefs.lastSessionId = null
        tearDownConnection(removePlayer = false)
        _screen.value    = Screen.Home
        _homeError.value = reason
    }

    private fun tearDownConnection(removePlayer: Boolean = true) {
        debounceJobs.values.forEach { it.cancel() }
        debounceJobs.clear()
        pendingDeltas.clear()
        serverUsers = emptyList()
        lastRoster  = emptyMap()
        hadSeat     = false
        sentFriendRequests.clear()
        changes = emptyList()
        _history.value = emptyList()
        unrecordedDeltas.clear()
        _pendingFriendRequests.value = emptyList()
        collectorJob?.cancel()
        collectorJob = null
        connection?.close(removePlayer)
        connection = null
        resetTimer()
    }

    override fun onCleared() {
        super.onCleared()
        // The activity is going away, not necessarily the player: keep their seat for resume.
        tearDownConnection(removePlayer = false)
        api.close()
    }
}
