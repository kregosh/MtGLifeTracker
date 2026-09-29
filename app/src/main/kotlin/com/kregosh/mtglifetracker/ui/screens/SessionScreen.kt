package com.kregosh.mtglifetracker.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kregosh.mtglifetracker.network.WsState
import com.kregosh.mtglifetracker.ui.components.PlayerCard
import com.kregosh.mtglifetracker.viewmodel.SessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(vm: SessionViewModel) {
    val ui      by vm.sessionUi.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Session")
                        if (ui.sessionCode.isNotEmpty()) {
                            Text(
                                text  = "Code: ${ui.sessionCode}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = vm::leaveSession) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Leave session",
                        )
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
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            // Connection state banner
            ConnectionBanner(ui.wsState)

            Spacer(Modifier.height(8.dp))

            if (ui.users.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Waiting for players to join…")
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding      = PaddingValues(bottom = 16.dp),
                ) {
                    items(ui.users, key = { it.id }) { user ->
                        val isMe = user.id == ui.myUserId
                        PlayerCard(
                            user        = user,
                            isMe        = isMe,
                            onIncrement = { if (isMe) vm.increment() },
                            onDecrement = { if (isMe) vm.decrement() },
                        )
                    }
                }
            }

            // Inline error toast
            ui.error?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(msg, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ConnectionBanner(state: WsState) {
    val (text, color) = when (state) {
        WsState.Connected    -> return  // no banner when healthy
        WsState.Connecting   -> "Connecting…" to MaterialTheme.colorScheme.tertiary
        WsState.Reconnecting -> "Reconnecting…" to MaterialTheme.colorScheme.secondary
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
