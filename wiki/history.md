# History

## Contents

- [This repo's history so far](#this-repos-history-so-far)
- [Decisions made while implementing, beyond AGENTS.md's own text](#decisions-made-while-implementing-beyond-agentsmds-own-text)

## This repo's history so far

`git log` is the accurate record at this size; this page exists for the
*reasoning* behind a decision once one needs more context than a commit
message carries, not as a running paraphrase of the log — see
[styleguide.md](styleguide.md)'s "index over restatement."

## Decisions made while implementing, beyond AGENTS.md's own text

- **Package name resolved**: `AGENTS.md` §3's tree uses
  `com.<yourdomain>.notifreader` as a placeholder; the real package is
  `net.breadthcharge.exigentheron` (`app/build.gradle.kts`). Not a
  deviation, just the placeholder filled in — noted here rather than
  silently, since a future reader diffing the spec's tree against the
  real one would otherwise wonder whether it was intentional.
- **`SecretDetector`'s downgrade mechanics, spelled out** (2026-09-05):
  `AGENTS.md` §4.5 says OTP-shaped content gets "suppress or downgrade to
  announce-only" but doesn't say what text an announce-only downgrade
  should actually carry. The literal-seeming answer — reuse the original
  `Decision.Speak.text` — would defeat the downgrade entirely, since that
  text is exactly what looked like a secret. `SecretDetector.scan`
  instead synthesizes a generic "New notification from X" from the title
  alone, and if the incoming decision is already `AnnounceOnly` with text
  that *itself* still contains the flagged body (a user `Rule.template`
  embedding `{body}` would do this), downgrades one step further to
  `Suppress` rather than let it through disguised as an announcement. See
  `SecretDetector.kt`'s own doc comment and `SecretDetectorTest`'s
  `announce-only decision that still embeds the flagged body` case.
- **`RuleEngine`'s regex-timeout mitigation is real but narrower than
  `AGENTS.md` §4.4 implies** (2026-09-05), verified rather than assumed:
  - `withTimeoutOrNull(100.milliseconds)` bounds how long `evaluate()`
    waits; it does not stop the match itself. Confirmed directly —
    interrupting a thread mid-match on a genuinely slow pattern does not
    stop it (`java.util.regex` has no cooperative-cancellation checks).
  - The "crafted message causes catastrophic backtracking" scenario the
    spec warns about is narrower than the textbook framing suggests on
    a modern JVM. OpenJDK memoizes failed backtracking positions
    (JDK-6328855), which makes classic nested-quantifier shapes like
    `(a+)+$` linear-time rather than exponential — measured directly:
    `(a+)+$`, `(a+)+b`, `(a|a)+$`, `(a|aa)+$`, and `(.*)+b`, the five
    textbook ReDoS examples, all resolved in ~0ms against adversarial
    input up to 40 characters on this JVM (OpenJDK 21.0.12). That
    memoization is disabled whenever the pattern has a backreference —
    `^(a+)+\1b$` measured 24 chars ≈ 277ms, 26 ≈ 1.1s, 28 ≈ 4.5s,
    doubling roughly every 2 characters, genuinely exponential and still
    not interruptible.
  - Net effect: the 100ms timeout mainly protects the *caller* from a
    backreference pattern, not the app from the CPU cost of one — a
    matched-but-timed-out rule leaves a real thread burning in
    `Dispatchers.Default` after `evaluate()` has moved on. The 2000-char
    input cap is what keeps that bounded per attack. See
    `RuleEngine.kt`'s own doc comment for the same explanation kept next
    to the code it describes, and
    [traps-and-skills.md](traps-and-skills.md) for how the *first*
    attempt at testing this got it wrong.
- Nothing else yet beyond the above. This page grows as real decisions
  get made that `AGENTS.md` doesn't already narrate — a library swapped
  for another, a phase's scope adjusted, something specified that turned
  out not to work as written. See skill
  [`wiki-sync`](../.claude/skills/wiki-sync/SKILL.md) for when a change is
  the kind that belongs here.
