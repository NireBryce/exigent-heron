# Status

## Contents

- [Phase status](#phase-status)
- [How "Verified" is earned](#how-verified-is-earned)

The nixos-configs equivalent of this page is `wiki/hosts.md` — what's
actually been switched/booted vs. only evaluated, because "the config
would build" and "this is running on the real machine" are different
claims that drift apart if nothing tracks which one is true. Same idea
here, one axis simpler: no fleet, just phases. `BUILD_PLAN.md` specifies
what each phase requires and what "done" means for it; this page tracks
whether that's actually true *right now*, dated and re-derived rather than
assumed.

## Phase status

| Phase | Spec (`BUILD_PLAN.md`) | Built | Verified |
|---|---|---|---|
| 0 — Skeleton | Scaffold, manifest hardening, `SafeLog`, empty `AppContainer` | Yes | **2026-09-05**: `gradle assembleDebug` → `BUILD SUCCESSFUL`. Merged debug manifest (`processDebugMainManifest`) inspected directly — no `INTERNET` permission present, only AGP's own auto-added `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. |
| 1 — Domain core | `NotificationPayload`, `Rule`, `Decision`, `RuleEngine`, `SecretDetector`, `Deduplicator` + tests, debug fake-notification injector | Yes | **2026-09-05**: `gradle testDebugUnitTest` → `BUILD SUCCESSFUL`, 31 tests, 0 failures (1 `NotificationPayloadTest`, 6 `DeduplicatorTest` — the five §4.3 cases plus the side-effect contract, 13 `SecretDetectorTest` — the four §4.5 realistic strings plus the floor, the visibility gate, and the downgrade-mechanics cases below, 11 `RuleEngineTest`). `gradle assembleDebug` also re-verified green with the new `debug/` source set present. See [architecture.md](architecture.md) for what actually landed and [history.md](history.md) for the two decisions this phase made that `AGENTS.md` doesn't narrate. |
| 2 — Listener + speech | `NotificationTtsListener`, `NotificationExtractor`, `SpeechQueue`, `AndroidTtsEngine`, `AudioFocusManager` | Yes | **2026-09-05**: `gradle testDebugUnitTest` → `BUILD SUCCESSFUL`, 53 tests, 0 failures — re-run 13 times in a row clean after fixing a real test-isolation race (see [traps-and-skills.md](traps-and-skills.md)). `gradle assembleDebug` green; merged debug manifest re-read directly — still no `INTERNET`, `BIND_NOTIFICATION_LISTENER_SERVICE` present on the new service as specified. `android.util.Log` re-confirmed present in exactly one file. **Not yet verified**: the phase's actual on-device acceptance criteria (a notification spoken once, three duplicates in 10s still once, music duck-and-recover) — no device available this session; see [testing.md](testing.md) for what to run once one is. |
| 3 — Persistence + rules UI | `SettingsRepository`, `RuleRepository`, DataStore wiring, rule editor, app picker | Yes | **2026-09-05**: `gradle testDebugUnitTest` → `BUILD SUCCESSFUL`, 73 tests, 0 failures (new: `RuleValidatorTest`, `RuleCodecTest`, `RuleEngineHolderTest`, `InterruptibleCharSequenceTest`; `RuleEngineTest`'s ReDoS test rewritten — see [history.md](history.md)). `gradle assembleDebug` green; merged debug manifest re-read directly — new `<queries>` block present (the installed-app picker's `PackageManager.queryIntentActivities` needs it under Android 11+'s package-visibility rules), still no `INTERNET`. **Not yet verified**: this phase's actual on-device acceptance criteria (a rule added via the UI survives force-stop, an invalid pattern errors at save time rather than crashing later) — no device available this session; see [testing.md](testing.md). |
| 4 — Gates and polish | `OutputRouteGate`, lock/DND gates, engine picker, queue collapse, announce-only, templates | No | — |
| 5 — Hardening pass | Logging-violation grep, release-injector check, `SECURITY.md` checklist | No | — |

## How "Verified" is earned

Per skill [`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md): a
"Verified" cell needs a date and the actual command/method that was run
*this session* — not "should pass," not carried over from a previous
session's claim without re-running it. `BUILD_PLAN.md`'s own acceptance
criteria are the check to run per phase; quote or summarize the real
output, not a restatement of what the criteria say should happen. If a
phase's acceptance criteria can't be re-run right now (no device
available for an on-device criterion, say), leave the cell blank or mark
it `unconfirmed since <date>` rather than reusing an old "Verified" as if
it still holds.
