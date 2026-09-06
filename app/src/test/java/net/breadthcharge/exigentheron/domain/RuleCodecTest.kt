package net.breadthcharge.exigentheron.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RuleCodecTest {

    private fun rule(id: String, priority: Int = 0) =
        Rule(id = id, enabled = true, action = RuleAction.SPEAK, priority = priority)

    @Test
    fun `encode then decode round-trips`() {
        val rules = listOf(rule("a"), rule("b", priority = 5))

        assertThat(RuleCodec.decode(RuleCodec.encode(rules))).isEqualTo(rules)
    }

    @Test
    fun `decoding null is an empty list`() {
        assertThat(RuleCodec.decode(null)).isEmpty()
    }

    @Test
    fun `decoding blank is an empty list`() {
        assertThat(RuleCodec.decode("  ")).isEmpty()
    }

    @Test
    fun `decoding malformed json is an empty list, not a crash`() {
        assertThat(RuleCodec.decode("{not valid json")).isEmpty()
    }

    @Test
    fun `upsert appends a rule with a new id`() {
        val result = RuleCodec.upsert(listOf(rule("a")), rule("b"))

        assertThat(result.map { it.id }).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `upsert replaces a rule sharing an existing id`() {
        val result = RuleCodec.upsert(listOf(rule("a", priority = 0)), rule("a", priority = 9))

        assertThat(result).hasSize(1)
        assertThat(result.single().priority).isEqualTo(9)
    }

    @Test
    fun `remove filters out the matching id and leaves others`() {
        val result = RuleCodec.remove(listOf(rule("a"), rule("b")), "a")

        assertThat(result.map { it.id }).containsExactly("b")
    }

    @Test
    fun `remove of an absent id is a no-op`() {
        val rules = listOf(rule("a"))

        assertThat(RuleCodec.remove(rules, "missing")).isEqualTo(rules)
    }
}
