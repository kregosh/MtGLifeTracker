package com.kregosh.mtglifetracker.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import com.kregosh.mtglifetracker.network.WsState
import com.kregosh.mtglifetracker.shared.StatType
import com.kregosh.mtglifetracker.ui.components.PlayerCard
import com.kregosh.mtglifetracker.ui.theme.LocalHasBackground
import com.kregosh.mtglifetracker.viewmodel.SessionUiState
import com.kregosh.mtglifetracker.viewmodel.SessionViewModel

// ─────────────────────────────────────────────────────────────────────────────
// Predefined per-player stat catalogue
// ─────────────────────────────────────────────────────────────────────────────

private data class StatPreset(val id: String, val label: String, val type: StatType)

private val PREDEFINED_STATS = listOf(
    StatPreset("commander",  "Commander Damage",  StatType.NUMERIC),
    StatPreset("poison",     "Poison / Infect",   StatType.NUMERIC),
    StatPreset("energy",     "Energy",            StatType.NUMERIC),
    StatPreset("experience", "Experience",        StatType.NUMERIC),
    StatPreset("storm",      "Storm Count",       StatType.NUMERIC),
    StatPreset("tax",        "Commander Tax",     StatType.NUMERIC),
    StatPreset("ring",       "The Ring",          StatType.RING_STAGE),
    StatPreset("monarch",    "Monarch",           StatType.TOGGLE),
    StatPreset("initiative", "Initiative",        StatType.TOGGLE),
    StatPreset("blessing",   "City's Blessing",   StatType.TOGGLE),
)

// Day/Night is a session-global toggle, stored in globalStats, NOT in customStatNames/statDefs.
private const val GLOBAL_DAY_NIGHT = "daynight"

// ─────────────────────────────────────────────────────────────────────────────
// Session screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(vm: SessionViewModel) {
    val ui                by vm.sessionUi.collectAsState()
    val timerElapsed      by vm.timerElapsed.collectAsState()
    val timerRunning      by vm.timerRunning.collectAsState()
    val timerVisible      by vm.timerVisible.collectAsState()
    val timerCountDown    by vm.timerCountDown.collectAsState()
    val timerLimitMinutes by vm.timerLimitMinutes.collectAsState()
    val context = LocalContext.current
    val hasBg   = LocalHasBackground.current

    SessionContent(vm, ui, timerElapsed, timerRunning, timerVisible, timerCountDown, timerLimitMinutes, context, hasBg)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionContent(
    vm                : SessionViewModel,
    ui                : SessionUiState,
    timerElapsed      : Duration,
    timerRunning      : Boolean,
    timerVisible      : Boolean,
    timerCountDown    : Boolean,
    timerLimitMinutes : UInt,
    context           : android.content.Context,
    hasBg             : Boolean,
) {

    var showStatPicker   by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }

    if (showStatPicker) {
        StatPickerSheet(
            ui          = ui,
            onAdd       = { name, type -> vm.addCustomStat(name, type); showStatPicker = false },
            onAddCustom = { showStatPicker = false; showCustomDialog = true },
            onRemove    = { name -> vm.removeCustomStat(name) },
            onEnableDayNight = { vm.setGlobal(GLOBAL_DAY_NIGHT, 0u) ; showStatPicker = false },
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
                        Text("Session")
                        if (ui.sessionCode.isNotEmpty()) {
                            Text(
                                text  = "Code: ${ui.sessionCode}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (hasBg) Color.White.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = vm::leaveSession) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Leave session")
                    }
                },
                actions = {
                    if (ui.sessionCode.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val shareText = "Join my MtG Life Tracker session!\n" +
                                        "Code: ${ui.sessionCode}\n" +
                                        "Or tap: mtgtracker://join/${ui.sessionCode}"
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share invite"))
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share invite")
                        }
                    }
                    IconButton(onClick = vm::openSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showStatPicker = true },
                icon    = { Icon(Icons.Default.Add, contentDescription = null) },
                text    = { Text("Stats") },
            )
        },
    ) { padding ->
        // Radial gradient emanating from the sun/moon icon in the Day/Night banner.
        // The icon sits at approximately (left-padding + icon-half, topbar-height + banner-half).
        // Using fixed dp values avoids layout measurement while staying close enough.
        val density = LocalDensity.current
        val dayNightBrush: Brush? = if (GLOBAL_DAY_NIGHT !in ui.globalStats) null else {
            val iconX = with(density) { (padding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + 24.dp).toPx() }
            val iconY = with(density) { (padding.calculateTopPadding() + 27.dp).toPx() }
            val radius = with(density) { 420.dp.toPx() }
            val isDaytime = (ui.globalStats[GLOBAL_DAY_NIGHT] ?: 0u) == 0u
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
            ConnectionBanner(ui.wsState)

            // Day/Night banner — only shown once the global has been initialised
            if (GLOBAL_DAY_NIGHT in ui.globalStats) {
                DayNightBanner(
                    isDaytime = (ui.globalStats[GLOBAL_DAY_NIGHT] ?: 0u) == 0u,
                    onToggle  = { vm.toggleGlobal(GLOBAL_DAY_NIGHT) },
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
                    Text("Waiting for players to join…")
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding      = PaddingValues(bottom = 80.dp),
                ) {
                    items(ui.users, key = { it.id }) { user ->
                        val isMe = user.id == ui.myUserId
                        PlayerCard(
                            user      = user,
                            isMe      = isMe,
                            sessionUi = ui,
                            onAdjust  = { stat, delta -> if (isMe) vm.adjust(stat, delta) },
                        )
                    }
                }
            }

            ui.error?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(msg, color = MaterialTheme.colorScheme.error)
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
    val label = if (isDaytime) "Day" else "Night"
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
                val nextLabel = if (isDaytime) "Night" else "Day"
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
    val limit      = kotlin.time.Duration.Companion.minutes(limitMinutes.toLong())
    val display    = if (countDown) (limit - elapsed).coerceAtLeast(Duration.ZERO) else elapsed
    val urgent     = countDown && display < kotlin.time.Duration.Companion.minutes(1) &&
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
            Icon(icon, contentDescription = if (running) "Pause" else "Start", tint = tint)
        }
        if (hasStarted) {
            IconButton(onClick = onReset, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Replay, contentDescription = "Reset timer",
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
private fun ConnectionBanner(state: WsState) {
    val (text, color) = when (state) {
        WsState.Connected    -> return
        WsState.Closed       -> return
        WsState.Connecting   -> "Connecting…"                   to MaterialTheme.colorScheme.tertiary
        WsState.Reconnecting -> "Reconnecting…"                 to MaterialTheme.colorScheme.secondary
        is WsState.Failed    -> "Disconnected: ${state.reason}" to MaterialTheme.colorScheme.error
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
    val dayNightEnabled = GLOBAL_DAY_NIGHT in ui.globalStats

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
                    text  = "ACTIVE STATS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                statDefs.forEach { (name, type) ->
                    ActiveStatRow(
                        label     = presetLabel(name),
                        typeLabel = typeLabel(type),
                        onRemove  = { onRemove(name) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
            }

            // ── Global options ────────────────────────────────────────────
            Text(
                text  = "GLOBAL",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            PickerRow(
                label     = "Day / Night",
                typeLabel = "Session-wide toggle",
                active    = dayNightEnabled,
                onClick   = { if (!dayNightEnabled) onEnableDayNight() },
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // ── Add a per-player stat ─────────────────────────────────────
            Text(
                text  = "ADD STAT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            PREDEFINED_STATS.forEach { preset ->
                val active = preset.id in statDefs
                PickerRow(
                    label     = preset.label,
                    typeLabel = typeLabel(preset.type),
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
                Text("Custom stat…")
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
            Icon(Icons.Default.Close, contentDescription = "Remove $label",
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
                Icon(Icons.Default.Check, contentDescription = "Already added",
                     tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Default.Add, contentDescription = "Add $label")
            }
        }
    }
}

private fun presetLabel(id: String): String =
    PREDEFINED_STATS.find { it.id == id }?.label ?: id

private fun typeLabel(type: StatType): String = when (type) {
    StatType.NUMERIC    -> "Counter"
    StatType.TOGGLE     -> "Toggle"
    StatType.RING_STAGE -> "Stage tracker (1–4)"
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add custom stat") },
        text  = {
            OutlinedTextField(
                value         = name,
                onValueChange = { if (it.length <= 32) name = it },
                label         = { Text("Stat name") },
                placeholder   = { Text("e.g. Gold, Lore…") },
                singleLine    = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick  = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled  = name.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
