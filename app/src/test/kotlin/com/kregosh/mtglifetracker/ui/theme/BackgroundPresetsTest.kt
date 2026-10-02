package com.kregosh.mtglifetracker.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackgroundPresetsTest {

    private val pkg    = "com.kregosh.mtglifetracker"
    private val legacy = mapOf(2131165270 to "bg_arcane_storm", 2131165271 to "bg_mana_orbs")

    @Test
    fun `numeric preset URIs are migrated to names`() {
        assertEquals(
            "android.resource://$pkg/drawable/bg_arcane_storm",
            migratedPresetUri("android.resource://$pkg/2131165270", pkg, legacy),
        )
        assertEquals(
            "android.resource://$pkg/drawable/bg_mana_orbs",
            migratedPresetUri("android.resource://$pkg/2131165271", pkg, legacy),
        )
    }

    @Test
    fun `everything else is left alone`() {
        assertNull(migratedPresetUri(null, pkg, legacy))
        assertNull(migratedPresetUri("android.resource://$pkg/drawable/bg_arcane_storm", pkg, legacy))
        assertNull(migratedPresetUri("android.resource://$pkg/1234", pkg, legacy))
        assertNull(migratedPresetUri("content://media/external/images/1", pkg, legacy))
        assertNull(migratedPresetUri("android.resource://other.app/2131165270", pkg, legacy))
    }
}
