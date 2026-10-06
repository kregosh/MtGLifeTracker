package com.kregosh.mtglifetracker.ui.components

import com.kregosh.mtglifetracker.ui.setPlatformContent
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.kregosh.mtglifetracker.shared.COMMANDER_STAT
import com.kregosh.mtglifetracker.shared.LIFE_STAT
import com.kregosh.mtglifetracker.shared.POISON_STAT
import com.kregosh.mtglifetracker.shared.StatType
import com.kregosh.mtglifetracker.shared.UserState
import com.kregosh.mtglifetracker.viewmodel.SessionUiState
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SessionPanelsTest {

    @get:Rule val compose = createComposeRule()

    private val me    = UserState("me", "Alice", life = 20u)
    private val bob   = UserState("bob", "Bob", life = 17u,
                                  customStats = mapOf(COMMANDER_STAT to 4u, POISON_STAT to 2u, "energy" to 3u),
                                  stats = mapOf(COMMANDER_STAT to StatType.NUMERIC, "energy" to StatType.NUMERIC))
    private val carol = UserState("carol", "Carol", online = false)

    private fun ui(monarch: String? = null) =
        SessionUiState(myUserId = "me", users = listOf(me, bob, carol), monarch = monarch)

    private val adjustments = mutableListOf<Pair<String, Int>>()
    private val tracked     = mutableListOf<String>()
    private var addStat     = 0
    private var tookMonarch = false

    private fun showPanel(user: UserState = me, state: SessionUiState = ui()) = compose.setPlatformContent {
        MyPanel(
            me            = user,
            ui            = state,
            onAdjust      = { stat, delta -> adjustments += stat to delta },
            onTrack       = { tracked += it },
            onAddStat     = { addStat++ },
            onTakeMonarch = { tookMonarch = true },
        )
    }

    // ── Your panel ─────────────────────────────────────────────────────

    @Test
    fun `the panel halves change life by one, a long press by five`() {
        showPanel()
        compose.onNodeWithContentDescription("Increase Life").performClick()
        compose.onNodeWithContentDescription("Decrease Life").performClick()
        compose.onNodeWithContentDescription("Decrease Life").performTouchInput { longClick() }
        assertEquals(listOf(LIFE_STAT to 1, LIFE_STAT to -1, LIFE_STAT to -5), adjustments)
    }

    @Test
    fun `picking the commander tab starts tracking it and the halves then change it`() {
        showPanel()
        compose.onNodeWithContentDescription("CMD Dmg").performClick()
        assertEquals(listOf(COMMANDER_STAT), tracked)

        compose.onNodeWithContentDescription("CMD Dmg").assertIsSelected()
        compose.onNodeWithText("Tapping changes: CMD Dmg").assertExists()
        compose.onNodeWithContentDescription("Increase CMD Dmg").performClick()
        assertEquals(listOf(COMMANDER_STAT to 1), adjustments)
    }

    @Test
    fun `a tracked tab doesn't start tracking again`() {
        showPanel(me.copy(stats = mapOf(POISON_STAT to StatType.NUMERIC)))
        compose.onNodeWithContentDescription("Poison").performClick()
        assertTrue(tracked.isEmpty())
    }

    @Test
    fun `commander damage and poison show how close they are to the limit`() {
        showPanel(me.copy(customStats = mapOf(COMMANDER_STAT to 5u, POISON_STAT to 2u)))
        compose.onNodeWithText("/21").assertExists()
        compose.onNodeWithText("/10").assertExists()
    }

    @Test
    fun `the add button reads + Stat until a minor stat is tracked`() {
        showPanel()
        compose.onNodeWithText("+ Stat").performClick()
        assertEquals(1, addStat)
    }

    @Test
    fun `with a minor stat the add button is a plain plus after its chip`() {
        showPanel(me.copy(stats = mapOf("energy" to StatType.NUMERIC), customStats = mapOf("energy" to 3u)))
        compose.onNodeWithText("+ Stat").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add a stat").performClick()
        assertEquals(1, addStat)

        compose.onNodeWithText("Energy 3").performClick()
        compose.onNodeWithText("Tapping changes: Energy").assertExists()
        compose.onNodeWithContentDescription("Increase Energy").performClick()
        assertEquals(listOf("energy" to 1), adjustments)
    }

    @Test
    fun `a toggle chip flips its stat`() {
        showPanel(me.copy(stats = mapOf("Ward" to StatType.TOGGLE)))
        compose.onNodeWithText("Ward: Inactive").performClick()
        assertEquals(listOf("Ward" to 1), adjustments)
    }

    @Test
    fun `you can take the monarch from another player`() {
        showPanel(state = ui(monarch = "bob"))
        compose.onNodeWithText("👑 Become the monarch").performClick()
        assertTrue(tookMonarch)
    }

    @Test
    fun `the monarch sees their crown`() {
        showPanel(state = ui(monarch = "me"))
        compose.onNodeWithText("👑 Monarch").assertExists()
        compose.onNodeWithText("👑 Become the monarch").assertDoesNotExist()
    }

    // ── Opponents ──────────────────────────────────────────────────────

    @Test
    fun `an opponent tile shows life, commander damage and poison only`() {
        compose.setPlatformContent { OpponentTile(bob, ui(), onOpen = {}) }
        compose.onNodeWithText("17").assertExists()
        compose.onNodeWithText("4").assertExists()
        compose.onNodeWithText("2").assertExists()
        compose.onNodeWithText("3").assertDoesNotExist()
    }

    @Test
    fun `tapping an opponent opens their details`() {
        var opened = false
        compose.setPlatformContent { OpponentTile(bob, ui(), onOpen = { opened = true }) }
        compose.onNodeWithText("Bob").performClick()
        assertTrue(opened)
    }

    @Test
    fun `an offline opponent says so`() {
        compose.setPlatformContent { OpponentTile(carol, ui(), onOpen = {}) }
        compose.onNodeWithText("offline").assertExists()
    }

    @Test
    fun `a dead opponent gets a skull`() {
        compose.setPlatformContent { OpponentTile(bob.copy(life = 0u), ui(), onOpen = {}) }
        compose.onNodeWithText("💀").assertExists()
    }

    @Test
    fun `the monarch wears the crown on their tile`() {
        compose.setPlatformContent { OpponentTile(bob, ui(monarch = "bob"), onOpen = {}) }
        compose.onNodeWithText("👑 Bob").assertExists()
    }

    @Test
    fun `details show everything, including the minor stats`() {
        compose.setPlatformContent {
            PlayerDetailsDialog(bob, ui(), isFriend = false, onAddFriend = {}, onRemove = null, onDismiss = {})
        }
        compose.onNodeWithText("4 / 21").assertExists()
        compose.onNodeWithText("2 / 10").assertExists()
        compose.onNodeWithText("Energy").assertExists()
        compose.onNodeWithText("3").assertExists()
        compose.onNodeWithText("Remove Bob from the session").assertDoesNotExist()
    }

    @Test
    fun `details offer a friend request to strangers`() {
        var requested = false
        compose.setPlatformContent {
            PlayerDetailsDialog(bob, ui(), isFriend = false, onAddFriend = { requested = true }, onRemove = null, onDismiss = {})
        }
        compose.onNodeWithText("Send friend request").performClick()
        assertTrue(requested)
    }

    @Test
    fun `details let the host remove a friend`() {
        var removed = false
        compose.setPlatformContent {
            PlayerDetailsDialog(bob, ui(), isFriend = true, onAddFriend = {}, onRemove = { removed = true }, onDismiss = {})
        }
        compose.onNodeWithText("Send friend request").assertDoesNotExist()
        compose.onNodeWithText("Remove Bob from the session").performClick()
        assertTrue(removed)
    }
}
