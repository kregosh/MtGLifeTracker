package com.kregosh.mtglifetracker.ui.screens

import com.kregosh.mtglifetracker.ui.Strings
import androidx.compose.foundation.BorderStroke
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
import com.kregosh.mtglifetracker.ui.message
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
    val pendingInvite by vm.pendingInviteCode.collectAsState()

    if (displayName.isBlank()) {
        DisplayNameDialog(
            initial     = "",
            // Arriving by invite, the game is joined as soon as the name is set.
            title       = pendingInvite?.let(Strings::homeFirstLaunchJoinTitle) ?: Strings.homeFirstLaunchTitle,
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
            onJoinSession  = { sessionId, watch -> vm.joinFriendSession(sessionId, watch) },
            onRemoveFriend = vm::removeFriend,
            onAddFriend    = { uid, name -> vm.addFriend(uid, name) },
            onDismiss      = { showFriendsSheet = false },
        )
    }

    Scaffold(
        containerColor = if (hasBg) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(Strings.appName) },
                colors = if (hasBg) TopAppBarDefaults.topAppBarColors(
                    containerColor         = Color.Black.copy(alpha = 0.45f),
                    titleContentColor      = Color.White,
                    actionIconContentColor = Color.White,
                ) else TopAppBarDefaults.topAppBarColors(),
                actions = {
                    IconButton(onClick = { showFriendsSheet = true }) {
                        Icon(Icons.Default.People, contentDescription = Strings.friends)
                    }
                    IconButton(onClick = vm::openSettings) {
                        Icon(Icons.Default.Settings, contentDescription = Strings.settings)
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
            // Over a background image, text and outlines drawn straight onto it are black or
            // white, like the timer, so they don't vanish into the picture.
            val onImage = if (hasBg) backdropTextColor() else null

            Text(
                text  = Strings.homeWelcome(displayName.ifBlank { Strings.defaultPlayerName }),
                style = MaterialTheme.typography.headlineSmall,
                color = onImage ?: LocalContentColor.current,
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick  = vm::createSession,
                enabled  = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(Strings.homeCreateSession)
            }

            OutlinedTextField(
                value          = codeInput,
                onValueChange  = { codeInput = it.uppercase() },
                label          = { Text(Strings.homeInviteCode) },
                placeholder    = { Text(Strings.homeInviteCodePlaceholder) },
                singleLine     = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction      = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = {
                    if (codeInput.isNotBlank()) vm.joinByCode(codeInput)
                }),
                modifier = Modifier.fillMaxWidth(),
                colors   = if (onImage == null) OutlinedTextFieldDefaults.colors() else OutlinedTextFieldDefaults.colors(
                    focusedTextColor          = onImage,
                    unfocusedTextColor        = onImage,
                    focusedBorderColor        = onImage,
                    unfocusedBorderColor      = onImage.copy(alpha = 0.7f),
                    focusedLabelColor         = onImage,
                    unfocusedLabelColor       = onImage.copy(alpha = 0.8f),
                    focusedPlaceholderColor   = onImage.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = onImage.copy(alpha = 0.6f),
                    cursorColor               = onImage,
                ),
            )

            OutlinedButton(
                onClick  = { if (codeInput.isNotBlank()) vm.joinByCode(codeInput) },
                enabled  = codeInput.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth(),
                colors   = if (onImage == null) ButtonDefaults.outlinedButtonColors() else ButtonDefaults.outlinedButtonColors(
                    contentColor         = onImage,
                    disabledContentColor = onImage.copy(alpha = 0.6f),
                ),
                border   = if (onImage == null) ButtonDefaults.outlinedButtonBorder(enabled = codeInput.isNotBlank() && !loading)
                           else BorderStroke(1.dp, onImage.copy(alpha = if (codeInput.isNotBlank() && !loading) 1f else 0.6f)),
            ) {
                Icon(Icons.Default.Link, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(Strings.homeJoinSession)
            }

            if (loading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val color = onImage ?: LocalContentColor.current
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = onImage ?: ProgressIndicatorDefaults.circularColor)
                    Text(Strings.homeConnecting, style = MaterialTheme.typography.bodySmall, color = color)
                }
            }

            error?.let { err ->
                val msg = err.message()
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

