# Wiki style guide

## Contents

- [Shape](#shape)
- [Naming](#naming)
- [Content shape](#content-shape)
- [Linking](#linking)
- [Keeping this from rotting](#keeping-this-from-rotting)
- [See also](#see-also)

How this wiki is organized and written. Read this before adding a page or
reorganizing links. Adapted from nixos-configs' `wiki/styleguide.md`,
collapsed from that repo's multi-tier hierarchy (cross-cutting pages,
per-category pages, a usage tier, an escape-hatch subdirectory) down to
what a single-module app actually needs: one flat tier, plus one page
that's the escape hatch's whole reason to exist elsewhere in that repo —
here it's just [testing.md](testing.md).

## Shape

**`wiki/*.md`**, flat, no subdirectories yet. Every page in
[README.md](README.md)'s own Pages list. If a page ever grows a
deep-dive that doesn't belong in its summary (nixos-configs did this once,
for `shell-config` → `blesh.md`/`carapace.md`), the pattern to copy then
is: the page becomes `wiki/<name>/README.md`, and each deep-dive gets its
own sibling file named after its subject. Don't reach for that until an
actual deep-dive exists to justify it.

## Naming

- kebab-case, matching the subject exactly (`open-threads.md`,
  `traps-and-skills.md`).
- `README.md` is reserved for a directory's own index — not used as a
  single-topic page name unless the escape hatch above is ever needed.

## Content shape

- **Every page opens with a `## Contents`** — a bullet list of section
  links, one per `##` heading, right after the title and before any intro
  prose. Each link's target is GitHub's own heading-slug algorithm applied
  to that heading's text (lowercase, strip everything that isn't a
  letter/digit/space/hyphen/underscore, then turn each space into a
  hyphen). **Don't hand-derive this** — `wiki/scripts/check_wiki.py`
  implements the exact algorithm and two checks that use it (`anchors`:
  every `#fragment` link resolves to a real heading; `contents`: every
  page's Contents list matches its own current headings). After adding,
  renaming, or removing a heading, run `python3 wiki/scripts/check_wiki.py
  gen-contents <page>` to regenerate it correctly rather than editing by
  hand; it's idempotent.
- **Index over restatement.** Link to the real source — a code comment,
  `AGENTS.md` itself, a skill, a GitHub issue — rather than copying its
  content into the wiki page. If a page starts accumulating paragraphs
  that argue a fact instead of linking to it, that fact probably belongs
  in the linked file's own header comment instead.
- **[testing.md](testing.md) is the one exception**, the same way
  nixos-configs' `homelab/` usage pages are: it documents *doing*
  something (building, installing, exercising the app on a device) where
  the real source is the act itself, not a file to link to. It's allowed
  to hold real procedural content, not just links.
- **Dates are absolute** (`2026-09-05`, never "today" or "last week") —
  the only thing that lets a stale claim be recognized as stale by its own
  text rather than by someone noticing the drift by chance.
- **See-also sections point two ways**: sideways to sibling pages, and
  outward to the general form of a trap where one exists — usually a
  skill. The wiki page stays the specific instance; the skill stays the
  reusable lesson.

## Linking

- Relative paths, recomputed for actual file depth if a page ever moves
  into a subdirectory.
- Link in both directions where it makes sense: [README.md](README.md)
  links down into a page, and that page links back to related pages.
- Verify a link resolves before leaving it. `wiki/scripts/check_wiki.py
  links` catches a broken file target mechanically; `anchors` catches a
  broken `#fragment`. Both run as part of `check`.

## Keeping this from rotting

The mechanical checks in `wiki/scripts/check_wiki.py` catch a moved file, a
renamed heading, an unknown skill name, or a phase's claimed status
disagreeing with what's actually in `app/src/main/java`. They do **not**
catch a fact that's simply become untrue in prose (a claim about what a
class does, a "why" that no longer applies) — that's a human/agent
judgment call, same as nixos-configs' own script draws this exact line.
The rule for everything the script can't see: whichever change makes a
page stale corrects it in the same change, not as a follow-up. Skill
[`wiki-sync`](../.claude/skills/wiki-sync/SKILL.md) is the checklist for
noticing when that applies.

## See also

- [README.md](README.md) — the wiki's own top-level index.
- [`wiki/scripts/check_wiki.py`](scripts/check_wiki.py) — the mechanical
  checks this page's rules exist to make possible.
- Skill [`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md) — the
  general discipline behind "dates are absolute" and "index over
  restatement," applied beyond just this wiki.
