package com.kregosh.mtglifetracker.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kregosh.mtglifetracker.network.WsState
import com.kregosh.mtglifetracker.ui.components.PlayerCard
import com.kregosh.mtglifetracker.ui.theme.LocalHasBackground
import com.kregosh.mtglifetracker.viewmodel.SessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(vm: SessionViewModel) {
    val ui           by vm.sessionUi.collectAsState()
    val context      = LocalContext.current
    val hasBg        = LocalHasBackground.current

    var showAddStatDialog by remember { mutableStateOf(false) }

    if (showAddStatDialog) {
        AddCustomStatDialog(
            onConfirm = { name ->
                vm.addCustomStat(name)
                showAddStatDialog = false
            },
            onDismiss = { showAddStatDialog = false },
        )
    }

    val topBarColors = if (hasBg) TopAppBarDefaults.topAppBarColors(
        containerColor         = Color.Black.copy(alpha = 0.45f),
        titleContentColor      = Color.White,
        navigationIconContentColor = Color.White,
        actionIconContentColor = Color.White,
    ) else TopAppBarDefaults.topAppBarColors()

    Scaffold(
        containerColor = if (hasBg) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = topBarColors,
                title = {
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
                onClick = { showAddStatDialog = true },
                icon    = { Icon(Icons.Default.Add, contentDescription = null) },
                text    = { Text("Add stat") },
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

            Spacer(Modifier.height(8.dp))

            if (ui.users.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Waiting for players to join…")
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding      = PaddingValues(bottom = 80.dp), // clear FAB
                ) {
                    items(ui.users, key = { it.id }) { user ->
                        val isMe = user.id == ui.myUserId
                        PlayerCard(
                            user       = user,
                            isMe       = isMe,
                            sessionUi  = ui,
                            onAdjust   = { stat, delta ->
                                if (isMe) vm.adjust(stat, delta)
                            },
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
// Connection state banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConnectionBanner(state: WsState) {
    val (text, color) = when (state) {
        WsState.Connected    -> return
        WsState.Closed       -> return  // intentional leave — no banner
        WsState.Connecting   -> "Connecting…"       to MaterialTheme.colorScheme.tertiary
        WsState.Reconnecting -> "Reconnecting…"     to MaterialTheme.colorScheme.secondary
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
// Add custom stat dialog
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
                placeholder   = { Text("e.g. Energy, Infect…") },
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
