# Wiki style guide (dense)

_Last modified: 2026-09-08_

_Text is llm generated with occasional human review_

## Contents

- [The split, and why](#the-split-and-why)
- [Splitting an existing page](#splitting-an-existing-page)
- [Exception grants](#exception-grants)
- [What the checker enforces, exactly](#what-the-checker-enforces-exactly)
- [Provenance and deliberate differences](#provenance-and-deliberate-differences)

Companion to [styleguide.md](styleguide.md), which holds the rules
themselves.

## The split, and why

Introduced **2026-09-08**. Before it, every page served both audiences at
once, and the result drifted toward the agent: long, provenance-heavy
prose that argued its own reasoning inline, where a human contributor
mostly wanted the command to run. Rather than cut the reasoning — it is
genuinely load-bearing, and several entries exist specifically to stop
someone helpfully reversing a decision — it moved into a `-4llm`
companion per subject.

What each half optimizes for:

| | article | companion |
|---|---|---|
| reader | a human contributor, mid-task | an agent filling context |
| optimize for | time-to-answer | facts per token, and completeness |
| tone | short sentences, imperative | telegraphic, tabular where possible |
| omits | provenance, superseded states, measurements | onboarding framing, restated commands |
| keeps | the current answer, the command, the caveat | why, when, what was rejected, what was not proven |

Consequences that are easy to get wrong:

- **The companion is not "the old page".** It is the residue of a real
  editorial decision about each fact. A companion that reads like the
  article plus filler means the split wasn't made.
- **The article is not a stub.** It must stand alone for its task. If
  doing the task requires opening the companion, the wrong half got the
  fact.
- **Neither half is exempt from the structural rules.** Date line,
  generated `## Contents`, resolving links, absolute dates. The checker
  scans `wiki/**/*.md`, so a companion is checked exactly like an
  article.
- **`check_wiki.py`'s `pairs` check covers the structural half of the
  convention**, added **2026-09-08**: a page with no counterpart is a hard
  failure, and a prose paragraph of 25+ words written verbatim into both
  halves is a REVIEW finding. What it still cannot see is a fact
  *restated in different words* across the pair — the common case, and a
  judgment call like every other prose claim the script can't read. Treat
  a green `pairs` as evidence about structure and copy-paste, not about
  whether the split was made well.

## Splitting an existing page

The procedure used for the 2026-09-08 split, if another page ever needs
it:

1. Read the whole page and sort each paragraph into *needed to do a task*
   vs. *needed to understand a past decision*. Anything dated, measured,
   superseded, or arguing against an alternative is the second kind.
2. Write the companion first, from the sorted material, keeping dates and
   evidence verbatim. Losing a measurement in a rewrite is the one
   unrecoverable failure here.
3. Write the article fresh — not by deleting from the original. A page
   trimmed down keeps the original's shape, which was the problem.
4. Cross-link: article → companion once in "See also"; companion → article
   sections where one is the entry point.
5. Re-point inbound links. External references (`AGENTS.md`, `SECURITY.md`,
   Kotlin doc comments, hooks, skills) name the article path; keep them
   pointing at the article.
6. `gen-contents` both, then `just wiki-lint`.

## Exception grants

"Index over restatement" has exactly two standing exceptions. Both are
recorded because an unexplained exception becomes a precedent.

**[testing.md](testing.md)** — documents *doing* something (building,
installing, exercising the app on a device) where the real source is the
act, not a file to link to. Same role `NireBryce/nixos-configs`'s
`homelab/` usage pages play there.

**[architecture.md](architecture.md)**, granted **2026-09-08** — holds
the canonical package tree and data-flow diagram rather than linking to
`AGENTS.md` §3 for them. This one was granted **to end a restatement, not
to add one**: a target tree and a real tree are the same shape, so two
copies meant reconciling every new file against a spec written before the
app existed, and the spec's copy could never be right about a file it
hadn't anticipated. Five such files existed by then, plus a package
(`ui/permission/`) the spec named and the code never grew. §3 now carries
the summary and the requirements; that page carries the tree. Full
account in [architecture-4llm.md](architecture-4llm.md).

A third exception needs the same kind of written reason. "This page is
long" is not one.

## What the checker enforces, exactly

[`scripts/check_wiki.py`](scripts/check_wiki.py) — its docstring is the
authority; this is the shape of what it can and cannot see.

**Structural, mechanical, trustworthy:**

- `links` — every relative markdown link across `wiki/`, `AGENTS.md` and
  `.claude/` resolves. Fully general, which is why this one reaches into
  `.claude/` when the name-matching checks deliberately don't: skills
  there cite nixos-configs' own skills and recipes this repo doesn't
  have, all of which would be false positives. (This goes further than
  `NireBryce/nixos-configs`'s own script, whose styleguide says outright
  "there's no automated check for this" for file targets. Verified
  2026-09-05 by reading `check_links`, not assumed from a docstring.)
- `anchors` — every `#fragment` resolves against a real GitHub-slug
  computation of the target page's headings.
- `contents` — every page's `## Contents` matches its own `##` headings.
- `dates` — the `_Last modified:_` line exists, is shaped right, and is
  not in the future.
- `freshness` — that date against git: uncommitted substantive edits with
  a non-today date fail hard; a last-substantive-commit newer than the
  stated date is REVIEW. Bookkeeping-only changes (the date line, the
  notice, the Contents block) are excused, so a gen-contents run never
  trips it.
- `recipes` — `` `just <recipe>` `` mentions against `.justfile`, which is
  a single file listing every valid name, so nothing is hand-maintained.

**Hand-maintained, so a finding may mean "update the list":**

- `gradle` — against `KNOWN_GRADLE_TASKS`, since this repo has no single
  file listing valid task names.
- `phases` — [status.md](status.md)'s table against `PHASE_FILES`, a
  filename map matched anywhere under `app/src` (so a file genuinely
  moved to another package doesn't false-positive). Built=Yes with a
  missing file is a hard failure; Built=No with every file present is a
  REVIEW finding only — files existing doesn't prove acceptance criteria
  pass.
- `skills` — the two mention forms (the word **skill** followed by a
  backticked name, or a backticked name followed by **skill**) against
  `.claude/skills/<name>/`. A bare backticked token is deliberately
  unmatched, since most of them here are code identifiers.

**Known gap:** `gen-contents` inserts a *fresh* `## Contents` block after
the title and date line but ahead of the provenance notice, since it
predates that notice. It rewrites an existing block in place correctly,
which is the case that actually comes up; a brand-new page needs the
block moved below the notice by hand.

**Cannot see, at all:** whether any sentence is true. A claim about what
a class does, a "why" that no longer applies, a date left un-bumped, a
duplicated fact across a page pair. That is the entire reason skill
[`wiki-sync`](../.claude/skills/wiki-sync/SKILL.md) exists as a
deliberate procedure rather than a lint rule.

Historical claims ("removed 2026-09-05") are **deliberately not** a
target. This repo keeps them on purpose, and a script cannot tell
historical prose from a live claim.

## Provenance and deliberate differences

Adapted from `NireBryce/nixos-configs`'s `wiki/styleguide.md`, collapsed
from that repo's multi-tier hierarchy (cross-cutting pages, per-category
pages, a usage tier, an escape-hatch subdirectory) to what a
single-module app needs: one flat tier, plus the page that is the escape
hatch's whole reason to exist there — here, [testing.md](testing.md).

The `-4llm` pairing is **this repo's own**, not adapted. Don't assume the
sibling repo has it.

`check_wiki.py` draws a version of the same structural-not-prose-aware
line there too, but its actual check set differs (its `hosts` check has
no equivalent here; this repo's `phases` and `links` have none there).
Don't read the two scripts as checking identical things.
