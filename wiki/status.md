# Status

_Last modified: 2026-09-08_

## Contents

- [Short version](#short-version)
- [Phase status](#phase-status)
- [Instrumented and end-to-end checks](#instrumented-and-end-to-end-checks)
- [What is still unproven](#what-is-still-unproven)
- [How "Verified" is earned](#how-verified-is-earned)

What is actually built and actually run, as opposed to what is specified.

## Short version

- **All six phases are built and verified.** The app works.
- **118 JVM unit tests + 19 instrumented tests, all passing** as of
  2026-09-08.
- **No physical Android device has ever run this app.** Every on-device
  claim below is an emulator claim.

## Phase status

| Phase | Spec | Built | Verified |
|---|---|---|---|
| 0 — Skeleton | Scaffold, manifest hardening, `SafeLog`, empty `AppContainer` | Yes | **2026-09-05** — build green; merged debug manifest read directly, no `INTERNET`. |
| 1 — Domain core | `NotificationPayload`, `Rule`, `Decision`, `RuleEngine`, `SecretDetector`, `Deduplicator` + tests, debug injector | Yes | **2026-09-05** — 31 tests green; build green with the new `debug/` source set. |
| 2 — Listener + speech | `NotificationTtsListener`, `NotificationExtractor`, `SpeechQueue`, `AndroidTtsEngine`, `AudioFocusManager` | Yes | **2026-09-05** — 53 tests green (after fixing a real test-isolation race). On-device criteria automated and passing **2026-09-08** via `just test-acceptance`, except duck-and-recover, which needs ears. |
| 3 — Persistence + rules UI | `SettingsRepository`, `RuleRepository`, DataStore wiring, rule editor, app picker | Yes | **2026-09-05** — 73 tests green; merged manifest re-read, `<queries>` present, still no `INTERNET`. On-device criteria (rule survives force-stop, invalid pattern errors at save) still unrun. |
| 4 — Gates and polish | `OutputRouteGate`, lock/DND gates, engine picker, queue collapse, announce-only, templates | Yes | **2026-09-06** — 86 tests green. **2026-09-07** on the emulator: headset-only blocking confirmed against the real audio path. Found and fixed the same session: the route gate was checked only at enqueue. Engine picker still unrun. |
| 5 — Hardening pass | Logging-violation grep, release-injector check, `SECURITY.md` checklist | Yes | **2026-09-06** — release build clean, no debug classes in the release output, merged release manifest read directly. **2026-09-07** on the emulator: locked-device and in-call confirmed. No source changes were needed. |

Full evidence for every cell — the exact commands, what each output
showed, and what it did not prove — is in
[status-4llm.md](status-4llm.md). `SECURITY.md` holds the Phase 5 trail.

## Instrumented and end-to-end checks

These cut across phases rather than belonging to one. Both were run on
the `nix develop` emulator (`dev`, API 37, `google_apis` x86_64).

| Check | Command | Verified |
|---|---|---|
| Instrumented tests | `just test-instrumented` | **2026-09-08**: 19 tests, 0 failures, 0 skipped |
| Listener acceptance | `just test-acceptance` | **2026-09-08**: PASS, three consecutive runs |

The acceptance script covers Phase 2's "spoken once" and "three
duplicates still once" against real posted notifications. Duck-and-recover
and headset routing need ears and stay manual — see
[testing.md](testing.md).

## What is still unproven

Worth knowing before you claim something works:

- **Any physical device.** OEM battery-killing, a real cellular radio,
  and real MDM enrollment are untested; `SECURITY.md` §4 has the caveats.
- **The TTS engine picker** actually switching engines — code review only.
- **Duck-and-recover** with real music — needs ears.
- **A populated Bluetooth device list** — the emulator has no bonded
  devices, so only the unit tests cover the per-device rows.
- **Work-profile behaviour** against a fully provisioned enterprise
  profile; only a non-DPC secondary profile was checked.

[open-threads.md](open-threads.md) tracks these alongside the known gaps.

## How "Verified" is earned

A "Verified" cell needs a **date** and the **actual command run this
session**. Not "should pass". Not carried over from a previous session
without re-running it.

If a criterion can't be re-run right now — no device available, say —
leave the cell blank or mark it `unconfirmed since <date>` rather than
reusing an old "Verified" as if it still held.

Skill [`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md) is the
general form of this rule.
