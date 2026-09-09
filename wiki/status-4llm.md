# Status (dense)

_Last modified: 2026-09-08_

_Text is llm generated with occasional human review_

## Contents

- [Purpose](#purpose)
- [Full verification evidence by phase](#full-verification-evidence-by-phase)
- [Cross-cutting checks](#cross-cutting-checks)
- [What each verification did not prove](#what-each-verification-did-not-prove)
- [Test-count history](#test-count-history)

Companion to [status.md](status.md). The article carries the current
summary table; this page carries the full evidence trail per cell, kept
so a later reader can tell what a "Verified" actually covered rather than
re-deriving it from a one-line cell.

## Purpose

`AGENTS.md` states requirements; it deliberately does not track whether
they hold right now. "The config would build" and "this is running on the
real machine" are different claims that drift apart if nothing tracks
which one is true — the same idea `NireBryce/nixos-configs`'s
`wiki/hosts.md` applies to boot status, one axis simpler here (phases,
not a fleet).

Each phase's own acceptance criteria originally lived in `BUILD_PLAN.md`,
removed **2026-09-08** once all six phases were built and verified. They
survive as [status.md](status.md)'s Spec column, [testing.md](testing.md)'s
manual scripts, and code comments citing "Phase N" directly. The phase
rule itself (build and install at each boundary; don't start N+1 before
N's criteria pass) moved into `AGENTS.md` §6.

`check_wiki.py phases` parses [status.md](status.md)'s table with a regex
requiring exactly `| <n> — ... | spec | Yes|No | verified |`. It checks
each phase claimed `Yes` against `PHASE_FILES`, a hand-maintained
filename map. A phase claimed `No` whose files all exist is a REVIEW
finding, not a failure — files existing does not prove acceptance
criteria pass.

## Full verification evidence by phase

**Phase 0 — Skeleton. 2026-09-05.**
`gradle assembleDebug` → `BUILD SUCCESSFUL`. Merged debug manifest
(`gradle :app:processDebugMainManifest`) inspected *directly* — no
`INTERNET` permission, only AGP's own auto-added
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Reading the merged output
rather than trusting task success is the point: a merge can succeed with
an unwanted permission pulled in from a dependency.

**Phase 1 — Domain core. 2026-09-05.**
`gradle testDebugUnitTest` → `BUILD SUCCESSFUL`, 31 tests, 0 failures:
1 `NotificationPayloadTest`, 6 `DeduplicatorTest` (the five §4.3 cases
plus the side-effect contract), 13 `SecretDetectorTest` (the four §4.5
realistic strings plus the floor, the visibility gate, and the
downgrade-mechanics cases), 11 `RuleEngineTest`.
`gradle assembleDebug` re-verified green with the new `debug/` source set
present.

**Phase 2 — Listener + speech. 2026-09-05.**
`gradle testDebugUnitTest` → `BUILD SUCCESSFUL`, 53 tests, 0 failures —
**re-run 13 times consecutively clean** after fixing a real
test-isolation race (the `scope.cancel()`-without-`join()` bug; the
original showed it roughly 1 run in 3). `gradle assembleDebug` green.
Merged debug manifest re-read: still no `INTERNET`,
`BIND_NOTIFICATION_LISTENER_SERVICE` present on the new service.
`android.util.Log` re-confirmed in exactly one file.
On-device criteria were unverified that session (no device). Automated
and passing **2026-09-08** via `scripts/listener-acceptance.sh`, except
duck-and-recover.

**Phase 3 — Persistence + rules UI. 2026-09-05.**
`gradle testDebugUnitTest` → `BUILD SUCCESSFUL`, 73 tests, 0 failures.
New: `RuleValidatorTest`, `RuleCodecTest`, `RuleEngineHolderTest`,
`InterruptibleCharSequenceTest`; `RuleEngineTest`'s ReDoS test rewritten
(the pattern it used is now rejected at compile time, making the old
timeout path unreachable for that input).
`gradle assembleDebug` green. Merged debug manifest re-read: new
`<queries>` block present (Android 11+ package visibility, needed by the
installed-app picker's `queryIntentActivities`), still no `INTERNET`.
On-device criteria unverified — still are.

**Phase 4 — Gates and polish. 2026-09-06 + 2026-09-07.**
`gradle testDebugUnitTest` → `BUILD SUCCESSFUL`, 86 tests, 0 failures.
New: `OutputRouteGateTest`, `LockStateGateTest`, plus `SpeechQueueTest`'s
DND-skip and burst-collapse cases. `gradle assembleDebug` green; merged
debug manifest re-read, still no `INTERNET`.
Announce-only mode and templates were **already done in Phase 1**
(`Rule.template` / `RuleAction.ANNOUNCE_ONLY` / `RuleEngine.render`) and
exposed in the editor since Phase 3 — confirmed by reading both files
rather than trusting the phase list. Phase 4 did not re-touch them.
**2026-09-07, emulator**: headset-only blocking confirmed via the real
`AudioManager` / notification-listener path. Engine picker not
re-tested this pass.
**Found and fixed the same session**: `OutputRouteGate.allows()` was
checked only at enqueue, not per utterance — a headset disconnecting
while items were queued would fall back to the speaker. `SpeechQueue` now
re-checks before every utterance and stops immediately on
`ACTION_AUDIO_BECOMING_NOISY` (the route change *during* playback, which
a per-item re-check cannot see). See §4.7 and
`AudioBecomingNoisyReceiver`.
**Also 2026-09-07**: per-device Bluetooth allow/deny (§4.9) — off by
default, mutually exclusive per device, the app's first and only runtime
permission (`BLUETOOTH_CONNECT`, requested only when the feature is
enabled). Emulator-verified: permission-grant flow, toggle persistence,
bonded-device list rendering — **empty on that emulator**, so a populated
row was never confirmed; that part is unit-tested only
(`OutputRouteGateTest`'s per-device cases).

**Phase 5 — Hardening pass. 2026-09-06 + 2026-09-07.**
`android.util.Log` re-confirmed in exactly one file (`SafeLog.kt`); its
API surface re-read, no free-text method.
`gradle :app:compileReleaseKotlin` → `BUILD SUCCESSFUL`, 139 classes in
the release output, none from the `debug/` source set, no
`FakeNotifications.class`.
`gradle :app:processReleaseMainManifest` → merged release manifest read
directly: no `INTERNET`, `allowBackup="false"`,
`fullBackupContent="false"`, `dataExtractionRules` present, the listener
service the only exported app-declared non-launcher component and with
the right permission. One AndroidX-injected exception found and
documented: `androidx.profileinstaller.ProfileInstallReceiver`, gated by
the signature-level `DUMP` permission, not this app's code.
`gradle testDebugUnitTest --rerun-tasks` → `BUILD SUCCESSFUL`, 86 tests,
0 failures.
**No source changes were needed.** Full evidence trail in
[`SECURITY.md`](../SECURITY.md).
**2026-09-07, emulator**: locked-device and in-call verified against real
`KeyguardManager`/`AudioManager` state. Work-profile checked, but only
against a **non-DPC secondary profile**, not a provisioned enterprise
one.

## Cross-cutting checks

Added **2026-09-08**, separate from the phase table because they cut
across phases. Both on the `nix develop` emulator (`dev`, API 37,
`google_apis` x86_64).

| Check | Command | Evidence |
|---|---|---|
| Instrumented tests | `gradle connectedDebugAndroidTest` | 19 tests, 0 failures, **0 skipped** — result XML read directly, confirming the TTS cases actually ran rather than being skipped by their `assumeTrue` guard |
| Listener acceptance | `./scripts/listener-acceptance.sh` | PASS, three consecutive runs |

The acceptance script grants notification access via
`cmd notification allow_listener` (no UI tap), posts real notifications
through the platform, and reads back `SafeLog.decision` lines. It asserts
on decision lines only — package, rule id, action, never content — and
scopes logcat to the app's own tag.

## What each verification did not prove

Enumerated because the gap between "green" and "works" is where this
repo's real bugs have lived.

- **Every on-device claim above is an emulator claim.** No physical
  Android device has run this app at any point. Untested by construction:
  OEM battery-killing, a real cellular radio, real MDM enrollment.
  `SECURITY.md` §4 holds the caveats and the exact repro steps.
- **The instrumented DataStore round-trips** do not show cross-process
  persistence: `preferencesDataStore` is a per-`Context` singleton, so a
  second repository in the same process shares the instance rather than
  re-reading the file. The manual force-stop step in
  [testing.md](testing.md) is still the only thing that shows it.
- **`assembleRelease` cannot run in CI** (no signing config there).
  `lintRelease` can, because lint analyzes without packaging or signing.
- **Green JVM tests said nothing about the output-route bug**, which
  lived in a seam every one of them passed straight through. That is the
  standing argument for `app/src/androidTest/`.
- **A green concurrency test is evidence about one machine's
  scheduling.** `SpeechQueueTest`'s burst-collapse case passed locally
  every time and failed on CI's slower runner. See
  [traps-and-skills.md](traps-and-skills.md).

## Test-count history

JVM unit tests, by the session that changed the count:

| Date | Count | What was added |
|---|---|---|
| 2026-09-05 | 31 | Phase 1 domain core |
| 2026-09-05 | 53 | Phase 2 listener + speech |
| 2026-09-05 | 73 | Phase 3 validators, codec, holder |
| 2026-09-06 | 86 | Phase 4 gates, DND skip, burst collapse |
| 2026-09-06 | 96 | `RuleFormValidatorTest` |
| 2026-09-08 | 118 | `GatePolicyTest`, `SecretDetectorHolderTest`, and the rest of the 2026-09-08 work |

Plus 19 instrumented tests as of 2026-09-08. Treat this table as history;
[status.md](status.md) carries the current number, and the actual test
run carries the truth.
