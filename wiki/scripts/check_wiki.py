#!/usr/bin/env python3
"""Static checks of wiki/ (plus AGENTS.md, and .claude/ for the two
link checks -- the files outside wiki/ that carry wiki-shaped claims)
against the actual source tree, for claims that silently go stale after a
refactor -- a file renamed, a phase's status claimed without re-checking
it, a skill renamed. Nothing about `gradle build` or `nix flake check`
reads prose, so a doc can say something the tree has stopped agreeing
with and nothing catches it.

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
            same shape as nixos-configs' own HOSTS constant, from each
            phase's own component list, originally BUILD_PLAN.md's before
            it was removed -- see wiki/history.md) -- for each phase
            claimed "Yes", every one of its key files must actually exist
            somewhere under app/src (found by filename, not by exact path,
            so a file genuinely moved to a different package doesn't
            false-positive). A phase claimed "No" whose files all exist
            anyway is a REVIEW finding, not a hard failure -- files
            existing doesn't prove the phase's real acceptance criteria
            pass, only that status.md may be under-claiming and is worth a
            look.

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

  recipes   Every backtick `just <recipe>` mention across wiki/ and
            AGENTS.md against the recipes .justfile actually defines.
            Mechanical, unlike `gradle` above: .justfile is a single file
            listing every valid name, so this needs no hand-maintained set.

  links     Every relative markdown link (`[text](target)`) across wiki/,
            AGENTS.md, and .claude/ resolves to a real file. Fully general
            -- a link target either exists or it doesn't, no judgement
            call needed, which is why this one check reaches into .claude/
            when the rest deliberately don't (see `link_files`).

  anchors   Every `#fragment` on a markdown link -- same-file or into
            another page -- against a real GitHub-slug computation of the
            target page's own headings (`github_slug`, the exact algorithm
            nixos-configs reverse-engineered against real rendered GitHub
            output).

  contents  Every page's `## Contents` block against what its own `##`
            headings say right now -- catches a heading renamed, added, or
            removed without the list above it following along.

  dates     Every page's `_Last modified: YYYY-MM-DD_` line (right after
            the title and before `## Contents`, see styleguide.md) exists,
            matches that exact format, and isn't a future date. Purely
            presence-and-shape -- it can't and doesn't check that the date
            is still *true*; that's on whoever edits the page's content,
            per skill `wiki-sync`, the same division `contents` draws
            between a heading list going stale mechanically and deciding
            what belongs on the page.

  pairs     Every page under wiki/ is half of a pair: `<page>.md` written
            for a human contributor, `<page>-4llm.md` holding the same
            subject's context-dense and historical material
            (styleguide.md's "The paired-page convention"). A page with no
            counterpart is a hard finding -- the convention says add both.
            This check also looks for the pairing's other failure mode,
            the same fact written into both halves: a prose paragraph of
            DUP_MIN_WORDS or more words appearing verbatim in both is a
            REVIEW finding, since the split's whole premise is that a fact
            lives in exactly one half and a duplicated one rots in the
            other. Headings, fenced code, and the mandatory header lines
            are excluded -- those are *supposed* to look alike. It cannot
            see a fact restated in different words; that stays a human
            judgement call, the same line every other check here draws.

  freshness Every page's `_Last modified:` date against what git says
            about that page, which is the half `dates` explicitly cannot
            do: `dates` checks the line's presence and shape, never
            whether it is still true. Two cases, deliberately different
            severities.

            A page with uncommitted substantive edits whose date isn't
            today is a hard finding -- you are editing it right now,
            bumping costs one line, and this cannot affect CI, which runs
            on a clean checkout where nothing is uncommitted.

            A page whose last substantive commit is newer than its stated
            date is a REVIEW finding. It is real drift, but styleguide.md
            deliberately exempts "a purely mechanical touch (a
            gen-contents run, a typo fix)" from needing a bump, and no
            script can tell a typo fix from a meaning change -- so this
            one prints loudly and leaves the call to a human.

            Both ignore changes that only touch the `_Last modified:`
            line, the provenance notice, or the `## Contents` block, so a
            gen-contents run or a date bump alone never trips it.

  check     Runs all ten of the above.

    check_wiki.py phases       [repo-root]
    check_wiki.py skills       [repo-root]
    check_wiki.py gradle       [repo-root]
    check_wiki.py recipes      [repo-root]
    check_wiki.py links        [repo-root]
    check_wiki.py anchors      [repo-root]
    check_wiki.py contents     [repo-root]
    check_wiki.py dates        [repo-root]
    check_wiki.py pairs        [repo-root]
    check_wiki.py freshness    [repo-root]
    check_wiki.py check        [repo-root]
    check_wiki.py gen-contents <file.md> [file.md ...]

repo-root defaults to two directories up from this script (wiki/scripts/ ->
wiki/ -> repo root). `gen-contents` is a fixer, not a checker: it rewrites
each given page's `## Contents` block in place to match that page's real
headings -- the actual fix for a `contents` finding.
"""
import re, sys, pathlib, datetime, subprocess

# Phase number -> key filenames from each phase's own component list
# (originally BUILD_PLAN.md's, before it was removed once all six phases
# were built and verified -- see wiki/history.md), matched by filename
# anywhere under app/src rather than a fixed path, so a file moved to a
# different package doesn't false-positive. SECURITY.md is the one entry
# that lives at the repo root instead of under app/src.
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
# docstring. Includes the module-qualified form the wiki itself uses.
KNOWN_GRADLE_TASKS = {
    'assembleDebug', 'assembleRelease', 'testDebugUnitTest', 'installDebug',
    'build', 'clean', 'tasks', ':app:processDebugMainManifest',
    ':app:processReleaseMainManifest', ':app:compileReleaseKotlin',
    # Instrumented tests (app/src/androidTest), added 2026-09-08. Needs a
    # device or emulator, unlike every other task above.
    'connectedDebugAndroidTest',
    'lintDebug', 'lintRelease',
}


def repo_root(argv):
    if len(argv) > 1:
        return pathlib.Path(argv[1]).resolve()
    return pathlib.Path(__file__).resolve().parents[2]


def doc_files(root):
    """Every markdown file the checks below scan: all of wiki/ (recursive)
    plus AGENTS.md."""
    return sorted(root.joinpath('wiki').rglob('*.md')) + [root / 'AGENTS.md']


def link_files(root):
    """`doc_files` plus every SKILL.md and hook doc under .claude/ -- the
    scope for `links` and `anchors` only.

    Those two are fully general (a path either resolves or it doesn't), so
    widening them costs nothing and catches a class of bug the narrower
    scope structurally could not: skill wiki-sync spent from 2026-09-05 to
    2026-09-08 pointing at `../../wiki/styleguide.md`, one level short of
    the repo root, and nothing noticed because .claude/ was never scanned.

    The other checks deliberately keep the narrower `doc_files` scope.
    They would drown here: skills under .claude/ cite nixos-configs' own
    skills by name (`ship`, `secrets-hygiene`), Claude Code's built-ins
    (`fewer-permission-prompts`), and recipes this repo deliberately does
    NOT have (`just preflight`, named precisely to say so) -- every one a
    false positive, and a check that cries wolf gets ignored."""
    return doc_files(root) + sorted(root.joinpath('.claude').rglob('*.md'))


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


JUST_MENTION = re.compile(r'`just ([^`]+)`')


def just_recipes(root):
    """Recipe names from .justfile: a line at column 0 ending in ':',
    optionally with parameters after the name. Deliberately mechanical --
    unlike KNOWN_GRADLE_TASKS there IS a single file listing these, so
    nothing here is hand-maintained."""
    justfile = root / '.justfile'
    if not justfile.exists():
        return None
    names = set()
    for line in justfile.read_text().splitlines():
        m = re.match(r'^([a-zA-Z][\w-]*)(\s+[^:]*)?:(?!=)', line)
        if m:
            names.add(m.group(1))
    return names


def check_recipes(root):
    """Every backtick `just <recipe>` mention across wiki/ and AGENTS.md
    against the recipes .justfile actually defines. The mechanical version
    of check_gradle -- a renamed or deleted recipe leaves every document
    that told someone to run it silently wrong."""
    recipes = just_recipes(root)
    if recipes is None:
        return []
    findings = []
    for path in doc_files(root):
        for m in JUST_MENTION.finditer(path.read_text()):
            tokens = m.group(1).split()
            if not tokens or '<' in m.group(1):
                continue  # a template, not a literal invocation
            # `just --list` and friends are just's own flags, not recipes.
            name = tokens[0]
            if name.startswith('-'):
                continue
            if name not in recipes:
                findings.append(
                    f"UNKNOWN RECIPE  {path}: `just {m.group(1)}` -- "
                    f"'{name}' is not a recipe in .justfile")
    return findings


# `[text](target)` -- the target only; `text` isn't checked against anything.
MD_LINK = re.compile(r'\[[^\]]*\]\(([^)]+)\)')


def check_links(root):
    """Every relative markdown link across `link_files` -- wiki/,
    AGENTS.md, and .claude/ -- resolves to a real file. Skips
    `http(s)://`/`mailto:` targets and a pure in-page anchor
    (`(#see-also)`, no file component)."""
    findings = []
    for path in link_files(root):
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
    the target page, per `github_slug` above. Same `link_files` scope as
    `check_links`, which already flags a broken file path, so this skips
    those."""
    cache = {}
    findings = []
    for path in link_files(root):
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


LAST_MODIFIED_LINE = re.compile(r'^_Last modified: (\d{4}-\d{2}-\d{2})_\s*$')
NOTICE_LINE = '_Text is llm generated with occasional human review_'


def check_dates(root):
    """Every page under wiki/ has a `_Last modified: YYYY-MM-DD_` line right
    after its title, in exactly the format styleguide.md's Content shape
    section specifies, and that date isn't in the future. Presence-and-shape
    only -- whether the date is still *accurate* needs a human reading the
    diff, which is what skill `wiki-sync` is for."""
    findings = []
    today = datetime.date.today()
    for path in sorted(root.joinpath('wiki').rglob('*.md')):
        lines = path.read_text().splitlines()
        if not lines or not lines[0].startswith('# '):
            continue  # no title line to anchor the check against
        i = 1
        while i < len(lines) and lines[i].strip() == '':
            i += 1
        m = LAST_MODIFIED_LINE.match(lines[i]) if i < len(lines) else None
        if not m:
            findings.append(
                f"MISSING LAST-MODIFIED  {path}: no `_Last modified: "
                f"YYYY-MM-DD_` line right after the title")
            continue
        date = datetime.date.fromisoformat(m.group(1))
        if date > today:
            findings.append(
                f"FUTURE DATE  {path}: Last modified says {date}, which is "
                f"after today ({today})")
    return findings


def git(root, *args):
    """Run a git command in `root`, returning stdout (empty on failure --
    this script must still work in a tarball with no .git)."""
    try:
        r = subprocess.run(('git', '-C', str(root)) + args,
                           capture_output=True, text=True, timeout=30)
    except (OSError, subprocess.SubprocessError):
        return ''
    return r.stdout if r.returncode == 0 else ''


def is_bookkeeping(line):
    """True for a changed line that styleguide.md doesn't consider a
    content edit: the `_Last modified:` line itself, the provenance
    notice, a `## Contents` heading or one of its generated bullets, or a
    blank line. A diff made only of these is a gen-contents run or a date
    bump, not something that makes the date stale."""
    s = line.strip()
    return (not s or s == NOTICE_LINE or s == '## Contents'
            or LAST_MODIFIED_LINE.match(s) is not None
            or CONTENTS_ITEM_LINE.match(s) is not None)


def has_substantive_change(diff):
    """True if a unified diff changes any line `is_bookkeeping` doesn't
    excuse. Skips the +++/--- file headers, which are not content."""
    for line in diff.splitlines():
        if line.startswith(('+++', '---')):
            continue
        if line.startswith(('+', '-')) and not is_bookkeeping(line[1:]):
            return True
    return False


# Bounds the history walk below. A page whose last 40 commits were all
# pure bookkeeping is not a case worth paying for on every run.
FRESHNESS_MAX_COMMITS = 40


def last_substantive_commit_date(root, path):
    """The `%cs` date of the newest commit that changed `path` in a way
    `is_bookkeeping` doesn't excuse, or None if there is no such commit
    (or no git). Walks newest-first and stops at the first hit, so the
    common case is two git calls."""
    rel = path.relative_to(root).as_posix()
    log = git(root, 'log', f'-{FRESHNESS_MAX_COMMITS}', '--format=%H %cs',
              '--', rel)
    for entry in log.splitlines():
        sha, _, date = entry.partition(' ')
        if not sha:
            continue
        diff = git(root, 'show', '--format=', '--unified=0', sha, '--', rel)
        if has_substantive_change(diff):
            return date.strip()
    return None


def working_tree_change(root, path):
    """The uncommitted diff for `path` against HEAD, or None if there
    isn't one. An untracked page counts as changed in full -- it is new,
    so its date should be today."""
    rel = path.relative_to(root).as_posix()
    if git(root, 'ls-files', '--error-unmatch', '--', rel).strip() == '':
        return path.read_text()  # untracked: treat the whole file as new
    return git(root, 'diff', '--unified=0', 'HEAD', '--', rel) or None


def stated_date(text):
    """The page's `_Last modified:` date, or None. Matches line by line on
    purpose: LAST_MODIFIED_LINE is anchored but compiled without re.M, so
    searching a whole file with it silently matches nothing -- which is
    exactly how the first version of this check passed on every page while
    testing none of them."""
    for line in text.splitlines():
        m = LAST_MODIFIED_LINE.match(line)
        if m:
            return m.group(1)
    return None


def check_freshness(root):
    """Every page's `_Last modified:` date against git -- see this
    module's docstring for the two cases and why their severities
    differ."""
    if not git(root, 'rev-parse', '--git-dir').strip():
        return []  # no git (a tarball, a vendored copy) -- nothing to say
    today = datetime.date.today().isoformat()
    findings = []
    for path in sorted(root.joinpath('wiki').rglob('*.md')):
        stated = stated_date(path.read_text())
        if stated is None:
            continue  # check_dates already reports a missing line

        pending = working_tree_change(root, path)
        if pending and has_substantive_change(pending) and stated != today:
            findings.append(
                f"STALE DATE  {path}: edited but not committed, and its "
                f"`_Last modified: {stated}_` isn't today ({today}) -- bump "
                f"it in this same change, per skill wiki-sync's step 4")
            continue  # the pending edit is the live fact; don't also
                      # report the older committed drift underneath it

        committed = last_substantive_commit_date(root, path)
        if committed and committed > stated:
            findings.append(
                f"REVIEW  {path}: `_Last modified: {stated}_` but its last "
                f"substantive commit was {committed} -- either the date was "
                f"never bumped, or that commit was the mechanical kind "
                f"styleguide.md exempts. A script can't tell which")
    return findings


COMPANION_SUFFIX = '-4llm'

# A shared paragraph shorter than this is usually an unavoidable one-liner
# ("See styleguide.md.", a repeated warning phrase), not a duplicated fact.
# Tuned against the 2026-09-08 split, which is clean at this threshold.
DUP_MIN_WORDS = 25


def article_of(path):
    """The article path for a `-4llm` companion, or None if `path` is
    already an article."""
    if not path.stem.endswith(COMPANION_SUFFIX):
        return None
    return path.with_name(path.stem[:-len(COMPANION_SUFFIX)] + '.md')


def companion_of(path):
    """The `-4llm` companion path for an article, or None if `path` is
    itself a companion."""
    if path.stem.endswith(COMPANION_SUFFIX):
        return None
    return path.with_name(path.stem + COMPANION_SUFFIX + '.md')


def prose_paragraphs(text):
    """Blank-line-separated prose paragraphs, normalized to single-spaced
    text. Excludes fenced code, headings, list items (a Contents block is
    all list items, and a shared bullet is usually a link line rather than
    a duplicated fact) and the mandatory header lines -- all of which are
    *meant* to look similar across a pair."""
    paragraphs, current, in_fence = [], [], False
    for line in text.splitlines():
        if FENCE.match(line):
            in_fence = not in_fence
            if current:
                paragraphs.append(' '.join(current))
                current = []
            continue
        if in_fence:
            continue
        stripped = line.strip()
        if not stripped:
            if current:
                paragraphs.append(' '.join(current))
                current = []
            continue
        if (HEADING.match(line) or stripped.startswith(('-', '*', '|', '>'))
                or LAST_MODIFIED_LINE.match(stripped) or stripped == NOTICE_LINE):
            if current:
                paragraphs.append(' '.join(current))
                current = []
            continue
        current.append(stripped)
    if current:
        paragraphs.append(' '.join(current))
    return {' '.join(p.split()) for p in paragraphs
            if len(p.split()) >= DUP_MIN_WORDS}


def check_pairs(root):
    """Every wiki page has its counterpart, and no long prose paragraph is
    written into both halves -- see this module's docstring."""
    pages = set(root.joinpath('wiki').rglob('*.md'))
    findings = []
    for path in sorted(pages):
        article = article_of(path)
        if article is not None:
            if article not in pages:
                findings.append(
                    f"ORPHAN COMPANION  {path}: no article at "
                    f"{article.name} -- a companion with no article has no "
                    f"entry point (styleguide.md, 'The paired-page "
                    f"convention')")
            continue
        companion = companion_of(path)
        if companion not in pages:
            findings.append(
                f"MISSING COMPANION  {path}: no {companion.name} -- "
                f"styleguide.md's paired-page convention says add both")
            continue
        shared = prose_paragraphs(path.read_text()) & \
            prose_paragraphs(companion.read_text())
        for para in sorted(shared):
            excerpt = para if len(para) <= 90 else para[:87] + '...'
            findings.append(
                f"REVIEW  {path} and {companion.name} share a paragraph "
                f"verbatim -- a fact belongs in exactly one half: "
                f'"{excerpt}"')
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
        # A `_Last modified: ..._` line (styleguide.md) sits between the
        # title and Contents -- skip past it too, so a fresh Contents block
        # lands after it rather than splitting title from date.
        if insert_at < len(lines) and LAST_MODIFIED_LINE.match(lines[insert_at]):
            insert_at += 1
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

    cmds = ('phases', 'skills', 'gradle', 'recipes', 'links', 'anchors',
            'contents', 'dates', 'pairs', 'freshness', 'check')
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
    if cmd in ('recipes', 'check'):
        findings += check_recipes(root)
    if cmd in ('links', 'check'):
        findings += check_links(root)
    if cmd in ('anchors', 'check'):
        findings += check_anchors(root)
    if cmd in ('contents', 'check'):
        findings += check_contents(root)
    if cmd in ('dates', 'check'):
        findings += check_dates(root)
    if cmd in ('pairs', 'check'):
        findings += check_pairs(root)
    if cmd in ('freshness', 'check'):
        findings += check_freshness(root)

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
