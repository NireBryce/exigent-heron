# Open threads

## Contents

- [Tracked as GitHub issues](#tracked-as-github-issues)
- [Left open right now](#left-open-right-now)
- [Not applicable yet](#not-applicable-yet)

Open questions and known gaps, plus anything tracked as a GitHub issue.
Adapted from nixos-configs' `open-threads.md` — same idea, much shorter,
since this repo is three commits old.

**Before starting work on any of these, or investigating a symptom that
might be one of them:** `gh issue list --repo NireBryce/exigent-heron
--search "<keywords>" --state all` in addition to reading this page — see
skill [`investigate-bug`](../.claude/skills/investigate-bug/SKILL.md).

## Tracked as GitHub issues

None yet — `NireBryce/exigent-heron` has issues enabled with a real label
set (checked 2026-09-05, see skill
[`propose-issue`](../.claude/skills/propose-issue/SKILL.md)) but nothing
filed. Nothing below has been promoted to an issue; it's all still at the
"noticed, not yet worth a round-trip" stage per that skill's Calibrate
section.

## Left open right now

- **`app_name` (`res/values/strings.xml`) is still the literal string
  `"exigent-heron"`** — the repo's own codename, not a real user-facing app
  name. `AGENTS.md` never names the app (it's specified generically as "a
  notification TTS reader"), so this may be intentional-for-now rather
  than an oversight — worth deciding before Phase 3 (the UI phase) rather
  than shipping the codename as the launcher label by default.
- **The android.util.Log CI grep `AGENTS.md` §4.6 asks for** ("Add a lint
  check or a simple CI grep asserting `android.util.Log` appears in
  exactly one file") was missing from `.github/workflows/check.yml` until
  **2026-09-05**, when it was added while building this wiki. Listed here
  as a closed thread, not an open one — kept as the record of when it
  actually landed, since `AGENTS.md` itself doesn't track that.
- **`kotlinx-coroutines-core` is imported directly in `RuleEngine.kt`**
  (`Dispatchers`, `withContext`, `withTimeoutOrNull` — needed for
  `AGENTS.md` §4.4's timeout requirement) but isn't in §2's explicit
  dependency list, which names `kotlinx-coroutines-test` as test-only. It
  compiles and runs today because `androidx.lifecycle:lifecycle-runtime-ktx`
  and Compose runtime both already pull it in transitively — not a new
  dependency added, just an existing one used directly from `domain/`
  code. Fine as long as that transitive graph holds; worth an explicit
  `implementation(libs.kotlinx.coroutines.core)` line if that ever feels
  fragile, rather than leaving `domain/`'s only non-Kotlin-stdlib import
  resting on something no build file actually declares.
- **`RuleEngine`'s backreference-regex gap has no product answer yet**
  (see [history.md](history.md)): a `Rule.bodyPattern`/`titlePattern`
  containing a backreference is still genuinely exponential and
  non-interruptible even with the 100ms timeout — the timeout bounds the
  caller, not the CPU cost. `AGENTS.md` §4.4 already asks for
  `PatternSyntaxException` to be caught "at rule-save time and show the
  error in the editor" once Phase 3 has an editor to show it in; whether
  that same save-time check should also reject backreferences outright
  (closing the gap entirely, at the cost of disallowing a rarely-needed
  but legal regex feature) is an open product decision, not a bug — worth
  deciding when Phase 3 builds that editor, not silently either way.

## Not applicable yet

`AGENTS.md` §8's "if OEM background-killing turns out to break it" clause
— nothing to investigate until there's a running listener service to
observe being killed (Phase 2+). Not forgotten, just not yet reachable.
