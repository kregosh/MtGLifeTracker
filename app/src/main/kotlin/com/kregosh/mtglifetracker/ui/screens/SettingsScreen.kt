package com.kregosh.mtglifetracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import android.net.Uri
import com.kregosh.mtglifetracker.R
import com.kregosh.mtglifetracker.data.AppColorScheme
import com.kregosh.mtglifetracker.ui.components.FriendsList
import com.kregosh.mtglifetracker.ui.components.RecentPlayersList
import com.kregosh.mtglifetracker.ui.theme.LocalHasBackground
import com.kregosh.mtglifetracker.viewmodel.MAX_DISPLAY_NAME_LENGTH
import com.kregosh.mtglifetracker.viewmodel.SessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SessionViewModel) {
    val hasBg   = LocalHasBackground.current
    val context = LocalContext.current

    val displayName    by vm.displayName.collectAsState()
    var showNameDialog by remember { mutableStateOf(false) }

    val appBgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            vm.setBackgroundImage(it.toString())
        }
    }

    val cardBgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            vm.setCardBackgroundImage(it.toString())
        }
    }

    if (showNameDialog) {
        DisplayNameDialog(
            initial   = displayName,
            onConfirm = { name ->
                vm.setDisplayName(name)
                showNameDialog = false
            },
            onDismiss = { showNameDialog = false },
        )
    }

    BackHandler(onBack = vm::closeSettings)

    val topBarColors = if (hasBg) TopAppBarDefaults.topAppBarColors(
        containerColor             = Color.Black.copy(alpha = 0.45f),
        titleContentColor          = Color.White,
        navigationIconContentColor = Color.White,
    ) else TopAppBarDefaults.topAppBarColors()

    Scaffold(
        containerColor = if (hasBg) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = topBarColors,
                title  = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = vm::closeSettings) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── Identity ──────────────────────────────────────────────────────
            SettingsSection(title = stringResource(R.string.settings_identity)) {
                Row(
                    modifier             = Modifier.fillMaxWidth(),
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text  = displayName.ifBlank { stringResource(R.string.default_player_name) },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    OutlinedButton(onClick = { showNameDialog = true }) {
                        Text(stringResource(R.string.action_change))
                    }
                }
            }

            // ── Appearance ────────────────────────────────────────────────────
            SettingsSection(title = stringResource(R.string.settings_appearance)) {
                val colorScheme by vm.colorScheme.collectAsState()

                Text(stringResource(R.string.settings_color_scheme), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                @OptIn(ExperimentalMaterial3Api::class)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    AppColorScheme.entries.forEachIndexed { idx, scheme ->
                        SegmentedButton(
                            selected = colorScheme == scheme,
                            onClick  = { vm.setColorScheme(scheme) },
                            shape    = SegmentedButtonDefaults.itemShape(index = idx, count = AppColorScheme.entries.size),
                        ) {
                            Text(when (scheme) {
                                AppColorScheme.DARK   -> stringResource(R.string.color_scheme_dark)
                                AppColorScheme.LIGHT  -> stringResource(R.string.color_scheme_light)
                                AppColorScheme.SYSTEM -> stringResource(R.string.color_scheme_system)
                            })
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                val appBgUri by vm.backgroundImageUri.collectAsState()
                Text(stringResource(R.string.settings_app_background), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { appBgPicker.launch("image/*") }) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(if (appBgUri != null) R.string.action_change else R.string.action_choose))
                    }
                    if (appBgUri != null) {
                        OutlinedButton(
                            onClick = { vm.setBackgroundImage(null) },
                            colors  = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(stringResource(R.string.action_remove)) }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_presets), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                val pkg = context.packageName
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        vm.setBackgroundImage("android.resource://$pkg/drawable/bg_arcane_storm")
                    }) { Text(stringResource(R.string.settings_preset_storm)) }
                    OutlinedButton(onClick = {
                        vm.setBackgroundImage("android.resource://$pkg/drawable/bg_mana_orbs")
                    }) { Text(stringResource(R.string.settings_preset_mana_orbs)) }
                }

                Spacer(Modifier.height(12.dp))

                val cardBgUri by vm.cardBackgroundImageUri.collectAsState()
                Text(stringResource(R.string.settings_card_background), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { cardBgPicker.launch("image/*") }) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(if (cardBgUri != null) R.string.action_change else R.string.action_choose))
                    }
                    if (cardBgUri != null) {
                        OutlinedButton(
                            onClick = { vm.setCardBackgroundImage(null) },
                            colors  = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(stringResource(R.string.action_remove)) }
                    }
                }
            }

            // ── Timer ─────────────────────────────────────────────────────────
            SettingsSection(title = stringResource(R.string.settings_timer)) {
                var timerVisible by remember { mutableStateOf(vm.timerVisible.value) }
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.settings_show_timer), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked         = timerVisible,
                        onCheckedChange = { v -> timerVisible = v; vm.setTimerVisible(v) },
                    )
                }

                if (timerVisible) {
                    Spacer(Modifier.height(12.dp))

                    var countDown by remember { mutableStateOf(vm.timerCountDown.value) }
                    Text(stringResource(R.string.settings_timer_mode), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    @OptIn(ExperimentalMaterial3Api::class)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !countDown,
                            onClick  = { countDown = false; vm.setTimerCountDown(false) },
                            shape    = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) { Text(stringResource(R.string.settings_timer_stopwatch)) }
                        SegmentedButton(
                            selected = countDown,
                            onClick  = { countDown = true; vm.setTimerCountDown(true) },
                            shape    = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) { Text(stringResource(R.string.settings_timer_countdown)) }
                    }

                    if (countDown) {
                        Spacer(Modifier.height(12.dp))
                        val timerLimitMinutes by vm.timerLimitMinutes.collectAsState()
                        PresetRow(
                            label    = stringResource(R.string.settings_timer_limit),
                            current  = timerLimitMinutes,
                            presets  = listOf(30u, 45u, 60u, 90u),
                            onSelect = vm::setTimerLimitMinutes,
                        )
                    }
                }
            }

            // ── Friends ───────────────────────────────────────────────────────
            val friendList     by vm.friendList.collectAsState()
            val knownPlayers   by vm.knownPlayers.collectAsState()
            val friendPresence by vm.friendPresence.collectAsState()
            val friendIds      = remember(friendList) { friendList.map { it.userId }.toSet() }

            if (friendList.isNotEmpty()) {
                SettingsSection(title = stringResource(R.string.friends)) {
                    Text(
                        stringResource(R.string.settings_friends_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    FriendsList(
                        friends          = friendList,
                        friendPresence   = friendPresence,
                        currentSessionId = null,
                        onJoinSession    = { sessionId, watch -> vm.joinFriendSession(sessionId, watch) },
                        onRemoveFriend   = vm::removeFriend,
                    )
                }
            }

            // ── Known players ─────────────────────────────────────────────────
            if (knownPlayers.isNotEmpty()) {
                SettingsSection(title = stringResource(R.string.settings_recent_players)) {
                    Text(
                        stringResource(R.string.settings_recent_players_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    RecentPlayersList(
                        players        = knownPlayers,
                        friendIds      = friendIds,
                        onAddFriend    = vm::addFriend,
                        onForgetPlayer = vm::forgetPlayer,
                    )
                }
            }

            // ── Game rules ────────────────────────────────────────────────────
            SettingsSection(title = stringResource(R.string.settings_game_rules)) {
                Text(
                    stringResource(R.string.settings_game_rules_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                val startLife          by vm.startLife.collectAsState()
                val commanderThreshold by vm.commanderThreshold.collectAsState()
                val infectThreshold    by vm.infectThreshold.collectAsState()

                PresetRow(
                    label    = stringResource(R.string.rules_starting_life),
                    current  = startLife,
                    presets  = listOf(20u, 30u, 40u),
                    onSelect = vm::setStartLife,
                )
                Spacer(Modifier.height(12.dp))
                PresetRow(
                    label    = stringResource(R.string.rules_commander_limit),
                    current  = commanderThreshold,
                    presets  = listOf(21u, 15u, 10u),
                    onSelect = vm::setCommanderThreshold,
                )
                Spacer(Modifier.height(12.dp))
                PresetRow(
                    label    = stringResource(R.string.rules_infect_limit),
                    current  = infectThreshold,
                    presets  = listOf(10u, 7u, 5u),
                    onSelect = vm::setInfectThreshold,
                )
                Spacer(Modifier.height(12.dp))
                var commanderDefault by remember { mutableStateOf(vm.commanderDefaultEnabled) }
                Row(
                    modifier             = Modifier.fillMaxWidth(),
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_commander_default), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.settings_commander_default_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked         = commanderDefault,
                        onCheckedChange = { v ->
                            commanderDefault = v
                            vm.setCommanderDefaultEnabled(v)
                        },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Display name dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun DisplayNameDialog(
    initial    : String,
    onConfirm  : (String) -> Unit,
    onDismiss  : () -> Unit,
    title      : String? = null,
    dismissible: Boolean = true,
) {
    var name by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties       = DialogProperties(
            dismissOnBackPress    = dismissible,
            dismissOnClickOutside = dismissible,
        ),
        title    = { Text(title ?: stringResource(R.string.name_dialog_title)) },
        text     = {
            OutlinedTextField(
                value          = name,
                onValueChange  = { if (it.length <= MAX_DISPLAY_NAME_LENGTH) name = it },
                singleLine     = true,
                placeholder    = { Text(stringResource(R.string.name_dialog_placeholder)) },
                supportingText = { Text(stringResource(R.string.name_dialog_counter, name.length, MAX_DISPLAY_NAME_LENGTH)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick  = { if (name.isNotBlank()) onConfirm(name) },
                enabled  = name.isNotBlank(),
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = if (dismissible) {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
        } else null,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Section card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text  = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                content  = content,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preset row (segmented buttons for numeric settings)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PresetRow(
    label    : String,
    current  : UInt,
    presets  : List<UInt>,
    onSelect : (UInt) -> Unit,
    valueText: (UInt) -> String = { it.toString() },
) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(4.dp))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        presets.forEachIndexed { idx, value ->
            SegmentedButton(
                selected = current == value,
                onClick  = { onSelect(value) },
                shape    = SegmentedButtonDefaults.itemShape(index = idx, count = presets.size),
            ) { Text(valueText(value)) }
        }
    }
}
