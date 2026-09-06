package net.breadthcharge.exigentheron.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RuleValidatorTest {

    @Test
    fun `null pattern is Ok -- match anything`() {
        assertThat(RuleValidator.validatePattern(null)).isEqualTo(PatternValidation.Ok)
    }

    @Test
    fun `an ordinary valid pattern is Ok`() {
        assertThat(RuleValidator.validatePattern("^Alex$")).isEqualTo(PatternValidation.Ok)
    }

    @Test
    fun `unbalanced parenthesis is Invalid`() {
        val result = RuleValidator.validatePattern("(unclosed")

        assertThat(result).isInstanceOf(PatternValidation.Invalid::class.java)
    }

    @Test
    fun `a numbered backreference is rejected`() {
        val result = RuleValidator.validatePattern("""^(a+)+\1b$""")

        assertThat(result).isInstanceOf(PatternValidation.Invalid::class.java)
        assertThat((result as PatternValidation.Invalid).message).contains("backreferences")
    }

    @Test
    fun `a named-group backreference is rejected`() {
        val result = RuleValidator.validatePattern("""(?<g>a+)\k<g>""")

        assertThat(result).isInstanceOf(PatternValidation.Invalid::class.java)
    }

    @Test
    fun `a named capturing group without a backreference is still Ok`() {
        val result = RuleValidator.validatePattern("""(?<g>a+)""")

        assertThat(result).isEqualTo(PatternValidation.Ok)
    }
}
