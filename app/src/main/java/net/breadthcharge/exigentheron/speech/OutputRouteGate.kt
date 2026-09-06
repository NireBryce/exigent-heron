package net.breadthcharge.exigentheron.speech

import android.media.AudioDeviceInfo

/**
 * Headset-only enforcement — see AGENTS.md §4.9. "The default should not
 * be broadcasting private messages to a room": [allows] returns `false`
 * whenever the headset-only setting is on and none of the currently
 * connected outputs is a headset.
 *
 * Takes [headsetOnlyEnabled]/[connectedOutputTypes] as function
 * references rather than an `AudioManager`/`SettingsRepository` directly
 * — the same reasoning as [SpeechQueue]'s constructor (see its doc
 * comment): this keeps the decision logic unit-testable on the JVM
 * without a real `AudioManager`. `AppContainer` wires the real
 * ones: a settings-backed boolean, and
 * `audioManager.getDevices(GET_DEVICES_OUTPUTS).map { it.type }.toSet()`.
 */
class OutputRouteGate(
    private val headsetOnlyEnabled: () -> Boolean,
    private val connectedOutputTypes: () -> Set<Int>,
) {
    fun allows(): Boolean {
        if (!headsetOnlyEnabled()) return true
        return connectedOutputTypes().any { it in HEADSET_DEVICE_TYPES }
    }

    companion object {
        val HEADSET_DEVICE_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
    }
}
