#!/usr/bin/env python3
"""Static checks of wiki/ (and AGENTS.md, the one file outside wiki/ that
carries wiki-shaped claims) against the actual source tree, for claims that
silently go stale after a refactor -- a file renamed, a phase's status
claimed without re-checking it, a skill renamed. Nothing about `gradle
build` or `nix flake check` reads prose, so a doc can say something the
tree has stopped agreeing with and nothing catches it.

Ported and cut down from nixos-configs' own `wiki/scripts/check_wiki.py`,
which checks a multi-host NixOS module tree (imports, a category-classes
table, a hosts table, sops enrollment, Caddy routes) -- none of which this
single-Gradle-module repo has an equivalent of. What carries over
unchanged is the fully general half (a link either resolves or it doesn't,
an anchor either matches a real heading or it doesn't); what's new is
`phases`, this repo's own version of the same idea nixos-configs' `hosts`
check applies to boot status: a claimed status checked against the tree,
not trusted from prose.

This does NOT replace human judgement about whether a change actually needs
a wiki update -- see skill `wiki-sync` for that. It only catches the
mechanical case: a claim phrased as a checkable fact and no longer true.
Historical claims ("removed 2026-09-05") are deliberately NOT the target --
this repo keeps those on purpose (AGENTS.md's own convention: a bug
recorded in a comment stays in the file), and a script can't tell
historical prose from a live claim by itself, so it checks structured,
extractable facts only:

  phases    wiki/status.md's "Phase status" table (Phase | ... | Built |
            Verified) against PHASE_FILES below (a hand-maintained map,
            same shape as nixos-configs' own HOSTS constant, from AGENTS.md
            §6's own component lists) -- for each phase claimed "Yes",
            every one of its key files must actually exist somewhere under
            app/src (found by filename, not by exact path, so a file
            genuinely moved to a different package doesn't false-positive).
            A phase claimed "No" whose files all exist anyway is a REVIEW
            finding, not a hard failure -- files existing doesn't prove the
            phase's real acceptance criteria in AGENTS.md §6 pass, only
            that status.md may be under-claiming and is worth a look.

  skills    Every "skill `name`"/"`name` skill" mention across wiki/ and
            AGENTS.md against real `.claude/skills/<name>/` directories --
            a rename or removal silently breaks every doc that told
            someone to read the old name.

  gradle    Every backtick `gradle <task...>` mention across wiki/ and
            AGENTS.md against KNOWN_GRADLE_TASKS below. Weaker than
            nixos-configs' `recipes` check (which derives its valid-name
            set mechanically from .justfile) -- this repo has no single
            file listing valid Gradle task names, so KNOWN_GRADLE_TASKS is
            hand-maintained; treat a false "unknown" here as a prompt to
            add the task to the list, not necessarily a real doc bug.

  links     Every relative markdown link (`[text](target)`) across wiki/
            and AGENTS.md resolves to a real file. Fully general -- a link
            target either exists or it doesn't, no judgement call needed.

  anchors   Every `#fragment` on a markdown link -- same-file or into
            another page -- against a real GitHub-slug computation of the
            target page's own headings (`github_slug`, the exact algorithm
            nixos-configs reverse-engineered against real rendered GitHub
            output).

  contents  Every page's `## Contents` block against what its own `##`
            headings say right now -- catches a heading renamed, added, or
            removed without the list above it following along.

  check     Runs all six of the above.

    check_wiki.py phases       [repo-root]
    check_wiki.py skills       [repo-root]
    check_wiki.py gradle       [repo-root]
    check_wiki.py links        [repo-root]
    check_wiki.py anchors      [repo-root]
    check_wiki.py contents     [repo-root]
    check_wiki.py check        [repo-root]
    check_wiki.py gen-contents <file.md> [file.md ...]

repo-root defaults to two directories up from this script (wiki/scripts/ ->
wiki/ -> repo root). `gen-contents` is a fixer, not a checker: it rewrites
each given page's `## Contents` block in place to match that page's real
headings -- the actual fix for a `contents` finding.
"""
import re, sys, pathlib

# Phase number -> key filenames from AGENTS.md §6's own component lists,
# matched by filename anywhere under app/src rather than a fixed path, so a
# file moved to a different package doesn't false-positive. SECURITY.md is
# the one entry that lives at the repo root instead of under app/src.
PHASE_FILES = {
    0: (['App.kt', 'AppContainer.kt', 'SafeLog.kt', 'MainActivity.kt'], 'app/src'),
    1: (['NotificationPayload.kt', 'Rule.kt', 'Decision.kt', 'RuleEngine.kt',
         'SecretDetector.kt', 'Deduplicator.kt'], 'app/src'),
    2: (['NotificationTtsListener.kt', 'NotificationExtractor.kt',
         'SpeechQueue.kt', 'AndroidTtsEngine.kt', 'AudioFocusManager.kt'], 'app/src'),
    3: (['SettingsRepository.kt', 'RuleRepository.kt'], 'app/src'),
    4: (['OutputRouteGate.kt'], 'app/src'),
    5: (['SECURITY.md'], '.'),
}

# Hand-maintained since there's no .justfile-equivalent single source of
# truth for valid Gradle task names in this repo -- see this module's
# docstring. Includes the module-qualified form AGENTS.md §6 itself uses.
KNOWN_GRADLE_TASKS = {
    'assembleDebug', 'assembleRelease', 'testDebugUnitTest', 'installDebug',
    'build', 'clean', 'tasks', ':app:processDebugMainManifest',
}


def repo_root(argv):
    if len(argv) > 1:
        return pathlib.Path(argv[1]).resolve()
    return pathlib.Path(__file__).resolve().parents[2]


def doc_files(root):
    """Every markdown file the checks below scan: all of wiki/ (recursive)
    plus AGENTS.md itself."""
    return sorted(root.joinpath('wiki').rglob('*.md')) + [root / 'AGENTS.md']


STATUS_ROW = re.compile(
    r'^\|\s*(?P<phase>\d+)\s*(?:—|-)[^|]*\|(?P<spec>[^|]*)\|'
    r'\s*(?P<built>Yes|No)\s*\|(?P<verified>[^|]*)\|\s*$', re.M)


def claimed_phase_status(root):
    """phase number -> True (Built=Yes) / False (Built=No), read from
    wiki/status.md's Phase status table."""
    page = root / 'wiki' / 'status.md'
    if not page.exists():
        return {}
    return {int(m.group('phase')): m.group('built') == 'Yes'
            for m in STATUS_ROW.finditer(page.read_text())}


def check_phases(root):
    """wiki/status.md's Phase status table against PHASE_FILES -- see this
    module's docstring for what's hard-checked vs. REVIEW."""
    claimed = claimed_phase_status(root)
    findings = []
    for phase, built in sorted(claimed.items()):
        if phase not in PHASE_FILES:
            continue  # no key-file list to check this phase against
        filenames, search_root = PHASE_FILES[phase]
        base = root / search_root
        missing = [f for f in filenames
                   if not any(base.rglob(f)) and not (base / f).exists()]
        if built and missing:
            findings.append(
                f"MISSING FILE(S)  wiki/status.md: Phase {phase} is marked "
                f"Built=Yes but {missing} not found anywhere under "
                f"{search_root}/")
        if not built and not missing:
            findings.append(
                f"REVIEW  wiki/status.md: Phase {phase} is marked Built=No "
                f"but every one of {filenames} already exists under "
                f"{search_root}/ -- confirm the phase truly isn't done, or "
                f"update status.md")
    return findings


# Either word order this repo actually uses: "skill `name`" or "`name`
# skill". Plain "the `name`" is deliberately not matched -- most backtick
# tokens in this wiki are code identifiers, not skill names.
SKILL_MENTION = re.compile(r'[Ss]kill `([a-zA-Z][\w-]*)`|`([a-zA-Z][\w-]*)` skill\b')


def check_skills(root):
    """Every "skill `name`" / "`name` skill" mention across wiki/ and
    AGENTS.md against real `.claude/skills/<name>/` directories."""
    skills_dir = root / '.claude' / 'skills'
    real = ({p.name for p in skills_dir.iterdir() if p.is_dir()}
            if skills_dir.exists() else set())

    findings = []
    for path in doc_files(root):
        for m in SKILL_MENTION.finditer(path.read_text()):
            name = m.group(1) or m.group(2)
            if name not in real:
                findings.append(
                    f"UNKNOWN SKILL  {path}: '{name}' has no "
                    f".claude/skills/{name}/ directory")
    return findings


GRADLE_MENTION = re.compile(r'`gradle ([^`]+)`')


def check_gradle(root):
    """Every backtick `gradle <task...>` mention across wiki/ and
    AGENTS.md against KNOWN_GRADLE_TASKS."""
    findings = []
    for path in doc_files(root):
        for m in GRADLE_MENTION.finditer(path.read_text()):
            tokens = m.group(1).split()
            if not tokens or '<' in m.group(1):
                continue  # a template, not a literal invocation
            name = tokens[0]
            if name not in KNOWN_GRADLE_TASKS:
                findings.append(
                    f"UNKNOWN TASK  {path}: `gradle {m.group(1)}` -- "
                    f"'{name}' is not in KNOWN_GRADLE_TASKS (add it if "
                    f"it's real)")
    return findings


# `[text](target)` -- the target only; `text` isn't checked against anything.
MD_LINK = re.compile(r'\[[^\]]*\]\(([^)]+)\)')


def check_links(root):
    """Every relative markdown link across wiki/ and AGENTS.md resolves to
    a real file. Skips `http(s)://`/`mailto:` targets and a pure in-page
    anchor (`(#see-also)`, no file component)."""
    findings = []
    for path in doc_files(root):
        for m in MD_LINK.finditer(path.read_text()):
            target = m.group(1).strip()
            if target.startswith(('http://', 'https://', 'mailto:')):
                continue
            file_part = target.split('#', 1)[0].strip('<>')
            if not file_part:
                continue  # pure in-page anchor
            resolved = path.parent / file_part
            if not resolved.exists():
                findings.append(
                    f"BROKEN LINK  {path}: ({target}) -> {resolved} does "
                    f"not exist")
    return findings


FENCE = re.compile(r'^(```|~~~)')
HEADING = re.compile(r'^(#{1,6})\s+(.+?)\s*$')
CONTENTS_HEADING = re.compile(r'^##\s+Contents\s*$', re.M)
CONTENTS_ITEM = re.compile(r'^-\s+\[(?P<text>.+)\]\(#(?P<slug>[^)]+)\)\s*$', re.M)
NEXT_HEADING = re.compile(r'^##\s+', re.M)


def _iter_headings(text):
    """Yields (level, raw_heading_text) for every real heading line -- `#`
    through `######` -- in document order, skipping anything inside a
    fenced code block."""
    in_fence = False
    for line in text.splitlines():
        if FENCE.match(line):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        m = HEADING.match(line)
        if m:
            yield len(m.group(1)), m.group(2)


def github_slug(raw, seen):
    """GitHub's own heading-anchor algorithm: lowercase, drop every
    character that isn't a letter/digit/space/hyphen/underscore, then turn
    each remaining space into a hyphen. `seen` is a dict this function
    mutates so repeated headings on one page get GitHub's own `-1`/`-2`
    suffix instead of colliding; pass a fresh `{}` per page."""
    s = raw.lower()
    s = ''.join(c for c in s if c.isalnum() or c in ' -_')
    s = s.replace(' ', '-')
    n = seen.get(s, 0)
    seen[s] = n + 1
    return s if n == 0 else f'{s}-{n}'


def _page_anchors(path):
    seen = {}
    return {github_slug(text, seen) for _, text in _iter_headings(path.read_text())}


def check_anchors(root):
    """Every `#fragment` on a markdown link resolves to a real heading on
    the target page, per `github_slug` above. Skips a target `check_links`
    would already flag as a broken file path."""
    cache = {}
    findings = []
    for path in doc_files(root):
        for m in MD_LINK.finditer(path.read_text()):
            target = m.group(1).strip()
            if target.startswith(('http://', 'https://', 'mailto:')):
                continue
            if '#' not in target:
                continue
            file_part, _, frag = target.partition('#')
            file_part = file_part.strip('<>')
            if not frag:
                continue
            resolved = (path.parent / file_part) if file_part else path
            if not resolved.exists():
                continue  # check_links already reports this
            if resolved not in cache:
                cache[resolved] = _page_anchors(resolved)
            if frag not in cache[resolved]:
                findings.append(
                    f"BROKEN ANCHOR  {path}: ({target}) -> {resolved} has "
                    f"no heading matching #{frag}")
    return findings


def expected_contents_items(text):
    """[(heading_text, slug), ...] a fresh `## Contents` block for this
    page should list, in document order -- level-2 headings only,
    excluding a heading literally named `Contents`."""
    seen = {}
    items = []
    for level, text_ in _iter_headings(text):
        slug = github_slug(text_, seen)
        if level == 2 and text_.strip() != 'Contents':
            items.append((text_, slug))
    return items


def actual_contents_items(text):
    m = CONTENTS_HEADING.search(text)
    if not m:
        return None
    rest = text[m.end():]
    end = NEXT_HEADING.search(rest)
    section = rest[:end.start()] if end else rest
    return [(mm.group('text'), mm.group('slug'))
            for mm in CONTENTS_ITEM.finditer(section)]


def check_contents(root):
    """Every page's `## Contents` block matches what
    `expected_contents_items` would generate from its own headings right
    now. Skips a page with no `## Contents` section."""
    findings = []
    for path in sorted(root.joinpath('wiki').rglob('*.md')):
        text = path.read_text()
        actual = actual_contents_items(text)
        if actual is None:
            continue
        if actual != expected_contents_items(text):
            findings.append(
                f"STALE CONTENTS  {path}: its '## Contents' list doesn't "
                f"match its own headings -- fix with `gen-contents {path}`")
    return findings


CONTENTS_ITEM_LINE = re.compile(r'^-\s+\[.+\]\(#[^)]+\)\s*$')


def regenerate_contents(path):
    """Rewrites `path`'s `## Contents` block in place to match its current
    headings exactly -- inserting one right after the title if the page
    doesn't have one yet. Idempotent."""
    text = path.read_text()
    items = expected_contents_items(text)
    block = '## Contents\n\n' + '\n'.join(f'- [{t}](#{s})' for t, s in items) + '\n'
    m = CONTENTS_HEADING.search(text)
    if m:
        rest = text[m.end():]
        lines = rest.splitlines(keepends=True)
        i = 0
        while i < len(lines) and lines[i].strip() == '':
            i += 1
        while i < len(lines) and CONTENTS_ITEM_LINE.match(lines[i]):
            i += 1
        tail = m.end() + sum(len(l) for l in lines[:i])
        new_text = text[:m.start()] + block + text[tail:]
    else:
        lines = text.splitlines(keepends=True)
        if not lines or not lines[0].startswith('# '):
            print(f"SKIP {path}: no '# Title' line to insert Contents after")
            return
        insert_at = 1
        while insert_at < len(lines) and lines[insert_at].strip() == '':
            insert_at += 1
        new_text = ''.join(lines[:insert_at]) + block + '\n' + ''.join(lines[insert_at:])
    if new_text != text:
        path.write_text(new_text)
        print(f"updated {path}")
    else:
        print(f"unchanged {path}")


def main():
    if len(sys.argv) > 1 and sys.argv[1] == 'gen-contents':
        if len(sys.argv) < 3:
            print("usage: check_wiki.py gen-contents <file.md> [file.md ...]")
            sys.exit(2)
        for p in sys.argv[2:]:
            regenerate_contents(pathlib.Path(p))
        sys.exit(0)

    cmd = sys.argv[1] if len(sys.argv) > 1 else 'check'
    root = repo_root([sys.argv[0]] + sys.argv[2:])

    cmds = ('phases', 'skills', 'gradle', 'links', 'anchors', 'contents', 'check')
    if cmd not in cmds:
        print(__doc__)
        sys.exit(2)

    findings = []
    if cmd in ('phases', 'check'):
        findings += check_phases(root)
    if cmd in ('skills', 'check'):
        findings += check_skills(root)
    if cmd in ('gradle', 'check'):
        findings += check_gradle(root)
    if cmd in ('links', 'check'):
        findings += check_links(root)
    if cmd in ('anchors', 'check'):
        findings += check_anchors(root)
    if cmd in ('contents', 'check'):
        findings += check_contents(root)

    for f in findings:
        print(f)
    hard = [f for f in findings if not f.startswith('REVIEW')]
    if not findings:
        print(f"{cmd}: no findings")
    elif not hard:
        print(f"{cmd}: only REVIEW findings (heuristic, needs a human look) "
              f"-- not failing on those alone")
    sys.exit(1 if hard else 0)


if __name__ == '__main__':
    main()
