package com.kregosh.mtglifetracker.ui.screens

import android.content.Intent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    val ui      by vm.sessionUi.collectAsState()
    val context = LocalContext.current
    val hasBg   = LocalHasBackground.current

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
        ) {
            ConnectionBanner(ui.wsState)

            // Day/Night banner — only shown once the global has been initialised
            if (GLOBAL_DAY_NIGHT in ui.globalStats) {
                val isDaytime = (ui.globalStats[GLOBAL_DAY_NIGHT] ?: 0u) == 0u
                DayNightBanner(
                    isDaytime = isDaytime,
                    onToggle  = { vm.toggleGlobal(GLOBAL_DAY_NIGHT) },
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
                else           MaterialTheme.colorScheme.secondary
    Surface(
        color    = color.copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Text(label, color = color, style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = onToggle) {
                Text("Toggle", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
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
