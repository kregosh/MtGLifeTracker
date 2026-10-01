package com.kregosh.mtglifetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.kregosh.mtglifetracker.ui.components.FriendsSheet
import com.kregosh.mtglifetracker.ui.theme.LocalHasBackground
import com.kregosh.mtglifetracker.viewmodel.SessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: SessionViewModel) {
    val loading      by vm.homeLoading.collectAsState()
    val error        by vm.homeError.collectAsState()
    val hasBg        = LocalHasBackground.current
    val friendList   by vm.friendList.collectAsState()
    val knownPlayers by vm.knownPlayers.collectAsState()
    val friendPresence by vm.friendPresence.collectAsState()
    val displayName  by vm.displayName.collectAsState()

    if (displayName.isBlank()) {
        DisplayNameDialog(
            initial     = "",
            title       = "Welcome! What's your name?",
            dismissible = false,
            onConfirm   = vm::setDisplayName,
            onDismiss   = {},
        )
    }

    var codeInput        by remember { mutableStateOf("") }
    var showFriendsSheet by remember { mutableStateOf(false) }

    val friendIds = remember(friendList) { friendList.map { it.userId }.toSet() }

    if (showFriendsSheet) {
        FriendsSheet(
            friendList     = friendList,
            knownPlayers   = knownPlayers,
            friendIds      = friendIds,
            friendPresence = friendPresence,
            currentSessionId = null,
            onJoinSession  = vm::joinFriendSession,
            onRemoveFriend = vm::removeFriend,
            onAddFriend    = { uid, name -> vm.addFriend(uid, name) },
            onForgetPlayer = vm::forgetPlayer,
            onDismiss      = { showFriendsSheet = false },
        )
    }

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
                    IconButton(onClick = { showFriendsSheet = true }) {
                        Icon(Icons.Default.People, contentDescription = "Friends")
                    }
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
                text  = "Welcome, ${displayName.ifBlank { "Player" }}",
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(Modifier.height(8.dp))

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
