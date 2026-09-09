# History

_Last modified: 2026-09-08_

## Contents

- [What this page is](#what-this-page-is)
- [Decisions you are most likely to trip over](#decisions-you-are-most-likely-to-trip-over)
- [Things that were removed on purpose](#things-that-were-removed-on-purpose)
- [Deliberate deferrals](#deliberate-deferrals)
- [See also](#see-also)

Why the code looks like this, where a commit message isn't enough.

## What this page is

`git log` is the accurate record of *what* changed. This page exists for
the *reasoning* behind a decision, and only where the reasoning is
non-obvious enough that someone would otherwise undo it by accident.

The full, dated entry list is in [history-4llm.md](history-4llm.md). What
follows is the subset most likely to matter while you're editing.

## Decisions you are most likely to trip over

**Regex safety is two mechanisms, not one.** `RuleValidator` rejects
backreferences outright at save time, *and* matching runs inside
`InterruptibleCharSequence` + `runInterruptible`. Both are needed:
`java.util.regex` has no cooperative cancellation, so a bare
`withTimeoutOrNull` abandons the caller while the thread keeps burning.
RE2J was considered and rejected — it would have removed the machinery
entirely, but it's outside `AGENTS.md` §2's dependency list and drops
lookahead/lookbehind for every future rule author.

**`SecretDetector` never reuses the flagged text.** A `Speak` →
`AnnounceOnly` downgrade synthesizes "New notification from X" from the
title alone, because the original text is exactly what looked like a
secret. If an incoming `AnnounceOnly` still embeds the flagged body — a
user template with `{body}` does this — it downgrades one step further to
`Suppress`.

**`SpeechQueue` takes function references, not Android objects.**
`AudioFocusManager`'s constructor calls `getSystemService` immediately,
which makes anything holding one impossible to construct in a JVM test.
This is the only reason `SpeechQueueTest` exists without a second fake
class.

**The in-call check runs before the audio-focus request.** The first
version had it backwards, so every notification during a call requested
and immediately abandoned focus for an utterance it was never going to
speak.

**Both `*Holder` classes rebuild reactively.** `RuleEngineHolder` and
`SecretDetectorHolder` rebuild from a `Flow` on every emission, so an
edit takes effect on the next notification rather than the next app
start. Rebuilding is cheap. Constructing once would make the editors feel
broken.

**No navigation-compose.** Four screens don't justify a dependency
outside §2's list. `MainActivity` holds a `Screen` sealed interface and a
`when`. The app picker is a `Dialog` inside the rule editor precisely so
a result doesn't need to cross a screen boundary.

**`AppContainer.ttsEngine`/`speechQueue` are `var`s.** `AndroidTtsEngine`
binds one `TextToSpeech` to one engine package for its lifetime, so
switching engines means rebuilding that pair — the smallest thing that
makes §4.8's "takes effect" true short of restarting the app.

## Things that were removed on purpose

- **`BUILD_PLAN.md`** (2026-09-08) — all six phases built and verified,
  so a forward-looking build order had nothing left to guide. Its
  criteria live on in [status.md](status.md) and [testing.md](testing.md);
  the phase rule moved into `AGENTS.md` §6.
- **`phase2HardcodedRules`** — a Phase 2 stopgap targeting Google
  Messages, arbitrary and superseded in Phase 3 by real persistence and
  the rule editor. A fresh install now speaks nothing until you add a
  rule.
- **`SpeechQueue`'s old `DROP_OLDEST` overflow test** — queue-collapse
  made the property it depended on unobservable. Removed rather than
  forced to pass with a flaky workaround. `DROP_OLDEST` itself is
  unchanged.

## Deliberate deferrals

Recorded so they read as choices rather than oversights:

- **Code style is an `.editorconfig`, not ktlint.** ktlint is the better
  tool and was recommended; `.editorconfig` is what landed, because a new
  dependency is a question to ask and the question hadn't been answered.
  The trade — this binds an editor, not CI — is named in
  `.editorconfig`'s own header. ktlint reads the same file, so switching
  later is additive.
- **`SettingsRepository` was scaffolded empty in Phase 3**, with no
  placeholder field, because a fake field would violate `AGENTS.md` §0's
  YAGNI rule for no gain.
- **`app_name` stays `"exigent-heron"`** — confirmed with the user while
  planning Phase 3, not a placeholder left behind.
- **Apache-2.0, not MIT** (2026-09-07) — for the express patent grant,
  the explicit default contribution terms, and because every shipped
  runtime dependency already carries it.

## See also

- [history-4llm.md](history-4llm.md) — the full dated record, including
  the `AGENTS.md` rewrite, the ReDoS measurements, and every decision not
  summarized here.
- [architecture-4llm.md](architecture-4llm.md) — divergences from the
  spec's original tree.
- [traps-and-skills.md](traps-and-skills.md) — mistakes, as distinct from
  decisions.
