# Traps & skills

_Last modified: 2026-09-08_

## Contents

- [How to use this page](#how-to-use-this-page)
- [Testing](#testing)
- [Scripts and the emulator](#scripts-and-the-emulator)
- [Docs and facts](#docs-and-facts)
- [Git](#git)
- [Editor tooling](#editor-tooling)
- [See also](#see-also)

Mistakes that have actually been made building this app, each reduced to
the thing worth remembering. Every entry here is a real, dated incident —
not a hypothetical. The full write-ups are in
[traps-and-skills-4llm.md](traps-and-skills-4llm.md).

## How to use this page

Skim it before doing something in one of these categories. The general
form of most entries lives in skill
[`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md); the specific
instance stays here.

## Testing

**A passing test is not evidence the path it names was reached.** A ReDoS
test using the textbook `^(a+)+$` pattern passed in 0.053s having
exercised no timeout at all — the input simply didn't match, and a fast
non-match returns the same `Suppress` a timeout does. A suspiciously fast
"slow path" test is a red flag worth measuring, not a lucky green.

**A comment explaining why a test is safe is a claim like any other.** A
burst-collapse test was documented as race-free because `enqueue()`
doesn't suspend. True of the producer; silent about the consumer, which
runs on another thread. It passed locally every time and failed on CI's
slower runner. **If a test's correctness depends on one thread losing a
race, gate the race** — don't weaken the assertion, and don't trust local
green.

**`scope.cancel()` does not wait.** Cancellation is requested, not
completed. A test's coroutine bled into the next one's setup roughly 1
run in 3 through the shared `Dispatchers.Default` pool. Use
`cancelAndJoin()` in teardown.

**A JVM test can reach a real Android SDK stub and crash on it.** Once
tests started covering `speech/` and `listener/`, a `SafeLog.error()`
inside a `catch` block threw `"Log not mocked"` — masking the exception it
was trying to report. Fixed with AGP's
`unitTests.isReturnDefaultValues = true`. Before testing anything outside
`domain/`, check whether it can reach a stub call.

## Scripts and the emulator

**`$!` gives you what you launched, not necessarily what you care
about.** `emulator -avd ... &` returns the launcher's PID; the actual
emulator is a separate `qemu-system-*` process that outlives it. The
liveness check reported a running emulator as dead, and teardown reported
success against a live qemu holding the AVD lock. Match the real process
by a property that identifies it (`pgrep -f "qemu-system.*-avd $AVD"`).

**A blocking call with no timeout turns "this failed" into "this is still
going".** `adb wait-for-device` sat silently for eleven minutes on a
stale AVD lock. Bound every wait, and never send a subprocess's output to
`/dev/null` — that's the one artifact that would have explained it.

**Assert on the world, not on the script's own output.** The emulator bug
above was found because `pgrep` disagreed with the "stopping the
emulator" line the script had just printed.

## Docs and facts

**Don't mirror state owned by another system.** A table of open GitHub
issues was added to [open-threads.md](open-threads.md) and went stale
within the hour. Nothing inside this repo can keep it true and
`check_wiki.py` can't check it. Link to the authority; keep only what the
authority doesn't know.

**A cross-reference outlives the thing it names.** `AGENTS.md` cited
`./gradlew` in acceptance criteria for days after a separate commit
decided there would be no `gradlew` in this repo at all.

**A class name and its logging tag are two different strings.** A hook's
suggested fix told people to run `adb logcat -s SafeLog`. The real tag is
`ExigentHeron`. It was plausible, and never read from the file.

## Git

**`git reset --hard` discards the working tree, not just the commit.** A
throwaway test commit was cleaned up with `git reset --hard HEAD~1` while
real unstaged edits sat in the same tree. They were lost, and only
recoverable because they were simple enough to retype.

The safe versions: commit real work immediately, before any cleanup step
that touches the tree wholesale; use `git reset --soft` to undo a
throwaway commit; and for anything that doesn't need the real repo's
history, use a separate throwaway `git init` elsewhere.

The hook warns about this already. The failure was reading the warning as
being about the command in general rather than about *this tree, right
now*.

## Editor tooling

**A cache that silently replays a stale result turns "fix the cause" into
"also clear the cache".** The Kotlin LSP reported unresolved AndroidX
imports; the real Gradle error was invisible until its per-workspace
SQLite cache was deleted, and even then it surfaced only in
`~/.gradle/daemon/*/daemon-*.out.log`, not in the extension's own output
channel.

When a tool's visible output looks suspiciously clean right up to the
point of failure, go read its on-disk daemon/cache logs.

There is a **known residual caveat** here: KLS embeds Kotlin 2.1.0 while
this project builds with 2.4.10, so delegate-shaped usages still show a
metadata-skew diagnostic. Upstream bug, no project-side fix — tracked as
[exigent-heron#24](https://github.com/NireBryce/exigent-heron/issues/24).

## See also

- [traps-and-skills-4llm.md](traps-and-skills-4llm.md) — the full
  incident write-ups, with dates, evidence, and what could not be
  determined.
- Skill [`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md) — the
  general discipline most of these are instances of.
- Skill [`investigate-bug`](../.claude/skills/investigate-bug/SKILL.md) —
  check whether a symptom is already tracked before digging.
