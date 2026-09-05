package net.breadthcharge.exigentheron.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextSanitizerTest {

    @Test
    fun `strips zero-width characters`() {
        assertThat(sanitizeNotificationText("Hi​there")).isEqualTo("Hithere")
    }

    @Test
    fun `strips bidi override marks`() {
        assertThat(sanitizeNotificationText("A‮evil‬B")).isEqualTo("AevilB")
    }

    @Test
    fun `strips control characters but keeps ordinary punctuation`() {
        assertThat(sanitizeNotificationText("line1line2 (ok!)")).isEqualTo("line1line2 (ok!)")
    }

    @Test
    fun `leaves ordinary text untouched`() {
        assertThat(sanitizeNotificationText("Hey, are you around?")).isEqualTo("Hey, are you around?")
    }

    @Test
    fun `null stays null`() {
        assertThat(sanitizeNotificationText(null)).isNull()
    }

    @Test
    fun `sanitizing down to nothing returns null, not empty string`() {
        assertThat(sanitizeNotificationText("​​​")).isNull()
    }
}
