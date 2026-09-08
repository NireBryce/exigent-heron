# Overview

## Contents

- [What this is](#what-this-is)
- [The three-document system](#the-three-document-system)
- [The pipeline](#the-pipeline)
- [Components worth reading first](#components-worth-reading-first)
- [Build and tooling](#build-and-tooling)
- [See also](#see-also)

Orientation for someone — human or agent — meeting this repo cold: what
the app is, which document answers which kind of question, and the path a
notification actually takes through the code.

This page is a map, not a second copy of the territory. Per
[styleguide.md](styleguide.md)'s "index over restatement," it points at
the real source for every fact it mentions rather than restating it —
most of what's summarized in a sentence here has a fuller treatment in a
class's own doc comment, which is the copy that stays correct. Where a
number would rot (test counts, phase status, line counts), it links to
[status.md](status.md) instead of freezing one.

## What this is

A personal, sideloaded Android app that reads selected notifications
aloud via on-device TTS, with user-controlled filtering rules. Package
`net.breadthcharge.exigentheron`, single Gradle module `:app`, Kotlin +
Compose. [`AGENTS.md`](../AGENTS.md) §1 has the in-scope and
explicitly-out-of-scope lists — the latter matters more than it usually
does here, since it rules out cloud sync, LLM summarization, notification
history, and reply actions by name.

The defining constraint, stated three separate ways in `AGENTS.md` §0:
**never log notification content, never request `INTERNET`, no
telemetry.** Most of the structural decisions downstream are in service
of that rather than of testability or elegance on their own terms.

Licensed Apache-2.0 as of 2026-09-07 ([LICENSE](../LICENSE)), chosen over
MIT for the express patent grant and because every shipped runtime
dependency already carries it — see [history.md](history.md) for the full
reasoning and [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) for what
the APK actually ships with.

## The three-document system

Three layers, deliberately not merged, because they answer different
questions and go stale at different rates:

- [`AGENTS.md`](../AGENTS.md) — the standing spec: *what* to build, in
  numbered sections (§4.1–§4.10 are the per-component specs everything
  else cites). Written as an instruction to whichever agent is working,
  including its own §0 instruction to say when it looks wrong rather than
  build around it silently.
- [`BUILD_PLAN.md`](../BUILD_PLAN.md) — *what order*: six phases, each
  with its own acceptance criteria and a rule against starting phase N+1
  before N passes.
- `wiki/` — what's *actually true right now*, which neither of the above
  tracks. [README.md](README.md) explains why that's a separate layer at
  all; [status.md](status.md) holds the phase table, where a "Verified"
  cell requires a date and the literal command run that session.

[README.md](../README.md) is upfront that most of the implementation,
tests, and this wiki are written by LLM coding agents under human
direction and review. [history.md](history.md) records decisions made
along the way that `AGENTS.md` doesn't narrate;
[traps-and-skills.md](traps-and-skills.md) records mistakes actually made
and caught, each paired with the skill holding its general form.

## The pipeline

One path, specified in `AGENTS.md` §3's data-flow diagram and implemented
literally in
[`NotificationTtsListener.route()`](../app/src/main/java/net/breadthcharge/exigentheron/listener/NotificationTtsListener.kt):

```
onNotificationPosted(sbn)
  → NotificationExtractor.extract()   → NotificationPayload?
  → Deduplicator.isDuplicate()        → drop
  → RuleEngine.evaluate()             → Decision
  → SecretDetector.scan()             → possibly downgrade
  → OutputRouteGate / LockStateGate   → drop
  → SpeechQueue.enqueue()
```

The listener is routing only — each step is exactly one call, no
branching logic of its own — and hops off the binder thread before
`route()`, since rule matching can spend its full timeout budget and
`onNotificationPosted` has to return promptly.

**The critical structural rule** (`AGENTS.md` §3): `domain/` has zero
Android imports. That's what makes the project testable at all — plain
JVM unit tests, no Robolectric, no emulator, no instrumentation.
Everything else is framework glue that's meant to hold no logic worth
testing. Where logic was *specified* inside an Android class but didn't
actually need Android, it was split out anyway —
`listener/NotificationExtractionPolicy.kt`, `domain/TextSanitizer.kt`,
`domain/ContentHash.kt`. All three are recorded as deliberate deviations,
with reasons, in [architecture.md](architecture.md)'s "Deviations"
section.

## Components worth reading first

Each of these has a doc comment that is the real explanation; the point
of listing them here is which ones repay reading directly.

- [`RuleEngine.kt`](../app/src/main/java/net/breadthcharge/exigentheron/domain/RuleEngine.kt)
  — default-deny, first matching enabled rule by priority wins. Its class
  doc is the single best thing to read in this codebase: it documents a
  verified limitation of its own ReDoS mitigation (`java.util.regex` has
  no cooperative cancellation, so a bare `withTimeoutOrNull` abandons the
  caller while the thread keeps burning), how `InterruptibleCharSequence`
  closes it, and why `RuleValidator` rejects backreferences outright
  rather than relying on interruption alone.
- [`SecretDetector.kt`](../app/src/main/java/net/breadthcharge/exigentheron/domain/SecretDetector.kt)
  — can only ever *downgrade* a decision, never upgrade one. The
  mechanics its doc spells out, which `AGENTS.md` §4.5 states as a rule
  without them: a `Speak` → `AnnounceOnly` downgrade must not carry the
  original text forward, since that text is exactly what looked like a
  secret.
- [`SpeechQueue.kt`](../app/src/main/java/net/breadthcharge/exigentheron/speech/SpeechQueue.kt)
  — single-consumer actor over a bounded channel. Takes its
  audio-focus/in-call/DND/route checks as function references rather than
  Android objects, which is what keeps it JVM-testable. Three layered
  fixes live here, each closing a gap the previous one couldn't see —
  the doc comment says which is which.
- [`SafeLog.kt`](../app/src/main/java/net/breadthcharge/exigentheron/SafeLog.kt)
  — the only file permitted to touch `android.util.Log` (`AGENTS.md`
  §4.6), with deliberately no arbitrary-string overload. Its logcat tag
  is `"ExigentHeron"`, not a class name — worth knowing before scoping an
  `adb logcat`, see [testing.md](testing.md).

## Build and tooling

- **Nix flake** pins the JDK, Kotlin, Gradle, and the Android SDK.
  **No `gradlew` is committed, by design** — the dev shell puts `gradle`
  on `PATH` instead. `direnv allow`, then `gradle assembleDebug` /
  `gradle testDebugUnitTest`; see [README.md](../README.md) and
  [flake.nix](../flake.nix).
- [**CI**](../.github/workflows/check.yml) runs inside `nix develop` so
  versions can't drift from local, and includes the hard grep `AGENTS.md`
  §4.6 asks for by name: `android.util.Log` must appear in exactly one
  file. Lint runs for both variants and its SARIF goes to code scanning.
- [**`.claude/hooks/`**](../.claude/hooks/) — a git guard (destructive
  git actions, and direct commit/merge/push to `main`) plus the signing
  and log-hygiene guards. Each is a mechanical backstop for a slip, not
  the policy itself: the rules live in `AGENTS.md` §0 and the skills
  below as plain files any agent can read, whether or not its harness
  fires hooks.
- [**`.claude/skills/`**](../.claude/skills/) — the repo-local skills.
  [`submit-a-pr`](../.claude/skills/submit-a-pr/SKILL.md) is the one to
  read before landing anything: every change goes via a branch and a PR,
  never a direct commit to `main`.
- [**`wiki/scripts/check_wiki.py`**](scripts/check_wiki.py) — mechanical
  checks of links, anchors, `## Contents` blocks, skill names, and
  claimed phase status against the real tree, since nothing about
  `gradle build` reads prose.

Both the spec and this wiki lean on a sibling `nixos-configs` repo as
their model — the wiki structure, the git-guard hook, `check_wiki.py`,
and several skills are adaptations of its equivalents, each with its
deliberate differences documented in place rather than silently applied.

## See also

- [README.md](README.md) — the wiki's own index, and why this link layer
  exists separately from `AGENTS.md`.
- [architecture.md](architecture.md) — the real package layout as it
  stands, against `AGENTS.md` §3's target tree.
- [status.md](status.md) — what's actually built and verified, dated,
  versus only specified.
- [testing.md](testing.md) — how to actually run and exercise the app.
- [styleguide.md](styleguide.md) — the house rules this page follows,
  including the "index over restatement" one it's most at risk of
  breaking.
