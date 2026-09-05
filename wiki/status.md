# Status

## Contents

- [Phase status](#phase-status)
- [How "Verified" is earned](#how-verified-is-earned)

The nixos-configs equivalent of this page is `wiki/hosts.md` — what's
actually been switched/booted vs. only evaluated, because "the config
would build" and "this is running on the real machine" are different
claims that drift apart if nothing tracks which one is true. Same idea
here, one axis simpler: no fleet, just phases. `AGENTS.md` §6 specifies
what each phase requires and what "done" means for it; this page tracks
whether that's actually true *right now*, dated and re-derived rather than
assumed.

## Phase status

| Phase | Spec (`AGENTS.md` §6) | Built | Verified |
|---|---|---|---|
| 0 — Skeleton | Scaffold, manifest hardening, `SafeLog`, empty `AppContainer` | Yes | **2026-09-05**: `gradle assembleDebug` → `BUILD SUCCESSFUL`. Merged debug manifest (`processDebugMainManifest`) inspected directly — no `INTERNET` permission present, only AGP's own auto-added `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. |
| 1 — Domain core | `NotificationPayload`, `Rule`, `Decision`, `RuleEngine`, `SecretDetector`, `Deduplicator` + tests, debug fake-notification injector | No | N/A — **2026-09-05**: `gradle testDebugUnitTest` → `NO-SOURCE` (confirmed no test sources exist yet, consistent with "not built" rather than "built but untested"). |
| 2 — Listener + speech | `NotificationTtsListener`, `NotificationExtractor`, `SpeechQueue`, `AndroidTtsEngine`, `AudioFocusManager` | No | — |
| 3 — Persistence + rules UI | `SettingsRepository`, `RuleRepository`, DataStore wiring, rule editor, app picker | No | — |
| 4 — Gates and polish | `OutputRouteGate`, lock/DND gates, engine picker, queue collapse, announce-only, templates | No | — |
| 5 — Hardening pass | Logging-violation grep, release-injector check, `SECURITY.md` checklist | No | — |

## How "Verified" is earned

Per skill [`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md): a
"Verified" cell needs a date and the actual command/method that was run
*this session* — not "should pass," not carried over from a previous
session's claim without re-running it. `AGENTS.md` §6's own acceptance
criteria are the check to run per phase; quote or summarize the real
output, not a restatement of what the criteria say should happen. If a
phase's acceptance criteria can't be re-run right now (no device
available for an on-device criterion, say), leave the cell blank or mark
it `unconfirmed since <date>` rather than reusing an old "Verified" as if
it still holds.
