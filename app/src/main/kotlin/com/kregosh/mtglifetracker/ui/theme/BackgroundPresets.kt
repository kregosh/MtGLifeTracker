package com.kregosh.mtglifetracker.ui.theme

/**
 * Presets used to be saved as numeric resource IDs, which aren't stable across builds
 * and never matched the name-based storm check. Returns the name-based URI for such a
 * legacy value, or null if [uri] needs no migration.
 */
internal fun migratedPresetUri(uri: String?, packageName: String, legacyIds: Map<Int, String>): String? {
    val prefix = "android.resource://$packageName/"
    if (uri == null || !uri.startsWith(prefix)) return null
    val name = uri.removePrefix(prefix).toIntOrNull()?.let(legacyIds::get) ?: return null
    return "${prefix}drawable/$name"
}
