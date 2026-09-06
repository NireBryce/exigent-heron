package net.breadthcharge.exigentheron.speech

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LockStateGateTest {

    @Test
    fun `respecting lock state while locked disallows speech`() {
        val gate = LockStateGate(respectLockState = { true }, isKeyguardLocked = { true })
        assertThat(gate.allows()).isFalse()
    }

    @Test
    fun `respecting lock state while unlocked allows speech`() {
        val gate = LockStateGate(respectLockState = { true }, isKeyguardLocked = { false })
        assertThat(gate.allows()).isTrue()
    }

    @Test
    fun `not respecting lock state allows speech even while locked`() {
        val gate = LockStateGate(respectLockState = { false }, isKeyguardLocked = { true })
        assertThat(gate.allows()).isTrue()
    }
}
