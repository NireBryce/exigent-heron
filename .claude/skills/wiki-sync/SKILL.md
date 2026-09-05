---
name: wiki-sync
description: Check whether a change you just made leaves a wiki/ article stale, and fix it in the same change.
---

# Keeping the local wiki in sync

## Applies to

Run this at the end of any change to this repo that could make something
`wiki/` states no longer true. Skip it when the change touches nothing the
wiki describes, which is most small internal edits — check by grepping
first rather than reflexively opening every page.

**Not purely discretionary once a change is actually landing.** Skill
`submit-a-pr`'s own step 2 runs this, by name, before a branch is pushed
— not "remember to do this," a numbered step between "commit" and "push
and open the PR." `git-guard-pretooluse.sh` also nudges at `gh pr create`
if the branch added or removed an `app/src/` file with nothing under
`wiki/` alongside it, as a backstop for a slip. Neither replaces actually
running the procedure below deliberately.

## Why this exists

`wiki/README.md` and `wiki/styleguide.md` already state the rule:
"whichever change makes a page stale corrects it in the same change, not
as a follow-up" — the same discipline `AGENTS.md` asks of itself. That
rule is easy to know and easy to forget in practice, because by the time a
change is done and verified, the wiki is the last thing still in mind.
This skill is the deliberate checklist for closing that loop instead of
trusting it'll happen by habit. (Adapted from nixos-configs' own
`wiki-sync` skill — same shape, same reasoning, applied to a much smaller
wiki.)

## When to run this

At the end of a change that could make a wiki fact wrong, for example:

- adding, renaming, or removing a class/file `architecture.md`,
  `traps-and-skills.md`, or `status.md` mentions by name
- finishing a phase, or making real progress on one — `status.md`'s Built
  and Verified columns need to reflect what's actually true, not what
  `BUILD_PLAN.md` says should eventually be true
- fixing a bug `open-threads.md` describes as open, or finding a new one
  worth recording there
- a decision made while implementing that `AGENTS.md` doesn't already
  narrate — `history.md`
- a mistake actually made and caught — `traps-and-skills.md`, with the
  skill that holds its general form

## Procedure

1. **Name what changed, in wiki terms.** Turn the diff into a short list
   of facts, not files — "Phase 1's domain classes now exist", "class X
   moved from package Y to Z", "bug D (open-threads.md) is fixed". This is
   the thing to search for, not the commit description.
2. **Find candidate pages** by grepping the wiki for the old name, path,
   or fact:
   ```sh
   grep -rln "<old-name-or-path-or-fact>" wiki/
   ```
   `status.md` and `architecture.md` are the usual hits for anything
   structural; `open-threads.md` for anything that was tracked as pending.
3. **Read each candidate against the new state, not against memory** —
   re-derive the fact (run the actual command, read the actual file) the
   way skill `fact-hygiene` insists on, rather than trusting what the page
   already says or what you assume changed.
4. **Edit stale pages in the same change**, following
   [`wiki/styleguide.md`](../../wiki/styleguide.md):
   - Dates absolute (`2026-09-05`), never relative ("today", "last week").
   - Relative links verified to resolve after editing.
   - kebab-case naming; `README.md` reserved for a directory's own index.
   - If a fix balloons into new prose that argues a fact rather than
     linking to it, that's a sign the fact belongs in the linked file's
     own header comment instead.
5. **Run the mechanical checks** before calling it done:
   ```sh
   python3 wiki/scripts/check_wiki.py check
   ```
   Fix any `contents`/`anchors`/`links`/`skills`/`gradle`/`phases`
   finding it reports — `gen-contents <page>` regenerates a stale
   `## Contents` block automatically rather than by hand.
6. **If nothing in `wiki/` actually mentions what changed, say so and
   stop.** Don't manufacture an edit to a page the change doesn't touch —
   most changes are exactly this case, and the check itself is the value,
   not an edit for its own sake.

## See also

- [`submit-a-pr`](../submit-a-pr/SKILL.md) — its step 2 is where this
  procedure is actually invoked before a change lands.
- [`wiki/styleguide.md`](../../wiki/styleguide.md) — the house rules
  this skill's edits have to follow.
- [`wiki/README.md`](../../wiki/README.md) — why the wiki is a link
  layer, and "keeping this from rotting".
- Skill `fact-hygiene` — the general discipline behind re-deriving a fact
  instead of trusting a stale one, applied beyond just the wiki.
