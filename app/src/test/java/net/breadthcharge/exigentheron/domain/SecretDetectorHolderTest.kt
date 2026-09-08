package net.breadthcharge.exigentheron.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SecretDetectorHolderTest {

    // Dispatchers.Unconfined for synchronous flow collection without
    // needing a virtual clock — same pattern as RuleEngineHolderTest.
    private fun testScope() = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun `rebuilds detector on keyword emission`(): Unit = runBlocking {
        val keywords = listOf("code", "otp")
        val holder = SecretDetectorHolder(
            otpKeywords = kotlinx.coroutines.flow.flowOf(keywords),
            scope = testScope(),
        )

        assertThat(holder.scan(Decision.Speak("test"), payload("123456 code"))).isInstanceOf(Decision.AnnounceOnly::class.java)
    }

    @Test
    fun `detector changes on each keyword flow emission`(): Unit = runBlocking {
        val flow = MutableStateFlow(listOf("magic"))
        val holder = SecretDetectorHolder(
            otpKeywords = flow,
            scope = testScope(),
        )

        // With "magic" keyword, this OTP-shaped content should be downgraded
        val result1 = holder.scan(Decision.Speak("test"), payload("The magic word is 123456."))
        assertThat(result1).isInstanceOf(Decision.AnnounceOnly::class.java)

        // Change to a different keyword that won't match
        flow.value = listOf("otherword")

        // Now the same content should NOT be downgraded (no keyword match)
        val result2 = holder.scan(Decision.Speak("test"), payload("The magic word is 123456."))
        assertThat(result2).isInstanceOf(Decision.Speak::class.java)
    }

    private fun payload(body: String) = NotificationPayload(
        key = "k",
        packageName = "com.example.app",
        postTime = 0,
        title = "Test",
        body = body,
        isGroupSummary = false,
        isOngoing = false,
        visibility = 1,
        contentHash = "irrelevant",
    )
}
