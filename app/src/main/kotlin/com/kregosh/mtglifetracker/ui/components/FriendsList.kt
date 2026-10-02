package com.kregosh.mtglifetracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kregosh.mtglifetracker.R
import com.kregosh.mtglifetracker.data.Friend
import com.kregosh.mtglifetracker.data.KnownPlayer

/**
 * Friends with a join button while they're in a session, and removal behind a
 * confirmation. Used by the friends sheet and the Settings screen.
 */
@Composable
fun FriendsList(
    friends         : List<Friend>,
    friendPresence  : Map<String, String?>,
    currentSessionId: String?,
    onJoinSession   : (sessionId: String, watch: Boolean) -> Unit,
    onRemoveFriend  : (userId: String) -> Unit,
    modifier        : Modifier = Modifier,
) {
    var unfriendTarget by remember { mutableStateOf<Friend?>(null) }
    var joinTarget     by remember { mutableStateOf<Pair<Friend, String>?>(null) }

    // Watching is only offered here: you only watch games your friends are in.
    joinTarget?.let { (friend, sessionId) ->
        AlertDialog(
            onDismissRequest = { joinTarget = null },
            title = { Text(stringResource(R.string.friend_join_title, friend.displayName)) },
            confirmButton = {
                TextButton(onClick = { joinTarget = null; onJoinSession(sessionId, false) }) {
                    Text(stringResource(R.string.friend_join_play))
                }
            },
            dismissButton = {
                TextButton(onClick = { joinTarget = null; onJoinSession(sessionId, true) }) {
                    Text(stringResource(R.string.friend_join_watch))
                }
            },
        )
    }

    unfriendTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { unfriendTarget = null },
            title = { Text(stringResource(R.string.friend_remove_title)) },
            text  = { Text(stringResource(R.string.friend_remove_text, target.displayName)) },
            confirmButton = {
                TextButton(
                    onClick = { onRemoveFriend(target.userId); unfriendTarget = null },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { unfriendTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        friends.forEach { friend ->
            val sessionId = friendPresence[friend.userId]
            PlayerRow(
                icon     = Icons.Default.Star,
                iconTint = MaterialTheme.colorScheme.primary,
                name     = friend.displayName,
                status   = when {
                    sessionId == null             -> null
                    sessionId == currentSessionId -> stringResource(R.string.friend_in_this_game)
                    else                          -> stringResource(R.string.friend_in_a_game)
                },
            ) {
                if (sessionId != null && sessionId != currentSessionId) {
                    RowAction(
                        icon        = Icons.AutoMirrored.Filled.Login,
                        description = stringResource(R.string.friend_join, friend.displayName),
                        tint        = MaterialTheme.colorScheme.primary,
                        onClick     = { joinTarget = friend to sessionId },
                    )
                }
                RowAction(
                    icon        = Icons.Default.PersonRemove,
                    description = stringResource(R.string.friend_remove, friend.displayName),
                    tint        = MaterialTheme.colorScheme.error,
                    onClick     = { unfriendTarget = friend },
                )
            }
        }
    }
}

/** Recently seen players, with add-as-friend and forget actions. */
@Composable
fun RecentPlayersList(
    players       : List<KnownPlayer>,
    friendIds     : Set<String>,
    onAddFriend   : (userId: String, displayName: String) -> Unit,
    onForgetPlayer: (userId: String) -> Unit,
    modifier      : Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        players.forEach { player ->
            PlayerRow(
                icon     = Icons.Default.People,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                name     = player.displayName,
                status   = null,
            ) {
                if (player.userId !in friendIds) {
                    RowAction(
                        icon        = Icons.Default.PersonAdd,
                        description = stringResource(R.string.friend_add, player.displayName),
                        tint        = MaterialTheme.colorScheme.primary,
                        onClick     = { onAddFriend(player.userId, player.displayName) },
                    )
                }
                RowAction(
                    icon        = Icons.Default.Close,
                    description = stringResource(R.string.player_forget, player.displayName),
                    tint        = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick     = { onForgetPlayer(player.userId) },
                )
            }
        }
    }
}

@Composable
private fun PlayerRow(
    icon    : ImageVector,
    iconTint: Color,
    name    : String,
    status  : String?,
    actions : @Composable RowScope.() -> Unit,
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier              = Modifier.weight(1f),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = iconTint)
            Column {
                Text(name, style = MaterialTheme.typography.bodyMedium)
                if (status != null) {
                    Text(
                        status,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
private fun RowAction(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp), tint = tint)
    }
}
