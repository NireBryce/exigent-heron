---
name: boyscouting
description: How to leave code you're already touching a little better than you found it, without letting the cleanup outgrow the task that brought you there.
---

# Boyscouting

## Applies to

You're editing a file for an actual task and notice something small and
local you could fix in passing — a stale comment, a dead import, a
misnamed variable, a duplicated three-liner, a missing `.gitignore` entry
next to code you're already changing. Not: a defect worth its own tracking
(that's `propose-issue`), a stale factual claim in `wiki/`/`AGENTS.md`
(`wiki-sync`), or a deliberate broad tidy-up pass (`boyscouting-all`, or
just say what you're doing and do it as the actual task).

Copied from NireBryce/nixos-configs' skill of the same name (2026-09-07) and
adapted below — see "What's different here."

## The rule

**Leave the code better than you found it — the campsite rule — but only
the ground you're already standing on.** The scope is "touched by this
diff or immediately adjacent to it," not "anything in this repo I noticed
is imperfect." A boyscouting fix rides in the same commit as the task that
motivated it, described honestly in the commit message as incidental
rather than folded in silently.

## What qualifies

- Small, obviously correct, and reviewable in seconds alongside the real
  change — a typo, an unused variable, a comment that describes code three
  edits ago, a formatting inconsistency in a block you already rewrote.
- Confined to a file (or a couple of adjacent lines) you're editing for the
  actual task anyway. If you'd have to open a file you otherwise wouldn't,
  it's not boyscouting anymore — it's a separate task.
- Doesn't change behavior, an interface, or anything another module
  depends on. If you're not sure it's behavior-neutral, it needs the same
  verification as the main change, which usually means it isn't a quick
  tidy — `AGENTS.md` §0's phase-boundary discipline exists precisely
  because a small nearby edit can still combine badly with something else.

## What doesn't

- **A real bug**, even a tiny one, once it has a failure scenario — that's
  `propose-issue`, not a drive-by fix, unless the user is right there and
  says fix it now.
- **A stale wiki/AGENTS.md claim** your change makes true or false —
  that's `wiki-sync`'s job specifically, in the same change, but follow
  that skill's steps rather than freelancing the wording.
- **Renaming or restructuring** something just because you'd have written
  it differently — no failure scenario, no incidental fix, just opinion;
  skip it or mention it and move on.
- **Anything that touches a file you weren't already going to touch.**
  That's scope creep wearing boyscouting's name — a separate task (or
  `propose-issue` if it's a bug, or a mention in your reply if it's not).
- **A rule from `wiki/module-style-guide.md`** (or `AGENTS.md`'s §4
  component rules) **violated in a file you're merely reading**, not
  editing — mention it, don't fix it uninvited.
- **Anything §0 already forbids outright** — a new dependency, a logging
  call touching notification content, an `INTERNET`-permission-adjacent
  change — is never boyscouting-small regardless of how few lines it is;
  stop and ask, per §0, rather than folding it in as incidental.

## Why the boundary matters here

This is a single-user sideloaded app, reviewed by one person (`AGENTS.md`
§0/§1: "YAGNI is the default," no hypothetical-future abstraction). A diff
that quietly grew past its stated purpose is harder to review, not
easier — a "fix the dedup window" PR that also silently reformats an
unrelated screen hides the actual change inside noise. Small and
honestly-labeled is what keeps this useful instead of becoming the thing
`submit-a-pr`'s one-branch-one-purpose discipline exists to prevent.

## Steps

1. Notice something small while editing a file you're already changing.
2. Ask "does fixing this need a file I wasn't already opening, or change
   behavior?" If yes to either, stop — it's not boyscouting; use
   `propose-issue`, `wiki-sync`, or a plain mention instead.
3. Make the fix, minimal and local.
4. Say so plainly when you report the change — "also fixed an unused
   import in the same file" — rather than letting it pass silently inside
   a diff described as doing only the main task. The commit message gets
   this too: a short trailing clause, not folded into the main summary
   line.
5. If the cleanup is real but bigger than a drive-by (more than a few
   lines, or a second file), stop and propose it as its own thing instead
   of doing it anyway.

## What's different here

Adapted from NireBryce/nixos-configs, not copied verbatim:

- **`ship` → `submit-a-pr`.** This repo's landing skill is
  `submit-a-pr`, not `ship` — same one-branch-one-PR discipline, different
  name and a few procedural differences (see that skill's own "Why, and
  what this deliberately does differently").
- **No `trim-docs` here.** NireBryce/nixos-configs' version cross-references a
  dedicated conciseness-pass skill this repo doesn't have; a deliberate
  wiki/skill trim here is just its own scoped task, described as such.
- **§0's hard bans are called out explicitly** (no new dependency, no
  content logging, no `INTERNET`) as automatically disqualifying, because
  this repo has bright-line rules NireBryce/nixos-configs' equivalent doesn't — a
  "small" fix that trips one of those isn't a judgment call the way an
  opinion-only rename is.

## See also

- `propose-issue` — for anything you noticed but didn't fix.
- `wiki-sync` — for docs that went stale because of your change.
- `boyscouting-all` — for a deliberate, scoped repo-wide sweep instead of
  an incidental one.
