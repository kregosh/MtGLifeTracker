package com.kregosh.mtglifetracker.ui.screens

import com.kregosh.mtglifetracker.ui.Strings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.kregosh.mtglifetracker.ui.platform.BuiltInImage
import com.kregosh.mtglifetracker.ui.platform.LocalPlatform
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
    val platform = LocalPlatform.current

    val displayName    by vm.displayName.collectAsState()
    var showNameDialog by remember { mutableStateOf(false) }

    val pickAppBackground  = platform.rememberImagePicker(vm::setBackgroundImage)
    val pickCardBackground = platform.rememberImagePicker(vm::setCardBackgroundImage)

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

    platform.BackHandler(enabled = true, onBack = vm::closeSettings)

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
                title  = { Text(Strings.settings) },
                navigationIcon = {
                    IconButton(onClick = vm::closeSettings) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.actionBack)
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
            SettingsSection(title = Strings.settingsIdentity) {
                Row(
                    modifier             = Modifier.fillMaxWidth(),
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text  = displayName.ifBlank { Strings.defaultPlayerName },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    OutlinedButton(onClick = { showNameDialog = true }) {
                        Text(Strings.actionChange)
                    }
                }
            }

            // ── Appearance ────────────────────────────────────────────────────
            SettingsSection(title = Strings.settingsAppearance) {
                val colorScheme by vm.colorScheme.collectAsState()

                Text(Strings.settingsColorScheme, style = MaterialTheme.typography.labelMedium)
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
                                AppColorScheme.DARK   -> Strings.colorSchemeDark
                                AppColorScheme.LIGHT  -> Strings.colorSchemeLight
                                AppColorScheme.SYSTEM -> Strings.colorSchemeSystem
                            })
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                val appBgUri by vm.backgroundImageUri.collectAsState()
                Text(Strings.settingsAppBackground, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickAppBackground() }) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text((if (appBgUri != null) Strings.actionChange else Strings.actionChoose))
                    }
                    if (appBgUri != null) {
                        OutlinedButton(
                            onClick = { vm.setBackgroundImage(null) },
                            colors  = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(Strings.actionRemove) }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(Strings.settingsPresets, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        vm.setBackgroundImage(platform.builtInImageUri(BuiltInImage.ARCANE_STORM))
                    }) { Text(Strings.settingsPresetStorm) }
                    OutlinedButton(onClick = {
                        vm.setBackgroundImage(platform.builtInImageUri(BuiltInImage.MANA_ORBS))
                    }) { Text(Strings.settingsPresetManaOrbs) }
                }

                Spacer(Modifier.height(12.dp))

                val cardBgUri by vm.cardBackgroundImageUri.collectAsState()
                Text(Strings.settingsCardBackground, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickCardBackground() }) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text((if (cardBgUri != null) Strings.actionChange else Strings.actionChoose))
                    }
                    if (cardBgUri != null) {
                        OutlinedButton(
                            onClick = { vm.setCardBackgroundImage(null) },
                            colors  = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(Strings.actionRemove) }
                    }
                }
            }

            // ── Timer ─────────────────────────────────────────────────────────
            SettingsSection(title = Strings.settingsTimer) {
                var timerVisible by remember { mutableStateOf(vm.timerVisible.value) }
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(Strings.settingsShowTimer, style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked         = timerVisible,
                        onCheckedChange = { v -> timerVisible = v; vm.setTimerVisible(v) },
                    )
                }

                if (timerVisible) {
                    Spacer(Modifier.height(12.dp))

                    var countDown by remember { mutableStateOf(vm.timerCountDown.value) }
                    Text(Strings.settingsTimerMode, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    @OptIn(ExperimentalMaterial3Api::class)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !countDown,
                            onClick  = { countDown = false; vm.setTimerCountDown(false) },
                            shape    = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) { Text(Strings.settingsTimerStopwatch) }
                        SegmentedButton(
                            selected = countDown,
                            onClick  = { countDown = true; vm.setTimerCountDown(true) },
                            shape    = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) { Text(Strings.settingsTimerCountdown) }
                    }

                    if (countDown) {
                        Spacer(Modifier.height(12.dp))
                        val timerLimitMinutes by vm.timerLimitMinutes.collectAsState()
                        PresetRow(
                            label    = Strings.settingsTimerLimit,
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
                SettingsSection(title = Strings.friends) {
                    Text(
                        Strings.settingsFriendsHint,
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
                SettingsSection(title = Strings.settingsRecentPlayers) {
                    Text(
                        Strings.settingsRecentPlayersHint,
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
            SettingsSection(title = Strings.settingsGameRules) {
                Text(
                    Strings.settingsGameRulesHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                val startLife          by vm.startLife.collectAsState()
                val commanderThreshold by vm.commanderThreshold.collectAsState()
                val infectThreshold    by vm.infectThreshold.collectAsState()

                PresetRow(
                    label    = Strings.rulesStartingLife,
                    current  = startLife,
                    presets  = listOf(40u, 20u),
                    onSelect = vm::setStartLife,
                    custom   = START_LIFE_RANGE,
                )
                Spacer(Modifier.height(12.dp))
                PresetRow(
                    label    = Strings.rulesCommanderLimit,
                    current  = commanderThreshold,
                    presets  = listOf(21u),
                    onSelect = vm::setCommanderThreshold,
                    custom   = DAMAGE_LIMIT_RANGE,
                )
                Spacer(Modifier.height(12.dp))
                PresetRow(
                    label    = Strings.rulesInfectLimit,
                    current  = infectThreshold,
                    presets  = listOf(10u),
                    onSelect = vm::setInfectThreshold,
                    custom   = DAMAGE_LIMIT_RANGE,
                )
                Spacer(Modifier.height(12.dp))
                var commanderDefault by remember { mutableStateOf(vm.commanderDefaultEnabled) }
                Row(
                    modifier             = Modifier.fillMaxWidth(),
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(Strings.settingsCommanderDefault, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            Strings.settingsCommanderDefaultHint,
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
        title    = { Text(title ?: Strings.nameDialogTitle) },
        text     = {
            OutlinedTextField(
                value          = name,
                onValueChange  = { if (it.length <= MAX_DISPLAY_NAME_LENGTH) name = it },
                singleLine     = true,
                placeholder    = { Text(Strings.nameDialogPlaceholder) },
                supportingText = { Text(Strings.nameDialogCounter(name.length, MAX_DISPLAY_NAME_LENGTH)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick  = { if (name.isNotBlank()) onConfirm(name) },
                enabled  = name.isNotBlank(),
            ) { Text(Strings.actionOk) }
        },
        dismissButton = if (dismissible) {
            { TextButton(onClick = onDismiss) { Text(Strings.actionCancel) } }
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

/**
 * Segmented buttons for a numeric setting. With [custom], a last "Custom" button lets the
 * player type any value in that range; it shows the value while one is chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PresetRow(
    label    : String,
    current  : UInt,
    presets  : List<UInt>,
    onSelect : (UInt) -> Unit,
    valueText: (UInt) -> String = { it.toString() },
    custom   : UIntRange? = null,
) {
    var editing by remember { mutableStateOf(false) }
    val count    = presets.size + if (custom != null) 1 else 0
    val isCustom = current !in presets

    Text(label, style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(4.dp))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        presets.forEachIndexed { idx, value ->
            SegmentedButton(
                selected = current == value,
                onClick  = { onSelect(value) },
                shape    = SegmentedButtonDefaults.itemShape(index = idx, count = count),
            ) { Text(valueText(value)) }
        }
        if (custom != null) {
            SegmentedButton(
                selected = isCustom,
                onClick  = { editing = true },
                shape    = SegmentedButtonDefaults.itemShape(index = presets.size, count = count),
            ) { Text(if (isCustom) Strings.rulesCustomValue(valueText(current)) else Strings.rulesCustom) }
        }
    }

    if (editing && custom != null) {
        NumberDialog(
            title     = label,
            initial   = current.takeIf { isCustom },
            range     = custom,
            onConfirm = { onSelect(it); editing = false },
            onDismiss = { editing = false },
        )
    }
}

/** Asks for a whole number in [range]; Enter confirms like OK. */
@Composable
private fun NumberDialog(
    title    : String,
    initial  : UInt?,
    range    : UIntRange,
    onConfirm: (UInt) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial?.toString().orEmpty()) }
    val value = parseInRange(text, range)
    // Straight into typing: the field takes focus, so the keyboard comes up.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text  = {
            OutlinedTextField(
                value           = text,
                onValueChange   = { input -> text = input.filter(Char::isDigit).take(4) },
                singleLine      = true,
                isError         = text.isNotEmpty() && value == null,
                supportingText  = { Text(Strings.rulesCustomRange(range.first, range.last)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { value?.let(onConfirm) }),
                modifier        = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onConfirm) }, enabled = value != null) { Text(Strings.actionOk) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.actionCancel) }
        },
    )
}

/** [text] as a number if it's one within [range], else null. */
internal fun parseInRange(text: String, range: UIntRange): UInt? =
    text.trim().toUIntOrNull()?.takeIf { it in range }

// The same bounds as database.rules.json, which refuses anything outside them.
internal val START_LIFE_RANGE   = 1u..1000u
internal val DAMAGE_LIMIT_RANGE = 1u..100u
