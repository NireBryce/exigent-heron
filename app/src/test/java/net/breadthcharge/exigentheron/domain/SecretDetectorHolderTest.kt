package net.breadthcharge.exigentheron.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Test

class SecretDetectorHolderTest {
    private val testScope = TestScope(StandardTestDispatcher())

    @Test
    fun rebuilds_detector_on_keyword_emission() {
        val keywords = listOf("code", "otp")
        val holder = SecretDetectorHolder(
            otpKeywords = flowOf(keywords),
            scope = testScope,
        )

        testScope.runCurrent()

        assertThat(holder.detector.value.keywords).containsExactlyElementsIn(keywords)
    }

    @Test
    fun detector_changes_on_each_keyword_flow_emission() {
        val flow = kotlinx.coroutines.flow.MutableStateFlow(listOf("code"))
        val holder = SecretDetectorHolder(
            otpKeywords = flow,
            scope = testScope,
        )

        testScope.runCurrent()
        val firstDetector = holder.detector.value
        assertThat(firstDetector.keywords).containsExactly("code")

        flow.value = listOf("code", "otp", "pin")
        testScope.runCurrent()

        val secondDetector = holder.detector.value
        assertThat(secondDetector.keywords).containsExactlyElementsIn(listOf("code", "otp", "pin"))
        assertThat(secondDetector).isNotSameInstanceAs(firstDetector)
    }

    // Access detector as a property for the test
    private val SecretDetectorHolder.detector: kotlinx.coroutines.flow.StateFlow<SecretDetector>
        get() = this::class.java.getDeclaredField("detector").let {
            it.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            it.get(this) as kotlinx.coroutines.flow.StateFlow<SecretDetector>
        }
}
