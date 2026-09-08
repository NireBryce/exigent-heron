package net.breadthcharge.exigentheron.speech

/**
 * PURE. No Android imports — so the decisions `AppContainer` used to
 * make inline, in lambdas it hands the gates, are unit-testable on the
 * JVM like everything in `domain/` is.
 *
 * Not in `domain/` (this is speech-gate wiring, not rule/secret/dedup
 * logic) and not in a file that may import the framework — the same
 * split, for the same reason, that `listener/NotificationExtractionPolicy.kt`
 * already makes against `NotificationExtractor`: the policy is a pure
 * function over plain values, and the caller is glue thin enough that
 * reading it is enough to believe it.
 *
 * `AppContainer` keeps the framework half: reading
 * `NotificationManager.getCurrentInterruptionFilter()` and mapping
 * `AudioDeviceInfo` to [OutputDevice]. What's here is only what those
 * values *mean*.
 */

/**
 * One connected audio output, reduced to the two fields any decision
 * here actually needs. `AppContainer` builds these from the real
 * `AudioDeviceInfo`s; [address] is nullable because `getAddress()` is
 * only meaningful for a Bluetooth device, and only readable at all with
 * `BLUETOOTH_CONNECT` granted.
 */
data class OutputDevice(val type: Int, val address: String?)

/**
 * Mirrors `android.app.NotificationManager.INTERRUPTION_FILTER_ALL` (1).
 * Same arrangement, and the same caveat, as `SecretDetector`'s
 * `VISIBILITY_PRIVATE`/`VISIBILITY_SECRET`: this file stays
 * framework-import-free, so the value must track the framework's rather
 * than be reinvented. `GatePolicyFrameworkConstantsTest` (instrumented,
 * `app/src/androidTest/`) asserts the two are still equal, which is the
 * check a JVM test structurally cannot make.
 */
const val INTERRUPTION_FILTER_ALL = 1

/**
 * Whether Do Not Disturb should stop an utterance. The user-facing
 * toggle is an *override*: with it on, DND is ignored entirely; with it
 * off (the default), anything other than "allow all" blocks speech.
 */
fun isBlockedByDnd(allowDndOverride: Boolean, interruptionFilter: Int): Boolean =
    !allowDndOverride && interruptionFilter != INTERRUPTION_FILTER_ALL

/**
 * The addresses of the currently-connected Bluetooth outputs, for
 * `OutputRouteGate`'s per-device allow/deny.
 *
 * Returns an empty set when [bluetoothConnectGranted] is false rather
 * than throwing or reporting addresses it can't actually read — an
 * empty set makes `OutputRouteGate` fall back to its plain type-based
 * check, i.e. behave exactly as if the per-device feature were off.
 * That permission is re-checked on every call rather than assumed from
 * the settings toggle, because a user can revoke it in system settings
 * without this app finding out except by asking again.
 */
fun bluetoothAddressesOf(
    devices: List<OutputDevice>,
    bluetoothTypes: Set<Int>,
    bluetoothConnectGranted: Boolean,
): Set<String> =
    if (!bluetoothConnectGranted) {
        emptySet()
    } else {
        devices.filter { it.type in bluetoothTypes }.mapNotNull { it.address }.toSet()
    }
