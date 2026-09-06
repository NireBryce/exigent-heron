package net.breadthcharge.exigentheron.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.breadthcharge.exigentheron.domain.Rule
import net.breadthcharge.exigentheron.domain.RuleCodec

private val Context.ruleDataStore: DataStore<Preferences> by preferencesDataStore(name = "rules")
private val RULES_KEY = stringPreferencesKey("rules_json")

/**
 * DataStore-backed persistence for [Rule]s (AGENTS.md §2: Preferences
 * DataStore + `kotlinx.serialization`, not Room). Deliberately thin —
 * [RuleCodec] holds the actual encode/decode/list-editing logic and is
 * unit-tested directly; this class only wires it to a real [Context].
 */
class RuleRepository(private val context: Context) {

    val rules: Flow<List<Rule>> = context.ruleDataStore.data.map { RuleCodec.decode(it[RULES_KEY]) }

    /** Inserts, or replaces the existing rule sharing [Rule.id]. */
    suspend fun upsert(rule: Rule) {
        context.ruleDataStore.edit { prefs ->
            val current = RuleCodec.decode(prefs[RULES_KEY])
            prefs[RULES_KEY] = RuleCodec.encode(RuleCodec.upsert(current, rule))
        }
    }

    suspend fun delete(id: String) {
        context.ruleDataStore.edit { prefs ->
            val current = RuleCodec.decode(prefs[RULES_KEY])
            prefs[RULES_KEY] = RuleCodec.encode(RuleCodec.remove(current, id))
        }
    }
}
