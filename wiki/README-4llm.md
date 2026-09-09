# Wiki (dense)

_Last modified: 2026-09-08_

_Text is llm generated with occasional human review_

## Contents

- [What this file is](#what-this-file-is)
- [Document layers](#document-layers)
- [Load order](#load-order)
- [Invariants worth holding in context](#invariants-worth-holding-in-context)
- [Mechanical checks](#mechanical-checks)
- [Provenance](#provenance)

## What this file is

The `-4llm` companion to [README.md](README.md). Every wiki subject here
exists as an article for humans and a `-4llm` companion for agents; this
is the companion for the index itself. Convention defined in
[styleguide.md](styleguide.md), dense rationale in
[styleguide-4llm.md](styleguide-4llm.md).

Rule: a fact lives in exactly one of the pair. Article = current answer +
link. Companion = reasoning chain, dated deviations, superseded states,
provenance. Do not duplicate across the pair; a duplicated fact rots in
one copy first.

## Document layers

Three layers, different rot rates, deliberately not merged:

| Layer | Answers | Stale when |
|---|---|---|
| `AGENTS.md` | what the app must keep satisfying (requirements) | a requirement genuinely changes |
| `wiki/*.md` | what is true right now, and how to work here | code changes and nobody updates it |
| class doc comments | why this class is shaped this way | the class changes |

Doc comments are the copy of record for per-class reasoning
(`RuleEngine.kt`, `SecretDetector.kt`, `SpeechQueue.kt` most of all).
Wiki pages summarize and link; they do not re-explain.

`AGENTS.md` was a build spec until **2026-09-08**, then rewritten into a
standing contract once all six phases were verified. Its §0–§8 and
§4.1–§4.10 numbers are cited from ~50 files (~230 citations): **stable by
policy — add, never renumber**. Several files quote its sentences
verbatim; those phrasings are load-bearing. Full account:
[history-4llm.md](history-4llm.md).

A third file, `BUILD_PLAN.md`, held phase order and was removed
2026-09-08 once every phase was built and verified. Its acceptance
criteria survive in [status.md](status.md)'s Spec column and
[testing.md](testing.md)'s manual scripts; the phase rule itself moved
into `AGENTS.md` §6.

## Load order

Cold start on this repo, cheapest useful order:

1. `AGENTS.md` — the contract. Includes §0's instruction to say when it
   looks wrong rather than build around it silently.
2. [overview.md](overview.md) — what the app is, the pipeline.
3. [architecture.md](architecture.md) — the tree. This page *is* the
   canonical tree as of 2026-09-08; `AGENTS.md` §3 keeps only a
   six-bullet summary and points here.
4. Then, task-shaped:
   - changing `domain/` logic → the class's own doc comment first
   - running anything → [testing.md](testing.md)
   - claiming something works → [status.md](status.md), and
     [status-4llm.md](status-4llm.md) for what each verification actually
     covered
   - hitting something weird → [traps-and-skills.md](traps-and-skills.md)
   - wondering why a decision went that way →
     [history-4llm.md](history-4llm.md)

## Invariants worth holding in context

- **No notification content in logs, ever. No `INTERNET`. No telemetry.**
  `AGENTS.md` §0 states it three ways. Most structural decisions
  downstream serve this rather than elegance.
- **`domain/` has zero Android imports.** CI greps `^import android.`
  under `domain/` (added 2026-09-08). This is what keeps the logic worth
  testing testable on the JVM.
- **`android.util.Log` appears in exactly one file** (`SafeLog.kt`). CI
  greps for it. `SafeLog` has no arbitrary-string overload by design; its
  logcat tag is `"ExigentHeron"`, not a class name.
- **Pipeline ordering is a requirement**, not an implementation detail:
  extract → dedup → rules → secrets → gates → enqueue. `SecretDetector`
  can only ever downgrade.
- **Every change lands via a branch and a PR.** Never commit, merge, or
  push directly to `main`. Skill
  [`submit-a-pr`](../.claude/skills/submit-a-pr/SKILL.md).
- **A "verified" claim needs a date and the command actually run this
  session.** Skill [`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md).
- **No new dependency without asking.** `AGENTS.md` §2's list is meant to
  be the whole list. See the ktlint and RE2J entries in
  [history-4llm.md](history-4llm.md) for what that has actually cost.
- **No physical Android device has ever run this app.** Every on-device
  claim in this wiki is an emulator claim. `SECURITY.md` §4 carries the
  caveats.

## Mechanical checks

[`scripts/check_wiki.py`](scripts/check_wiki.py), run as `just wiki-lint`
or `python3 wiki/scripts/check_wiki.py check`. Ten checks: `phases`,
`skills`, `gradle`, `recipes`, `links`, `anchors`, `contents`, `dates`,
`pairs`, `freshness`.
The script's own docstring is the authority on what each does and what it
deliberately does not catch.

Consequences worth knowing before editing:

- `phases` parses [status.md](status.md)'s table with a regex requiring
  the exact four columns `Phase | Spec | Built | Verified` and a row
  starting `| <n> —`. Reshaping that table silently disables the check.
- `skills` matches the two mention forms — the word **skill** followed by
  a backticked name, or a backticked name followed by **skill** — against
  real `.claude/skills/<name>/` directories.
- `gradle` matches against a hand-maintained `KNOWN_GRADLE_TASKS`; a
  false "unknown" usually means add the task to the set.
- `recipes` matches `` `just <recipe>` `` against `.justfile`, fully
  mechanically.
- `contents` compares each page's `## Contents` list to its own `##`
  headings. Fix with `python3 wiki/scripts/check_wiki.py gen-contents
  <page>` — never by hand.
- `dates` checks the `_Last modified:` line exists, is shaped right, and
  is not in the future. It cannot check that it is still true — that is
  `freshness`'s job.
- `freshness` (added **2026-09-08**) checks the date against git. A page
  with uncommitted substantive edits whose date isn't today is a hard
  finding; a page whose last substantive commit postdates its stated date
  is REVIEW, because styleguide.md exempts mechanical touches and no
  script can tell a typo fix from a meaning change. Changes confined to
  the date line, the notice, or the `## Contents` block never count.
- `pairs` (added **2026-09-08**) checks every page has its counterpart —
  a missing or orphaned half is a hard failure — and flags a prose
  paragraph of 25+ words written verbatim into both halves as a REVIEW
  finding. It cannot see a fact restated in different words.

Nothing here reads prose. A sentence can become false with every check
green.

## Provenance

This wiki, `check_wiki.py`, the git-guard hook, and several skills are
adaptations of the sibling `NireBryce/nixos-configs` repo's equivalents,
sized down from a multi-host NixOS fleet to one Gradle module. Mapping:

| Here | There | Difference |
|---|---|---|
| `wiki/` flat tier | multi-tier hierarchy | one module doesn't need tiers |
| [status.md](status.md) | `wiki/hosts.md` | phases instead of hosts |
| `check_wiki.py phases` | its `hosts` check | claimed status vs. real tree, same idea |
| `check_wiki.py links` | (absent there) | file-target checking is new here |
| `check_wiki.py recipes` | its `recipes` check | same, both derive from `.justfile` |
| [testing.md](testing.md) | `homelab/` usage pages | the "documents doing, not stating" exception |

Cite the difference when adapting something else from there; do not
assume the two scripts check identical things.
