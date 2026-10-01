package com.kregosh.mtglifetracker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kregosh.mtglifetracker.R
import com.kregosh.mtglifetracker.shared.PredefinedStat
import com.kregosh.mtglifetracker.shared.StatType

/** Full name, as shown in the stat picker. Custom stats are shown by their own name. */
@Composable
fun statLabel(id: String): String = when (PredefinedStat.fromId(id)) {
    PredefinedStat.COMMANDER  -> stringResource(R.string.stat_commander)
    PredefinedStat.POISON     -> stringResource(R.string.stat_poison)
    PredefinedStat.ENERGY     -> stringResource(R.string.stat_energy)
    PredefinedStat.EXPERIENCE -> stringResource(R.string.stat_experience)
    PredefinedStat.STORM      -> stringResource(R.string.stat_storm)
    PredefinedStat.TAX        -> stringResource(R.string.stat_tax)
    PredefinedStat.RING       -> stringResource(R.string.stat_ring)
    PredefinedStat.MONARCH    -> stringResource(R.string.stat_monarch)
    PredefinedStat.INITIATIVE -> stringResource(R.string.stat_initiative)
    PredefinedStat.BLESSING   -> stringResource(R.string.stat_blessing)
    null                      -> id
}

/** Compact name, as shown on a player card. */
@Composable
fun statShortLabel(id: String): String = when (PredefinedStat.fromId(id)) {
    PredefinedStat.COMMANDER  -> stringResource(R.string.stat_commander_short)
    PredefinedStat.POISON     -> stringResource(R.string.stat_poison_short)
    PredefinedStat.EXPERIENCE -> stringResource(R.string.stat_experience_short)
    PredefinedStat.STORM      -> stringResource(R.string.stat_storm_short)
    PredefinedStat.TAX        -> stringResource(R.string.stat_tax_short)
    else                      -> statLabel(id)
}

@Composable
fun statTypeLabel(type: StatType): String = stringResource(
    when (type) {
        StatType.NUMERIC    -> R.string.stat_type_counter
        StatType.TOGGLE     -> R.string.stat_type_toggle
        StatType.RING_STAGE -> R.string.stat_type_ring_stage
    }
)
