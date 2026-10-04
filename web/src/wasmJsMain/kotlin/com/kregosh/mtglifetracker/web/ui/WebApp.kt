package com.kregosh.mtglifetracker.web.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kregosh.mtglifetracker.data.AppColorScheme
import com.kregosh.mtglifetracker.network.ConnectionState
import com.kregosh.mtglifetracker.shared.LIFE_STAT
import com.kregosh.mtglifetracker.shared.UserState
import com.kregosh.mtglifetracker.viewmodel.HomeError
import com.kregosh.mtglifetracker.viewmodel.Screen
import com.kregosh.mtglifetracker.viewmodel.SessionViewModel

@Composable
fun WebApp(vm: SessionViewModel, configured: Boolean) {
    val scheme by vm.colorScheme.collectAsState()
    val dark = when (scheme) {
        AppColorScheme.DARK   -> true
        AppColorScheme.LIGHT  -> false
        AppColorScheme.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            val screen by vm.screen.collectAsState()
            when (screen) {
                is Screen.Session -> SessionPage(vm)
                else              -> HomePage(vm, configured)
            }
        }
    }
}

// ── home ──────────────────────────────────────────────────────────────────────

@Composable
private fun HomePage(vm: SessionViewModel, configured: Boolean) {
    val name    by vm.displayName.collectAsState()
    val loading by vm.homeLoading.collectAsState()
    val error   by vm.homeError.collectAsState()
    var code    by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(24.dp).widthIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("MtG Life Tracker", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Browser preview", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

        if (!configured) {
            Text("This page isn't connected to a Firebase project yet.", color = MaterialTheme.colorScheme.error)
            return@Column
        }

        OutlinedTextField(
            value = name, onValueChange = vm::setDisplayName, singleLine = true,
            label = { Text("Your name") }, modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = vm::createSession, enabled = !loading && name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Create session")
        }
        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = code, onValueChange = { code = it.uppercase().take(8) }, singleLine = true,
                label = { Text("Session code") }, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            )
            FilledTonalButton(onClick = { vm.joinByCode(code.trim()) }, enabled = !loading && code.isNotBlank() && name.isNotBlank()) {
                Text("Join")
            }
        }
        if (loading) CircularProgressIndicator()
        error?.let { Text(it.text(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
    }
}

private fun HomeError.text(): String = when (this) {
    HomeError.SessionNotFound    -> "No session with that code. Check the code and try again."
    HomeError.RemovedFromSession -> "You are no longer in that session."
    HomeError.SessionEnded       -> "The session has ended."
    is HomeError.SessionFull     -> "This session is full ($maxPlayers players)."
    is HomeError.CreateFailed    -> "Couldn't create a session: ${detail ?: "unknown error"}"
    is HomeError.JoinFailed      -> "Couldn't join the session: ${detail ?: "unknown error"}"
}

// ── session ───────────────────────────────────────────────────────────────────

@Composable
private fun SessionPage(vm: SessionViewModel) {
    val ui by vm.sessionUi.collectAsState()

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Code", style = MaterialTheme.typography.labelMedium)
                Text(ui.sessionCode, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            }
            ConnectionBadge(ui.connectionState)
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = vm::leaveSession) { Text("Leave") }
        }
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Your own card first, the rest in the session's order.
            items(ui.users.sortedByDescending { it.id == ui.myUserId }, key = { it.id }) { user ->
                PlayerCard(
                    user  = user,
                    mine  = user.id == ui.myUserId,
                    host  = user.id == ui.hostUserId,
                    onAdjust = { delta -> vm.adjust(LIFE_STAT, delta) },
                )
            }
        }
    }
}

@Composable
private fun ConnectionBadge(state: ConnectionState) {
    val (label, color) = when (state) {
        ConnectionState.Connected    -> "Live" to Color(0xFF2FBF4A)
        ConnectionState.Connecting,
        ConnectionState.Reconnecting -> "Connecting" to Color(0xFFE0A030)
        ConnectionState.Closed       -> "Closed" to Color.Gray
        is ConnectionState.Failed    -> "Offline" to MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PlayerCard(user: UserState, mine: Boolean, host: Boolean, onAdjust: (Int) -> Unit) {
    val colors = if (mine) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                 else CardDefaults.cardColors()
    Card(colors = colors) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).background(if (user.online) Color(0xFF2FBF4A) else Color.Gray, CircleShape))
                Text(
                    user.displayName,
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (host) Text("host", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                user.life.toString(),
                fontSize = 56.sp, fontWeight = FontWeight.Bold,
                color = if (user.conceded) MaterialTheme.colorScheme.outline else LocalContentColor.current,
            )
            if (user.conceded) Text("conceded", style = MaterialTheme.typography.labelMedium)
            if (mine) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (delta in listOf(-5, -1, +1, +5)) {
                        FilledTonalButton(
                            onClick = { onAdjust(delta) },
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            modifier = Modifier.weight(1f),
                        ) { Text(if (delta > 0) "+$delta" else "$delta") }
                    }
                }
            }
        }
    }
}
