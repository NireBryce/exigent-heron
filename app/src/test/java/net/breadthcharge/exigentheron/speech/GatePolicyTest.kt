package net.breadthcharge.exigentheron.speech

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Covers the two decisions `AppContainer` used to make inline, in
 * lambdas no test could reach. The framework halves it kept — reading
 * the interruption filter, mapping `AudioDeviceInfo` — are not tested
 * here and can't be; see `app/src/androidTest/` for those.
 */
class GatePolicyTest {

    private val a2dp = 8
    private val wired = 3

    @Test
    fun `DND blocks speech when the override is off and a filter is active`() {
        assertThat(isBlockedByDnd(allowDndOverride = false, interruptionFilter = 2)).isTrue()
    }

    @Test
    fun `allow-all is not DND, so nothing is blocked`() {
        assertThat(isBlockedByDnd(allowDndOverride = false, interruptionFilter = INTERRUPTION_FILTER_ALL))
            .isFalse()
    }

    @Test
    fun `the override wins over an active filter`() {
        assertThat(isBlockedByDnd(allowDndOverride = true, interruptionFilter = 2)).isFalse()
    }

    @Test
    fun `bluetooth addresses are empty without the permission, whatever is connected`() {
        val devices = listOf(OutputDevice(type = a2dp, address = "AA:BB:CC:DD:EE:FF"))

        assertThat(bluetoothAddressesOf(devices, setOf(a2dp), bluetoothConnectGranted = false)).isEmpty()
    }

    @Test
    fun `only bluetooth-typed outputs contribute addresses`() {
        val devices = listOf(
            OutputDevice(type = a2dp, address = "AA:BB:CC:DD:EE:FF"),
            OutputDevice(type = wired, address = "not-a-bluetooth-address"),
        )

        assertThat(bluetoothAddressesOf(devices, setOf(a2dp), bluetoothConnectGranted = true))
            .containsExactly("AA:BB:CC:DD:EE:FF")
    }

    /**
     * `AudioDeviceInfo.getAddress()` can come back null even for a
     * device whose type qualifies — dropping those rather than
     * propagating a null into the gate's address set is the behaviour
     * `OutputRouteGate` depends on.
     */
    @Test
    fun `a bluetooth output with no readable address is dropped, not propagated`() {
        val devices = listOf(
            OutputDevice(type = a2dp, address = null),
            OutputDevice(type = a2dp, address = "11:22:33:44:55:66"),
        )

        assertThat(bluetoothAddressesOf(devices, setOf(a2dp), bluetoothConnectGranted = true))
            .containsExactly("11:22:33:44:55:66")
    }

    @Test
    fun `no connected outputs yields no addresses`() {
        assertThat(bluetoothAddressesOf(emptyList(), setOf(a2dp), bluetoothConnectGranted = true)).isEmpty()
    }
}
