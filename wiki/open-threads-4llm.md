# Open threads (dense)

_Last modified: 2026-09-08_

_Text is llm generated with occasional human review_

## Contents

- [Why this page holds no issue table](#why-this-page-holds-no-issue-table)
- [Issue-to-thread cross-reference](#issue-to-thread-cross-reference)
- [Closed threads, with what resolved them](#closed-threads-with-what-resolved-them)
- [Open, with full context](#open-with-full-context)

Companion to [open-threads.md](open-threads.md). Closed threads are kept
here rather than deleted — the record of when something actually landed
is the part `AGENTS.md` does not track and `git log` states only
obliquely.

## Why this page holds no issue table

Twice-burned, recorded in [traps-and-skills.md](traps-and-skills.md):

1. "Tracked as GitHub issues: **None yet**" stood from 2026-09-05, went
   false 2026-09-07 when the first issues were filed, and stayed false a
   full day — on the page whose job is tracking what's tracked.
2. The fix was a table of all seven open issues. **Two were closed within
   the hour** by CI work in the same session. The new text even said "it
   is a snapshot, not a mirror" while being a mirror.

A table of open issues is state owned by a system outside this repo. No
discipline applied inside the repo can hold it true, and `check_wiki.py`
cannot check it — it has no idea what the tracker says. The shape was
wrong, not the diligence. What belongs here is only the cross-reference
`gh` cannot give you.

Open **PRs** are the known blind spot: an open branch with real work in
it had no entry anywhere, and the same test race was consequently
diagnosed and fixed twice — see [history-4llm.md](history-4llm.md). If
that happens a second time, this page should start listing open PRs.

## Issue-to-thread cross-reference

Live state: `gh issue list --repo NireBryce/exigent-heron --state all`.

| Issue | Thread |
|---|---|
| #24 | Kotlin LSP metadata skew — [traps-and-skills.md](traps-and-skills.md) |
| #27 | whether an OEM kills the listener; repro in [testing.md](testing.md) |
| #28 | long notifications cut off — *character* limits, not §4.7's time cap |
| #29 | on-device acceptance criteria |
| #30 | CodeQL — closed 2026-09-08 |
| #31 | SHA-pinning third-party actions — closed 2026-09-08 |
| #48 | a failing rule reaches only logcat (§4.4), filed 2026-09-08 |
| #49 | `onListenerDisconnected` doesn't reset TTS state (§4.10), filed 2026-09-08 |

## Closed threads, with what resolved them

**`app_name` stays `"exigent-heron"`** — resolved 2026-09-05 while
planning Phase 3, the UI phase that ships it as the visible launcher
label. Confirmed with the user: deliberate, not an oversight.

**The `android.util.Log` CI grep §4.6 asks for by name** was missing from
`check.yml` until **2026-09-05**, when it was added while building this
wiki. Kept as the record of when it actually landed, since `AGENTS.md`
doesn't track that.

**`lintDebug`/`lintRelease` added to CI** (**2026-09-06**, post-Phase-5).
Android Lint is built into AGP and already in use, so it cost no new
dependency — it just wasn't wired into anything. Findings upload to code
scanning via `github/codeql-action/upload-sarif`, which needs
`security-events: write` on the job, since this repo's default
`GITHUB_TOKEN` doesn't carry it. `lintRelease` runs in CI despite
`assembleRelease` not being able to (no signing config there) — lint only
analyzes, never packages or signs. The six findings present when this
landed (four dependency-version-bump suggestions already handled by
`update-flake-lock`'s review process, `ObsoleteSdkInt` on
`mipmap-anydpi-v26`, `MonochromeLauncherIcon`) were left as-is: cosmetic,
not worth a round-trip.

**`kotlinx-coroutines-core` imported directly in `RuleEngine.kt`** —
resolved **2026-09-07**. `libs.versions.toml` now declares it explicitly,
sharing a `kotlinxCoroutines` version ref with `-test` since the two must
stay in lockstep, and `app/build.gradle.kts` has the matching
`implementation` line. `domain/`'s only non-stdlib import no longer rests
on a transitive graph nothing in the build files named.

**`RuleEngine`'s backreference-regex gap** — resolved **2026-09-05**,
Phase 3. `RuleValidator` rejects backreferences at rule-save time and
defensively in `RuleEngine.compileOrNull`; matching runs inside
`InterruptibleCharSequence` + `runInterruptible`, so a non-backreference
timeout now actually stops the thread instead of leaking it. Full
reasoning, including the RE2J alternative, in
[history-4llm.md](history-4llm.md).

**`SafeLog.decision`'s `ruleId` was always `null` in practice** —
resolved **2026-09-07**. `Decision` now carries a common `ruleId: String?`
populated by `RuleEngine.toDecision` and the match-timeout `Suppress`,
threaded through `SecretDetector`'s downgrades so a downgraded decision
keeps the id of the rule that originally matched.
`NotificationTtsListener.route()` passes `decision.ruleId`.
`RuleEngineTest` gained two cases covering the matched/unmatched sides.

**Third-party actions SHA-pinned, and CodeQL added** (#31, #30) —
resolved **2026-09-08**. The three `@main` refs
(`DeterminateSystems/nix-installer-action` in both `check.yml` and
`update-flake-lock.yml`, `DeterminateSystems/update-flake-lock` in the
latter) now pin to the commit SHA behind their current release tag, with
the tag in a trailing comment; bump deliberately, the same policy
`libs.versions.toml` states for Gradle deps. `update-flake-lock.yml`
mattered most — it holds `contents:write` and `pull-requests:write`.
`actions/checkout` and `github/codeql-action` were deliberately **left on
major-version tags**: they're first-party GitHub actions, and
`actions/checkout` is on `v4` here while `v7` is current, so SHA-pinning
it would have smuggled a version bump into a security change. New
`codeql.yml` runs CodeQL over `java-kotlin` with `build-mode: manual` —
autobuild looks for a committed `gradlew`, which this repo deliberately
doesn't have. Verified with `actionlint` across all three workflows. Much
of this was first worked out on the `ci-hardening` branch (PR #17,
2026-09-06) and re-derived against current `main` rather than rebased.

**§4.5's user-editable OTP keyword list** — noticed **2026-09-08** while
rewriting `AGENTS.md`, resolved the same day. `Settings` carries
`otpKeywords: Set<String>? = null`, backed by `OTP_KEYWORDS_KEY` in
`SettingsRepository` with a setter. `SecretDetectorHolder` mirrors
`RuleEngineHolder` and rebuilds on every `settings.otpKeywords` emission
(null = defaults). Wired in `AppContainer`, called from
`NotificationTtsListener`. `SettingsScreen` shows the list with
add/remove and reset-to-defaults; an empty list shows a warning (keyword
detection off, hardcoded floor still holds). `SecretDetectorTest` gained
empty-list and floor-with-empty-list cases;
`SecretDetectorHolderTest` verifies rebuild-on-emission.

## Open, with full context

**§4.4's "mark the rule as failing in the UI" is not built** (#48).
Noticed **2026-09-08** while auditing `AGENTS.md` against the code.
`RuleEngine` already reports both halves of the requirement — a pattern
that won't compile, and a match hitting the 100ms timeout — through
`onRuleFailure`, and `RuleEngineTest` covers both. The missing hop is the
last one: `AppContainer` wires `onRuleFailure` to `SafeLog.error`, so the
failure lands in logcat and never reaches the rule editor or list.
`RuleEngine.kt`'s doc comment has been honest about this all along ("for
a future settings screen to surface"); `AGENTS.md` §4.4 was the copy that
read as though it were done, corrected the same day. Genuinely open: a
rule silently never matching, reason visible only under `adb logcat`, is
the failure mode §4.8 rejects for TTS engines ("Handle init failure ...
with a visible error in the UI, not a silent no-op") applied to another
subsystem.

**§4.10's "reset TTS state" on listener disconnect is not built** (#49).
Same audit, **2026-09-08**. `onListenerDisconnected` calls
`SafeLog.lifecycle("listener disconnected")` and returns; nothing stops
the engine or drains the queue, so revoking notification access — or the
system rebinding the service, which it does unpredictably, the reason
§4.10 exists — leaves an in-flight utterance playing to completion. Small
to fix (`container.speechQueue.stopCurrent()` is already the
mid-utterance stop path `AudioBecomingNoisyReceiver` uses) but it is a
behaviour change, so it wants its own change rather than riding along
with a docs correction. §4.10 corrected the same day.

**On-device criteria, by phase** (#29 is the tracked form). See
[status-4llm.md](status-4llm.md) for what each emulator session actually
covered and [testing-4llm.md](testing-4llm.md) for the criteria as
originally written. The standing caveat: **no physical Android device has
been used against this app at any point**, so OEM battery-killing, a real
cellular radio, and real MDM enrollment remain untested regardless of
what the emulator showed. `SECURITY.md` §4 holds the repro steps and
every caveat.

**§8's OEM background-killing clause** (#27) is not open so much as
unreachable: nothing to investigate until the listener has run
unattended on real hardware long enough to observe a kill.
[testing.md](testing.md) has the concrete day-long repro.
