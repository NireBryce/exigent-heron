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
}
