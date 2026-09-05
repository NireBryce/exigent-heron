package net.breadthcharge.exigentheron.domain

import kotlinx.serialization.Serializable

/**
 * PURE. No Android imports. User-authored, persisted via
 * `RuleRepository` (Phase 3) as JSON through DataStore — see AGENTS.md
 * §2.
 */
@Serializable
data class Rule(
    val id: String,
    val enabled: Boolean,
    /** Empty = all apps. */
    val packageNames: Set<String> = emptySet(),
    /** Regex; null = match anything. */
    val titlePattern: String? = null,
    val bodyPattern: String? = null,
    val action: RuleAction,
    /** e.g. "Message from {title}". Null = a plain "title: body" render. */
    val template: String? = null,
    val priority: Int = 0,
)

@Serializable
enum class RuleAction { SPEAK, ANNOUNCE_ONLY, SUPPRESS }
