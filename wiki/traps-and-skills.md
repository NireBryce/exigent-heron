# Traps & skills

## Contents

- [Stale `./gradlew` references in AGENTS.md](#stale-gradlew-references-in-agentsmd)
- [A logcat tag guessed from a class name instead of read from the source](#a-logcat-tag-guessed-from-a-class-name-instead-of-read-from-the-source)

Mistakes that have actually happened building this app, each linked to the
skill that holds the general form of the lesson. Adapted from
nixos-configs' own `traps-and-skills.md` — same idea (specific incident
here, reusable rule in the skill), same requirement that an entry be a
real, dated thing that happened, not a hypothetical.

## Stale `./gradlew` references in AGENTS.md

**2026-09-05.** `AGENTS.md` §6's Phase 0 and Phase 1 acceptance criteria
said `./gradlew assembleDebug` / `./gradlew testDebugUnitTest`, correct
when that spec section was first written. A later, separate commit ("Phase
0: Kotlin/Android project skeleton, Nix dev shell...") decided there would
be no `gradlew` in this repo at all — `flake.nix`'s own shellHook says so
explicitly — and had no reason to touch §6 while making that call. The two
stale mentions sat there until a session reading that section closely
enough for an unrelated reason (adapting another repo's wiki skills)
happened to notice the contradiction. Fixed the same day.

**General form:** skill
[`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md), category 3 (a
cross-reference surviving after the thing it names changes) — this
incident is that skill's own worked example.

## A logcat tag guessed from a class name instead of read from the source

**2026-09-05.** While writing `signing-guard-pretooluse.sh`'s suggested fix
for an unscoped `adb logcat`, the suggestion named `SafeLog` as the tag to
scope to (`adb logcat -s SafeLog`) — plausible, since that's the class's
name, but never actually checked against `SafeLog.kt`. The real tag is
`"ExigentHeron"` (`SafeLog.kt`'s own `TAG` constant). Caught the same
session, while writing [architecture.md](architecture.md) from the real
source file instead of from memory of writing the hook a few messages
earlier.

**General form:** skill
[`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md), category 1 (a
specific external fact stated with more confidence than what was actually
checked) — a class name and its logging tag are two different strings,
and only one of them was ever read from the file.
