# Wiki style guide

_Last modified: 2026-09-08_

## Contents

- [The paired-page convention](#the-paired-page-convention)
- [Which half does a fact go in](#which-half-does-a-fact-go-in)
- [Page shape](#page-shape)
- [Naming and layout](#naming-and-layout)
- [Writing rules](#writing-rules)
- [Linking](#linking)
- [Before you land a wiki change](#before-you-land-a-wiki-change)
- [See also](#see-also)

Read this before adding a page or reorganizing links.

## The paired-page convention

Every subject here exists twice:

- **`<page>.md`** — written for a human contributor. Task-first, scannable,
  short. Answers "what do I need to know to do this?"
- **`<page>-4llm.md`** — written for an agent loading context. Dense,
  telegraphic, exhaustive. Holds the reasoning chains, the dated
  deviations, the incident write-ups, the provenance.

Both are real pages with the same structural requirements (date line,
`## Contents`, working links). The `-4llm` half is not a draft, an
appendix, or a dumping ground — it is the material deliberately kept out
of the article so the article stays usable.

**When you add a page, add both.** A subject with only an article loses
its history the first time someone compresses it; a subject with only a
companion has no entry point.

## Which half does a fact go in

A fact lives in **exactly one** of the pair. Duplicating it means one
copy rots first and nothing catches which.

| Goes in the article | Goes in the companion |
|---|---|
| the current answer | how it got to be the answer |
| the command to run | what running it does and doesn't prove |
| "X is checked twice" | the bug that made the second check necessary |
| a short caveat | the measurement behind the caveat |
| the rule | the two options weighed, and why the other lost |

The article links to the companion once, in "See also". The companion
links back where a specific section is the entry point.

If you are editing and can't tell which half something belongs in: does a
contributor need it to *do the task*, or to *understand a past decision*?
The first is the article.

## Page shape

Every page — both halves — opens exactly like this:

```
# Page title

_Last modified: 2026-09-08_

## Contents

- (one bullet per `##` heading, generated)
```

- **The date line is mandatory**, absolute, and bumped to today by
  whoever edits the page's actual content. A purely mechanical touch (a
  `gen-contents` run, a typo fix) doesn't need it. The checker verifies
  the line exists and isn't in the future; it cannot verify the date is
  still *true*. That half is yours.
- **The `## Contents` list is generated, not hand-written.** After adding,
  renaming, or removing a heading:

  ```sh
  python3 wiki/scripts/check_wiki.py gen-contents wiki/<page>.md
  ```

  It implements GitHub's own heading-slug algorithm and is idempotent.
  Don't hand-derive slugs.

## Naming and layout

- `wiki/*.md`, flat, no subdirectories.
- kebab-case, matching the subject exactly (`open-threads.md`).
- The companion is the article's name plus `-4llm`, same directory.
- `README.md` is reserved for a directory's own index.
- If a page ever grows a deep-dive that doesn't belong in its summary,
  the pattern is `wiki/<name>/README.md` with siblings named after their
  subjects. Don't reach for it until an actual deep-dive exists.

## Writing rules

- **Index over restatement.** Link to the real source — a code comment,
  `AGENTS.md`, a skill, an issue — rather than copying it. If a page
  accumulates paragraphs arguing a fact instead of linking to it, that
  fact probably belongs in the linked file's own header comment.
- **Two pages are standing exceptions**, both with reasons recorded:
  [testing.md](testing.md), because it documents *doing* something where
  the source is the act, not a file; and [architecture.md](architecture.md),
  which holds the canonical tree. The second was granted **to end a
  restatement, not to add one** — see
  [styleguide-4llm.md](styleguide-4llm.md). Neither is licence to add a
  third without the same kind of reason written down.
- **Never mirror state owned by another system.** No table of open
  issues, no copy of anything `gh` can answer live. Link to the
  authority; keep only what the authority doesn't know.
- **Dates are absolute** (`2026-09-05`, never "today" or "recently"). It
  is the only thing that lets a stale claim be recognized as stale from
  its own text.
- **A "verified" claim names the command and the date.** Skill
  [`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md).
- **See-also sections point two ways**: sideways to sibling pages, and
  outward to the general form of a trap where one exists — usually a
  skill. The page stays the specific instance; the skill stays the
  reusable lesson.

## Linking

- Relative paths, recomputed if a page ever moves into a subdirectory.
- Link in both directions where it makes sense.
- Verify a link resolves before leaving it — the checker's `links` and
  `anchors` checks catch a broken file target and a broken `#fragment`
  respectively, and both run as part of `check`.

## Before you land a wiki change

```sh
just wiki-lint          # or: python3 wiki/scripts/check_wiki.py check
```

Fix any finding. `gen-contents <page>` is the fix for a stale Contents
block.

Then the rule none of that enforces: **whichever change makes a page
stale corrects it in the same change, not as a follow-up.** Skill
[`wiki-sync`](../.claude/skills/wiki-sync/SKILL.md) is the checklist, and
skill [`submit-a-pr`](../.claude/skills/submit-a-pr/SKILL.md)'s step 2
runs it by name before a branch is pushed.

The checks are structural. They will not notice that a sentence became
untrue.

## See also

- [styleguide-4llm.md](styleguide-4llm.md) — the dense companion: the
  full reasoning behind each rule, the exception grants, and this
  guide's provenance.
- [README.md](README.md) — the wiki's own index.
- [`wiki/scripts/check_wiki.py`](scripts/check_wiki.py) — the checks
  these rules exist to make possible.
