package com.kregosh.mtglifetracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kregosh.mtglifetracker.data.Friend
import com.kregosh.mtglifetracker.data.KnownPlayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsSheet(
    friendList     : List<Friend>,
    knownPlayers   : List<KnownPlayer>,
    friendIds      : Set<String>,
    onRemoveFriend : (String) -> Unit,
    onAddFriend    : (String, String) -> Unit,
    onForgetPlayer : (String) -> Unit,
    onDismiss      : () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Friends", "Recently Played")

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        text     = { Text(title) },
                    )
                }
            }

            when (selectedTab) {
                0 -> FriendsTab(friendList = friendList, onRemoveFriend = onRemoveFriend)
                1 -> RecentlyPlayedTab(
                    knownPlayers   = knownPlayers,
                    friendIds      = friendIds,
                    onAddFriend    = onAddFriend,
                    onForgetPlayer = onForgetPlayer,
                )
            }
        }
    }
}

@Composable
private fun FriendsTab(
    friendList    : List<Friend>,
    onRemoveFriend: (String) -> Unit,
) {
    if (friendList.isEmpty()) {
        Box(
            modifier         = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No friends yet — send a friend request during a session",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        friendList.forEach { friend ->
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
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        modifier           = Modifier.size(16.dp),
                        tint               = MaterialTheme.colorScheme.primary,
                    )
                    Text(friend.displayName, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(
                    onClick  = { onRemoveFriend(friend.userId) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.PersonRemove,
                        contentDescription = "Remove ${friend.displayName}",
                        modifier           = Modifier.size(18.dp),
                        tint               = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentlyPlayedTab(
    knownPlayers  : List<KnownPlayer>,
    friendIds     : Set<String>,
    onAddFriend   : (String, String) -> Unit,
    onForgetPlayer: (String) -> Unit,
) {
    if (knownPlayers.isEmpty()) {
        Box(
            modifier         = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No recent players — join a session to see who you've played with",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        knownPlayers.forEach { player ->
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
                    Icon(
                        Icons.Default.People,
                        contentDescription = null,
                        modifier           = Modifier.size(16.dp),
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(player.displayName, style = MaterialTheme.typography.bodyMedium)
                }
                Row {
                    if (player.userId !in friendIds) {
                        IconButton(
                            onClick  = { onAddFriend(player.userId, player.displayName) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.PersonAdd,
                                contentDescription = "Add ${player.displayName} as friend",
                                modifier           = Modifier.size(18.dp),
                                tint               = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    IconButton(
                        onClick  = { onForgetPlayer(player.userId) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Forget ${player.displayName}",
                            modifier           = Modifier.size(18.dp),
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
