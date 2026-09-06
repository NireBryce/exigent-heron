package net.breadthcharge.exigentheron.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RuleFormValidatorTest {

    private fun validate(
        matchAllApps: Boolean = true,
        selectedPackages: Set<String> = emptySet(),
        titlePattern: String = "",
        bodyPattern: String = "",
        priorityText: String = "0",
    ) = RuleFormValidator.validate(matchAllApps, selectedPackages, titlePattern, bodyPattern, priorityText)

    @Test
    fun `blank optional fields and a valid priority validate with no errors`() {
        val result = validate()

        assertThat(result.errors.isValid).isTrue()
        assertThat(result.titlePattern).isNull()
        assertThat(result.bodyPattern).isNull()
        assertThat(result.priority).isEqualTo(0)
    }

    @Test
    fun `title and body patterns are trimmed and blank becomes null`() {
        val result = validate(titlePattern = "  Alex  ", bodyPattern = "   ")

        assertThat(result.titlePattern).isEqualTo("Alex")
        assertThat(result.bodyPattern).isNull()
    }

    @Test
    fun `an invalid title regex reports titleError and fails validation`() {
        val result = validate(titlePattern = "(unclosed")

        assertThat(result.errors.titleError).isNotNull()
        assertThat(result.errors.isValid).isFalse()
    }

    @Test
    fun `an invalid body regex reports bodyError and fails validation`() {
        val result = validate(bodyPattern = "(unclosed")

        assertThat(result.errors.bodyError).isNotNull()
        assertThat(result.errors.isValid).isFalse()
    }

    @Test
    fun `selected-apps mode with no apps chosen reports appsError`() {
        val result = validate(matchAllApps = false, selectedPackages = emptySet())

        assertThat(result.errors.appsError).isNotNull()
        assertThat(result.errors.isValid).isFalse()
    }

    @Test
    fun `selected-apps mode with at least one app chosen has no appsError`() {
        val result = validate(matchAllApps = false, selectedPackages = setOf("com.example"))

        assertThat(result.errors.appsError).isNull()
    }

    @Test
    fun `all-apps mode never reports appsError even with nothing selected`() {
        val result = validate(matchAllApps = true, selectedPackages = emptySet())

        assertThat(result.errors.appsError).isNull()
    }

    @Test
    fun `a non-numeric priority reports priorityError and leaves priority null`() {
        val result = validate(priorityText = "not a number")

        assertThat(result.errors.priorityError).isNotNull()
        assertThat(result.errors.isValid).isFalse()
        assertThat(result.priority).isNull()
    }

    @Test
    fun `a whitespace-padded numeric priority is parsed and trimmed`() {
        val result = validate(priorityText = "  -3  ")

        assertThat(result.errors.priorityError).isNull()
        assertThat(result.priority).isEqualTo(-3)
    }

    @Test
    fun `multiple invalid fields all report their own error simultaneously`() {
        val result = validate(
            matchAllApps = false,
            selectedPackages = emptySet(),
            titlePattern = "(unclosed",
            priorityText = "nope",
        )

        assertThat(result.errors.titleError).isNotNull()
        assertThat(result.errors.appsError).isNotNull()
        assertThat(result.errors.priorityError).isNotNull()
        assertThat(result.errors.bodyError).isNull()
        assertThat(result.errors.isValid).isFalse()
    }
}
