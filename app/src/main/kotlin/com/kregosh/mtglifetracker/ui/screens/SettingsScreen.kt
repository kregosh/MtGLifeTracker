package com.kregosh.mtglifetracker.ui.screens

import android.net.Uri
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
import androidx.compose.ui.unit.dp
import com.kregosh.mtglifetracker.ui.theme.LocalHasBackground
import com.kregosh.mtglifetracker.viewmodel.SessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SessionViewModel) {
    val hasBg   = LocalHasBackground.current
    val context = LocalContext.current

    var nameInput      by remember { mutableStateOf(vm.displayName) }
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
            initial   = nameInput,
            onConfirm = { name ->
                vm.setDisplayName(name)
                nameInput      = name
                showNameDialog = false
            },
        )
    }

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
                title  = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = vm::closeSettings) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            SettingsSection(title = "Identity") {
                Row(
                    modifier             = Modifier.fillMaxWidth(),
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text  = nameInput.ifBlank { "Player" },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    OutlinedButton(onClick = { showNameDialog = true }) {
                        Text("Change")
                    }
                }
            }

            // ── Appearance ────────────────────────────────────────────────────
            SettingsSection(title = "Appearance") {
                val colorScheme by vm.colorScheme.collectAsState()

                Text("Color scheme", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                @OptIn(ExperimentalMaterial3Api::class)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("dark" to "Dark", "light" to "Light", "system" to "System").forEachIndexed { idx, (key, label) ->
                        SegmentedButton(
                            selected = colorScheme == key,
                            onClick  = { vm.setColorScheme(key) },
                            shape    = SegmentedButtonDefaults.itemShape(index = idx, count = 3),
                        ) { Text(label) }
                    }
                }

                Spacer(Modifier.height(12.dp))

                val appBgUri by vm.backgroundImageUri.collectAsState()
                Text("App background image", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { appBgPicker.launch("image/*") }) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (appBgUri != null) "Change" else "Choose")
                    }
                    if (appBgUri != null) {
                        OutlinedButton(
                            onClick = { vm.setBackgroundImage(null) },
                            colors  = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text("Remove") }
                    }
                }

                Spacer(Modifier.height(12.dp))

                val cardBgUri by vm.cardBackgroundImageUri.collectAsState()
                Text("Your card background image", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { cardBgPicker.launch("image/*") }) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (cardBgUri != null) "Change" else "Choose")
                    }
                    if (cardBgUri != null) {
                        OutlinedButton(
                            onClick = { vm.setCardBackgroundImage(null) },
                            colors  = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text("Remove") }
                    }
                }
            }

            // ── Game rules ────────────────────────────────────────────────────
            SettingsSection(title = "Game Rules") {
                PresetRow(
                    label    = "Starting life total",
                    current  = vm.startLife,
                    presets  = listOf(20u, 30u, 40u),
                    onSelect = vm::setStartLife,
                )
                Spacer(Modifier.height(12.dp))
                PresetRow(
                    label    = "Commander damage limit",
                    current  = vm.commanderThreshold,
                    presets  = listOf(21u, 15u, 10u),
                    onSelect = vm::setCommanderThreshold,
                )
                Spacer(Modifier.height(12.dp))
                PresetRow(
                    label    = "Infect damage limit",
                    current  = vm.infectThreshold,
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
                        Text("Commander damage by default", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Auto-add commander damage when joining a session",
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
    initial  : String,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = { if (name.isNotBlank()) onConfirm(name) },
        title    = { Text("Your display name") },
        text     = {
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                singleLine    = true,
                placeholder   = { Text("e.g. Alice") },
            )
        },
        confirmButton = {
            TextButton(
                onClick  = { if (name.isNotBlank()) onConfirm(name) },
                enabled  = name.isNotBlank(),
            ) { Text("OK") }
        },
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
private fun PresetRow(
    label   : String,
    current : UInt,
    presets : List<UInt>,
    onSelect: (UInt) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(4.dp))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        presets.forEachIndexed { idx, value ->
            SegmentedButton(
                selected = current == value,
                onClick  = { onSelect(value) },
                shape    = SegmentedButtonDefaults.itemShape(index = idx, count = presets.size),
            ) { Text(value.toString()) }
        }
    }
}
