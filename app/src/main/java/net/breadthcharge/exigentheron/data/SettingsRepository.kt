package net.breadthcharge.exigentheron.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.breadthcharge.exigentheron.speech.BluetoothDeviceDecision

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val HEADSET_ONLY_KEY = booleanPreferencesKey("headset_only")
private val RESPECT_LOCK_STATE_KEY = booleanPreferencesKey("respect_lock_state")
private val ALLOW_DND_OVERRIDE_KEY = booleanPreferencesKey("allow_dnd_override")
private val TTS_ENGINE_PACKAGE_KEY = stringPreferencesKey("tts_engine_package")
private val BLUETOOTH_DEVICE_CONTROL_ENABLED_KEY = booleanPreferencesKey("bluetooth_device_control_enabled")
private val ALLOWED_BLUETOOTH_ADDRESSES_KEY = stringSetPreferencesKey("allowed_bluetooth_addresses")
private val DENIED_BLUETOOTH_ADDRESSES_KEY = stringSetPreferencesKey("denied_bluetooth_addresses")
private val TRUNCATION_LENGTH_SECONDS_KEY = intPreferencesKey("truncation_length_seconds")

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
 *
 * [bluetoothDeviceControlEnabled] defaults **off** — this is the one
 * setting in this class gated behind a runtime permission
 * (`BLUETOOTH_CONNECT`), and this app requests zero runtime permissions
 * otherwise (AGENTS.md §0/§5). Off by default means the permission
 * prompt itself is never shown until the user deliberately opts into
 * this feature from the settings screen, not on first launch.
 * [allowedBluetoothAddresses]/[deniedBluetoothAddresses] are mutually
 * exclusive by construction — [setBluetoothDeviceDecision] is the only
 * way to write either, and it always removes an address from the other
 * set first, so a device can never end up in both at once. Empty
 * ("unset") is every device's default state, meaning
 * [OutputRouteGate]'s plain type-based check as if this feature were off.
 *
 * [truncationLengthSeconds] is null ("no limit") by default, per
 * AGENTS.md §4.7's silence-over-guessing stance — cutting a notification
 * off mid-sentence needs the user to opt in, not a default that could
 * drop the important half of a message the user never asked to have
 * shortened. When set, [SpeechQueue] stops an utterance's audio once it
 * has been playing this long, however far through the text it's gotten
 * — it caps *playback time*, not character count, since speech rate
 * varies by engine/voice/locale and a char-count cap couldn't promise
 * the same number of seconds two engines would actually take to say it.
 */
data class Settings(
    val headsetOnly: Boolean = true,
    val respectLockState: Boolean = true,
    val allowDndOverride: Boolean = false,
    val ttsEnginePackage: String? = null,
    val bluetoothDeviceControlEnabled: Boolean = false,
    val allowedBluetoothAddresses: Set<String> = emptySet(),
    val deniedBluetoothAddresses: Set<String> = emptySet(),
    val truncationLengthSeconds: Int? = null,
) {
    fun bluetoothDeviceDecision(address: String): BluetoothDeviceDecision = when (address) {
        in allowedBluetoothAddresses -> BluetoothDeviceDecision.ALLOWED
        in deniedBluetoothAddresses -> BluetoothDeviceDecision.DENIED
        else -> BluetoothDeviceDecision.UNSET
    }
}

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
            bluetoothDeviceControlEnabled = prefs[BLUETOOTH_DEVICE_CONTROL_ENABLED_KEY] ?: false,
            allowedBluetoothAddresses = prefs[ALLOWED_BLUETOOTH_ADDRESSES_KEY] ?: emptySet(),
            deniedBluetoothAddresses = prefs[DENIED_BLUETOOTH_ADDRESSES_KEY] ?: emptySet(),
            truncationLengthSeconds = prefs[TRUNCATION_LENGTH_SECONDS_KEY],
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

    suspend fun setBluetoothDeviceControlEnabled(enabled: Boolean) {
        dataStore.edit { it[BLUETOOTH_DEVICE_CONTROL_ENABLED_KEY] = enabled }
    }

    /** Null (or non-positive, treated the same as null) clears the limit — see [Settings.truncationLengthSeconds]. */
    suspend fun setTruncationLengthSeconds(seconds: Int?) {
        dataStore.edit {
            if (seconds == null || seconds <= 0) it.remove(TRUNCATION_LENGTH_SECONDS_KEY) else it[TRUNCATION_LENGTH_SECONDS_KEY] = seconds
        }
    }

    /**
     * The only way to change either Bluetooth address set — always
     * removes [address] from *both* first, then adds it back to
     * whichever one [decision] asks for (neither, for
     * [BluetoothDeviceDecision.UNSET]). This is what makes "in both
     * lists at once" structurally unreachable rather than a state the
     * UI has to avoid on its own.
     */
    suspend fun setBluetoothDeviceDecision(address: String, decision: BluetoothDeviceDecision) {
        dataStore.edit { prefs ->
            val allowed = (prefs[ALLOWED_BLUETOOTH_ADDRESSES_KEY] ?: emptySet()) - address
            val denied = (prefs[DENIED_BLUETOOTH_ADDRESSES_KEY] ?: emptySet()) - address
            prefs[ALLOWED_BLUETOOTH_ADDRESSES_KEY] = if (decision == BluetoothDeviceDecision.ALLOWED) allowed + address else allowed
            prefs[DENIED_BLUETOOTH_ADDRESSES_KEY] = if (decision == BluetoothDeviceDecision.DENIED) denied + address else denied
        }
    }
}
