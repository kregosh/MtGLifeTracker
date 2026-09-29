package com.kregosh.mtglifetracker.viewmodel

import com.kregosh.mtglifetracker.shared.UserState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsDeadTest {

    private val defaultUi = SessionUiState(
        commanderDeathThreshold = 21u,
        poisonDeathThreshold    = 10u,
    )

    private fun user(
        life            : UInt = 20u,
        commanderDamage : UInt = 0u,
        poisonDamage    : UInt = 0u,
    ) = UserState("u1", "Alice", life, commanderDamage, poisonDamage)

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
        val ui = defaultUi.copy(commanderDeathThreshold = 15u)
        assertFalse(user(commanderDamage = 14u).isDead(ui))
        assertTrue(user(commanderDamage = 15u).isDead(ui))
    }

    @Test
    fun `custom poison threshold of 5 applies correctly`() {
        val ui = defaultUi.copy(poisonDeathThreshold = 5u)
        assertFalse(user(poisonDamage = 4u).isDead(ui))
        assertTrue(user(poisonDamage = 5u).isDead(ui))
    }

    @Test
    fun `all stats at max boundary values but alive`() {
        assertFalse(user(life = 1u, commanderDamage = 20u, poisonDamage = 9u).isDead(defaultUi))
    }
}
