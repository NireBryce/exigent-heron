# Status

## Contents

- [Phase status](#phase-status)
- [How "Verified" is earned](#how-verified-is-earned)

The nixos-configs equivalent of this page is `wiki/hosts.md` — what's
actually been switched/booted vs. only evaluated, because "the config
would build" and "this is running on the real machine" are different
claims that drift apart if nothing tracks which one is true. Same idea
here, one axis simpler: no fleet, just phases. Each phase's own spec
(originally `BUILD_PLAN.md`, since removed now that all six are built and
verified — see [history.md](history.md)) specified what it required and
what "done" meant for it; this page tracks whether that's actually true
*right now*, dated and re-derived rather than assumed.

## Phase status

| Phase | Spec | Built | Verified |
|---|---|---|---|
| 0 — Skeleton | Scaffold, manifest hardening, `SafeLog`, empty `AppContainer` | Yes | **2026-09-05**: `gradle assembleDebug` → `BUILD SUCCESSFUL`. Merged debug manifest (`processDebugMainManifest`) inspected directly — no `INTERNET` permission present, only AGP's own auto-added `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. |
| 1 — Domain core | `NotificationPayload`, `Rule`, `Decision`, `RuleEngine`, `SecretDetector`, `Deduplicator` + tests, debug fake-notification injector | Yes | **2026-09-05**: `gradle testDebugUnitTest` → `BUILD SUCCESSFUL`, 31 tests, 0 failures (1 `NotificationPayloadTest`, 6 `DeduplicatorTest` — the five §4.3 cases plus the side-effect contract, 13 `SecretDetectorTest` — the four §4.5 realistic strings plus the floor, the visibility gate, and the downgrade-mechanics cases below, 11 `RuleEngineTest`). `gradle assembleDebug` also re-verified green with the new `debug/` source set present. See [architecture.md](architecture.md) for what actually landed and [history.md](history.md) for the two decisions this phase made that `AGENTS.md` doesn't narrate. |
| 2 — Listener + speech | `NotificationTtsListener`, `NotificationExtractor`, `SpeechQueue`, `AndroidTtsEngine`, `AudioFocusManager` | Yes | **2026-09-05**: `gradle testDebugUnitTest` → `BUILD SUCCESSFUL`, 53 tests, 0 failures — re-run 13 times in a row clean after fixing a real test-isolation race (see [traps-and-skills.md](traps-and-skills.md)). `gradle assembleDebug` green; merged debug manifest re-read directly — still no `INTERNET`, `BIND_NOTIFICATION_LISTENER_SERVICE` present on the new service as specified. `android.util.Log` re-confirmed present in exactly one file. **Not yet verified**: the phase's actual on-device acceptance criteria (a notification spoken once, three duplicates in 10s still once, music duck-and-recover) — no device available this session; see [testing.md](testing.md) for what to run once one is. |
| 3 — Persistence + rules UI | `SettingsRepository`, `RuleRepository`, DataStore wiring, rule editor, app picker | Yes | **2026-09-05**: `gradle testDebugUnitTest` → `BUILD SUCCESSFUL`, 73 tests, 0 failures (new: `RuleValidatorTest`, `RuleCodecTest`, `RuleEngineHolderTest`, `InterruptibleCharSequenceTest`; `RuleEngineTest`'s ReDoS test rewritten — see [history.md](history.md)). `gradle assembleDebug` green; merged debug manifest re-read directly — new `<queries>` block present (the installed-app picker's `PackageManager.queryIntentActivities` needs it under Android 11+'s package-visibility rules), still no `INTERNET`. **Not yet verified**: this phase's actual on-device acceptance criteria (a rule added via the UI survives force-stop, an invalid pattern errors at save time rather than crashing later) — no device available this session; see [testing.md](testing.md). |
| 4 — Gates and polish | `OutputRouteGate`, lock/DND gates, engine picker, queue collapse, announce-only, templates | Yes | **2026-09-06**: `gradle testDebugUnitTest` → `BUILD SUCCESSFUL`, 86 tests, 0 failures (new: `OutputRouteGateTest`, `LockStateGateTest`, plus `SpeechQueueTest`'s DND-skip and burst-collapse cases). `gradle assembleDebug` green. Merged debug manifest re-read directly — still no `INTERNET`. Announce-only mode and templates were already done in Phase 1 (`Rule.template`/`RuleAction.ANNOUNCE_ONLY`, `RuleEngine.render`) — this phase didn't re-touch them, per `AGENTS.md`'s note that Phase 4's own bullets for them were already satisfied. **On-device acceptance criteria verified 2026-09-07** on the `nix develop` emulator (still not a physical device — see `SECURITY.md` §4): headset-only blocking confirmed via the real `AudioManager`/notification-listener path, engine picker not separately re-tested this pass. Also found and fixed the same session: `OutputRouteGate.allows()` was only checked once at enqueue time, not per-utterance — a headset disconnecting while items were still queued would fall back to the speaker. `SpeechQueue` now re-checks it before every utterance and also stops immediately on `AudioManager.ACTION_AUDIO_BECOMING_NOISY` (a route change *during* playback, which the per-item re-check alone can't see) — see `AGENTS.md` §4.7 and the new `AudioBecomingNoisyReceiver`. **Also 2026-09-07**: per-device Bluetooth allow/deny added (`AGENTS.md` §4.9) — off by default, mutually-exclusive Allow/Deny per paired device, this app's first and only runtime permission (`BLUETOOTH_CONNECT`, requested only when the feature is turned on). Verified on the emulator: permission-grant flow, toggle-on/off persistence, and the bonded-device list rendering (empty on this emulator — no bonded devices to actually confirm a populated row against; that part is unit-tested only, see `OutputRouteGateTest`'s per-device cases). |
| 5 — Hardening pass | Logging-violation grep, release-injector check, `SECURITY.md` checklist | Yes | **2026-09-06**: `android.util.Log` re-confirmed present in exactly one file (`SafeLog.kt`); its own API surface re-read, no free-text method. `gradle :app:compileReleaseKotlin` → `BUILD SUCCESSFUL`, 139 classes in the release output, none from the `debug/` source set, no `FakeNotifications.class`. `gradle :app:processReleaseMainManifest` → merged release manifest read directly: no `INTERNET`, `allowBackup="false"`, `fullBackupContent="false"`, `dataExtractionRules` present, listener service the only exported app-declared non-launcher component with the right permission (one AndroidX-injected exception found and documented — `androidx.profileinstaller.ProfileInstallReceiver`, gated by the signature-level `DUMP` permission, not this app's code). `gradle testDebugUnitTest --rerun-tasks` → `BUILD SUCCESSFUL`, 86 tests, 0 failures, re-verified after the phase's checks. No source changes were needed — see [`SECURITY.md`](../SECURITY.md) for the full evidence trail. **Locked-device and in-call verified 2026-09-07** on the emulator (real `KeyguardManager`/`AudioManager` state); work-profile checked too, but only against a non-DPC secondary profile. **Still not a physical device** for any of it — see `SECURITY.md` §4 and [testing.md](testing.md) for exactly what was run and its caveats. |

## How "Verified" is earned

Per skill [`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md): a
"Verified" cell needs a date and the actual command/method that was run
*this session* — not "should pass," not carried over from a previous
session's claim without re-running it. Each phase's own acceptance
criteria (this table's Spec column, and `history.md` for the original
`BUILD_PLAN.md` wording where it matters) are the check to run per phase;
quote or summarize the real output, not a restatement of what the
criteria say should happen. If a
phase's acceptance criteria can't be re-run right now (no device
available for an on-device criterion, say), leave the cell blank or mark
it `unconfirmed since <date>` rather than reusing an old "Verified" as if
it still holds.
