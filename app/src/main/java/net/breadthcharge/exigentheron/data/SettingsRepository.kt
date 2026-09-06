package net.breadthcharge.exigentheron.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Scaffold only (BUILD_PLAN.md Phase 3): the DataStore file exists and
 * is held here so Phase 4 (`OutputRouteGate`, lock/DND gates, engine
 * picker, announce-only mode — AGENTS.md §4.8-§4.10) only has to add
 * preference keys and a `Settings` data class, not wiring. No fields
 * exist yet because nothing needs one yet — see AGENTS.md §0's YAGNI
 * ground rule.
 */
class SettingsRepository(context: Context) {
    internal val dataStore: DataStore<Preferences> = context.settingsDataStore
}
