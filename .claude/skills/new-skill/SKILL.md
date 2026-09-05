---
name: new-skill
description: How to write a new SKILL.md in this repo, keeping its frontmatter description concise and accurate and its scope details elsewhere.
---

# Writing a new skill

## Applies to

Creating a new `.claude/skills/<name>/SKILL.md` in this repo, or editing an
existing one's frontmatter `description`. Not for editing a skill's body
content alone — only touch this when the description needs to change too.

## The rule

Frontmatter `description` is **one sentence: what the skill does or is
for.** Nothing else lives there — no file paths, no parenthetical scope
lists, no "use before/when X, but not Y" trigger conditions. All of that
goes in `## Applies to`, the section immediately after the H1 title, every
time.

This isn't just tone. The live skill listing every session gets shows only
`name: description` — the block above this file's own body, in this
conversation, is exactly that listing, and it's the only information
available when deciding whether a skill is relevant *before* loading it. A
description crammed with scope caveats reads as noise there. The body, by
contrast, is loaded in full the moment the skill actually fires — trigger
detail placed in `## Applies to` costs nothing and reads better once you're
already reading the whole file.

## Why

Compare a description that crams in scope with one that states purpose:

Bad — crams scope and triggers into the sentence itself:

> Guard covering keystore passwords, env dumps, and unscoped adb logcat
> invocations (see below for exact patterns and exceptions).

Good — states purpose; everything after "in this repo" moved into
`## Applies to` and the body:

> How to avoid printing release signing credentials or live notification
> content into the conversation in this repo.

(`signing-and-log-hygiene`, this repo's own worked example, landed on the
second form.)

## Steps

1. **Pick a name**: kebab-case, matching the directory exactly
   (`.claude/skills/<name>/SKILL.md`). `prune-permissions` and
   `signing-and-log-hygiene` are the existing precedents in this repo. One
   `SKILL.md` per directory; there's no separate registry to update — the
   directory is discovered automatically.
2. **Draft the description first, alone, before writing the body.** One
   sentence, stating what the skill does. Then test it by covering the body
   and asking: from the name plus this one sentence, would a session
   deciding whether to load this skill understand what it's for? If the
   honest answer needs a second clause — "…but only when X" or a
   parenthetical — that clause belongs in `## Applies to`, not here.
3. **Write `## Applies to` immediately after the title.** This is where all
   of the following belongs, wherever it exists for this skill: which
   files or situations trigger it, explicit non-triggers, named example
   files, exceptions to the general rule.
4. **Write the rest of the body** in whatever shape the task actually
   needs, picking only sections that earn their place: `Why this exists`
   (with a real, verifiable story where there is one — don't invent a dated
   incident that didn't happen), `Steps`/`Procedure`, gotchas specific to
   the task, `See also` linking sibling skills. Cite real files and
   commands, not invented ones — grep to confirm a path before naming it.
5. **Re-read the description against the finished body.** Descriptions get
   written first and bodies grow while writing; if the body ends up
   covering more, or less, than the description claims, fix the
   description to match. It has to stay accurate, not merely short —
   undersold and overclaimed are both wrong.
6. **Check it next to its siblings.** Skim a few other `SKILL.md`
   frontmatters for length and tone. If this one reads noticeably longer or
   more parenthetical than its neighbors, it hasn't had this treatment yet.

## See also

- Any existing `.claude/skills/*/SKILL.md` in this repo — read a couple
  before writing a new one; they're the worked examples, not this file's
  prose about them.
