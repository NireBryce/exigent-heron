package net.breadthcharge.exigentheron.speech

/**
 * "Don't speak while locked" enforcement — a separate toggle defaulted on,
 * alongside [OutputRouteGate]'s headset-only gate.
 *
 * Same function-reference pattern as [OutputRouteGate] and
 * [SpeechQueue]'s `isInCall`: [isKeyguardLocked] stands in for
 * `KeyguardManager.isKeyguardLocked()` so this is testable on the JVM
 * without a real `KeyguardManager`. `AppContainer` wires the real ones.
 */
class LockStateGate(
    private val respectLockState: () -> Boolean,
    private val isKeyguardLocked: () -> Boolean,
) {
    fun allows(): Boolean = !respectLockState() || !isKeyguardLocked()
}
