package com.kregosh.mtglifetracker.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kregosh.mtglifetracker.R
import com.kregosh.mtglifetracker.shared.PredefinedStat
import com.kregosh.mtglifetracker.shared.StatType

/** Full name, as shown in the stat picker. */
@StringRes
internal fun PredefinedStat.labelRes(): Int = when (this) {
    PredefinedStat.COMMANDER  -> R.string.stat_commander
    PredefinedStat.POISON     -> R.string.stat_poison
    PredefinedStat.ENERGY     -> R.string.stat_energy
    PredefinedStat.EXPERIENCE -> R.string.stat_experience
    PredefinedStat.STORM      -> R.string.stat_storm
    PredefinedStat.TAX        -> R.string.stat_tax
    PredefinedStat.RING       -> R.string.stat_ring
    PredefinedStat.MONARCH    -> R.string.stat_monarch
    PredefinedStat.INITIATIVE -> R.string.stat_initiative
    PredefinedStat.BLESSING   -> R.string.stat_blessing
}

/** Compact name, as shown on a player card. */
@StringRes
internal fun PredefinedStat.shortLabelRes(): Int = when (this) {
    PredefinedStat.COMMANDER  -> R.string.stat_commander_short
    PredefinedStat.POISON     -> R.string.stat_poison_short
    PredefinedStat.EXPERIENCE -> R.string.stat_experience_short
    PredefinedStat.STORM      -> R.string.stat_storm_short
    PredefinedStat.TAX        -> R.string.stat_tax_short
    else                      -> labelRes()
}

@StringRes
internal fun StatType.labelRes(): Int = when (this) {
    StatType.NUMERIC    -> R.string.stat_type_counter
    StatType.TOGGLE     -> R.string.stat_type_toggle
    StatType.RING_STAGE -> R.string.stat_type_ring_stage
}

/** Custom stats are shown by their own name. */
@Composable
fun statLabel(id: String): String =
    PredefinedStat.fromId(id)?.let { stringResource(it.labelRes()) } ?: id

@Composable
fun statShortLabel(id: String): String =
    PredefinedStat.fromId(id)?.let { stringResource(it.shortLabelRes()) } ?: id

@Composable
fun statTypeLabel(type: StatType): String = stringResource(type.labelRes())
