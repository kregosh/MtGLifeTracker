package com.kregosh.mtglifetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.kregosh.mtglifetracker.ui.theme.LocalHasBackground
import com.kregosh.mtglifetracker.viewmodel.Screen
import com.kregosh.mtglifetracker.viewmodel.SessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: SessionViewModel) {
    val loading        by vm.homeLoading.collectAsState()
    val error          by vm.homeError.collectAsState()
    val screen         by vm.screen.collectAsState()
    val hasBg          = LocalHasBackground.current
    val friendList     by vm.friendList.collectAsState()
    val friendPresence by vm.friendPresence.collectAsState()
    val inSession      = screen is Screen.Session

    var codeInput by remember { mutableStateOf("") }

    Scaffold(
        containerColor = if (hasBg) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("MtG Life Tracker") },
                colors = if (hasBg) TopAppBarDefaults.topAppBarColors(
                    containerColor         = Color.Black.copy(alpha = 0.45f),
                    titleContentColor      = Color.White,
                    actionIconContentColor = Color.White,
                ) else TopAppBarDefaults.topAppBarColors(),
                actions = {
                    IconButton(onClick = vm::openSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text  = "Welcome, ${vm.displayName.ifBlank { "Player" }}",
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(Modifier.height(8.dp))

            // ── Friends online ────────────────────────────────────────────────
            HorizontalDivider()
            Text(
                text  = "FRIENDS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Start),
            )
            if (friendList.isEmpty()) {
                Text(
                    text  = "Add friends during a session to see them here",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start),
                )
            } else {
                Column(
                    modifier            = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    friendList.forEach { friend ->
                        val sessionId  = friendPresence[friend.userId]
                        val canJoin    = sessionId != null && !inSession && !loading
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier              = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector        = Icons.Default.Star,
                                    contentDescription = null,
                                    modifier           = Modifier.size(14.dp),
                                    tint               = if (sessionId != null)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text       = friend.displayName,
                                    style      = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (sessionId != null) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                if (sessionId != null) {
                                    Text(
                                        text  = "• in session",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            FilledTonalButton(
                                onClick  = { if (sessionId != null) vm.joinFriendSession(sessionId) },
                                enabled  = canJoin,
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            ) {
                                Icon(
                                    Icons.Default.Login,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Join", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Button(
                onClick  = vm::createSession,
                enabled  = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Create new session")
            }

            OutlinedTextField(
                value          = codeInput,
                onValueChange  = { codeInput = it.uppercase() },
                label          = { Text("Invite code") },
                placeholder    = { Text("ABC123") },
                singleLine     = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction      = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = {
                    if (codeInput.isNotBlank()) vm.joinByCode(codeInput)
                }),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(
                onClick  = { if (codeInput.isNotBlank()) vm.joinByCode(codeInput) },
                enabled  = codeInput.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Link, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Join session")
            }

            if (loading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("Connecting to Firebase…", style = MaterialTheme.typography.bodySmall)
                }
            }

            error?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

