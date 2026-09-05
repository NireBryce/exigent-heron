---
name: propose-issue
description: How to propose filing a GitHub issue when you notice a genuine bug in this repo's own config/code/docs while working on something else.
---

# Proposing repo bugs for the issue tracker

## Applies to

You're doing something else — building a phase, reading a module, checking
a manifest — and you notice an actual defect along the way: a command in
`AGENTS.md` that doesn't work, a rule the code now contradicts, dead code.
Not something you were asked to look into (fix that directly and say so),
and not a style opinion (no failure scenario, no issue).

## This repo has no bug-tracking convention yet — a filed issue is the first one

Unlike a repo with an established lessons-learned log or wiki, the only
record a bug gets here right now is whatever a fix commit's message says,
or a comment left in the code describing what went wrong (AGENTS.md's own
"§0 ground rules" instinct — say what's wrong before working around it).
Neither of those makes a bug discoverable or trackable once the session
that noticed it ends. Filing a GitHub issue adds that: a real backlog
entry with a status, not a fact buried in git history.

Checked 2026-09-05: `NireBryce/exigent-heron` has issues enabled with a
real label set (`bug`, `documentation`, `enhancement`, `accessibility`,
`question`, `duplicate`, `invalid`, `wontfix`, `help wanted`, `good first
issue`) but zero issues filed — there's no backlog yet, so one bug going
unfiled is easy to lose entirely.

## This never extends to a third-party repo

Every `gh issue create` in this skill hardcodes `--repo
NireBryce/exigent-heron`. That's not incidental — this skill files in this
repo only, even for a bug whose real fix belongs upstream (AndroidX, a
library, AOSP itself). Filing there instead is a different, heavier
action — do it only if the user says so explicitly, not as an inferred
extension of this flow's own confirmation step.

## Why propose instead of just filing

Filing is outward-facing the moment `gh issue create` returns. Asking
first costs one confirmation; closing a wrongly-filed issue afterward
costs a round trip for nothing gained.

## Steps

1. **Verify it, don't recall it.** Re-open the file or re-run the command
   that shows the defect. A half-remembered impression is how a false bug
   gets filed.
2. **Check it isn't already tracked** — `investigate-bug`'s step 1 (`gh
   issue list --search`, plus a grep of `AGENTS.md`). Stop here if it's
   already covered.
3. **Ask, showing the real content.** Ask the user with the title and a
   short body already drafted — the decision should land on the actual
   text, not on a vague "should I file something?" — and name which label
   you'd use (`bug` for a defect, `documentation` for a doc that's wrong,
   `enhancement` for a gap that isn't strictly broken, `accessibility` if
   it affects the app's actual accessibility behavior).
4. **On yes:**

   ```sh
   gh issue create --repo NireBryce/exigent-heron \
     --title "..." --label bug \
     --body "..."
   ```

   Body: what's wrong, where (`file:line`), how you noticed it, and a fix
   sketch if one's obvious. End it with:

   ```
   🤖 Generated with [Claude Code](https://claude.com/claude-code)
   ```

   Report the issue URL back in your reply.
5. **On no:** don't file it, say so, and still leave whatever code comment
   the bug warrants regardless — declining the issue doesn't mean the
   knowledge should evaporate too.

## Calibrate

AGENTS.md §0 is explicit that this is a single-user, YAGNI-first app, not
a large team's backlog. Propose for what would actually bite the next
phase or violate one of AGENTS.md's hard rules (no logging notification
content, no committed keystore, no `INTERNET` permission, etc.) — not
every small wart noticed in passing. Mention the minor ones in your reply
and leave it at that; run this flow only on things worth a round-trip.
