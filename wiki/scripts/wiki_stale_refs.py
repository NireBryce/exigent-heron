#!/usr/bin/env python3
"""Find backtick-quoted file/path mentions across wiki/ and AGENTS.md that
don't resolve to any file actually tracked in the repo -- a name that was
renamed, moved, or removed out from under a doc that still mentions the old
one. `check_wiki.py links` already covers the reliable half of this idea --
a real markdown link target either resolves or it doesn't, no judgement
call needed. This script covers the OTHER half: a bare filename mentioned
in backticks with no link at all (`SafeLog.kt`, `RuleEngine.kt`), which is
genuinely ambiguous to check mechanically:

  - **Historical names are deliberately kept.** AGENTS.md's own convention
    ("a bug recorded in a comment stays in the file") extends to prose: a
    wiki page explaining a rename or removal may correctly still name the
    old filename. It will never resolve, and correctly so.
  - **Shortened paths are a real convention here** (`SafeLog.kt` instead
    of the full `app/src/main/java/net/breadthcharge/exigentheron/
    SafeLog.kt`) -- resolved below by matching a tracked path's *suffix*,
    not requiring an exact match.
  - **Some backtick spans aren't this repo's files at all** -- an Android
    framework class, a library name, an upstream file cited for
    comparison.

None of that is something a script can reliably tell apart from a genuine
stale reference. So, same as `wiki_churn.py`: this is NOT a pass/fail
gate. It only lists candidates for a human to skim; nothing here fails a
build or blocks a commit. Expect real noise every run -- that's the trade
for catching the genuine case without missing it by trying to auto-filter
the rest and getting that wrong instead.

    wiki_stale_refs.py [repo-root]

Matches only against files git actually tracks (`git ls-files`) -- an
untracked file is invisible to this check the same way it's invisible to
the rest of this repo's tooling, so a brand-new wiki page will flag its
own filename and its neighbors' until `git add` runs, not a bug in the
script.
"""
import pathlib
import re
import subprocess
import sys

EXTS = ('.kt', '.kts', '.py', '.sh', '.md', '.xml', '.yaml', '.yml', '.json',
        '.toml', '.pro')
BACKTICK_FILE = re.compile(
    r'`([\w./-]+(?:' + '|'.join(re.escape(e) for e in EXTS) + r'))`')


def repo_root(argv):
    if len(argv) > 1:
        return pathlib.Path(argv[1]).resolve()
    return pathlib.Path(__file__).resolve().parents[2]


def doc_files(root):
    """Same scope as check_wiki.py's own `doc_files` -- all of wiki/
    (recursive) plus AGENTS.md, not re-imported since these two scripts are
    meant to be runnable independently (one Python file each, no shared
    module to keep in sync)."""
    return sorted(root.joinpath('wiki').rglob('*.md')) + [root / 'AGENTS.md']


def tracked_paths(root):
    out = subprocess.run(['git', 'ls-files'], cwd=root, capture_output=True,
                          text=True, check=True).stdout
    return set(out.splitlines())


def normalize(token):
    """Drop leading `.`/`..` segments so a relative link written from a
    nested page still suffix-matches the repo-relative tracked path."""
    return '/'.join(p for p in token.split('/') if p not in ('.', '..'))


def resolves(token, tracked):
    """True if `token` is exactly a tracked path, or a tracked path ends
    with `/` + `token` -- the shortened-relative-path convention this wiki
    actually uses (see this module's docstring)."""
    name = normalize(token)
    suffix = '/' + name
    return any(t == name or t.endswith(suffix) for t in tracked)


def find_stale(root):
    """path (str) -> sorted list of backtick tokens that don't resolve."""
    tracked = tracked_paths(root)
    out = {}
    for f in doc_files(root):
        misses = sorted({m.group(1) for m in BACKTICK_FILE.finditer(f.read_text())
                          if not resolves(m.group(1), tracked)})
        if misses:
            out[str(f.relative_to(root))] = misses
    return out


def main():
    root = repo_root(sys.argv)
    stale = find_stale(root)

    if not stale:
        print("wiki_stale_refs: no candidates")
        return

    total = sum(len(v) for v in stale.values())
    for path, tokens in stale.items():
        print(f"{path}:")
        for t in tokens:
            print(f"    `{t}`")
    print(f"\n{total} candidate mention{'s' if total != 1 else ''} across "
          f"{len(stale)} file{'s' if len(stale) != 1 else ''} -- read this "
          f"module's own docstring before treating any of these as a bug; "
          f"most won't be one.")


if __name__ == '__main__':
    main()
