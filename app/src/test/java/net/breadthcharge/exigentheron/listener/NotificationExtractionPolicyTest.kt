package net.breadthcharge.exigentheron.listener

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The drop conditions from AGENTS.md §4.2, one at a time. */
class NotificationExtractionPolicyTest {

    private fun drop(
        isOngoing: Boolean = false,
        isGroupSummary: Boolean = false,
        packageName: String = "com.example.chat",
        ownPackageName: String = "net.breadthcharge.exigentheron",
        title: String? = "Alex",
        body: String? = "hi",
    ) = shouldDropNotification(isOngoing, isGroupSummary, packageName, ownPackageName, title, body)

    @Test
    fun `an ordinary notification is not dropped`() {
        assertThat(drop()).isFalse()
    }

    @Test
    fun `ongoing notifications are dropped`() {
        assertThat(drop(isOngoing = true)).isTrue()
    }

    @Test
    fun `group summaries are dropped`() {
        assertThat(drop(isGroupSummary = true)).isTrue()
    }

    @Test
    fun `own package is dropped`() {
        assertThat(drop(packageName = "net.breadthcharge.exigentheron")).isTrue()
    }

    @Test
    fun `empty title and body is dropped`() {
        assertThat(drop(title = null, body = null)).isTrue()
    }

    @Test
    fun `title alone is enough to keep it`() {
        assertThat(drop(title = "Alex", body = null)).isFalse()
    }

    @Test
    fun `body alone is enough to keep it`() {
        assertThat(drop(title = null, body = "hi")).isFalse()
    }
}
