package net.breadthcharge.exigentheron.domain

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test

class InterruptibleCharSequenceTest {

    // Thread.interrupted() clears the flag as a side effect — always
    // run it after a test that sets the flag, so it can't bleed into
    // another test sharing this JVM's test-runner thread.
    @After
    fun clearInterruptFlag() {
        Thread.interrupted()
    }

    @Test
    fun `get returns the underlying character when not interrupted`() {
        val sequence = InterruptibleCharSequence("hello")

        assertThat(sequence[1]).isEqualTo('e')
        assertThat(sequence.length).isEqualTo(5)
    }

    @Test
    fun `get throws once the current thread is interrupted`() {
        val sequence = InterruptibleCharSequence("hello")
        Thread.currentThread().interrupt()

        assertThrows(InterruptedMatchException::class.java) { sequence[0] }
    }

    @Test
    fun `subSequence still checks interruption`() {
        val sub = InterruptibleCharSequence("hello world").subSequence(6, 11)
        assertThat(sub.toString()).isEqualTo("world")

        Thread.currentThread().interrupt()

        assertThrows(InterruptedMatchException::class.java) { sub[0] }
    }
}
