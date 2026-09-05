package net.breadthcharge.exigentheron.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NotificationPayloadTest {

    @Test
    fun `toString does not contain title or body`() {
        val payload = NotificationPayload(
            key = "key-1",
            packageName = "com.example.chat",
            postTime = 0,
            title = "TITLE_MARKER_should_never_appear",
            body = "BODY_MARKER_should_never_appear",
            isGroupSummary = false,
            isOngoing = false,
            visibility = 1,
            contentHash = "irrelevant-for-this-test",
        )

        val rendered = payload.toString()

        assertThat(rendered).doesNotContain("TITLE_MARKER_should_never_appear")
        assertThat(rendered).doesNotContain("BODY_MARKER_should_never_appear")
        assertThat(rendered).contains("key-1")
        assertThat(rendered).contains("com.example.chat")
    }
}
