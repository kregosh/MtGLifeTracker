package com.kregosh.mtglifetracker.ui.components

import com.kregosh.mtglifetracker.ui.setPlatformContent
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
    private val bob   = UserState("bob", "Bob", life = 20u, customStats = mapOf(COMMANDER_STAT to 4u))
    private val carol = UserState("carol", "Carol", life = 20u, online = false)

    private val commander = mapOf(COMMANDER_STAT to StatType.NUMERIC)

    private fun ui(monarch: String? = null) =
        SessionUiState(myUserId = "me", users = listOf(me, bob, carol), monarch = monarch)

    @Test
    fun `commander damage is one counter, even without opponents`() {
        compose.setPlatformContent {
            PlayerCard(
                user      = me.copy(stats = commander),
                isMe      = true,
                sessionUi = SessionUiState(myUserId = "me", users = listOf(me)),
            )
        }
        compose.onAllNodesWithText("CMD Dmg").assertCountEquals(1)
    }

    @Test
    fun `tapping plus on commander damage adds to the one counter`() {
        val adjustments = mutableListOf<Pair<String, Int>>()
        compose.setPlatformContent {
            PlayerCard(
                user      = me.copy(stats = commander),
                isMe      = true,
                sessionUi = ui(),
                onAdjust  = { stat, delta -> adjustments += stat to delta },
            )
        }
        compose.onNodeWithContentDescription("Increase CMD Dmg").performClick()
        assertEquals(listOf(COMMANDER_STAT to 1), adjustments)
    }

    @Test
    fun `an opponent's card shows their commander damage without buttons`() {
        compose.setPlatformContent {
            PlayerCard(user = bob.copy(stats = commander), isMe = false, sessionUi = ui())
        }
        compose.onNodeWithText("CMD Dmg").assertExists()
        compose.onNodeWithText("4").assertExists()
        compose.onNodeWithContentDescription("Increase CMD Dmg").assertDoesNotExist()
    }

    // ── skull and white flag (#85) ───────────────────────────────────────────

    @Test
    fun `an opponent at 0 life gets the skull`() {
        compose.setPlatformContent { PlayerCard(user = bob.copy(life = 0u), isMe = false, sessionUi = ui()) }
        compose.onNodeWithText("💀").assertExists()
    }

    @Test
    fun `an opponent at the commander damage threshold gets the skull`() {
        val dead = bob.copy(stats = commander, customStats = mapOf(COMMANDER_STAT to 21u))
        compose.setPlatformContent { PlayerCard(user = dead, isMe = false, sessionUi = ui()) }
        compose.onNodeWithText("💀").assertExists()
    }

    @Test
    fun `an opponent who conceded gets the white flag`() {
        compose.setPlatformContent { PlayerCard(user = bob.copy(conceded = true), isMe = false, sessionUi = ui()) }
        compose.onNodeWithText("🏳️").assertExists()
    }

    @Test
    fun `my own card shows the white flag and undo concede still works`() {
        var undone = false
        compose.setPlatformContent {
            PlayerCard(user = me.copy(conceded = true), isMe = true, sessionUi = ui(), onUnconcede = { undone = true })
        }
        compose.onNodeWithText("🏳️").assertExists()
        compose.onNodeWithText("Undo").performClick()
        assertEquals(true, undone)
    }

    @Test
    fun `players still in the game have no overlay`() {
        compose.setPlatformContent { PlayerCard(user = bob, isMe = false, sessionUi = ui()) }
        compose.onNodeWithText("💀").assertDoesNotExist()
        compose.onNodeWithText("🏳️").assertDoesNotExist()
    }

    @Test
    fun `offline players are labelled`() {
        compose.setPlatformContent { PlayerCard(user = carol, isMe = false, sessionUi = ui()) }
        compose.onNodeWithText("offline").assertExists()
    }

    @Test
    fun `the remove button appears only when removing is allowed`() {
        var removed = false
        compose.setPlatformContent {
            PlayerCard(user = bob, isMe = false, sessionUi = ui(), onRemove = { removed = true })
        }
        compose.onNodeWithContentDescription("Remove Bob from the session").performClick()
        assertEquals(true, removed)
    }

    @Test
    fun `without permission there is no remove button`() {
        compose.setPlatformContent { PlayerCard(user = bob, isMe = false, sessionUi = ui()) }
        compose.onNodeWithContentDescription("Remove Bob from the session").assertDoesNotExist()
    }

    @Test
    fun `a card shows only the counters that player tracks`() {
        compose.setPlatformContent {
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
        compose.setPlatformContent { PlayerCard(user = bob, isMe = false, sessionUi = ui(monarch = "bob")) }
        compose.onNodeWithText("👑 Monarch").assertExists()
    }

    @Test
    fun `other players' cards show no crown`() {
        compose.setPlatformContent { PlayerCard(user = carol, isMe = false, sessionUi = ui(monarch = "bob")) }
        compose.onNodeWithText("👑 Monarch").assertDoesNotExist()
    }

    @Test
    fun `while someone else is the monarch my card can take it`() {
        var taken = false
        compose.setPlatformContent {
            PlayerCard(user = me, isMe = true, sessionUi = ui(monarch = "bob"), onTakeMonarch = { taken = true })
        }
        compose.onNodeWithText("Become the monarch").performClick()
        assertEquals(true, taken)
    }

    @Test
    fun `without a monarch in play there is nothing to take`() {
        compose.setPlatformContent { PlayerCard(user = me, isMe = true, sessionUi = ui(), onTakeMonarch = {}) }
        compose.onNodeWithText("Become the monarch").assertDoesNotExist()
    }
}
