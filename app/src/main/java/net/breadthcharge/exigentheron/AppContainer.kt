package net.breadthcharge.exigentheron

import android.content.Context

/**
 * Manual DI container: constructs and holds this app's singletons.
 *
 * No Hilt, no framework — see AGENTS.md §2. This gets wired up
 * incrementally as later phases introduce things worth holding here
 * (SettingsRepository, RuleRepository, the speech stack, ...). Empty by
 * design for now.
 */
class AppContainer(private val appContext: Context)
