package net.breadthcharge.exigentheron.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.breadthcharge.exigentheron.speech.BluetoothDeviceDecision
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Same reasoning as [RuleRepositoryTest]: a DataStore wrapper with no
 * JVM test. Two things here are worth asserting beyond a plain
 * round-trip.
 *
 * The **defaults** are a privacy posture, not an arbitrary choice —
 * headset-only and respect-lock-state on, DND-override off, truncation
 * unset — and nothing checked that the code still produced them.
 *
 * The **mutual exclusion** of the allow/deny address sets is a real
 * invariant `SettingsRepository` maintains by hand
 * (`setBluetoothDeviceDecision` removes from the other set first), and
 * `OutputRouteGate` relies on it: an address in both would make its
 * allow-wins-then-deny-checks logic order-dependent.
 */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {

    private val repository = SettingsRepository(
        ApplicationProvider.getApplicationContext<android.content.Context>(),
    )

    private val address = "AA:BB:CC:DD:EE:FF"

    @After
    fun restoreDefaults() = runBlocking<Unit> {
        repository.setHeadsetOnly(true)
        repository.setRespectLockState(true)
        repository.setAllowDndOverride(false)
        repository.setTruncationLengthSeconds(null)
        repository.setTtsEnginePackage(null)
        repository.setBluetoothDeviceControlEnabled(false)
        repository.setBluetoothDeviceDecision(address, BluetoothDeviceDecision.UNSET)
    }

    @Test
    fun defaultsAreThePrivacyPreservingOnes() = runBlocking<Unit> {
        val settings = repository.settings.first()

        assertThat(settings.headsetOnly).isTrue()
        assertThat(settings.respectLockState).isTrue()
        assertThat(settings.allowDndOverride).isFalse()
        assertThat(settings.bluetoothDeviceControlEnabled).isFalse()
        assertThat(settings.truncationLengthSeconds).isNull()
        assertThat(settings.ttsEnginePackage).isNull()
    }

    @Test
    fun eachSetterRoundTrips() = runBlocking<Unit> {
        repository.setHeadsetOnly(false)
        repository.setAllowDndOverride(true)
        repository.setTruncationLengthSeconds(12)
        repository.setTtsEnginePackage("com.example.tts")

        val settings = repository.settings.first()
        assertThat(settings.headsetOnly).isFalse()
        assertThat(settings.allowDndOverride).isTrue()
        assertThat(settings.truncationLengthSeconds).isEqualTo(12)
        assertThat(settings.ttsEnginePackage).isEqualTo("com.example.tts")
    }

    @Test
    fun truncationLengthClearsBackToNoLimit() = runBlocking<Unit> {
        repository.setTruncationLengthSeconds(9)
        repository.setTruncationLengthSeconds(null)

        assertThat(repository.settings.first().truncationLengthSeconds).isNull()
    }

    @Test
    fun anAddressMovedFromAllowToDenyIsNeverLeftInBoth() = runBlocking<Unit> {
        repository.setBluetoothDeviceDecision(address, BluetoothDeviceDecision.ALLOWED)
        repository.setBluetoothDeviceDecision(address, BluetoothDeviceDecision.DENIED)

        val settings = repository.settings.first()
        assertThat(settings.allowedBluetoothAddresses).doesNotContain(address)
        assertThat(settings.deniedBluetoothAddresses).contains(address)
        assertThat(settings.bluetoothDeviceDecision(address)).isEqualTo(BluetoothDeviceDecision.DENIED)
    }

    @Test
    fun unsetRemovesAnAddressFromBothSets() = runBlocking<Unit> {
        repository.setBluetoothDeviceDecision(address, BluetoothDeviceDecision.DENIED)
        repository.setBluetoothDeviceDecision(address, BluetoothDeviceDecision.UNSET)

        val settings = repository.settings.first()
        assertThat(settings.allowedBluetoothAddresses).doesNotContain(address)
        assertThat(settings.deniedBluetoothAddresses).doesNotContain(address)
        assertThat(settings.bluetoothDeviceDecision(address)).isEqualTo(BluetoothDeviceDecision.UNSET)
    }
}
