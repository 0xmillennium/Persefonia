"""Every source change gets the same ordinary contract; diagnostics require a human."""
from pathlib import Path
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import selection as s

class SelectionContracts(unittest.TestCase):
    def test_every_change_has_fixed_ordinary_requirements(self):
        for path in ['frontend/src/styles/home.css', 'frontend/src/homepage/writing/first.ts',
                     'frontend/src/identity/physics/core.ts', 'frontend/src/homepage/film/seek-film.ts',
                     'web-admin/src/main/java/Form.java', 'unknown/new-engine.xyz']:
            for status in ['M', 'A', 'D']:
                self.assertEqual(s.classify([{'status':status, 'path':path}])['required'], ['ordinary'])
        self.assertEqual(s.classify([])['required'], ['ordinary'])
        self.assertEqual(s.classify([{'status':'R100','previous':'old.ts','path':'new.ts'}])['required'], ['ordinary'])

    def test_malformed_change_inventory_fails_closed(self):
        for changes in [[{'status':'M','path':'../unsafe.ts'}], [{'status':'M','path':'a\nb'}],
                        [{'status':'???','path':'a'}], [{'status':'M','path':'a'}]*2]:
            with self.assertRaises(ValueError): s.classify(changes)
        with self.assertRaises(ValueError): s.classify([], truncated=True)

    def test_one_small_manifest_has_all_native_capabilities_and_no_risk_rules(self):
        self.assertEqual([(p['engine'],p['context']) for p in s.POLICY['native']],
                         [('chromium','desktop'),('chromium','touch'),('firefox','desktop'),('webkit','desktop')])
        count=sum(len(p['cases']) for p in s.POLICY['native'])
        self.assertGreaterEqual(count,10)
        self.assertLessEqual(count,16)
        self.assertEqual(s.POLICY['expectedSkips'],[])
        self.assertNotIn('riskRules',s.POLICY)
        self.assertEqual(s.POLICY['receiptSchema'],3)

class SelectionGitContracts(unittest.TestCase):
    def test_pr_selection_preserves_tested_tree_provenance(self):
        from test_qualification import GitIdentityFixture
        from unittest.mock import patch
        import qualification as q
        fixture=GitIdentityFixture(); self.addCleanup(fixture.close)
        tested=fixture.identity('pr-merge',shallow=False)
        master=fixture.clone('squash',shallow=False)
        with patch.object(q,'ROOT',master): plan=s.plan(tested)
        self.assertEqual(plan['required'],['ordinary'])
        self.assertEqual(plan['source'],tested)
        self.assertEqual(len(plan['changes']),64)

class LocalPolicyDeletionContracts(unittest.TestCase):
    def test_deleted_control_source_still_has_a_distinct_local_policy_identity(self):
        import tempfile
        from unittest.mock import patch
        import qualification as q
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);file=root/'frontend/retired.config.ts';file.parent.mkdir();file.write_text('original policy')
            def inventory(*args):
                return '100644 '+'a'*40+' 0\tfrontend/retired.config.ts' if args[:2]==('ls-files','--stage') else ''
            with patch.object(q,'ROOT',root),patch.object(q,'git',side_effect=inventory):
                before=q.policy();file.unlink();after=q.policy()
                self.assertNotEqual(before,after)
                self.assertEqual(len(after),64)


class ManualContractTests(unittest.TestCase):
    def test_manual_dispatch_runs_the_same_contract_with_source_provenance(self):
        from unittest.mock import patch
        source={'event':'workflow_dispatch','sourceSha':'a'*40,'tree':'b'*40}
        with patch.object(s.subprocess,'check_output',return_value=b'') as command:
            plan=s.plan(source)
        self.assertEqual(plan['required'],['ordinary'])
        self.assertEqual(plan['source'],source)
        self.assertEqual(command.call_args.args[0][-2:],['a'*40,'b'*40])
