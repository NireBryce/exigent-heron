package net.breadthcharge.exigentheron.speech

import android.media.AudioDeviceInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OutputRouteGateTest {

    @Test
    fun `headset-only off allows speech regardless of connected devices`() {
        val gate = OutputRouteGate(headsetOnlyEnabled = { false }, connectedOutputTypes = { emptySet() })
        assertThat(gate.allows()).isTrue()
    }

    @Test
    fun `headset-only on with no devices connected disallows speech`() {
        val gate = OutputRouteGate(headsetOnlyEnabled = { true }, connectedOutputTypes = { emptySet() })
        assertThat(gate.allows()).isFalse()
    }

    @Test
    fun `headset-only on with only a speaker connected disallows speech`() {
        val gate = OutputRouteGate(
            headsetOnlyEnabled = { true },
            connectedOutputTypes = { setOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) },
        )
        assertThat(gate.allows()).isFalse()
    }

    @Test
    fun `headset-only on with a wired headset connected allows speech`() {
        val gate = OutputRouteGate(
            headsetOnlyEnabled = { true },
            connectedOutputTypes = { setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET) },
        )
        assertThat(gate.allows()).isTrue()
    }

    @Test
    fun `headset-only on with wired headphones connected allows speech`() {
        val gate = OutputRouteGate(
            headsetOnlyEnabled = { true },
            connectedOutputTypes = { setOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES) },
        )
        assertThat(gate.allows()).isTrue()
    }

    @Test
    fun `headset-only on with a bluetooth A2DP device connected allows speech`() {
        val gate = OutputRouteGate(
            headsetOnlyEnabled = { true },
            connectedOutputTypes = { setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) },
        )
        assertThat(gate.allows()).isTrue()
    }

    @Test
    fun `headset-only on with a BLE headset connected allows speech`() {
        val gate = OutputRouteGate(
            headsetOnlyEnabled = { true },
            connectedOutputTypes = { setOf(AudioDeviceInfo.TYPE_BLE_HEADSET) },
        )
        assertThat(gate.allows()).isTrue()
    }

    @Test
    fun `a headset alongside other outputs still allows speech`() {
        val gate = OutputRouteGate(
            headsetOnlyEnabled = { true },
            connectedOutputTypes = { setOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_WIRED_HEADSET) },
        )
        assertThat(gate.allows()).isTrue()
    }

    // Bluetooth per-device control (off by default; the tests above all
    // rely on that default, matching bluetoothDeviceControlEnabled = { false }).

    @Test
    fun `A2DP still allows speech by type when device control is off, regardless of a saved decision`() {
        val gate = OutputRouteGate(
            headsetOnlyEnabled = { true },
            connectedOutputTypes = { setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) },
            bluetoothDeviceControlEnabled = { false },
            connectedBluetoothAddresses = { setOf("AA:BB") },
            bluetoothDeviceDecision = { BluetoothDeviceDecision.DENIED },
        )
        assertThat(gate.allows()).isTrue()
    }

    @Test
    fun `device control on, connected A2DP device denied, disallows speech`() {
        val gate = OutputRouteGate(
            headsetOnlyEnabled = { true },
            connectedOutputTypes = { setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) },
            bluetoothDeviceControlEnabled = { true },
            connectedBluetoothAddresses = { setOf("AA:BB") },
            bluetoothDeviceDecision = { address -> if (address == "AA:BB") BluetoothDeviceDecision.DENIED else BluetoothDeviceDecision.UNSET },
        )
        assertThat(gate.allows()).isFalse()
    }

    @Test
    fun `device control on, connected A2DP device unset, falls back to type check and allows`() {
        val gate = OutputRouteGate(
            headsetOnlyEnabled = { true },
            connectedOutputTypes = { setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) },
            bluetoothDeviceControlEnabled = { true },
            connectedBluetoothAddresses = { setOf("AA:BB") },
            bluetoothDeviceDecision = { BluetoothDeviceDecision.UNSET },
        )
        assertThat(gate.allows()).isTrue()
    }

    @Test
    fun `device control on, connected non-headset-type device explicitly allowed, allows anyway`() {
        val gate = OutputRouteGate(
            headsetOnlyEnabled = { true },
            connectedOutputTypes = { setOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) },
            bluetoothDeviceControlEnabled = { true },
            connectedBluetoothAddresses = { setOf("AA:BB") },
            bluetoothDeviceDecision = { BluetoothDeviceDecision.ALLOWED },
        )
        assertThat(gate.allows()).isTrue()
    }

    @Test
    fun `a denied bluetooth device does not block a wired headset connected alongside it`() {
        val gate = OutputRouteGate(
            headsetOnlyEnabled = { true },
            connectedOutputTypes = { setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) },
            bluetoothDeviceControlEnabled = { true },
            connectedBluetoothAddresses = { setOf("AA:BB") },
            bluetoothDeviceDecision = { BluetoothDeviceDecision.DENIED },
        )
        assertThat(gate.allows()).isTrue()
    }

    @Test
    fun `device control on with no bluetooth device connected at all still allows a wired headset`() {
        val gate = OutputRouteGate(
            headsetOnlyEnabled = { true },
            connectedOutputTypes = { setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET) },
            bluetoothDeviceControlEnabled = { true },
        )
        assertThat(gate.allows()).isTrue()
    }
}
