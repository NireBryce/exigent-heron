---
name: fact-hygiene
description: How to write a specific fact, a dated status snapshot, or a cross-reference to something elsewhere in this repo without it quietly rotting into a false claim.
---

# Writing facts, dated status, and cross-references without them rotting silently

## Applies to

Three related but distinct things, mainly in `AGENTS.md` and code comments
(this repo has no wiki):

1. **A specific, checkable fact about an external system** (an Android
   API's actual behavior on a given SDK level, a device/emulator you
   tested on, a library's actual runtime behavior) — stated with more
   confidence than what was actually observed this session.
2. **A "Status as of `<date>`" / "as of `<date>`" snapshot line**, or a
   Phase acceptance criterion marked done — asserting the *current* state
   of something that can change (a test passes, a manifest check holds,
   a phase's acceptance criteria are met), where the date or the checkmark
   makes it look freshly verified but nothing re-runs it as the code
   around it changes.
3. **A cross-reference to something else in this repo** — a class name, a
   file, a skill — mentioned in a comment or in `AGENTS.md` as a
   currently-true fact ("see `SafeLog`", "`RuleEngine` handles X"), where
   the thing referenced can be renamed or removed by a *later, unrelated*
   change that has no reason to know this comment exists.

**Not this skill, write these freely and generously — this is most of
what a good comment or spec section actually is:**

- Design rationale — *why* a choice was made, trade-offs weighed, an
  alternative rejected. That's the agent's own reasoning, not a claim
  about external reality; it doesn't need "I watched this happen."
- **Event dates** — *on `<date>`, X happened/was decided/was tried and
  failed.* A past event is fixed the moment it happens; it can't go
  stale. This is the opposite of category 2 above — the tell is whether
  the sentence describes something that happened, or asserts something
  that is currently true.
- Plans, hypotheses, and open questions — clearly labeled as such (a
  hypothesis stated as a hypothesis is not the failure mode below; a
  hypothesis, or a stale status snapshot, stated as a confirmed current
  fact is).
- A plain comment describing what the code in front of you does.

## Why this exists

**The cross-reference trap (category 3), found live in this repo,
2026-09-05.** `AGENTS.md`'s Phase 0 and Phase 1 acceptance criteria
(§6) both said `./gradlew assembleDebug` / `./gradlew testDebugUnitTest`
— accurate when that text was written, in the commit that added the build
spec. A later, unrelated commit ("Phase 0: Kotlin/Android project
skeleton, Nix dev shell...") decided there would be no `gradlew` in this
repo at all — `flake.nix`'s own shellHook says so explicitly ("No gradlew
in this repo by design — use the 'gradle' on PATH") — and had no reason to
touch §6's acceptance criteria while making that call. The two
`./gradlew` mentions sat there, silently wrong, until a session hunting
for something unrelated (adapting a different repo's skills) happened to
read that section closely enough to notice the contradiction. Nobody had
re-run those exact commands against the actual repo since the decision
that broke them.

This is the same mechanism nixos-configs' own version of this skill
documents for its wiki (a removed host's name surviving in comments that
had no reason to be touched by the commit that removed it) — it isn't
specific to wikis, or to this one incident. Anything phrased as settled —
a command, a class name, a "done" checkbox — stops looking like something
that needs checking, and survives long after the thing it describes has
changed.

## The rule

**Category 1** (a specific external fact): state only what you watched
happen, in this session, by a named method — a command's actual output, a
real build/test run, a file you read. Everything else — inferred from a
similar API, carried over from an earlier comment, assumed because it's
the common case — gets an explicit qualifier (`UNVERIFIED`, `not confirmed
on-device`, `assumed from ...`) *in the same sentence*, not hedged once in
a separate caveat paragraph a later trim or copy can drop while the
confident sentence survives.

**Category 2** (a dated status line or a "done" marker): a date or a
checkmark on a claim about current state is not a freshness guarantee —
it's when someone last checked, and it starts going stale the instant the
code around it changes. Before writing or trusting an acceptance criterion
or a "tested on ..." line for anything checkable right now, re-derive it
(actually run the command) rather than repeat it.

**Category 3** (a cross-reference): a name written into `AGENTS.md` or a
code comment as a durable fact ("`X` handles `Y`", "run `./gradlew Z`") is
a live pointer, not prose that stays true on its own — a later, unrelated
change can invalidate it with nothing watching for the break. See
"Preventing it" below for the concrete grep.

## Preventing it

1. **For each specific noun in a "why" comment or a spec section** — a
   command, a class name, a file path, an SDK/API level — name the exact
   command or artifact that showed you this, from *this* session. If you
   can't, it's inferred: say so inline, or leave the detail out.
2. **Before restating or copying forward any acceptance criterion or
   "tested"/"verified" claim**, check whether it's still true right now —
   run the command, don't trust that it was true when written.
3. **When renaming or removing something with a name other code or docs
   might mention** (a class, a file, a Gradle task, a skill) — grep for
   that exact name across `AGENTS.md` and `app/src`, not just the files
   the change itself touches, before considering the removal finished. A
   stale mention with no "(removed — see history)" note is the tell that
   it was missed.
4. **Precision or a "done" marker beyond what you actually checked is a
   liability, not a feature**, in text that reads as settled. The vaguer
   true statement (or the explicitly-marked-stale one) outlives the
   sharper false one.

## Catching it when a claim like this turns out wrong

1. Grep the fact (or the stale claim's exact wording) across the whole
   tree — `grep -rn "<old command/name>" AGENTS.md app/src` — a
   dated-sounding or settled-sounding claim gets copied or left standing
   longer than an obviously-uncertain one does, exactly because it reads
   as safe to reuse verbatim.
2. Fix it everywhere it appears in the same change, and correct the
   *confidence level*, not just the content — replace a stale command or
   claim with either the verified current fact or an explicit
   `UNVERIFIED`/"since removed" marker, not just one unqualified claim for
   another. For anything under `wiki/`, skill `wiki-sync` is the dedicated
   checklist (plus `wiki/scripts/check_wiki.py`'s mechanical checks); for
   `AGENTS.md` and code comments, which neither of those covers, a plain
   repo-wide grep is still the whole mechanism.
3. If a comment recorded *why* the wrong claim was believed, leave that
   reasoning visible rather than deleting it — the next reader benefits
   from seeing how a plausible-sounding claim got made.

## See also

- `AGENTS.md` §0 ("Where you think it's wrong, say so before writing
  code, not after") — this skill is the mechanical half of that same
  instinct, applied to text already in the tree rather than a new
  disagreement.
- `AGENTS.md` §6, Phase 0/1 acceptance criteria — the worked (mis)example
  this skill's own history section documents, and the two lines that were
  the actual fix.
