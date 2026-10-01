package com.kregosh.mtglifetracker.ui.screens

import com.kregosh.mtglifetracker.ui.components.statTypeLabel
import com.kregosh.mtglifetracker.ui.components.statLabel
import com.kregosh.mtglifetracker.shared.PredefinedStat
import com.kregosh.mtglifetracker.shared.DAY_NIGHT_GLOBAL
import androidx.compose.ui.platform.LocalContext
import com.kregosh.mtglifetracker.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import com.kregosh.mtglifetracker.data.Friend
import com.kregosh.mtglifetracker.data.KnownPlayer
import com.kregosh.mtglifetracker.network.ConnectionState
import com.kregosh.mtglifetracker.shared.SessionSettings
import com.kregosh.mtglifetracker.shared.StatType
import com.kregosh.mtglifetracker.shared.UserState
import com.kregosh.mtglifetracker.viewmodel.LifeChange
import com.kregosh.mtglifetracker.ui.components.FriendsSheet
import com.kregosh.mtglifetracker.ui.components.InviteDialog
import com.kregosh.mtglifetracker.viewmodel.Screen
import com.kregosh.mtglifetracker.ui.components.PlayerCard
import com.kregosh.mtglifetracker.ui.theme.LocalHasBackground
import com.kregosh.mtglifetracker.viewmodel.FriendRequestInfo
import com.kregosh.mtglifetracker.viewmodel.MAX_STAT_NAME_LENGTH
import com.kregosh.mtglifetracker.viewmodel.RESERVED_STAT_NAMES
import com.kregosh.mtglifetracker.viewmodel.isValidStatName
import com.kregosh.mtglifetracker.viewmodel.SessionUiState
import com.kregosh.mtglifetracker.viewmodel.SessionViewModel

// ─────────────────────────────────────────────────────────────────────────────
// Predefined per-player stat catalogue
// ─────────────────────────────────────────────────────────────────────────────


// ─────────────────────────────────────────────────────────────────────────────
// Session screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(vm: SessionViewModel) {
    val ui                    by vm.sessionUi.collectAsState()
    val timerElapsed          by vm.timerElapsed.collectAsState()
    val timerRunning          by vm.timerRunning.collectAsState()
    val timerVisible          by vm.timerVisible.collectAsState()
    val timerCountDown        by vm.timerCountDown.collectAsState()
    val timerLimitMinutes     by vm.timerLimitMinutes.collectAsState()
    val pendingFriendRequests by vm.pendingFriendRequests.collectAsState()
    val friendList            by vm.friendList.collectAsState()
    val knownPlayers          by vm.knownPlayers.collectAsState()
    val hasBg   = LocalHasBackground.current
    val view    = LocalView.current

    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val friendIds = remember(friendList) { friendList.map { it.userId }.toSet() }

    pendingFriendRequests.firstOrNull()?.let { req ->
        FriendRequestDialog(
            request   = req,
            onAccept  = { vm.acceptFriendRequest(req.fromUserId, req.fromDisplayName) },
            onDecline = { vm.declineFriendRequest(req.fromUserId) },
        )
    }

    SessionContent(
        vm, ui, friendIds, friendList, knownPlayers,
        timerElapsed, timerRunning, timerVisible, timerCountDown, timerLimitMinutes,
        hasBg,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionContent(
    vm                : SessionViewModel,
    ui                : SessionUiState,
    friendIds         : Set<String>,
    friendList        : List<Friend>,
    knownPlayers      : List<KnownPlayer>,
    timerElapsed      : Duration,
    timerRunning      : Boolean,
    timerVisible      : Boolean,
    timerCountDown    : Boolean,
    timerLimitMinutes : UInt,
    hasBg             : Boolean,
) {

    var showStatPicker   by remember { mutableStateOf(false) }
    var showLeaveDialog  by remember { mutableStateOf(false) }
    var showHistory      by remember { mutableStateOf(false) }
    var showGameRules    by remember { mutableStateOf(false) }
    var showNewGame      by remember { mutableStateOf(false) }
    var showMenu         by remember { mutableStateOf(false) }
    var removeTarget     by remember { mutableStateOf<UserState?>(null) }

    val lifeHistory  by vm.lifeHistory.collectAsState()
    val resources    = LocalContext.current.resources
    val snackbarHost = remember { SnackbarHostState() }
    var announcedUpTo by remember { mutableLongStateOf(-1L) }
    val latestChange = lifeHistory.firstOrNull()

    // Offer undo once per new change; undoing exposes older entries, which were already offered.
    LaunchedEffect(latestChange?.id) {
        val change = latestChange ?: return@LaunchedEffect
        if (change.id <= announcedUpTo) return@LaunchedEffect
        announcedUpTo = change.id
        val result = snackbarHost.showSnackbar(
            message     = resources.getString(R.string.life_change_snackbar, change.delta.signed(), change.lifeAfter.toInt()),
            actionLabel = resources.getString(R.string.action_undo),
            duration    = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) vm.undoLastLifeChange()
    }

    if (showHistory) {
        LifeHistorySheet(
            history   = lifeHistory,
            onUndo    = vm::undoLastLifeChange,
            onDismiss = { showHistory = false },
        )
    }

    if (showGameRules) {
        GameRulesDialog(
            settings  = ui.settings,
            editable  = ui.isHost,
            onSave    = { vm.updateSessionSettings(it); showGameRules = false },
            onDismiss = { showGameRules = false },
        )
    }

    if (showNewGame) {
        AlertDialog(
            onDismissRequest = { showNewGame = false },
            title = { Text(stringResource(R.string.new_game_title)) },
            text  = { Text(stringResource(R.string.new_game_text, ui.settings.startLife.toInt())) },
            confirmButton = {
                TextButton(onClick = { showNewGame = false; vm.startNewGame() }) { Text(stringResource(R.string.session_new_game)) }
            },
            dismissButton = {
                TextButton(onClick = { showNewGame = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text(stringResource(R.string.remove_player_title, target.displayName)) },
            text  = { Text(stringResource(R.string.remove_player_text)) },
            confirmButton = {
                TextButton(
                    onClick = { removeTarget = null; vm.removePlayer(target.id) },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    BackHandler { showLeaveDialog = true }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text(stringResource(R.string.leave_title)) },
            text  = { Text(stringResource(R.string.leave_text)) },
            confirmButton = {
                TextButton(
                    onClick = { showLeaveDialog = false; vm.leaveSession() },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.leave_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text(stringResource(R.string.leave_stay)) }
            },
        )
    }
    var showCustomDialog by remember { mutableStateOf(false) }
    var showFriendsSheet by remember { mutableStateOf(false) }

    val friendPresence by vm.friendPresence.collectAsState()
    val screen         by vm.screen.collectAsState()
    var showInvite     by remember { mutableStateOf(false) }

    if (showInvite) {
        InviteDialog(code = ui.sessionCode, onDismiss = { showInvite = false })
    }

    if (showFriendsSheet) {
        FriendsSheet(
            friendList   = friendList,
            knownPlayers = knownPlayers,
            friendIds    = friendIds,
            friendPresence   = friendPresence,
            currentSessionId = (screen as? Screen.Session)?.sessionId,
            onJoinSession    = vm::joinFriendSession,
            onRemoveFriend    = vm::removeFriend,
            onAddFriend       = { uid, name -> vm.addFriend(uid, name) },
            onForgetPlayer    = vm::forgetPlayer,
            onDismiss         = { showFriendsSheet = false },
        )
    }

    if (showStatPicker) {
        StatPickerSheet(
            ui          = ui,
            onAdd       = { name, type -> vm.addCustomStat(name, type); showStatPicker = false },
            onAddCustom = { showStatPicker = false; showCustomDialog = true },
            onRemove    = { name -> vm.removeCustomStat(name) },
            onEnableDayNight = { vm.setGlobal(DAY_NIGHT_GLOBAL, 0u) ; showStatPicker = false },
            onDismiss   = { showStatPicker = false },
        )
    }

    if (showCustomDialog) {
        AddCustomStatDialog(
            onConfirm = { name -> vm.addCustomStat(name, StatType.NUMERIC); showCustomDialog = false },
            onDismiss = { showCustomDialog = false },
        )
    }

    val topBarColors = if (hasBg) TopAppBarDefaults.topAppBarColors(
        containerColor             = Color.Black.copy(alpha = 0.45f),
        titleContentColor          = Color.White,
        navigationIconContentColor = Color.White,
        actionIconContentColor     = Color.White,
    ) else TopAppBarDefaults.topAppBarColors()

    Scaffold(
        containerColor = if (hasBg) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = topBarColors,
                title  = {
                    Column {
                        Text(stringResource(R.string.session_title))
                        if (ui.sessionCode.isNotEmpty()) {
                            Text(
                                text  = stringResource(R.string.session_code, ui.sessionCode),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (hasBg) Color.White.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showLeaveDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.session_leave))
                    }
                },
                actions = {
                    IconButton(onClick = { showFriendsSheet = true }) {
                        Icon(Icons.Default.People, contentDescription = stringResource(R.string.friends))
                    }
                    if (ui.sessionCode.isNotEmpty()) {
                        IconButton(onClick = { showInvite = true }) {
                            Icon(Icons.Default.QrCode2, contentDescription = stringResource(R.string.session_invite))
                        }
                    }
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Default.History, contentDescription = stringResource(R.string.session_life_history))
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.session_more))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text        = { Text(stringResource(R.string.session_game_rules)) },
                                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                                onClick     = { showMenu = false; showGameRules = true },
                            )
                            if (ui.isHost) {
                                DropdownMenuItem(
                                    text        = { Text(stringResource(R.string.session_new_game)) },
                                    leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                                    onClick     = { showMenu = false; showNewGame = true },
                                )
                            }
                            DropdownMenuItem(
                                text        = { Text(stringResource(R.string.settings)) },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick     = { showMenu = false; vm.openSettings() },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showStatPicker = true },
                icon    = { Icon(Icons.Default.Add, contentDescription = null) },
                text    = { Text(stringResource(R.string.session_stats)) },
            )
        },
    ) { padding ->
        // Radial gradient emanating from the sun/moon icon in the Day/Night banner.
        // The icon sits at approximately (left-padding + icon-half, topbar-height + banner-half).
        // Using fixed dp values avoids layout measurement while staying close enough.
        val density = LocalDensity.current
        val dayNightBrush: Brush? = if (DAY_NIGHT_GLOBAL !in ui.globalStats) null else {
            val iconX = with(density) { (padding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + 24.dp).toPx() }
            val iconY = with(density) { (padding.calculateTopPadding() + 27.dp).toPx() }
            val radius = with(density) { 420.dp.toPx() }
            val isDaytime = (ui.globalStats[DAY_NIGHT_GLOBAL] ?: 0u) == 0u
            val centerColor = if (isDaytime) Color.White.copy(alpha = 0.10f)
                              else           Color.Black.copy(alpha = 0.14f)
            Brush.radialGradient(
                colors = listOf(centerColor, Color.Transparent),
                center = Offset(iconX, iconY),
                radius = radius,
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
        ) {
            ConnectionBanner(ui.connectionState)

            // Day/Night banner — only shown once the global has been initialised
            if (DAY_NIGHT_GLOBAL in ui.globalStats) {
                DayNightBanner(
                    isDaytime = (ui.globalStats[DAY_NIGHT_GLOBAL] ?: 0u) == 0u,
                    onToggle  = { vm.toggleGlobal(DAY_NIGHT_GLOBAL) },
                )
            }

            if (timerVisible) {
                GameTimerRow(
                    elapsed      = timerElapsed,
                    running      = timerRunning,
                    countDown    = timerCountDown,
                    limitMinutes = timerLimitMinutes,
                    onToggle     = vm::startPauseTimer,
                    onReset      = vm::resetTimer,
                )
            }

            Spacer(Modifier.height(8.dp))

            if (ui.users.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.session_waiting))
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding      = PaddingValues(bottom = 80.dp),
                ) {
                    itemsIndexed(ui.users, key = { _, user -> user.id }) { index, user ->
                        val isMe = user.id == ui.myUserId
                        PlayerCard(
                            user        = user,
                            isMe        = isMe,
                            sessionUi   = ui,
                            onAdjust    = { stat, delta -> if (isMe) vm.adjust(stat, delta) },
                            onConcede   = if (isMe) vm::concede   else null,
                            onUnconcede = if (isMe) vm::unconcede else null,
                            isFriend    = user.id in friendIds,
                            onAddFriend = if (!isMe) { { vm.sendFriendRequest(user.id) } } else null,
                            onRemove    = if (ui.isHost && !isMe) { { removeTarget = user } } else null,
                            playerIndex = index,
                        )
                    }
                }
            }

            ui.error?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.connection_error, msg), color = MaterialTheme.colorScheme.error)
            }
        }
        if (dayNightBrush != null) {
            Box(modifier = Modifier.matchParentSize().background(dayNightBrush))
        }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Day / Night global banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DayNightBanner(isDaytime: Boolean, onToggle: () -> Unit) {
    val icon  = if (isDaytime) Icons.Default.WbSunny else Icons.Default.Bedtime
    val label = stringResource(if (isDaytime) R.string.day else R.string.night)
    val color = if (isDaytime) MaterialTheme.colorScheme.tertiary
                else           MaterialTheme.colorScheme.primary
    Surface(
        color    = color.copy(alpha = 0.18f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                Text(
                    text  = label,
                    color = color,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            FilledTonalButton(
                onClick      = onToggle,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier     = Modifier.height(32.dp),
            ) {
                val nextIcon  = if (isDaytime) Icons.Default.Bedtime else Icons.Default.WbSunny
                val nextLabel = stringResource(if (isDaytime) R.string.night else R.string.day)
                Icon(nextIcon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(nextLabel, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Game timer row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GameTimerRow(
    elapsed      : Duration,
    running      : Boolean,
    countDown    : Boolean,
    limitMinutes : UInt,
    onToggle     : () -> Unit,
    onReset      : () -> Unit,
) {
    val limit      = limitMinutes.toLong().minutes
    val display    = if (countDown) (limit - elapsed).coerceAtLeast(Duration.ZERO) else elapsed
    val urgent     = countDown && display < 1.minutes &&
                     (running || elapsed > Duration.ZERO)
    val hasStarted = elapsed > Duration.ZERO || running
    val tint       = if (urgent) MaterialTheme.colorScheme.error
                     else        MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector        = Icons.Default.Timer,
            contentDescription = null,
            tint               = tint,
            modifier           = Modifier.size(18.dp),
        )
        Text(
            text     = display.toTimerString(),
            style    = MaterialTheme.typography.titleMedium,
            color    = tint,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
            val icon = if (running) Icons.Default.Pause else Icons.Default.PlayArrow
            Icon(icon, contentDescription = stringResource(if (running) R.string.timer_pause else R.string.timer_start), tint = tint)
        }
        if (hasStarted) {
            IconButton(onClick = onReset, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Replay, contentDescription = stringResource(R.string.timer_reset),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun Duration.toTimerString(): String {
    val total = inWholeSeconds
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

// ─────────────────────────────────────────────────────────────────────────────
// Connection state banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConnectionBanner(state: ConnectionState) {
    val (text, color) = when (state) {
        ConnectionState.Connected    -> return
        ConnectionState.Closed       -> return
        ConnectionState.Connecting   -> stringResource(R.string.connection_connecting)               to MaterialTheme.colorScheme.tertiary
        ConnectionState.Reconnecting -> stringResource(R.string.connection_reconnecting)             to MaterialTheme.colorScheme.secondary
        is ConnectionState.Failed    -> stringResource(R.string.connection_failed, state.reason) to MaterialTheme.colorScheme.error
    }
    Surface(
        color    = color.copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text     = text,
            modifier = Modifier.padding(8.dp),
            color    = color,
            style    = MaterialTheme.typography.labelMedium,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stat picker bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatPickerSheet(
    ui               : SessionUiState,
    onAdd            : (name: String, type: StatType) -> Unit,
    onAddCustom      : () -> Unit,
    onRemove         : (name: String) -> Unit,
    onEnableDayNight : () -> Unit,
    onDismiss        : () -> Unit,
) {
    val statDefs        = ui.statDefs
    val dayNightEnabled = DAY_NIGHT_GLOBAL in ui.globalStats

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            // ── Active per-player stats ───────────────────────────────────
            if (statDefs.isNotEmpty()) {
                Text(
                    text  = stringResource(R.string.stats_active).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                statDefs.forEach { (name, type) ->
                    ActiveStatRow(
                        label     = statLabel(name),
                        typeLabel = statTypeLabel(type),
                        onRemove  = { onRemove(name) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
            }

            // ── Global options ────────────────────────────────────────────
            Text(
                text  = stringResource(R.string.stats_global).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            PickerRow(
                label     = stringResource(R.string.stats_day_night),
                typeLabel = stringResource(R.string.stats_day_night_hint),
                active    = dayNightEnabled,
                onClick   = { if (!dayNightEnabled) onEnableDayNight() },
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // ── Add a per-player stat ─────────────────────────────────────
            Text(
                text  = stringResource(R.string.stats_add).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            PredefinedStat.entries.forEach { preset ->
                val active = preset.id in statDefs
                PickerRow(
                    label     = statLabel(preset.id),
                    typeLabel = statTypeLabel(preset.type),
                    active    = active,
                    onClick   = { if (!active) onAdd(preset.id, preset.type) },
                )
            }

            OutlinedButton(
                onClick  = onAddCustom,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.stats_custom))
            }
        }
    }
}

@Composable
private fun ActiveStatRow(label: String, typeLabel: String, onRemove: () -> Unit) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(typeLabel, style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.stats_remove, label),
                 tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PickerRow(label: String, typeLabel: String, active: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick  = onClick,
        enabled  = !active,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(typeLabel, style = MaterialTheme.typography.labelSmall)
            }
            if (active) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.stats_already_added),
                     tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.stats_add_named, label))
            }
        }
    }
}


// FriendsSheet is defined in ui/components/FriendsSheet.kt and shared with HomeScreen.

// ─────────────────────────────────────────────────────────────────────────────
// Friend request confirmation dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FriendRequestDialog(
    request  : FriendRequestInfo,
    onAccept : () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.friend_request_title)) },
        text  = { Text(stringResource(R.string.friend_request_text, request.fromDisplayName)) },
        confirmButton = {
            TextButton(onClick = onAccept) { Text(stringResource(R.string.friend_request_accept)) }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text(stringResource(R.string.friend_request_decline)) }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Custom stat name dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddCustomStatDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val trimmed    = name.trim()
    val isReserved = trimmed.lowercase() in RESERVED_STAT_NAMES
    val isValid    = isValidStatName(trimmed)
    val showError  = trimmed.isNotEmpty() && !isValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_stat_title)) },
        text  = {
            OutlinedTextField(
                value         = name,
                onValueChange = { if (it.length <= MAX_STAT_NAME_LENGTH) name = it },
                label         = { Text(stringResource(R.string.custom_stat_name)) },
                placeholder   = { Text(stringResource(R.string.custom_stat_placeholder)) },
                singleLine    = true,
                isError       = showError,
                supportingText = if (showError) {
                    {
                        Text(
                            if (isReserved) stringResource(R.string.custom_stat_reserved, trimmed)
                            else stringResource(R.string.custom_stat_invalid)
                        )
                    }
                } else null,
            )
        },
        confirmButton = {
            TextButton(
                onClick  = { if (isValid) onConfirm(trimmed) },
                enabled  = isValid,
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Life history
// ─────────────────────────────────────────────────────────────────────────────

private fun Int.signed(): String = if (this > 0) "+$this" else "−${-this}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LifeHistorySheet(
    history  : List<LifeChange>,
    onUndo   : () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onUndo, enabled = history.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.history_undo_last))
                }
            }
            if (history.isEmpty()) {
                Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(history, key = { it.id }) { change ->
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            change.delta.signed(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (change.delta < 0) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary,
                        )
                        Text(stringResource(R.string.history_life_after, change.lifeAfter.toInt()), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared game rules (host edits, everyone else can look)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GameRulesDialog(
    settings : SessionSettings,
    editable : Boolean,
    onSave   : (SessionSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    val anyPlayers = stringResource(R.string.rules_any_players)
    fun players(n: UInt) = if (n == 0u) anyPlayers else n.toString()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_game_rules)) },
        text  = {
            Column(
                modifier            = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (editable) {
                    PresetRow(stringResource(R.string.rules_starting_life_short), draft.startLife, listOf(20u, 30u, 40u),
                        { draft = draft.copy(startLife = it) })
                    PresetRow(stringResource(R.string.rules_commander_limit), draft.commanderDeathThreshold, listOf(21u, 15u, 10u),
                        { draft = draft.copy(commanderDeathThreshold = it) })
                    PresetRow(stringResource(R.string.rules_infect_limit), draft.infectDeathThreshold, listOf(10u, 7u, 5u),
                        { draft = draft.copy(infectDeathThreshold = it) })
                    PresetRow(stringResource(R.string.rules_max_players), draft.maxPlayers.toUInt(), listOf(0u, 2u, 4u, 6u),
                        { draft = draft.copy(maxPlayers = it.toInt()) }, valueText = ::players)
                    Text(
                        stringResource(R.string.rules_next_game_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(stringResource(R.string.rules_summary_starting_life, settings.startLife.toInt()))
                    Text(stringResource(R.string.rules_summary_commander, settings.commanderDeathThreshold.toInt()))
                    Text(stringResource(R.string.rules_summary_infect, settings.infectDeathThreshold.toInt()))
                    Text(stringResource(R.string.rules_summary_players, players(settings.maxPlayers.toUInt())))
                    Text(
                        stringResource(R.string.rules_host_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (editable) TextButton(onClick = { onSave(draft) }) { Text(stringResource(R.string.action_save)) }
            else TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        dismissButton = if (editable) {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
        } else null,
    )
}
