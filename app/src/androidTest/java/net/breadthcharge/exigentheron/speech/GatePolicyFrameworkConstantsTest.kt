package net.breadthcharge.exigentheron.speech

import android.app.Notification
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
// Explicit even though this file shares GatePolicy.kt's package: a
// top-level const in main/ is a different source set here, and does
// not resolve implicitly the way it would within main/ itself.
import net.breadthcharge.exigentheron.speech.INTERRUPTION_FILTER_ALL
import net.breadthcharge.exigentheron.domain.SecretDetector
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two Android-free files mirror framework constants as plain Ints so they can
 * stay Android-import-free: [SecretDetector]'s visibility levels and
 * [INTERRUPTION_FILTER_ALL]. Both carry a comment saying the values must
 * track the framework's rather than be reinvented — and until this file
 * existed, nothing checked that they still did. A drift would not fail
 * to compile; it would silently misclassify every notification.
 *
 * This is the smallest possible instrumented test and the clearest
 * example of why the source set exists at all: the assertion is one
 * `==`, and a JVM test cannot make it, because making it requires the
 * real `android.app` classes on the classpath.
 */
@RunWith(AndroidJUnit4::class)
class GatePolicyFrameworkConstantsTest {

    @Test
    fun interruptionFilterAllMatchesTheFramework() {
        assertThat(INTERRUPTION_FILTER_ALL).isEqualTo(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    @Test
    fun visibilityConstantsMatchTheFramework() {
        assertThat(SecretDetector.VISIBILITY_PRIVATE).isEqualTo(Notification.VISIBILITY_PRIVATE)
        assertThat(SecretDetector.VISIBILITY_SECRET).isEqualTo(Notification.VISIBILITY_SECRET)
    }

    /**
     * `OutputRouteGate`'s Bluetooth type set is the one place the app
     * decides "this route counts as private". It reads the framework
     * constants directly rather than mirroring them, so this asserts
     * membership rather than equality — a guard against the set being
     * edited to include a type that isn't a headset at all.
     */
    @Test
    fun bluetoothHeadsetTypesAreOnlyBluetoothSinks() {
        assertThat(OutputRouteGate.BLUETOOTH_HEADSET_TYPES).containsExactly(
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
    }
}
