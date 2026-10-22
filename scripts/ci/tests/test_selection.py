"""Fail-closed change classification, without network or expensive suites."""
import copy
from pathlib import Path
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import selection as s

class SelectionContracts(unittest.TestCase):
    def test_routine_writing_css_and_policy_do_not_expand_to_all_deep(self):
        for path in ['frontend/src/styles/home.css','frontend/src/homepage/writing/first.ts','.github/workflows/ci.yml']:
            self.assertEqual(s.classify([{'status':'M','path':path}])['required'], ['ordinary'])

    def test_owner_risks_are_narrow_and_mandatory(self):
        for path, expected in [('frontend/src/identity/physics/core.ts',['physics-live','physics-scalar']),('frontend/src/homepage/film/seek-film.ts',['media']),('web-admin/src/main/java/Form.java',['admin']),('scripts/ci/admin-preview.py',['admin']),('scripts/ci/tests/test_admin_preview.py',['admin']),('frontend/playwright.admin.required.config.ts',['admin']),('frontend/scripts/verification/admin-contract.test.ts',['admin'])]:
            plan=s.classify([{'status':'M','path':path}])
            self.assertEqual(plan['required'],['ordinary',*expected])

    def test_unknown_unsafe_deleted_renamed_truncated_empty_fail_closed(self):
        for changes in [[],[{'status':'M','path':'unknown/new-engine.xyz'}],[{'status':'D','path':'frontend/src/homepage/writing/first.ts'}],[{'status':'R100','path':'frontend/src/a.ts','previous':'frontend/src/b.ts'}],[{'status':'M','path':'../frontend/src/a.ts'}],[{'status':'M','path':'frontend/src/a.ts\nforged'}]]:
            with self.subTest(changes=changes), self.assertRaises(ValueError): s.classify(changes)
        with self.assertRaises(ValueError): s.classify([{'status':'M','path':'frontend/src/a.ts'}],truncated=True)

    def test_policy_has_exact_engines_contexts_and_no_skip_escape(self):
        self.assertEqual([(p['engine'],p['context']) for p in s.POLICY['native']], [('chromium','desktop'),('chromium','touch'),('firefox','desktop'),('webkit','desktop')])
        self.assertEqual(s.POLICY['expectedSkips'],[])
        self.assertEqual(len(s.POLICY_HASH),64)


    def test_exact_reviewed_rename_and_delete_preserve_original_owner_risks(self):
        from unittest.mock import patch
        rename = {'status':'R100','previous':'frontend/src/homepage/film/seek-film.ts','path':'frontend/src/homepage/timeline/renamed.ts'}
        deletion = {'status':'D','path':'frontend/src/identity/physics/core.ts'}
        policy = copy.deepcopy(s.POLICY)
        policy['classifiedChanges'] = [rename,deletion]
        with patch.object(s,'POLICY',policy):
            self.assertEqual(s.classify([rename])['required'], ['ordinary','media'])
            self.assertEqual(s.classify([deletion])['required'], ['ordinary','physics-live','physics-scalar'])
            changed = {**rename,'previous':'../unsafe.ts'}
            policy['classifiedChanges'].append(changed)
            with self.assertRaises(ValueError): s.classify([changed])



class SelectionGitContracts(unittest.TestCase):
    def test_pr_selection_uses_complete_base_to_tested_tree_even_without_merge_commit_object(self):
        from test_qualification import GitIdentityFixture
        from unittest.mock import patch
        import qualification as q
        fixture = GitIdentityFixture()
        self.addCleanup(fixture.close)
        tested = fixture.identity('pr-merge', shallow=False)
        master = fixture.clone('squash', shallow=False)
        # Squash checkouts need not retain the generated historical PR merge object.
        with self.assertRaises(__import__('subprocess').CalledProcessError):
            fixture.git(master, 'cat-file', '-e', tested['sourceSha'])
        with patch.object(q, 'ROOT', master):
            plan = s.plan(tested)
        self.assertEqual(plan['required'], ['ordinary'])
        self.assertEqual(plan['owners'], [{'path':'frontend/src/product.ts','owner':'frontend-mechanics'}])
