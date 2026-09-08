package net.breadthcharge.exigentheron.speech

import android.media.AudioDeviceInfo

/** A user-authored decision about one specific paired Bluetooth device, by address. */
enum class BluetoothDeviceDecision { ALLOWED, DENIED, UNSET }

/**
 * Headset-only enforcement — enabled by default to prevent broadcasting private notifications
 * to a room. [allows] returns `false` when this setting is on and no connected output is a headset.
 *
 * Takes [headsetOnlyEnabled]/[connectedOutputTypes] as function
 * references rather than an `AudioManager`/`SettingsRepository` directly
 * — the same reasoning as [SpeechQueue]'s constructor (see its doc
 * comment): this keeps the decision logic unit-testable on the JVM
 * without a real `AudioManager`. `AppContainer` wires the real
 * ones: a settings-backed boolean, and
 * `audioManager.getDevices(GET_DEVICES_OUTPUTS).map { it.type }.toSet()`.
 *
 * **Per-device Bluetooth override, off by default.** `TYPE_BLUETOOTH_A2DP`
 * is Android's generic "Bluetooth audio sink" type — a car stereo, a TV
 * soundbar, and real Bluetooth headphones all report exactly the same
 * type, so the type-only check above can't tell them apart. When
 * [bluetoothDeviceControlEnabled] is on, [bluetoothDeviceDecision] can
 * override the type-only result for one specific connected address:
 * [BluetoothDeviceDecision.DENIED] blocks it even though its type would
 * otherwise qualify (a Bluetooth speaker misreporting as a headset);
 * [BluetoothDeviceDecision.ALLOWED] lets it through regardless. A wired
 * headset/headphones connection always qualifies on its own — an
 * explicit Bluetooth deny entry never overrides a *different*, wired
 * output that's also connected. [BluetoothDeviceDecision.UNSET] (the
 * default for every device until the user decides otherwise) falls back
 * to the plain type check, i.e. identical to this feature being off.
 * `AppContainer` only wires the two Bluetooth-specific parameters for
 * real once the user has both turned this feature on *and* granted
 * `BLUETOOTH_CONNECT` — see `Settings.bluetoothDeviceControlEnabled`.
 */
class OutputRouteGate(
    private val headsetOnlyEnabled: () -> Boolean,
    private val connectedOutputTypes: () -> Set<Int>,
    private val bluetoothDeviceControlEnabled: () -> Boolean = { false },
    private val connectedBluetoothAddresses: () -> Set<String> = { emptySet() },
    private val bluetoothDeviceDecision: (address: String) -> BluetoothDeviceDecision =
        { BluetoothDeviceDecision.UNSET },
) {
    fun allows(): Boolean {
        if (!headsetOnlyEnabled()) return true

        val types = connectedOutputTypes()
        // A wired connection always qualifies on its own, regardless of
        // anything the Bluetooth-specific override below decides — a
        // denied car stereo must never silence a headset that's also
        // physically plugged in.
        if (types.any { it in NON_BLUETOOTH_HEADSET_TYPES }) return true

        val bluetoothTypeQualifies = types.any { it in BLUETOOTH_HEADSET_TYPES }
        if (!bluetoothDeviceControlEnabled()) return bluetoothTypeQualifies

        val addresses = connectedBluetoothAddresses()
        if (addresses.any { bluetoothDeviceDecision(it) == BluetoothDeviceDecision.ALLOWED }) return true
        if (!bluetoothTypeQualifies) return false
        return addresses.none { bluetoothDeviceDecision(it) == BluetoothDeviceDecision.DENIED }
    }

    companion object {
        val NON_BLUETOOTH_HEADSET_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        )
        val BLUETOOTH_HEADSET_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
        val HEADSET_DEVICE_TYPES = NON_BLUETOOTH_HEADSET_TYPES + BLUETOOTH_HEADSET_TYPES
    }
}
