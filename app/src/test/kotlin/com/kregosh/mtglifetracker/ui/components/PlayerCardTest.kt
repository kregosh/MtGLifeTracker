package com.kregosh.mtglifetracker.ui.components

import androidx.compose.foundation.layout.Column
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

    private val commander = mapOf(COMMANDER_STAT to StatType.NUMERIC)

    private fun ui(monarch: String? = null) =
        SessionUiState(myUserId = "me", users = listOf(me, bob, carol), monarch = monarch)

    @Test
    fun `commander damage has one row per opponent`() {
        compose.setContent {
            PlayerCard(user = me.copy(stats = commander), isMe = true, sessionUi = ui())
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
                user      = me.copy(stats = commander),
                isMe      = true,
                sessionUi = ui(),
                onAdjust  = { stat, delta -> adjustments += stat to delta },
            )
        }
        compose.onNodeWithContentDescription("Increase CMD Carol").performClick()
        assertEquals(listOf(commanderDamageStat("carol") to 1), adjustments)
    }

    @Test
    fun `an opponent's card shows the damage they took from me without buttons`() {
        compose.setContent {
            PlayerCard(user = bob.copy(stats = commander), isMe = false, sessionUi = ui())
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

    @Test
    fun `a card shows only the counters that player tracks`() {
        compose.setContent {
            Column {
                PlayerCard(user = me.copy(stats = mapOf("Gold" to StatType.NUMERIC)), isMe = true, sessionUi = ui())
                PlayerCard(user = bob, isMe = false, sessionUi = ui())
            }
        }
        compose.onAllNodesWithText("Gold").assertCountEquals(1)
        compose.onAllNodesWithText("CMD Alice").assertCountEquals(0)
    }

    @Test
    fun `the monarch's card wears the crown`() {
        compose.setContent { PlayerCard(user = bob, isMe = false, sessionUi = ui(monarch = "bob")) }
        compose.onNodeWithText("👑 Monarch").assertExists()
    }

    @Test
    fun `other players' cards show no crown`() {
        compose.setContent { PlayerCard(user = carol, isMe = false, sessionUi = ui(monarch = "bob")) }
        compose.onNodeWithText("👑 Monarch").assertDoesNotExist()
    }

    @Test
    fun `while someone else is the monarch my card can take it`() {
        var taken = false
        compose.setContent {
            PlayerCard(user = me, isMe = true, sessionUi = ui(monarch = "bob"), onTakeMonarch = { taken = true })
        }
        compose.onNodeWithText("Become the monarch").performClick()
        assertEquals(true, taken)
    }

    @Test
    fun `without a monarch in play there is nothing to take`() {
        compose.setContent { PlayerCard(user = me, isMe = true, sessionUi = ui(), onTakeMonarch = {}) }
        compose.onNodeWithText("Become the monarch").assertDoesNotExist()
    }
}
