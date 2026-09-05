# History

## Contents

- [This repo's history so far](#this-repos-history-so-far)
- [Decisions made while implementing, beyond AGENTS.md's own text](#decisions-made-while-implementing-beyond-agentsmds-own-text)

## This repo's history so far

Three commits as of 2026-09-05: `55cbeb1` (initial commit), `0914143`
(the `AGENTS.md` build spec itself), `7291a29` (Phase 0 — Kotlin/Android
skeleton, the Nix dev shell, VS Code setup). `git log` is the accurate
record at this size; this page exists for the *reasoning* behind a
decision once one needs more context than a commit message carries, not
as a running paraphrase of the log — see
[styleguide.md](styleguide.md)'s "index over restatement."

## Decisions made while implementing, beyond AGENTS.md's own text

- **Package name resolved**: `AGENTS.md` §3's tree uses
  `com.<yourdomain>.notifreader` as a placeholder; the real package is
  `net.breadthcharge.exigentheron` (`app/build.gradle.kts`). Not a
  deviation, just the placeholder filled in — noted here rather than
  silently, since a future reader diffing the spec's tree against the
  real one would otherwise wonder whether it was intentional.
- Nothing else yet. This page grows as real decisions get made that
  `AGENTS.md` doesn't already narrate — a library swapped for another, a
  phase's scope adjusted, something specified that turned out not to work
  as written. See skill
  [`wiki-sync`](../.claude/skills/wiki-sync/SKILL.md) for when a change is
  the kind that belongs here.
