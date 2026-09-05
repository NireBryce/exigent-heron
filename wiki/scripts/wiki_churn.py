#!/usr/bin/env python3
"""Rank wiki/ pages by edit churn, to catch a page turning into
hand-maintained toil -- a claim that needs someone to remember to touch it
on every unrelated nearby change, rather than something that only changes
when the fact it describes genuinely does -- before that toil outweighs
what the page tells a reader that reading the source directly wouldn't
already.

This is NOT a `check_wiki.py`-style pass/fail gate -- churn is a heuristic
that needs a human to look at *why* a page keeps changing (an actively
evolving app legitimately churns its status/history pages; a page
hand-tracking something that drifts every time something nearby changes
churns for a worse reason, and those two look identical in a commit count
alone). It only ranks and prints; nothing here fails a build or blocks a
commit.

    wiki_churn.py [repo-root] [--since DATE] [--top N] [--min-commits N]

--since DATE       Only count commits on/after DATE (git's own date syntax,
                    e.g. 2026-08-01 or "3 weeks ago"). Default: full history.
--top N            Print only the N highest-churn pages. Default: 15.
--min-commits N    Omit pages touched fewer than N times -- a page edited
                    once or twice isn't churning, it's just recent.
                    Default: 2.

Limitation, deliberate: run with --no-renames, so a file that got renamed
mid-history shows as two separate lower counts (its old name and its new
one) rather than one merged one. `git log --follow` only tracks a single
path at a time, which doesn't compose with walking the whole history once
for every file in one pass -- and this is a heuristic ranking tool, not an
audit, so an undercount on the rare renamed page is an acceptable trade for
one fast `git log` call instead of one per file.
"""
import argparse
import pathlib
import re
import subprocess
import sys
from collections import defaultdict

COMMIT_LINE = re.compile(r'^@@(?P<hash>[0-9a-f]+) (?P<date>\d{4}-\d{2}-\d{2})$')
NUMSTAT_LINE = re.compile(r'^(?P<added>\d+|-)\t(?P<deleted>\d+|-)\t(?P<path>.+)$')


def repo_root(argv):
    if len(argv) > 1 and not argv[1].startswith('-'):
        return pathlib.Path(argv[1]).resolve()
    return pathlib.Path(__file__).resolve().parents[2]


def churn(root, since=None):
    """path (str, repo-relative) -> dict with commits (int), added (int),
    deleted (int), first_date, last_date (both 'YYYY-MM-DD' strings)."""
    args = ['log', '--no-renames', '--format=@@%H %ad', '--date=short',
             '--numstat']
    if since:
        args.append(f'--since={since}')
    args += ['--', 'wiki']

    out = subprocess.run(['git'] + args, cwd=root, capture_output=True,
                          text=True, check=True).stdout

    stats = defaultdict(lambda: {'commits': 0, 'added': 0, 'deleted': 0,
                                  'first_date': None, 'last_date': None})
    date = None
    touched_this_commit = set()

    def flush():
        for path in touched_this_commit:
            s = stats[path]
            s['commits'] += 1
            # git log walks newest-first, so the first date seen for a path
            # is its most recent touch and the last date seen is its oldest.
            if s['last_date'] is None:
                s['last_date'] = date
            s['first_date'] = date

    for line in out.splitlines():
        m = COMMIT_LINE.match(line)
        if m:
            flush()
            date = m.group('date')
            touched_this_commit = set()
            continue
        m = NUMSTAT_LINE.match(line)
        if m and m.group('path').startswith('wiki/'):
            path = m.group('path')
            touched_this_commit.add(path)
            added = m.group('added')
            deleted = m.group('deleted')
            # '-' means git considers the file binary; not a real case for
            # wiki/*.md, but numstat's own escape hatch, so honor it as 0
            # rather than crashing on int().
            stats[path]['added'] += int(added) if added != '-' else 0
            stats[path]['deleted'] += int(deleted) if deleted != '-' else 0
    flush()
    return stats


def main():
    ap = argparse.ArgumentParser(add_help=True)
    ap.add_argument('repo_root', nargs='?')
    ap.add_argument('--since')
    ap.add_argument('--top', type=int, default=15)
    ap.add_argument('--min-commits', type=int, default=2)
    # argparse chokes on a positional that looks like a flag; repo_root is
    # resolved separately (same fallback-to-script-location convention
    # check_wiki.py uses) so --since/--top/--min-commits work with or
    # without an explicit repo-root argument ahead of them.
    args = ap.parse_args()

    root = (pathlib.Path(args.repo_root).resolve() if args.repo_root
            else repo_root(sys.argv))

    stats = churn(root, since=args.since)
    rows = [(path, s) for path, s in stats.items()
            if s['commits'] >= args.min_commits]
    rows.sort(key=lambda r: (r[1]['commits'], r[1]['added'] + r[1]['deleted']),
               reverse=True)

    if not rows:
        print(f"wiki_churn: no page touched >= {args.min_commits} times"
              f"{f' since {args.since}' if args.since else ''}")
        return

    width = max(len(path) for path, _ in rows[:args.top])
    print(f"{'page':{width}}  {'commits':>7}  {'+/-':>9}  {'first':>10}  {'last':>10}")
    for path, s in rows[:args.top]:
        churn_str = f"+{s['added']}/-{s['deleted']}"
        print(f"{path:{width}}  {s['commits']:>7}  {churn_str:>9}  "
              f"{s['first_date']:>10}  {s['last_date']:>10}")

    if len(rows) > args.top:
        print(f"... and {len(rows) - args.top} more page"
              f"{'s' if len(rows) - args.top != 1 else ''} at or above "
              f"--min-commits {args.min_commits}")


if __name__ == '__main__':
    main()
