package net.breadthcharge.exigentheron.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContentHashTest {

    @Test
    fun `same title and body produce the same hash`() {
        assertThat(contentHashOf("Alex", "hi")).isEqualTo(contentHashOf("Alex", "hi"))
    }

    @Test
    fun `different body produces a different hash`() {
        assertThat(contentHashOf("Alex", "hi")).isNotEqualTo(contentHashOf("Alex", "bye"))
    }

    @Test
    fun `null title and null body still produce a stable, non-blank hash`() {
        val hash = contentHashOf(null, null)
        assertThat(hash).isNotEmpty()
        assertThat(hash).isEqualTo(contentHashOf(null, null))
    }
}
