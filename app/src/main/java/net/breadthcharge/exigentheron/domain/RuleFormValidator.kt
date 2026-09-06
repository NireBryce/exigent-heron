package net.breadthcharge.exigentheron.domain

/**
 * PURE. No Android imports — see AGENTS.md §3.
 *
 * The field-level checks `RuleEditorScreen`'s save button gates on,
 * pulled out of `RuleEditorViewModel.save()` into a pure function —
 * this codebase's own pattern elsewhere for exactly this shape of logic
 * ([RuleValidator], [RuleCodec]): a `ViewModel`/`Context`-facing caller
 * stays thin, and the actual decision is unit-testable on the JVM
 * without a real `ViewModel`.
 *
 * BUILD_PLAN.md Phase 3: "invalid regex shows an error at save time
 * rather than crashing later" — [RuleValidator] supplies the per-pattern
 * half of that; this adds the two form-only checks (an empty app
 * selection, a non-numeric priority) that were never regex-shaped to
 * begin with.
 */
object RuleFormValidator {

    fun validate(
        matchAllApps: Boolean,
        selectedPackages: Set<String>,
        titlePattern: String,
        bodyPattern: String,
        priorityText: String,
    ): RuleFormValidationResult {
        val title = titlePattern.trim().ifBlank { null }
        val body = bodyPattern.trim().ifBlank { null }
        val priority = priorityText.trim().toIntOrNull()

        val errors = RuleFormErrors(
            titleError = (RuleValidator.validatePattern(title) as? PatternValidation.Invalid)?.message,
            bodyError = (RuleValidator.validatePattern(body) as? PatternValidation.Invalid)?.message,
            appsError = if (!matchAllApps && selectedPackages.isEmpty()) {
                "Choose at least one app, or switch to \"All apps\""
            } else {
                null
            },
            priorityError = if (priority == null) "Priority must be a whole number" else null,
        )

        return RuleFormValidationResult(
            errors = errors,
            titlePattern = title,
            bodyPattern = body,
            priority = priority,
        )
    }
}

data class RuleFormErrors(
    val titleError: String? = null,
    val bodyError: String? = null,
    val appsError: String? = null,
    val priorityError: String? = null,
) {
    val isValid: Boolean get() = titleError == null && bodyError == null && appsError == null && priorityError == null
}

/**
 * [titlePattern]/[bodyPattern]/[priority] are the parsed, trimmed field
 * values a caller needs to actually build a [Rule] — populated
 * regardless of [errors], but only meaningful to use once
 * `errors.isValid` is true. [priority] is null exactly when
 * [RuleFormErrors.priorityError] is set.
 */
data class RuleFormValidationResult(
    val errors: RuleFormErrors,
    val titlePattern: String?,
    val bodyPattern: String?,
    val priority: Int?,
)
