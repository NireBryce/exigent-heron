package net.breadthcharge.exigentheron.domain

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * PURE. No Android imports — see AGENTS.md §3.
 *
 * The part of "RuleRepository" (Phase 3, `data/`) worth unit-testing on
 * its own: JSON shape and list-editing, independent of DataStore/Context.
 * `data.RuleRepository` is a thin wrapper calling straight into this.
 */
object RuleCodec {

    fun encode(rules: List<Rule>): String = Json.encodeToString(rules)

    /** Malformed or missing JSON decodes to an empty list, never throws. */
    fun decode(json: String?): List<Rule> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            Json.decodeFromString(json)
        } catch (e: SerializationException) {
            emptyList()
        }
    }

    /** Replaces the rule with a matching [Rule.id], or appends if none matches. */
    fun upsert(rules: List<Rule>, rule: Rule): List<Rule> {
        val index = rules.indexOfFirst { it.id == rule.id }
        return if (index >= 0) {
            rules.toMutableList().also { it[index] = rule }
        } else {
            rules + rule
        }
    }

    fun remove(rules: List<Rule>, id: String): List<Rule> = rules.filterNot { it.id == id }
}
