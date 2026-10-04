package com.kregosh.mtglifetracker.ui.components

import com.kregosh.mtglifetracker.ui.Strings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    friendList      : List<Friend>,
    knownPlayers    : List<KnownPlayer>,
    friendIds       : Set<String>,
    friendPresence  : Map<String, String?>,
    currentSessionId: String?,
    onJoinSession   : (sessionId: String, watch: Boolean) -> Unit,
    onRemoveFriend  : (String) -> Unit,
    onAddFriend     : (String, String) -> Unit,
    onForgetPlayer  : (String) -> Unit,
    onDismiss       : () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(Strings.friendsTab, Strings.recentTab)

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

            val listModifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)

            when (selectedTab) {
                0 -> if (friendList.isEmpty()) {
                    EmptyHint(Strings.friendsEmpty)
                } else {
                    FriendsList(
                        friends          = friendList,
                        friendPresence   = friendPresence,
                        currentSessionId = currentSessionId,
                        onJoinSession    = { sessionId, watch -> onDismiss(); onJoinSession(sessionId, watch) },
                        onRemoveFriend   = onRemoveFriend,
                        modifier         = listModifier,
                    )
                }
                1 -> if (knownPlayers.isEmpty()) {
                    EmptyHint(Strings.recentEmpty)
                } else {
                    RecentPlayersList(
                        players        = knownPlayers,
                        friendIds      = friendIds,
                        onAddFriend    = onAddFriend,
                        onForgetPlayer = onForgetPlayer,
                        modifier       = listModifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier         = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
