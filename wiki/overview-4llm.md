# Overview (dense)

_Last modified: 2026-09-08_

_Text is llm generated with occasional human review_

## Contents

- [Scope](#scope)
- [Constraint provenance](#constraint-provenance)
- [Pipeline, annotated](#pipeline-annotated)
- [Classes whose doc comments are the real source](#classes-whose-doc-comments-are-the-real-source)
- [Tooling inventory](#tooling-inventory)
- [Licensing](#licensing)

Companion to [overview.md](overview.md). Everything here is context the
article deliberately compresses.

## Scope

`net.breadthcharge.exigentheron` — personal sideloaded Android TTS
notification reader. Single Gradle module `:app`. Kotlin + Compose.
`AGENTS.md` §1 holds in-scope and out-of-scope lists; the out-of-scope
list matters more than usual here because it names cloud sync, LLM
summarization, notification history, and reply actions explicitly, so
each is a settled decision rather than an unexplored idea.

All six phases (0 skeleton, 1 domain core, 2 listener/speech,
3 persistence/UI, 4 gates/polish, 5 hardening) built and verified — see
[status.md](status.md) for the evidence and
[status-4llm.md](status-4llm.md) for what each verification did and did
not actually cover.

## Constraint provenance

`AGENTS.md` §0: never log notification content, never request `INTERNET`,
no telemetry. Stated three ways in that file on purpose. Mechanisms that
enforce it, and what each actually proves:

| Mechanism | Enforces | Does not prove |
|---|---|---|
| `SafeLog.kt` sole `android.util.Log` caller | no ad-hoc logging | that its three methods are called with safe arguments |
| CI grep `android.util.Log` in exactly one file | the above, mechanically | anything about `println`/stdout |
| CI grep `^import android.` under `domain/` (2026-09-08) | the purity rule | that non-`domain/` code holds no logic |
| merged-manifest re-read | no `INTERNET` in the merge result | anything about a future dependency's manifest |
| `.claude/hooks/` signing + log-hygiene guards | credentials and live notification text staying out of transcripts | anything in a harness that doesn't fire hooks |

`SafeLog`'s API is exactly `decision(pkg, ruleId, action)`,
`lifecycle(msg)`, `error(msg, t?)`. No arbitrary-string overload, by
design. Logcat tag is the literal `"ExigentHeron"` — **not** the class
name; guessing that from the class name is a mistake already made here
once, see [traps-and-skills.md](traps-and-skills.md).

Hooks are backstops, not the policy. The policy is plain files any agent
can read (`AGENTS.md` §0, the skills), because a hook only fires in a
harness that runs hooks.

## Pipeline, annotated

`NotificationTtsListener.route()`:

```
onNotificationPosted(sbn)                     [binder thread — must return promptly]
  → hop off the binder thread
  → NotificationExtractor.extract(sbn)        → NotificationPayload?  (null = dropped)
  → Deduplicator.isDuplicate(payload)         → drop if true          (LRU + TTL, injected clock)
  → RuleEngine.evaluate(payload, rules)       → Decision              (default-deny, 100ms budget)
  → SecretDetector.scan(decision)             → downgrade only        (never upgrades)
  → OutputRouteGate.allows()                  → drop if false
  → LockStateGate.allows()                    → drop if false
  → SpeechQueue.enqueue(SpeechRequest)
  → AudioFocusManager.request() → TtsEngine.speak() → abandon focus on drain
```

Ordering rationale, all requirement-level (`AGENTS.md` §3):

- Dedup before rules: a repost must cost no regex work. Regex work is the
  only unbounded cost in the pipeline.
- Secrets after rules: a `Speak` → `AnnounceOnly` downgrade must not carry
  the original text, because that text is what looked like a secret.
  §4.5 states the rule; `SecretDetector.kt`'s doc comment states the
  mechanics.
- Gates last: they are the cheapest checks and the most time-sensitive.

`OutputRouteGate` is checked **twice** — at enqueue, and again per
utterance inside `SpeechQueue` — because a headset connected at post time
can disconnect before a busy queue reaches that item.
`AudioBecomingNoisyReceiver` covers the third case neither check sees: a
route change *during* an utterance. All three exist because the
enqueue-only version shipped and was wrong; see
[history-4llm.md](history-4llm.md).

## Classes whose doc comments are the real source

Read these directly. The wiki summarizes; the comment is correct.

- **`domain/RuleEngine.kt`** — default-deny, first matching enabled rule
  by priority wins. Documents a *verified* limitation of its own ReDoS
  mitigation: `java.util.regex` has no cooperative cancellation, so a
  bare `withTimeoutOrNull` abandons the caller while the thread keeps
  burning. `InterruptibleCharSequence` + `runInterruptible` closes most
  of it; `RuleValidator` rejects backreferences outright because even a
  cleanly interrupted match costs the full 100ms on every notification
  from that app, forever. Measurements in
  [history-4llm.md](history-4llm.md).
- **`domain/SecretDetector.kt`** — downgrade-only. Synthesizes a generic
  "New notification from X" from the title alone rather than reusing the
  flagged text; downgrades a step further to `Suppress` if an incoming
  `AnnounceOnly` still embeds the flagged body (a user template with
  `{body}` does this). Mirrors framework `VISIBILITY_PRIVATE`/
  `VISIBILITY_SECRET` constants — drift risk covered by an instrumented
  test.
- **`speech/SpeechQueue.kt`** — single-consumer actor over a bounded
  channel. Takes audio-focus / in-call / DND / route checks as function
  references, not Android objects; that is the only reason it is
  JVM-testable. Three layered fixes live here, each closing a gap the
  previous could not see.
- **`SafeLog.kt`** — see above.

## Tooling inventory

- **Nix flake** pins JDK, Kotlin, Gradle, Android SDK, and (since
  2026-09-06) an emulator system image. No `gradlew` committed, by
  design; `gradle/wrapper/gradle-wrapper.properties` *is* committed, with
  version metadata only, so Tooling-API clients discover the right
  distribution — see the fwcd.kotlin entry in
  [traps-and-skills-4llm.md](traps-and-skills-4llm.md).
- **`.justfile`** — the interface. One line of dispatch per recipe;
  logic lives in [`scripts/test.sh`](../scripts/test.sh). `just --list`
  shows only the last comment line above a recipe.
- **CI** ([`check.yml`](../.github/workflows/check.yml)) runs inside `nix
  develop` so versions cannot drift from local. Carries both structural
  greps, `just wiki-lint` (self-test then claims — added **2026-09-08**;
  the wiki checks were local-only before that), `lintDebug`/`lintRelease` with SARIF upload to code scanning,
  and (since 2026-09-08) CodeQL in a separate workflow with
  `build-mode: manual`.
- **`.claude/hooks/`** — git guard (destructive git, direct
  commit/merge/push to `main`, and a `wiki/`-alongside-`app/src/` nudge at
  `gh pr create`), plus signing and log-hygiene guards.
- **`.claude/skills/`** — repo-local skills. `submit-a-pr` is the one to
  read before landing anything.
- **`wiki/scripts/check_wiki.py`** — the wiki's mechanical checks; see
  [README-4llm.md](README-4llm.md). `test_check_wiki.py` alongside it
  proves each check still fires on input built to break it, and runs
  first.

## Licensing

Apache-2.0 as of 2026-09-07, chosen over MIT for the express patent grant
and explicit default contribution terms, and because every shipped
runtime dependency already carries it — one license across the tree.
`app/build.gradle.kts` strips `META-INF/{AL2.0,LGPL2.1}` (the standard
template exclusion); [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)
is what closes that gap if the APK is ever distributed. JUnit (EPL-1.0)
is the one non-Apache dependency and is test-only, never shipped.
