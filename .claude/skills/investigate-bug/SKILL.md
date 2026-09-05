---
name: investigate-bug
description: How to check whether a reported bug or symptom is already a known, tracked issue before investigating it yourself.
---

# Checking before investigating

## Applies to

Someone reports an error, a crash, or unexpected/"weird" behavior in this
repo, and you're about to start reproducing or diagnosing it. Run this
**before** that — not after you've already found something and are
deciding whether to write it up (that's the `propose-issue` skill, the
filing side of this same problem; this is the checking side). Doesn't
apply when the user already points you at the specific cause or file —
there's nothing to check for in that case.

## Why this exists

Re-deriving a diagnosis from scratch — tracing through source, reproducing
on a device, hours of it — for something already filed and partly worked
is wasted effort the moment a `gh issue list --search` would have found
it. This repo (`NireBryce/exigent-heron`) has GitHub issues enabled with a
real label set but starts with zero issues filed — no backlog yet, which
is exactly why a check-first habit matters from the start rather than
being retrofitted after the first duplicate investigation actually
happens. (Adapted from nixos-configs' `investigate-bug` skill, which
exists there because that exact thing happened — a bug re-diagnosed for
hours before anyone checked it was already written up two days earlier.)

## Steps

1. **Before reproducing anything**, run `gh issue list --repo
   NireBryce/exigent-heron --search "<keywords>" --state all` with a
   couple of guesses from the report's own wording (symptom text, error
   message, class/function name), and grep `AGENTS.md`, `BUILD_PLAN.md`,
   and `wiki/` for the same keywords — `wiki/open-threads.md` is where a
   known gap or open question like this would already be tracked, and
   `wiki/traps-and-skills.md` is where a past mistake matching the
   symptom would already be written up with its general form.
2. **A hit means read it fully** before doing anything else. Pick up from
   where it left off (an untested fix, an open question, a "not yet
   confirmed" status) rather than re-deriving from zero. If it's stale or
   wrong, fix *that* rather than starting a parallel investigation.
3. **No hit**: proceed as normal — reproduce for real (on an emulator or
   device, per AGENTS.md's own phase acceptance criteria) rather than
   reasoning from source alone. Once something is actually diagnosed,
   don't leave it only in your reply: follow `propose-issue`'s flow to
   file it, and `fact-hygiene` for anything in `AGENTS.md` or a comment
   that the bug shows is now stale.
4. **State fixed vs. verified precisely.** A fix that hasn't actually been
   built and re-tested (`gradle assembleDebug`/`testDebugUnitTest`, or a
   real run on-device per the relevant Phase's acceptance criteria in
   BUILD_PLAN.md) is *in the tree*, not *fixed* — say which one, in the
   issue and your reply both, not just implied.

## See also

- `propose-issue` skill — the filing side of this same problem, for a bug
  you noticed rather than one that was reported to you.
- `fact-hygiene` skill — fixing anything a confirmed bug shows is now
  wrong in `AGENTS.md` or a code comment.
- `wiki-sync` skill — the same, for anything a confirmed bug shows is now
  wrong or missing in `wiki/`, such as `open-threads.md` needing a new
  entry or an existing one closed out.
