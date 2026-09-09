package net.breadthcharge.exigentheron.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.breadthcharge.exigentheron.domain.Rule
import net.breadthcharge.exigentheron.domain.RuleAction
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `RuleRepository` has no JVM test — it is a DataStore wrapper, and
 * DataStore needs a real `Context`. That left the whole write-read path
 * (encode → Preferences → disk → decode) unexercised except by hand:
 * wiki/testing.md's "Persistence" manual check is a human adding a rule,
 * force-stopping the app, and looking.
 *
 * **What this does and does not prove.** It covers serialization, the
 * DataStore write, and that a file really lands on disk. It does *not*
 * prove survival across a process death: `preferencesDataStore` is a
 * per-Context singleton, so a second `RuleRepository` in this process
 * shares the same instance rather than re-reading the file. The
 * force-stop check in wiki/testing.md is still the only thing that shows
 * cross-process persistence, and it stays in that document for that
 * reason.
 */
@RunWith(AndroidJUnit4::class)
class RuleRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val repository = RuleRepository(context)

    private fun rule(id: String, priority: Int = 0) = Rule(
        id = id,
        enabled = true,
        packageNames = setOf("com.example.messenger"),
        titlePattern = "^Alice",
        action = RuleAction.SPEAK,
        priority = priority,
    )

    // The DataStore is the app's real one, not a temp file — anything
    // left behind would leak into the next test and into a manual run on
    // the same device.
    @Before fun clear() = runBlocking<Unit> { clearAllRules() }

    @After fun cleanUp() = runBlocking<Unit> { clearAllRules() }

    private suspend fun clearAllRules() {
        repository.rules.first().forEach { repository.delete(it.id) }
    }

    @Test
    fun aStoredRuleComesBackWithEveryFieldIntact() = runBlocking<Unit> {
        repository.upsert(rule("r1", priority = 7))

        val stored = repository.rules.first().single()
        assertThat(stored.id).isEqualTo("r1")
        assertThat(stored.priority).isEqualTo(7)
        assertThat(stored.packageNames).containsExactly("com.example.messenger")
        assertThat(stored.titlePattern).isEqualTo("^Alice")
        assertThat(stored.action).isEqualTo(RuleAction.SPEAK)
    }

    @Test
    fun upsertReplacesTheRuleSharingAnIdRatherThanAppending() = runBlocking<Unit> {
        repository.upsert(rule("r1", priority = 1))
        repository.upsert(rule("r1", priority = 99))

        val stored = repository.rules.first()
        assertThat(stored).hasSize(1)
        assertThat(stored.single().priority).isEqualTo(99)
    }

    @Test
    fun deleteRemovesOnlyTheNamedRule() = runBlocking<Unit> {
        repository.upsert(rule("r1"))
        repository.upsert(rule("r2"))

        repository.delete("r1")

        assertThat(repository.rules.first().map { it.id }).containsExactly("r2")
    }

    /**
     * The part of "persisted" that a same-process read can't show: a
     * real file, in this app's own data directory, with the rules in it.
     */
    @Test
    fun writingARuleProducesAFileOnDisk() = runBlocking<Unit> {
        repository.upsert(rule("r1"))

        val file = java.io.File(context.filesDir, "datastore/rules.preferences_pb")
        assertThat(file.exists()).isTrue()
        assertThat(file.length()).isGreaterThan(0L)
    }
}
