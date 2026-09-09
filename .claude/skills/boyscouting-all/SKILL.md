---
name: boyscouting-all
description: How to deliberately sweep the whole repo for the same small, local cleanups boyscouting fixes incidentally, and land them as one scoped change.
---

# Boyscouting, repo-wide

## Applies to

Asked to "boyscout the repo", "clean up the small stuff everywhere", or
similar — a deliberate, standalone pass looking for `boyscouting`-shaped
opportunities across files you weren't already editing for another task.
Not: a fix noticed while doing something else (that's `boyscouting`
itself, landed in that task's commit); a correctness/simplification review
of a diff (`/code-review`, `/simplify`); a stale factual claim
(`wiki-sync`); anything with a real failure scenario (`propose-issue`).

Copied from `~/nixos-configs`' skill of the same name (2026-09-07) and
adapted below — see "What's different here."

## Relationship to `boyscouting`

Same eligibility bar — read that skill's "What qualifies" and "What
doesn't" first, they're not repeated here. The only thing that changes is
scope: `boyscouting` limits itself to files a real task already opened;
this skill *is* the task, so it's the file-opening step that's now
deliberate instead of forbidden. Everything else — behavior-neutral, no
new failure scenario, no opinion-only renames, real bugs go to
`propose-issue`, §0's hard bans are never boyscouting-small — still
applies exactly as written there.

## Why this needs its own skill, not just "run boyscouting on everything"

A repo-wide sweep is outward-facing in a way a single incidental fix isn't:
it touches many unrelated files in one pass, which is exactly the
single-purpose-branch problem `submit-a-pr` and `boyscouting` both guard
against, just inverted. The discipline here isn't "keep the diff small"
(it won't be), it's **keep every individual hunk independently
justifiable and trivially reviewable**, and don't let the sweep smuggle in
anything that needed its own task.

## Steps

1. **Use a worktree** (`use-a-worktree`) — this touches many files across
   the tree, exactly the shared-checkout risk that skill exists for.
2. **Scope the sweep before starting.** "Whole repo" is rarely what's
   wanted — confirm whether it's everything, one area (`app/src/`,
   `wiki/`, `.claude/`), or one class of finding (dead imports, stale
   comments, unused variables). A vague ask is worth one clarifying
   question rather than guessing at the diff size.
3. **Search, don't skim.** Grep for the concrete shapes that
   `boyscouting`'s "what qualifies" names — unused imports, TODO/FIXME
   comments older than the code around them, duplicated small blocks,
   obviously-stale comments referencing code that moved. This repo has no
   `statix`/`deadnix` equivalent — `just lint` (Android Lint) and `just
   test-all` exist, but neither finds what this skill sweeps for (a dead
   import, a stale comment, a drifted name). Hand-searching is still the
   whole method; lean on Android Studio's own inspections locally if
   available, but don't assume any are run automatically.
4. **Apply `boyscouting`'s qualifying bar to each candidate individually.**
   Finding it in a sweep doesn't relax the bar — a real bug found this way
   still goes to `propose-issue`, not into this branch, even though you're
   already looking at the file. Same for anything §0 bans outright (a new
   dependency, content logging, `INTERNET`) — never in scope here either.
5. **One commit per logical cleanup, not one giant commit.** A reviewer
   should be able to revert "remove dead import in RuleEngine.kt" without
   reverting "fix stale comment in SpeechQueue.kt". Group only truly
   identical mechanical fixes into one commit.
6. **Run `just test-all`** before shipping — a sweep this wide is exactly
   where a typo becomes a build failure or a silently broken test, and
   that recipe is structure + wiki + build + unit + lint + device,
   cheapest first. `just build` and `just test` are the fast subset if
   you're iterating. (No `./gradlew` in this repo — `just` and the
   `gradle` on `PATH` both come from the Nix dev shell, per
   `wiki/testing.md`.)
7. **Ship normally** (`submit-a-pr`) — one branch, one PR, describing the
   sweep's scope and listing what categories of fix it contains, not just
   "cleanup". Step 2 of that skill (`wiki-sync`) still applies if any
   sweep hunk touches something the wiki documents.

## Calibrate

If the candidate list is long enough that step 5 would produce more than a
handful of commits, that's a signal the sweep found real work, not tidying
— split it into a proposal (`propose-issue` per finding, or one issue
listing several) rather than one mega-branch. This skill is for the case
where each fix really is boyscouting-small; it isn't a shortcut for
running a full audit under a friendlier name.

## What's different here

Adapted from NireBryce/nixos-configs, not copied verbatim:

- **`ship` → `submit-a-pr`**, per `boyscouting`'s own note.
- **No `statix`/`deadnix` equivalent**, though a preflight now exists.
  NireBryce/nixos-configs leans on Nix-specific dead-code/style linters;
  this repo's nearest equivalent is Android Lint, which doesn't find what
  this skill sweeps for — so step 3 is still explicit that hand-searching
  is the whole method here, not a supplement to tooling.
- **Verification command swapped for this repo's actual one** — `just
  test-all` in place of `just preflight` (**2026-09-08**; it was `gradle
  assembleDebug`/`gradle testDebugUnitTest` until the `just` runner
  landed), matching `wiki/testing.md` and each phase's own acceptance
  criteria (see `wiki/status.md`).
- **`trim-docs` cross-reference dropped** — this repo has no dedicated
  conciseness-pass skill; a docs-only sweep is just its own scoped task
  here.

## See also

- `boyscouting` — the eligibility bar this skill reuses verbatim.
- `/code-review`, `/simplify` — for correctness or simplification work
  that needs actual judgment, not this skill's mechanical bar.
- `propose-issue` — where anything with a failure scenario goes instead.
- `use-a-worktree`, `submit-a-pr` — mechanics for running and landing the
  sweep.
