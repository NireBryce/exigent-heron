#!/usr/bin/env python3
"""Prove every check in `check_wiki.py` still fires on input built to
break it.

Why this exists, specifically: a check that passes because the tree is
clean and a check that passes because it is examining nothing look
identical from the outside. That has happened twice in this repo --

  - `RuleEngineTest`'s first ReDoS case (2026-09-05) passed in 0.053s
    having exercised none of the timeout path it was named for;
  - `check_freshness`'s first version (2026-09-08) reported "no findings"
    across all 18 pages having examined none of them, because
    LAST_MODIFIED_LINE is anchored but compiled without re.M, so a
    whole-file `.search()` silently matched nothing.

Both were caught by luck: a suspiciously fast test, and a case built to
fail that didn't. Neither is a repeatable method. This file is the
repeatable version -- every check gets at least one fixture that MUST
produce a finding, plus the clean-tree case that must produce none. A
regression that silently disables a check turns a green run red here
instead of going unnoticed.

Stdlib `unittest` on purpose -- pytest would be a dependency, and
AGENTS.md §2's list is meant to be the whole list.

    python3 wiki/scripts/test_check_wiki.py           # all
    python3 wiki/scripts/test_check_wiki.py -v        # per-test names
    python3 wiki/scripts/test_check_wiki.py FreshnessTest

Run by `just wiki-lint` ahead of the checks themselves, so the checks are
never trusted without first proving they can fail.
"""
import contextlib
import datetime
import importlib.util
import io
import pathlib
import shutil
import subprocess
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location('check_wiki',
                                               HERE / 'check_wiki.py')
cw = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(cw)

TODAY = datetime.date.today().isoformat()
NOTICE = '_Text is llm generated with occasional human review_'


def page(title, date=TODAY, sections=(('A', 'body text'),), notice=True):
    """A structurally valid page: title, date line, notice, a Contents
    block matching its own headings, then those sections."""
    out = [f'# {title}', '', f'_Last modified: {date}_', '']
    if notice:
        out += [NOTICE, '']
    out.append('## Contents')
    out.append('')
    for heading, _ in sections:
        out.append(f'- [{heading}](#{cw.github_slug(heading, {})})')
    for heading, body in sections:
        out += ['', f'## {heading}', '', body]
    return '\n'.join(out) + '\n'


class Fixture:
    """A throwaway repo just complete enough for every check to have
    something real to look at: one wiki pair, a skill, a .justfile, and a
    status.md whose phase table matches the files present."""

    def __init__(self):
        self.root = pathlib.Path(tempfile.mkdtemp(prefix='check-wiki-test-'))
        (self.root / 'wiki').mkdir()
        (self.root / '.claude' / 'skills' / 'wiki-sync').mkdir(parents=True)
        self.write('.claude/skills/wiki-sync/SKILL.md', '# wiki-sync\n')
        self.write('.justfile', 'wiki-lint:\n    @echo hi\n')
        self.write('AGENTS.md', '# AGENTS\n')
        self.write('wiki/p.md', page('P'))
        self.write('wiki/p-4llm.md', page('P (dense)',
                                          sections=(('B', 'other body'),)))

    def write(self, rel, text):
        path = self.root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        return path

    def git(self, *args, date=None):
        env = None
        if date:
            env = {'GIT_AUTHOR_DATE': f'{date}T12:00:00',
                   'GIT_COMMITTER_DATE': f'{date}T12:00:00'}
        import os
        full = dict(os.environ, **(env or {}))
        return subprocess.run(('git', '-C', str(self.root)) + args,
                              capture_output=True, text=True, env=full)

    def git_init(self):
        self.git('init', '-q', '.')
        self.git('config', 'user.email', 'test@example.invalid')
        self.git('config', 'user.name', 'test')

    def commit(self, message, date):
        self.git('add', '-A')
        self.git('commit', '-qm', message, date=date)

    def cleanup(self):
        shutil.rmtree(self.root, ignore_errors=True)


class CheckTest(unittest.TestCase):
    def setUp(self):
        self.fx = Fixture()
        self.addCleanup(self.fx.cleanup)

    def assertFires(self, findings, needle):
        joined = '\n'.join(findings)
        self.assertTrue(
            any(needle in f for f in findings),
            f'expected a finding containing {needle!r}, got:\n{joined or "  (none)"}')

    def assertSilent(self, findings):
        self.assertEqual(findings, [], f'expected no findings, got:\n' +
                         '\n'.join(findings))


class CleanTreeTest(CheckTest):
    """The baseline every other test is measured against: a well-formed
    tree must produce nothing. A check that fires here cries wolf, which
    is how checks get ignored."""

    def test_every_check_silent_on_a_clean_fixture(self):
        for name in ('check_skills', 'check_gradle', 'check_recipes',
                     'check_links', 'check_anchors', 'check_contents',
                     'check_dates', 'check_pairs'):
            with self.subTest(check=name):
                self.assertSilent(getattr(cw, name)(self.fx.root))


class PairsTest(CheckTest):
    def test_missing_companion(self):
        self.fx.write('wiki/lonely.md', page('Lonely'))
        self.assertFires(cw.check_pairs(self.fx.root), 'MISSING COMPANION')

    def test_orphan_companion(self):
        self.fx.write('wiki/ghost-4llm.md', page('Ghost (dense)'))
        self.assertFires(cw.check_pairs(self.fx.root), 'ORPHAN COMPANION')

    def test_paragraph_duplicated_across_a_pair(self):
        shared = ('This paragraph is long enough to clear the duplication '
                  'threshold and is written into both halves of the pair, '
                  'which is exactly the thing the split exists to prevent.')
        self.fx.write('wiki/p.md', page('P', sections=(('A', shared),)))
        self.fx.write('wiki/p-4llm.md',
                      page('P (dense)', sections=(('B', shared),)))
        self.assertFires(cw.check_pairs(self.fx.root), 'REVIEW')

    def test_short_shared_line_is_not_flagged(self):
        """A one-liner shared between halves is unavoidable, not a
        duplicated fact -- DUP_MIN_WORDS is what separates them."""
        for name, title in (('wiki/p.md', 'P'), ('wiki/p-4llm.md', 'P (dense)')):
            self.fx.write(name, page(title, sections=(('A', 'See styleguide.md.'),)))
        self.assertSilent(cw.check_pairs(self.fx.root))


class LinksAndAnchorsTest(CheckTest):
    def test_broken_link_in_wiki(self):
        self.fx.write('wiki/p.md',
                      page('P', sections=(('A', '[gone](nope.md)'),)))
        self.assertFires(cw.check_links(self.fx.root), 'BROKEN LINK')

    def test_broken_link_under_dot_claude(self):
        """Locks in the widened `link_files` scope. Skill wiki-sync
        pointed at `../../wiki/...`, one level short of the repo root,
        from 2026-09-05 to 2026-09-08 with every check green -- because
        .claude/ was not scanned at all."""
        self.fx.write('.claude/skills/wiki-sync/SKILL.md',
                      '# wiki-sync\n\n[styleguide](../../wiki/styleguide.md)\n')
        self.assertFires(cw.check_links(self.fx.root), 'BROKEN LINK')

    def test_broken_anchor(self):
        self.fx.write('wiki/p.md',
                      page('P', sections=(('A', '[x](p-4llm.md#no-such)'),)))
        self.assertFires(cw.check_anchors(self.fx.root), 'BROKEN ANCHOR')


class ContentsAndDatesTest(CheckTest):
    def test_stale_contents_block(self):
        text = (self.fx.root / 'wiki/p.md').read_text()
        self.fx.write('wiki/p.md', text.replace('## A', '## Renamed'))
        self.assertFires(cw.check_contents(self.fx.root), 'STALE CONTENTS')

    def test_gen_contents_fixes_a_stale_block(self):
        text = (self.fx.root / 'wiki/p.md').read_text()
        path = self.fx.write('wiki/p.md', text.replace('## A', '## Renamed'))
        with contextlib.redirect_stdout(io.StringIO()):
            cw.regenerate_contents(path)  # it prints its own progress
        self.assertSilent(cw.check_contents(self.fx.root))

    def test_missing_date_line(self):
        self.fx.write('wiki/p.md', '# P\n\n## Contents\n\n- [A](#a)\n\n## A\n\nx\n')
        self.assertFires(cw.check_dates(self.fx.root), 'MISSING LAST-MODIFIED')

    def test_future_date(self):
        soon = (datetime.date.today() + datetime.timedelta(days=3)).isoformat()
        self.fx.write('wiki/p.md', page('P', date=soon))
        self.assertFires(cw.check_dates(self.fx.root), 'FUTURE DATE')


class NameChecksTest(CheckTest):
    def test_unknown_skill(self):
        self.fx.write('wiki/p.md',
                      page('P', sections=(('A', 'see skill `no-such-skill`'),)))
        self.assertFires(cw.check_skills(self.fx.root), 'UNKNOWN SKILL')

    def test_unknown_gradle_task(self):
        self.fx.write('wiki/p.md',
                      page('P', sections=(('A', 'run `gradle bogusTask`'),)))
        self.assertFires(cw.check_gradle(self.fx.root), 'UNKNOWN TASK')

    def test_unknown_just_recipe(self):
        self.fx.write('wiki/p.md',
                      page('P', sections=(('A', 'run `just bogus-recipe`'),)))
        self.assertFires(cw.check_recipes(self.fx.root), 'UNKNOWN RECIPE')

    def test_known_just_recipe_is_accepted(self):
        self.fx.write('wiki/p.md',
                      page('P', sections=(('A', 'run `just wiki-lint`'),)))
        self.assertSilent(cw.check_recipes(self.fx.root))


class PhasesTest(CheckTest):
    TABLE = ('| Phase | Spec | Built | Verified |\n'
             '|---|---|---|---|\n'
             '| 0 — Skeleton | scaffold | {built} | 2026-09-08 |\n')

    def _status(self, built):
        body = self.TABLE.format(built=built)
        self.fx.write('wiki/status.md',
                      page('Status', sections=(('Phase status', body),)))
        self.fx.write('wiki/status-4llm.md', page('Status (dense)'))

    def test_built_yes_with_missing_files(self):
        self._status('Yes')
        self.assertFires(cw.check_phases(self.fx.root), 'MISSING FILE(S)')

    def test_built_no_with_every_file_present_is_review_only(self):
        self._status('No')
        for name in ('App.kt', 'AppContainer.kt', 'SafeLog.kt', 'MainActivity.kt'):
            self.fx.write(f'app/src/main/java/{name}', '// x\n')
        findings = cw.check_phases(self.fx.root)
        self.assertFires(findings, 'REVIEW')
        self.assertTrue(all(f.startswith('REVIEW') for f in findings),
                        f'under-claiming must not hard-fail, got: {findings}')


class FreshnessTest(CheckTest):
    """The check whose first version passed on every page while examining
    none. `test_examines_pages_at_all` is the direct regression test for
    that bug; the rest cover the behaviour."""

    def setUp(self):
        super().setUp()
        self.fx.git_init()

    def test_examines_pages_at_all(self):
        """LAST_MODIFIED_LINE is anchored and compiled WITHOUT re.M, so a
        whole-file `.search()` finds nothing. If `stated_date` ever goes
        back to that, every page silently skips and the check passes
        vacuously."""
        text = (self.fx.root / 'wiki/p.md').read_text()
        self.assertEqual(cw.stated_date(text), TODAY)

    def test_uncommitted_substantive_edit_with_stale_date(self):
        self.fx.commit('initial', '2026-09-01')
        self.fx.write('wiki/p.md',
                      page('P', date='2026-09-01',
                           sections=(('A', 'a materially different claim'),)))
        findings = cw.check_freshness(self.fx.root)
        self.assertFires(findings, 'STALE DATE')
        self.assertFalse(any(f.startswith('REVIEW') for f in findings),
                         'an uncommitted stale date must fail hard, not REVIEW')

    def test_uncommitted_edit_dated_today_is_fine(self):
        self.fx.commit('initial', '2026-09-01')
        self.fx.write('wiki/p.md',
                      page('P', sections=(('A', 'a materially different claim'),)))
        self.assertSilent(cw.check_freshness(self.fx.root))

    def test_bookkeeping_only_commit_is_excused(self):
        """A gen-contents run or a bare date bump must never make a page
        look stale -- styleguide.md exempts a purely mechanical touch."""
        self.fx.write('wiki/p.md', page('P', date='2026-09-01'))
        self.fx.write('wiki/p-4llm.md', page('P (dense)', date='2026-09-01',
                                             sections=(('B', 'other body'),)))
        self.fx.commit('initial', '2026-09-01')
        text = (self.fx.root / 'wiki/p.md').read_text()
        self.fx.write('wiki/p.md', text.replace('- [A](#a)', '- [A](#a)\n- [B](#b)'))
        self.fx.commit('gen-contents', '2026-09-05')
        self.assertSilent(cw.check_freshness(self.fx.root))

    def test_committed_drift_is_review_not_hard(self):
        self.fx.write('wiki/p.md', page('P', date='2026-09-01'))
        self.fx.write('wiki/p-4llm.md', page('P (dense)', date='2026-09-01',
                                             sections=(('B', 'other body'),)))
        self.fx.commit('initial', '2026-09-01')
        self.fx.write('wiki/p.md',
                      page('P', date='2026-09-01',
                           sections=(('A', 'a materially different claim'),)))
        self.fx.commit('real edit, forgot the bump', '2026-09-06')
        findings = cw.check_freshness(self.fx.root)
        self.assertFires(findings, 'REVIEW')
        self.assertTrue(all(f.startswith('REVIEW') for f in findings),
                        f'committed drift is judgement-laden, got: {findings}')

    def test_silent_without_git(self):
        """Must degrade to silence in a tarball or vendored copy rather
        than erroring."""
        nogit = Fixture()
        self.addCleanup(nogit.cleanup)
        self.assertSilent(cw.check_freshness(nogit.root))


class DispatchTest(CheckTest):
    """The usage block is hand-maintained; `recipes` went unlisted for
    days because nothing compared it to the code."""

    def test_every_dispatchable_command_is_documented(self):
        import re
        src = (HERE / 'check_wiki.py').read_text()
        cmds = {c.strip().strip("'") for c in
                re.search(r'cmds = \((.*?)\)', src, re.S).group(1).split(',')
                if c.strip()}
        listed = set(re.findall(r'check_wiki\.py (\w+)\s+\[repo-root\]', src))
        self.assertEqual(cmds - listed, set(),
                         'subcommands dispatch but are absent from the usage block')


if __name__ == '__main__':
    unittest.main(verbosity=2)
