package com.kregosh.mtglifetracker.ui.components

import com.kregosh.mtglifetracker.ui.Strings
import androidx.compose.runtime.Composable
import com.kregosh.mtglifetracker.shared.PredefinedStat
import com.kregosh.mtglifetracker.shared.StatType

/** Full name, as shown in the stat picker. */
internal fun PredefinedStat.label(): String = when (this) {
    PredefinedStat.COMMANDER  -> Strings.statCommander
    PredefinedStat.POISON     -> Strings.statPoison
    PredefinedStat.ENERGY     -> Strings.statEnergy
    PredefinedStat.EXPERIENCE -> Strings.statExperience
    PredefinedStat.STORM      -> Strings.statStorm
    PredefinedStat.TAX        -> Strings.statTax
    PredefinedStat.RING       -> Strings.statRing
    PredefinedStat.INITIATIVE -> Strings.statInitiative
    PredefinedStat.BLESSING   -> Strings.statBlessing
}

/** Compact name, as shown on a player card. */
internal fun PredefinedStat.shortLabel(): String = when (this) {
    PredefinedStat.COMMANDER  -> Strings.statCommanderShort
    PredefinedStat.POISON     -> Strings.statPoisonShort
    PredefinedStat.EXPERIENCE -> Strings.statExperienceShort
    PredefinedStat.STORM      -> Strings.statStormShort
    PredefinedStat.TAX        -> Strings.statTaxShort
    else                      -> label()
}

internal fun StatType.label(): String = when (this) {
    StatType.NUMERIC    -> Strings.statTypeCounter
    StatType.TOGGLE     -> Strings.statTypeToggle
    StatType.RING_STAGE -> Strings.statTypeRingStage
}

/** Custom stats are shown by their own name. */
@Composable
fun statLabel(id: String): String =
    PredefinedStat.fromId(id)?.let { it.label() } ?: id

@Composable
fun statShortLabel(id: String): String = statShortLabelPlain(id)

/** [statShortLabel] for use outside composition, e.g. in semantics. */
internal fun statShortLabelPlain(id: String): String =
    PredefinedStat.fromId(id)?.shortLabel() ?: id

@Composable
fun statTypeLabel(type: StatType): String = type.label()
