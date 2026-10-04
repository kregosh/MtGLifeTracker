package com.kregosh.mtglifetracker.ui.screens

import com.kregosh.mtglifetracker.ui.Strings
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import com.kregosh.mtglifetracker.ui.platform.LocalPlatform
import androidx.compose.ui.unit.dp
import com.kregosh.mtglifetracker.data.Friend
import com.kregosh.mtglifetracker.data.KnownPlayer
import com.kregosh.mtglifetracker.network.ConnectionState
import com.kregosh.mtglifetracker.shared.DAY_NIGHT_GLOBAL
import com.kregosh.mtglifetracker.shared.PredefinedStat
import com.kregosh.mtglifetracker.shared.SessionSettings
import com.kregosh.mtglifetracker.shared.StatTarget
import com.kregosh.mtglifetracker.shared.StatType
import com.kregosh.mtglifetracker.shared.UserState
import com.kregosh.mtglifetracker.shared.statTarget
import com.kregosh.mtglifetracker.ui.components.FriendsSheet
import com.kregosh.mtglifetracker.ui.signed
import com.kregosh.mtglifetracker.ui.toTimerString
import com.kregosh.mtglifetracker.ui.components.InviteDialog
import com.kregosh.mtglifetracker.ui.components.PlayerCard
import com.kregosh.mtglifetracker.ui.components.statLabel
import com.kregosh.mtglifetracker.ui.components.statTypeLabel
import com.kregosh.mtglifetracker.ui.theme.LocalBackdropLuminance
import com.kregosh.mtglifetracker.ui.theme.LocalHasBackground
import com.kregosh.mtglifetracker.ui.prefersDarkText
import com.kregosh.mtglifetracker.viewmodel.FriendRequestInfo
import com.kregosh.mtglifetracker.viewmodel.MAX_STAT_NAME_LENGTH
import com.kregosh.mtglifetracker.viewmodel.RESERVED_STAT_NAMES
import com.kregosh.mtglifetracker.viewmodel.Screen
import com.kregosh.mtglifetracker.viewmodel.SessionUiState
import com.kregosh.mtglifetracker.viewmodel.SessionViewModel
import com.kregosh.mtglifetracker.viewmodel.Change
import com.kregosh.mtglifetracker.viewmodel.GlobalChange
import com.kregosh.mtglifetracker.viewmodel.MonarchChange
import com.kregosh.mtglifetracker.viewmodel.StatChange
import com.kregosh.mtglifetracker.viewmodel.StatToggled
import com.kregosh.mtglifetracker.viewmodel.isValidStatName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

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
    val platform = LocalPlatform.current

    platform.KeepScreenOn()

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
    var showWatchDialog  by remember { mutableStateOf(false) }
    var showMenu         by remember { mutableStateOf(false) }
    var removeTarget     by remember { mutableStateOf<UserState?>(null) }

    val history      by vm.history.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    var announcedUpTo by remember { mutableLongStateOf(-1L) }
    val latestChange = history.firstOrNull()
    val latestText   = latestChange?.let { changeText(it, ui.users, ui.myStats) }

    // Offer undo once per new change; undoing exposes older entries, which were already offered.
    LaunchedEffect(latestChange?.id) {
        val change = latestChange ?: return@LaunchedEffect
        val text   = latestText ?: return@LaunchedEffect
        if (change.id <= announcedUpTo) return@LaunchedEffect
        announcedUpTo = change.id
        val result = snackbarHost.showSnackbar(
            message     = text,
            actionLabel = Strings.actionUndo,
            duration    = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) vm.undoLastChange()
    }

    if (showHistory) {
        HistorySheet(
            history   = history,
            users     = ui.users,
            statDefs  = ui.myStats,
            onUndo    = vm::undoLastChange,
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
            title = { Text(Strings.newGameTitle) },
            text  = { Text(Strings.newGameText(ui.settings.startLife.toInt())) },
            confirmButton = {
                TextButton(onClick = { showNewGame = false; vm.startNewGame() }) { Text(Strings.sessionNewGame) }
            },
            dismissButton = {
                TextButton(onClick = { showNewGame = false }) { Text(Strings.actionCancel) }
            },
        )
    }

    if (showWatchDialog) {
        AlertDialog(
            onDismissRequest = { showWatchDialog = false },
            title = { Text(Strings.watchTitle) },
            text  = { Text(Strings.watchText) },
            confirmButton = {
                TextButton(onClick = { showWatchDialog = false; vm.watchInstead() }) { Text(Strings.sessionWatch) }
            },
            dismissButton = {
                TextButton(onClick = { showWatchDialog = false }) { Text(Strings.actionCancel) }
            },
        )
    }

    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text(Strings.removePlayerTitle(target.displayName)) },
            text  = { Text(Strings.removePlayerText) },
            confirmButton = {
                TextButton(
                    onClick = { removeTarget = null; vm.removePlayer(target.id) },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(Strings.actionRemove) }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text(Strings.actionCancel) }
            },
        )
    }

    LocalPlatform.current.BackHandler(enabled = true) { showLeaveDialog = true }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text(Strings.leaveTitle) },
            text  = {
                val successor = if (ui.isHost) vm.nextHost else null
                Text(
                    if (successor != null) Strings.leaveTextHost(successor.displayName)
                    else Strings.leaveText
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showLeaveDialog = false; vm.leaveSession() },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(Strings.leaveConfirm) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text(Strings.leaveStay) }
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
            onJoinSession    = { sessionId, watch -> vm.joinFriendSession(sessionId, watch) },
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
            onSetMonarch     = { id -> vm.setMonarch(id); showStatPicker = false },
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
                        Text(Strings.sessionTitle)
                        if (ui.sessionCode.isNotEmpty()) {
                            Text(
                                text  = Strings.sessionCode(ui.sessionCode),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (hasBg) Color.White.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showLeaveDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.sessionLeave)
                    }
                },
                actions = {
                    IconButton(onClick = { showFriendsSheet = true }) {
                        Icon(Icons.Default.People, contentDescription = Strings.friends)
                    }
                    if (ui.sessionCode.isNotEmpty()) {
                        IconButton(onClick = { showInvite = true }) {
                            Icon(Icons.Default.QrCode2, contentDescription = Strings.sessionInvite)
                        }
                    }
                    if (!ui.observing) {
                        IconButton(onClick = { showHistory = true }) {
                            Icon(Icons.Default.History, contentDescription = Strings.sessionLifeHistory)
                        }
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = Strings.sessionMore)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text        = { Text(Strings.sessionGameRules) },
                                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                                onClick     = { showMenu = false; showGameRules = true },
                            )
                            if (ui.observing) {
                                DropdownMenuItem(
                                    text        = { Text((
                                        if (ui.hasFreeSeat) Strings.sessionPlay else Strings.sessionPlayFull)) },
                                    leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                                    enabled     = ui.hasFreeSeat,
                                    onClick     = { showMenu = false; vm.playInstead() },
                                )
                            } else {
                                DropdownMenuItem(
                                    text        = { Text(Strings.sessionWatch) },
                                    leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                                    onClick     = { showMenu = false; showWatchDialog = true },
                                )
                            }
                            if (ui.isHost) {
                                DropdownMenuItem(
                                    text        = { Text(Strings.sessionNewGame) },
                                    leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                                    onClick     = { showMenu = false; showNewGame = true },
                                )
                            }
                            DropdownMenuItem(
                                text        = { Text(Strings.settings) },
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
            if (!ui.observing) {
                ExtendedFloatingActionButton(
                    onClick = { showStatPicker = true },
                    icon    = { Icon(Icons.Default.Add, contentDescription = null) },
                    text    = { Text(Strings.sessionStats) },
                )
            }
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

            WatchersRow(ui)

            Spacer(Modifier.height(8.dp))

            if (ui.users.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(Strings.sessionWaiting)
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
                            onTakeMonarch = if (isMe) { { vm.setMonarch(user.id) } } else null,
                            playerIndex = index,
                        )
                    }
                }
            }

            ui.error?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(Strings.connectionError(msg), color = MaterialTheme.colorScheme.error)
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
    val label = (if (isDaytime) Strings.day else Strings.night)
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
                val nextLabel = (if (isDaytime) Strings.night else Strings.day)
                Icon(nextIcon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(nextLabel, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Observers: who is watching, and a note when that's you
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WatchersRow(ui: SessionUiState) {
    val others = ui.observers.filterKeys { it != ui.myUserId }.values.sorted()
    if (!ui.observing && others.isEmpty()) return
    val text = when {
        ui.observing && others.isEmpty() -> Strings.watchingYou
        ui.observing                     -> Strings.watchingYouAnd(others.joinToString())
        else                             -> Strings.watchingOthers(others.joinToString())
    }
    val color = backdropTextColor()
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp),
             tint = color)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/**
 * Text drawn straight onto the screen's backdrop (timer, who's watching). Over a background
 * image it's black or white, whichever contrasts better with how bright the image is there;
 * without one, black in light mode and the theme's muted colour in dark mode.
 */
internal fun onBackdropColor(backdropLuminance: Float?, lightTheme: Boolean, themeColor: Color): Color = when {
    backdropLuminance != null -> if (prefersDarkText(backdropLuminance)) Color.Black else Color.White
    lightTheme                -> Color.Black
    else                      -> themeColor
}

/** [onBackdropColor] for the current background and theme. */
@Composable
internal fun backdropTextColor(): Color = onBackdropColor(
    backdropLuminance = LocalBackdropLuminance.current,
    lightTheme        = MaterialTheme.colorScheme.background.luminance() > 0.5f,
    themeColor        = MaterialTheme.colorScheme.onSurfaceVariant,
)

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
    val idleTint   = backdropTextColor()
    val tint       = if (urgent) MaterialTheme.colorScheme.error else idleTint

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
            Icon(icon, contentDescription = (if (running) Strings.timerPause else Strings.timerStart), tint = tint)
        }
        if (hasStarted) {
            IconButton(onClick = onReset, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Replay, contentDescription = Strings.timerReset,
                    tint = idleTint)
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Connection state banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConnectionBanner(state: ConnectionState) {
    // Over a background image the notices are black or white like the timer, so they stay
    // readable; a failure stays red either way.
    val onImage = if (LocalBackdropLuminance.current != null) backdropTextColor() else null
    val (text, color) = when (state) {
        ConnectionState.Connected    -> return
        ConnectionState.Closed       -> return
        ConnectionState.Connecting   -> Strings.connectionConnecting           to (onImage ?: MaterialTheme.colorScheme.tertiary)
        ConnectionState.Reconnecting -> Strings.connectionReconnecting         to (onImage ?: MaterialTheme.colorScheme.secondary)
        is ConnectionState.Failed    -> Strings.connectionFailed(state.reason) to MaterialTheme.colorScheme.error
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
    onSetMonarch     : (userId: String?) -> Unit,
    onDismiss        : () -> Unit,
) {
    val statDefs        = ui.myStats
    val dayNightEnabled = DAY_NIGHT_GLOBAL in ui.globalStats
    val monarchName     = ui.monarch?.let { id -> ui.users.find { it.id == id }?.displayName }

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
                    text  = Strings.statsActive.uppercase(),
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
                text  = Strings.statsGlobal.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            PickerRow(
                label     = Strings.statsDayNight,
                typeLabel = Strings.statsDayNightHint,
                active    = dayNightEnabled,
                onClick   = { if (!dayNightEnabled) onEnableDayNight() },
            )
            if (ui.monarch != null) {
                ActiveStatRow(
                    label     = Strings.statMonarch,
                    typeLabel = Strings.statsMonarchHeldBy(monarchName ?: Strings.historyUnknownPlayer),
                    onRemove  = { onSetMonarch(null) },
                )
            } else {
                PickerRow(
                    label     = Strings.statMonarch,
                    typeLabel = Strings.statsMonarchHint,
                    active    = false,
                    onClick   = { onSetMonarch(ui.myUserId) },
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // ── Add a per-player stat ─────────────────────────────────────
            Text(
                text  = Strings.statsAdd.uppercase(),
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
                Text(Strings.statsCustom)
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
            Icon(Icons.Default.Close, contentDescription = Strings.statsRemove(label),
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
                Icon(Icons.Default.Check, contentDescription = Strings.statsAlreadyAdded,
                     tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Default.Add, contentDescription = Strings.statsAddNamed(label))
            }
        }
    }
}

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
        title = { Text(Strings.friendRequestTitle) },
        text  = { Text(Strings.friendRequestText(request.fromDisplayName)) },
        confirmButton = {
            TextButton(onClick = onAccept) { Text(Strings.friendRequestAccept) }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text(Strings.friendRequestDecline) }
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
        title = { Text(Strings.customStatTitle) },
        text  = {
            OutlinedTextField(
                value         = name,
                onValueChange = { if (it.length <= MAX_STAT_NAME_LENGTH) name = it },
                label         = { Text(Strings.customStatName) },
                placeholder   = { Text(Strings.customStatPlaceholder) },
                singleLine    = true,
                isError       = showError,
                // Enter (or the keyboard's Done key) adds the stat, like the Add button.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (isValid) onConfirm(trimmed) }),
                supportingText = if (showError) {
                    {
                        Text(
                            if (isReserved) Strings.customStatReserved(trimmed)
                            else Strings.customStatInvalid
                        )
                    }
                } else null,
            )
        },
        confirmButton = {
            TextButton(
                onClick  = { if (isValid) onConfirm(trimmed) },
                enabled  = isValid,
            ) { Text(Strings.actionAdd) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.actionCancel) }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Life history
// ─────────────────────────────────────────────────────────────────────────────


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(
    history  : List<Change>,
    users    : List<UserState>,
    statDefs : Map<String, StatType>,
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
                Text(Strings.historyTitle, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onUndo, enabled = history.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(Strings.historyUndoLast)
                }
            }
            if (history.isEmpty()) {
                Text(
                    Strings.historyEmpty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(history, key = { it.id }) { change ->
                    val line = describe(change, users, statDefs)
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            line.label,
                            style    = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        line.delta?.let { delta ->
                            Text(
                                delta.signed(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (delta < 0) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(line.result, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/** A change as shown in the history: what changed, by how much (counters only), and the result. */
private data class ChangeLine(val label: String, val delta: Int?, val result: String)

@Composable
private fun describe(change: Change, users: List<UserState>, statDefs: Map<String, StatType>): ChangeLine =
    when (change) {
        is StatChange   -> ChangeLine(
            label  = changeLabel(change.stat),
            delta  = change.delta,
            result = Strings.historyValueAfter(valueText(change, statDefs)),
        )
        is StatToggled  -> ChangeLine(
            label  = statLabel(change.stat),
            delta  = null,
            result = (if (change.enabled) Strings.historyTurnedOn else Strings.historyTurnedOff),
        )
        is MonarchChange -> ChangeLine(
            label  = Strings.statMonarch,
            delta  = null,
            result = change.after?.let { id ->
                Strings.historyValueAfter(users.find { it.id == id }?.displayName ?: Strings.historyUnknownPlayer)
            } ?: Strings.historyTurnedOff,
        )
        is GlobalChange -> ChangeLine(
            label  = Strings.statsDayNight,
            delta  = null,
            result = if (change.before == null) Strings.historyTurnedOn
                     else Strings.historyValueAfter((if (change.after == 0u) Strings.day else Strings.night)),
        )
    }

@Composable
private fun changeText(change: Change, users: List<UserState>, statDefs: Map<String, StatType>): String {
    val line = describe(change, users, statDefs)
    return if (line.delta != null) Strings.changeSnackbar(line.label, line.delta.signed(), line.result)
           else                    Strings.changeSnackbarPlain(line.label, line.result)
}

/** Which counter a change was made to: life, or a stat by name. */
@Composable
private fun changeLabel(stat: String): String = when (val target = statTarget(stat)) {
    StatTarget.Life      -> Strings.statLife
    is StatTarget.Custom -> statLabel(target.name)
}

/** Toggles read as on/off; everything else as its number. */
@Composable
private fun valueText(change: StatChange, statDefs: Map<String, StatType>): String {
    val target = statTarget(change.stat)
    val toggle = target is StatTarget.Custom && statDefs[target.name] == StatType.TOGGLE
    return when {
        !toggle                  -> change.valueAfter.toString()
        change.valueAfter > 0u   -> Strings.historyToggleOn
        else                     -> Strings.historyToggleOff
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
    val anyPlayers = Strings.rulesAnyPlayers
    fun players(n: UInt) = if (n == 0u) anyPlayers else n.toString()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.sessionGameRules) },
        text  = {
            Column(
                modifier            = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (editable) {
                    PresetRow(Strings.rulesStartingLifeShort, draft.startLife, listOf(40u, 20u),
                        { draft = draft.copy(startLife = it) }, custom = START_LIFE_RANGE)
                    PresetRow(Strings.rulesCommanderLimit, draft.commanderDeathThreshold, listOf(21u),
                        { draft = draft.copy(commanderDeathThreshold = it) }, custom = DAMAGE_LIMIT_RANGE)
                    PresetRow(Strings.rulesInfectLimit, draft.infectDeathThreshold, listOf(10u),
                        { draft = draft.copy(infectDeathThreshold = it) }, custom = DAMAGE_LIMIT_RANGE)
                    PresetRow(Strings.rulesMaxPlayers, draft.maxPlayers.toUInt(), listOf(0u, 2u, 4u, 6u),
                        { draft = draft.copy(maxPlayers = it.toInt()) }, valueText = ::players)
                    Text(
                        Strings.rulesNextGameHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(Strings.rulesSummaryStartingLife(settings.startLife.toInt()))
                    Text(Strings.rulesSummaryCommander(settings.commanderDeathThreshold.toInt()))
                    Text(Strings.rulesSummaryInfect(settings.infectDeathThreshold.toInt()))
                    Text(Strings.rulesSummaryPlayers(players(settings.maxPlayers.toUInt())))
                    Text(
                        Strings.rulesHostOnly,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (editable) TextButton(onClick = { onSave(draft) }) { Text(Strings.actionSave) }
            else TextButton(onClick = onDismiss) { Text(Strings.actionClose) }
        },
        dismissButton = if (editable) {
            { TextButton(onClick = onDismiss) { Text(Strings.actionCancel) } }
        } else null,
    )
}
