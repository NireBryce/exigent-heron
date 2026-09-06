package net.breadthcharge.exigentheron.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val HEADSET_ONLY_KEY = booleanPreferencesKey("headset_only")
private val RESPECT_LOCK_STATE_KEY = booleanPreferencesKey("respect_lock_state")
private val ALLOW_DND_OVERRIDE_KEY = booleanPreferencesKey("allow_dnd_override")
private val TTS_ENGINE_PACKAGE_KEY = stringPreferencesKey("tts_engine_package")

/**
 * The gates and toggles AGENTS.md §4.8/§4.9 actually ask for — nothing
 * more (AGENTS.md §0's YAGNI: this held zero fields through Phase 3
 * because nothing needed one yet).
 *
 * [headsetOnly] and [respectLockState] default **on** — AGENTS.md §4.9
 * and §7's "Default the headset gate off 'for convenience'": the
 * default must not broadcast private messages to a room or a glanced-at
 * lock screen. [allowDndOverride] defaults **off** — AGENTS.md §4.7:
 * "do not speak under DND unless the user explicitly opts in."
 * [ttsEnginePackage] is null until the user picks one in settings;
 * AGENTS.md §4.8 is explicit that a null choice falls back to the
 * system default only as a *temporary* state, not a permanent silent one.
 */
data class Settings(
    val headsetOnly: Boolean = true,
    val respectLockState: Boolean = true,
    val allowDndOverride: Boolean = false,
    val ttsEnginePackage: String? = null,
)

/**
 * DataStore-backed persistence for [Settings] (AGENTS.md §2: Preferences
 * DataStore, not Room) — the Phase 4 fields the Phase 3 scaffold's doc
 * comment said would land here.
 */
class SettingsRepository(context: Context) {

    private val dataStore: DataStore<Preferences> = context.settingsDataStore

    val settings: Flow<Settings> = dataStore.data.map { prefs ->
        Settings(
            headsetOnly = prefs[HEADSET_ONLY_KEY] ?: true,
            respectLockState = prefs[RESPECT_LOCK_STATE_KEY] ?: true,
            allowDndOverride = prefs[ALLOW_DND_OVERRIDE_KEY] ?: false,
            ttsEnginePackage = prefs[TTS_ENGINE_PACKAGE_KEY],
        )
    }

    suspend fun setHeadsetOnly(enabled: Boolean) {
        dataStore.edit { it[HEADSET_ONLY_KEY] = enabled }
    }

    suspend fun setRespectLockState(enabled: Boolean) {
        dataStore.edit { it[RESPECT_LOCK_STATE_KEY] = enabled }
    }

    suspend fun setAllowDndOverride(enabled: Boolean) {
        dataStore.edit { it[ALLOW_DND_OVERRIDE_KEY] = enabled }
    }

    suspend fun setTtsEnginePackage(packageName: String?) {
        dataStore.edit {
            if (packageName == null) it.remove(TTS_ENGINE_PACKAGE_KEY) else it[TTS_ENGINE_PACKAGE_KEY] = packageName
        }
    }
}
