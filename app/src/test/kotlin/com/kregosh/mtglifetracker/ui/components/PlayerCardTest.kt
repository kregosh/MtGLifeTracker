package com.kregosh.mtglifetracker.ui.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kregosh.mtglifetracker.shared.COMMANDER_STAT
import com.kregosh.mtglifetracker.shared.StatType
import com.kregosh.mtglifetracker.shared.UserState
import com.kregosh.mtglifetracker.shared.commanderDamageStat
import com.kregosh.mtglifetracker.viewmodel.SessionUiState
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class PlayerCardTest {

    @get:Rule val compose = createComposeRule()

    private val me    = UserState("me", "Alice", life = 20u)
    private val bob   = UserState("bob", "Bob", life = 20u, commanderDamage = mapOf("me" to 4u))
    private val carol = UserState("carol", "Carol", life = 20u, online = false)

    private fun ui(statDefs: Map<String, StatType> = emptyMap()) =
        SessionUiState(myUserId = "me", users = listOf(me, bob, carol), statDefs = statDefs)

    @Test
    fun `commander damage has one row per opponent`() {
        compose.setContent {
            PlayerCard(user = me, isMe = true, sessionUi = ui(mapOf(COMMANDER_STAT to StatType.NUMERIC)))
        }
        compose.onNodeWithText("CMD Bob").assertExists()
        compose.onNodeWithText("CMD Carol").assertExists()
        compose.onAllNodesWithText("CMD Alice").assertCountEquals(0)
    }

    @Test
    fun `tapping plus on a commander row adds damage from that opponent only`() {
        val adjustments = mutableListOf<Pair<String, Int>>()
        compose.setContent {
            PlayerCard(
                user      = me,
                isMe      = true,
                sessionUi = ui(mapOf(COMMANDER_STAT to StatType.NUMERIC)),
                onAdjust  = { stat, delta -> adjustments += stat to delta },
            )
        }
        compose.onNodeWithContentDescription("Increase CMD Carol").performClick()
        assertEquals(listOf(commanderDamageStat("carol") to 1), adjustments)
    }

    @Test
    fun `an opponent's card shows the damage they took from me without buttons`() {
        compose.setContent {
            PlayerCard(user = bob, isMe = false, sessionUi = ui(mapOf(COMMANDER_STAT to StatType.NUMERIC)))
        }
        compose.onNodeWithText("CMD Alice").assertExists()
        compose.onNodeWithText("4").assertExists()
        compose.onNodeWithContentDescription("Increase CMD Alice").assertDoesNotExist()
    }

    @Test
    fun `offline players are labelled`() {
        compose.setContent { PlayerCard(user = carol, isMe = false, sessionUi = ui()) }
        compose.onNodeWithText("offline").assertExists()
    }

    @Test
    fun `the remove button appears only when removing is allowed`() {
        var removed = false
        compose.setContent {
            PlayerCard(user = bob, isMe = false, sessionUi = ui(), onRemove = { removed = true })
        }
        compose.onNodeWithContentDescription("Remove Bob from the session").performClick()
        assertEquals(true, removed)
    }

    @Test
    fun `without permission there is no remove button`() {
        compose.setContent { PlayerCard(user = bob, isMe = false, sessionUi = ui()) }
        compose.onNodeWithContentDescription("Remove Bob from the session").assertDoesNotExist()
    }
}
