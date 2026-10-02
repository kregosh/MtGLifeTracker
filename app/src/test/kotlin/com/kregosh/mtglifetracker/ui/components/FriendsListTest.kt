package com.kregosh.mtglifetracker.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kregosh.mtglifetracker.data.Friend
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class FriendsListTest {

    @get:Rule val compose = createComposeRule()

    private val joins = mutableListOf<Pair<String, Boolean>>()

    private fun showFriendInGame() {
        compose.setContent {
            FriendsList(
                friends          = listOf(Friend("bob", "Bob")),
                friendPresence   = mapOf("bob" to "sid-1"),
                currentSessionId = null,
                onJoinSession    = { sessionId, watch -> joins += sessionId to watch },
                onRemoveFriend   = {},
            )
        }
        compose.onNodeWithContentDescription("Join Bob's session").performClick()
    }

    @Test
    fun `joining a friend asks whether to play or watch`() {
        showFriendInGame()
        compose.onNodeWithText("Join Bob").assertExists()
        compose.onNodeWithText("Play").performClick()
        assertEquals(listOf("sid-1" to false), joins)
    }

    @Test
    fun `choosing watch joins as an observer`() {
        showFriendInGame()
        compose.onNodeWithText("Watch").performClick()
        assertEquals(listOf("sid-1" to true), joins)
    }
}
