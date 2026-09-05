# Wiki

## Contents

- [Common tasks](#common-tasks)
- [Pages](#pages)
- [Keeping this from rotting](#keeping-this-from-rotting)

A small index over what's actually happened while building this app, as
distinct from what `AGENTS.md` specifies should be built. Adapted from the
nixos-configs repo's own wiki (same link-layer idea, same style rules),
sized down from a multi-host NixOS fleet to one Gradle module.

**Why a separate layer at all, on a repo this small:** `AGENTS.md` is a
spec — it says what each phase should do and what "done" means for it. It
deliberately doesn't track *whether that's actually true right now* (a
phase marked "done" in prose the day it's written can go stale the moment
a later change touches the same code, the same way nixos-configs' own
wiki documents a "Status as of `<date>`" line rotting — see the
`fact-hygiene` skill). This wiki is where that current-state tracking
lives instead, plus the things a build spec has no natural home for:
decisions made while actually implementing it, mistakes hit along the
way, open questions, and how to actually run the thing.

**Not a replacement for `AGENTS.md`.** `AGENTS.md` is still the
agent-facing entry point and the one document worth reading cold before
touching this repo — including its own instruction to say when it looks
wrong rather than build around it silently. This wiki is for everything
that document isn't the right place for.

**Index over restatement**, same rule as the repo this was adapted from:
pages here link to the real source (a code comment, `AGENTS.md` itself, an
issue) rather than copying it. The one exception is [testing.md](testing.md),
which — like nixos-configs' `homelab/` usage pages — documents *doing*
something (running the app on a device) rather than a fact that lives in a
file, so it holds real procedural content instead of just links.

## Common tasks

| I want to... | Start here |
|---|---|
| see what's actually built and verified vs. only specified | [status.md](status.md) |
| understand the real package layout as it stands right now | [architecture.md](architecture.md) |
| run or test the app on an emulator or device | [testing.md](testing.md), skill [`signing-and-log-hygiene`](../.claude/skills/signing-and-log-hygiene/SKILL.md) |
| check whether a bug or decision is already tracked | [open-threads.md](open-threads.md), skill [`investigate-bug`](../.claude/skills/investigate-bug/SKILL.md) |
| propose filing a bug found in passing | skill [`propose-issue`](../.claude/skills/propose-issue/SKILL.md) |
| avoid a mistake this repo has already made once | [traps-and-skills.md](traps-and-skills.md) |
| keep this wiki honest after a change | skill [`wiki-sync`](../.claude/skills/wiki-sync/SKILL.md) |

## Pages

- [Status](status.md) — phase-by-phase: what `BUILD_PLAN.md` specifies,
  what's actually built, and what's been verified (by running the actual
  command, dated) rather than assumed from the spec text alone.
- [Architecture](architecture.md) — the real package layout as it stands,
  cross-referenced against `AGENTS.md` §3's target tree, and any place the
  two have already diverged.
- [Traps & skills](traps-and-skills.md) — mistakes that have actually
  happened building this app, and the skill that holds the general form of
  each.
- [History](history.md) — decisions made while implementing that
  `AGENTS.md` doesn't narrate, and anything specified but later changed.
- [Open threads](open-threads.md) — open questions and known gaps, plus
  anything tracked as a GitHub issue.
- [Testing](testing.md) — how to actually build, install, and exercise the
  app on an emulator or device; the one page here allowed to hold real
  procedural content rather than just links.
- [Wiki style guide](styleguide.md) — this wiki's own house style.

## Keeping this from rotting

No CI ties these pages to the code they describe except the mechanical
checks in [`scripts/check_wiki.py`](scripts/check_wiki.py) (links, anchors,
skill names, a phase's claimed status against what's actually in the
tree) — see that script's own docstring for exactly what it does and
doesn't catch. Everything else is the same rule `AGENTS.md` holds itself
to: whichever change makes a page stale corrects it in the same change,
not as a follow-up. Skill [`wiki-sync`](../.claude/skills/wiki-sync/SKILL.md)
is the checklist for that — as of **2026-09-05**, not just a checklist
someone might remember to run: skill
[`submit-a-pr`](../.claude/skills/submit-a-pr/SKILL.md)'s own step 2 runs
it, by name, before a branch is pushed, and
[`git-guard-pretooluse.sh`](../.claude/hooks/git-guard-pretooluse.sh)
nudges a second time at `gh pr create` if the branch added or removed an
`app/src/` file with nothing under `wiki/` alongside it. Neither is a
substitute for actually running the procedure deliberately — see that
hook's own comment for exactly what it can and can't catch.
