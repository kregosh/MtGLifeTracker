package com.kregosh.mtglifetracker.viewmodel

import com.kregosh.mtglifetracker.shared.COMMANDER_STAT
import com.kregosh.mtglifetracker.shared.POISON_STAT
import com.kregosh.mtglifetracker.shared.SessionSettings
import com.kregosh.mtglifetracker.shared.StatType
import com.kregosh.mtglifetracker.shared.UserState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsDeadTest {

    private val defaultUi = SessionUiState(
        settings = SessionSettings(commanderDeathThreshold = 21u, infectDeathThreshold = 10u),
        statDefs = mapOf(COMMANDER_STAT to StatType.NUMERIC, POISON_STAT to StatType.NUMERIC),
    )

    private fun user(
        life            : UInt = 20u,
        commanderDamage : UInt = 0u,
        poisonDamage    : UInt = 0u,
    ) = UserState(
        id          = "u1",
        displayName = "Alice",
        life        = life,
        customStats     = if (poisonDamage > 0u) mapOf("poison" to poisonDamage) else emptyMap(),
        commanderDamage = if (commanderDamage > 0u) mapOf("opponent" to commanderDamage) else emptyMap(),
    )

    @Test
    fun `alive player is not dead`() {
        assertFalse(user().isDead(defaultUi))
    }

    @Test
    fun `life zero means dead`() {
        assertTrue(user(life = 0u).isDead(defaultUi))
    }

    @Test
    fun `life one is not dead`() {
        assertFalse(user(life = 1u).isDead(defaultUi))
    }

    @Test
    fun `commander damage at threshold means dead`() {
        assertTrue(user(commanderDamage = 21u).isDead(defaultUi))
    }

    @Test
    fun `commander damage one below threshold is not dead`() {
        assertFalse(user(commanderDamage = 20u).isDead(defaultUi))
    }

    @Test
    fun `poison damage at threshold means dead`() {
        assertTrue(user(poisonDamage = 10u).isDead(defaultUi))
    }

    @Test
    fun `poison damage one below threshold is not dead`() {
        assertFalse(user(poisonDamage = 9u).isDead(defaultUi))
    }

    @Test
    fun `custom commander threshold of 15 applies correctly`() {
        val ui = defaultUi.copy(settings = defaultUi.settings.copy(commanderDeathThreshold = 15u))
        assertFalse(user(commanderDamage = 14u).isDead(ui))
        assertTrue(user(commanderDamage = 15u).isDead(ui))
    }

    @Test
    fun `custom poison threshold of 5 applies correctly`() {
        val ui = defaultUi.copy(settings = defaultUi.settings.copy(infectDeathThreshold = 5u))
        assertFalse(user(poisonDamage = 4u).isDead(ui))
        assertTrue(user(poisonDamage = 5u).isDead(ui))
    }

    @Test
    fun `commander damage counts per commander, not in total`() {
        val user = user().copy(commanderDamage = mapOf("bob" to 15u, "carol" to 15u))
        assertFalse(user.isDead(defaultUi))
        assertTrue(user.copy(commanderDamage = mapOf("bob" to 21u, "carol" to 0u)).isDead(defaultUi))
    }

    @Test
    fun `all stats at max boundary values but alive`() {
        assertFalse(user(life = 1u, commanderDamage = 20u, poisonDamage = 9u).isDead(defaultUi))
    }
}
