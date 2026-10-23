"""Adversarial contracts for trusted CI evidence; no GitHub credentials or network."""
import copy
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import qualification as q
import selection


def fixture_plan(source):
    return {**selection.classify([{'status': 'M', 'path': 'frontend/src/product.ts'}]), 'source': source}


def unit_cases(seeds=None):
    seeds = selection.POLICY['ordinaryPhysicsSeeds'] if seeds is None else seeds
    return [{'file': '/fixture/frontend/src/identity/physics/tests/'+file,
             'fullName': title.format(seed), 'status': 'passed'}
            for file, title in [('stress.test.ts', 'seed {} converges without invariant failures'),
                                ('live-stress.test.ts', 'live seed {}: coalesced fast strokes preserve invariants and recover')]
            for seed in seeds]


def browser_fixture(source, checksum):
    receipts = []
    inventory = []
    for p in selection.POLICY['native']:
        outcomes = [{**c, 'id': p['project']+':'+str(i), 'project': p['project'], 'status': 'expected',
            'expectedStatus': 'passed', 'annotations': [], 'results': [{'status': 'passed', 'retry': 0, 'duration': 1}]}
            for i, c in enumerate(p['cases'])]
        ids = [r['id'] for r in outcomes]
        inventory.extend(copy.deepcopy(outcomes))
        receipts.append({'schema': 3, 'policy': selection.POLICY_HASH, 'status': 'passed', 'exitCode': 0, 'errors': [],
            'environment': {'browsers': {p['engine']: {'sha256': 'e'*64}}, 'ffmpeg': 'qualified ffmpeg', 'ffprobe': 'qualified ffprobe'},
            'partition': {'name': 'ordinary-'+p['name'], 'suite': 'ordinary', 'project': p['project'], 'shard': '1/1'},
            'identity': {'sourceSha': source['sourceSha'], 'artifactSha256': checksum}, 'sourceContext': source,
            'selectionPlan': fixture_plan(source), 'runner': {'run': source['run'], 'attempt': source['attempt']},
            'selected': ids, 'outcomes': outcomes,
            'selectionHash': __import__('hashlib').sha256(json.dumps(sorted(ids), separators=(',', ':')).encode()).hexdigest()})
    return {'schema': 3, 'policy': selection.POLICY_HASH, 'profiles': ['ordinary'], 'status': 'passed',
        'sourceContext': source, 'selectionPlan': fixture_plan(source),
        'identity': {'sourceSha': source['sourceSha'], 'artifactSha256': checksum},
        'runner': {'run': source['run'], 'attempt': source['attempt']}, 'partitions': len(receipts),
        'selected': len(inventory), 'receipts': receipts, 'inventory': {'ordinary': [{**r,'results':[]} for r in inventory]},
        'native': {'discovery': native_report([{**r,'results':[]} for r in inventory]), 'execution':native_report(inventory)}}


def native_report(rows):
    return {'config': {'forbidOnly':True,'fullyParallel':False,'workers':1,
        'projects':[{'name':p['project'],'retries':0,'repeatEach':1} for p in selection.POLICY['native']]},
        'errors':[], 'stats':{'expected':sum(bool(r['results']) for r in rows),'unexpected':0,'flaky':0,'skipped':0},
        'suites':[{'specs':[{'id':r['id'].split(':',1)[1], 'file':r['file'],'title':r['title'],
            'tests':[{'projectName':r['project'],**{k:r[k] for k in ['expectedStatus','annotations','results','status']}}]} for r in rows]}]}


class GitIdentityFixture:
    """Small SHA-1 graph; all Git writes and event files stay in owned temp storage."""
    def __init__(self):
        self.temporary = tempfile.TemporaryDirectory(dir=os.environ.get('RUNNER_TEMP'))
        self.directory = Path(self.temporary.name)
        self.env = {'PATH': os.environ['PATH'], 'HOME': str(self.directory), 'LC_ALL': 'C',
            'GIT_CONFIG_NOSYSTEM': '1', 'GIT_CONFIG_GLOBAL': os.devnull,
            'GIT_CONFIG_COUNT': '3', 'GIT_CONFIG_KEY_0': 'core.hooksPath',
            'GIT_CONFIG_VALUE_0': os.devnull, 'GIT_CONFIG_KEY_1': 'commit.gpgsign',
            'GIT_CONFIG_VALUE_1': 'false', 'GIT_CONFIG_KEY_2': 'tag.gpgsign',
            'GIT_CONFIG_VALUE_2': 'false', 'GIT_TERMINAL_PROMPT': '0',
            'GIT_AUTHOR_NAME': 'CI Fixture', 'GIT_AUTHOR_EMAIL': 'ci-fixture@example.invalid',
            'GIT_COMMITTER_NAME': 'CI Fixture', 'GIT_COMMITTER_EMAIL': 'ci-fixture@example.invalid',
            'GIT_AUTHOR_DATE': '2026-10-20T10:00:00Z', 'GIT_COMMITTER_DATE': '2026-10-20T10:00:00Z'}
        self.origin = self.directory/'origin'
        self.git(self.directory, 'init', '--object-format=sha1', '--template=',
                 '--initial-branch=fixture', str(self.origin))
        source = self.origin/'frontend/src/product.ts'
        source.parent.mkdir(parents=True)
        self.graph = {}
        for name, content in [('root', 'root'), ('normal', 'base'), ('head', 'head')]:
            source.write_text(content+'\n')
            self.git(self.origin, 'add', 'frontend/src/product.ts')
            self.git(self.origin, 'commit', '-m', name)
            sha = self.git(self.origin, 'rev-parse', 'HEAD')
            tree = self.git(self.origin, 'rev-parse', 'HEAD^{tree}')
            parents = [] if name == 'root' else [self.graph['root' if name == 'normal' else 'normal']['sha']]
            self.graph[name] = {'sha': sha, 'tree': tree, 'parents': parents}
        base, head = self.graph['normal']['sha'], self.graph['head']['sha']
        tree = self.graph['head']['tree']
        for name, parents in [('pr-merge', [base, head]), ('squash', [base])]:
            arguments = [argument for parent in parents for argument in ('-p', parent)]
            # A convincing message header must never become part of the parent list.
            sha = self.git(self.origin, 'commit-tree', tree, *arguments,
                           input=b'message\n\nparent '+b'f'*40+b'\nnon-UTF8: \xff\n')
            self.graph[name] = {'sha': sha, 'tree': tree, 'parents': parents}
        for name, node in self.graph.items():
            self.git(self.origin, 'update-ref', 'refs/heads/'+name, node['sha'])
        self.copies = {}

    def close(self):
        self.temporary.cleanup()

    def git(self, directory, *arguments, input=None):
        return subprocess.run(['git', *arguments], cwd=directory, env=self.env, input=input,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True, timeout=10).stdout.decode('ascii').strip()

    def clone(self, name, shallow):
        key = (name, shallow)
        if key not in self.copies:
            directory = self.directory/(name+('-shallow' if shallow else '-full'))
            depth = ['--depth=1'] if shallow else []
            self.git(self.directory, 'clone', '--no-tags', '--single-branch', '--branch', name,
                     *depth, self.origin.as_uri(), str(directory))
            self.copies[key] = directory
        return self.copies[key]

    def identity(self, name, shallow, source_only=False):
        node = self.graph[name]
        context = {'GITHUB_SHA': node['sha'], 'GITHUB_RUN_ATTEMPT': '1'}
        if name == 'pr-merge':
            event = self.directory/'pull-request.json'
            event.write_text(json.dumps({'pull_request': {'number': 1,
                'base': {'sha': self.graph['normal']['sha'], 'repo': {'id': q.REPOSITORY_ID}},
                'head': {'sha': self.graph['head']['sha'], 'repo': {'id': q.REPOSITORY_ID}}}}))
            context.update(GITHUB_EVENT_PATH=str(event), GITHUB_EVENT_NAME='pull_request',
                           GITHUB_REF='refs/pull/1/merge', GITHUB_RUN_ID='200')
        else:
            context.update(GITHUB_EVENT_NAME='push', GITHUB_REF='refs/heads/master', GITHUB_RUN_ID='300')
        with patch.object(q, 'ROOT', self.clone(name, shallow)), \
                patch.dict(os.environ, {**self.env, **context}, clear=True):
            return q.identity(source_only=source_only)


class SourceParentContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture = GitIdentityFixture()
        cls.addClassCleanup(cls.fixture.close)

    def test_full_and_depth_one_identities_retain_actual_commit_graph(self):
        for name in ['root', 'normal', 'pr-merge', 'squash']:
            node = self.fixture.graph[name]
            for shallow in (False, True):
                directory = self.fixture.clone(name, shallow)
                if shallow and node['parents']:
                    self.assertEqual(self.fixture.git(directory, 'rev-parse', '--is-shallow-repository'), 'true')
                    for parent in node['parents']:
                        with self.assertRaises(subprocess.CalledProcessError):
                            self.fixture.git(directory, 'cat-file', '-e', parent)
                elif not shallow:
                    self.assertEqual(self.fixture.git(directory, 'rev-parse', '--is-shallow-repository'), 'false')
                for source_only in (False, True):
                    with self.subTest(case=name, shallow=shallow, source_only=source_only):
                        actual = self.fixture.identity(name, shallow, source_only)
                        self.assertEqual(actual['sourceSha'], node['sha'])
                        self.assertEqual(actual['tree'], node['tree'])
                        self.assertEqual(actual['parents'], node['parents'])
                        self.assertFalse(actual['dirty'])
        self.assertEqual(self.fixture.graph['pr-merge']['tree'], self.fixture.graph['squash']['tree'])

    def test_parent_headers_ignore_signatures_and_arbitrary_message_bytes(self):
        sha = 'a'*40
        raw = (b'tree '+b'b'*40+b'\nparent '+b'c'*40+b'\ngpgsig signature\n parent '+b'd'*40+
               b'\n continuation\nparent '+b'e'*40+b'\nencoding ISO-8859-1\n\nparent '+b'f'*40+b'\n\xff')
        with patch.object(q.subprocess, 'check_output', return_value=raw) as read:
            self.assertEqual(q.commit_parents(sha), ['c'*40, 'e'*40])
        read.assert_called_once_with(['git', '--no-replace-objects', 'cat-file', 'commit', sha], cwd=q.ROOT)

    def test_malformed_parent_or_missing_header_separator_fails_closed(self):
        for parent in [b'', b'a'*39, b'a'*41, b'A'*40, b'g'*40, b'\xff'*40, b'a'*40+b' extra']:
            with self.subTest(parent=parent), \
                    patch.object(q.subprocess, 'check_output', return_value=b'tree '+b'b'*40+b'\nparent '+parent+b'\n\nmessage'):
                with self.assertRaises(ValueError): q.commit_parents('a'*40)
        for raw in [b'', b'tree '+b'b'*40+b'\nparent '+b'c'*40, b'\n\nmessage',
                    b'tree invalid\n\nmessage', b'tree '+b'b'*40+b'\nparent\n\nmessage',
                    b'tree '+b'b'*40+b'\nparent\t'+b'c'*40+b'\n\nmessage']:
            with self.subTest(raw=raw), patch.object(q.subprocess, 'check_output', return_value=raw):
                with self.assertRaises(ValueError): q.commit_parents('a'*40)

    def test_failed_commit_read_is_not_a_root_commit(self):
        with patch.object(q.subprocess, 'check_output', side_effect=subprocess.CalledProcessError(128, 'git')):
            with self.assertRaises(subprocess.CalledProcessError): q.commit_parents('a'*40)

    def test_identity_rejects_a_non_sha1_source_before_reading_the_object(self):
        for sha in ['a'*64, 'HEAD', 'a'*39, 'A'*40]:
            with self.subTest(sha=sha), patch.dict(os.environ, {}, clear=True), \
                    patch.object(q, 'git', return_value=sha) as git:
                with self.assertRaisesRegex(ValueError, 'Malformed source SHA'): q.identity()
                git.assert_called_once_with('rev-parse', 'HEAD')


class QualificationContracts(unittest.TestCase):
    def test_modes_are_closed(self):
        self.assertEqual(q.reuse_mode(None), 'shadow')
        for mode in ('off', 'shadow', 'enforce'):
            self.assertEqual(q.reuse_mode(mode), mode)
        for mode in ('true', 'ENFORCE', ' shadow', 'unknown'):
            with self.assertRaises(ValueError): q.reuse_mode(mode)

    def test_archive_is_bounded_and_does_not_execute_members(self):
        def archive(names):
            buffer = io.BytesIO()
            with zipfile.ZipFile(buffer, 'w') as z:
                for name, content in names: z.writestr(name, content)
            return buffer.getvalue()
        self.assertEqual(q.archive_members(archive([('receipt.json', '{}')])), {'receipt.json': b'{}'})
        for names in [ [('../escape', 'x')], [('/absolute', 'x')], [('a\\b', 'x')],
                       [('a', '1'), ('a', '2')], [('a/../b', 'x')]]:
            with self.assertRaises(ValueError): q.archive_members(archive(names))
        with self.assertRaises(ValueError): q.archive_members(b'not a zip')
        with self.assertRaises(ValueError): q.load_json(b'{"mode":"passed","mode":"failed"}')

    def test_malformed_compression_and_unapproved_methods_are_classified_misses(self):
        import struct
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
            archive.writestr('receipt.json', b'bounded evidence'*100)
        raw = bytearray(stream.getvalue())
        offset = 30 + struct.unpack_from('<H', raw, 26)[0] + struct.unpack_from('<H', raw, 28)[0]
        raw[offset] = 255
        with self.assertRaises(ValueError): q.archive_members(bytes(raw))
        for method in [zipfile.ZIP_BZIP2, zipfile.ZIP_LZMA]:
            stream = io.BytesIO()
            with zipfile.ZipFile(stream, 'w', compression=method) as archive:
                archive.writestr('receipt.json', b'bounded evidence')
            with self.assertRaises(ValueError): q.archive_members(stream.getvalue())

    def test_unknown_policy_paths_and_renames_are_not_safe(self):
        for path in ['.github/new.yml', 'app/build.gradle.kts', 'gradle/new', 'buildSrc/a.java',
                     'frontend/e2e/support/test.ts', 'frontend/scripts/media/new.mjs',
                     'frontend/new.config.ts', 'compose.yaml', 'Dockerfile', '.npmrc',
                     'new-runner.py', 'frontend/new-runner.mjs', '.tool-versions']:
            self.assertTrue(q.protected(path), path)
        for path in ['app/src/main/java/dev/Example.java', 'frontend/src/navigation/marker.test.ts',
                     'frontend/e2e/homepage/film/video.spec.ts', 'docs/decisions/0039-example.md']:
            self.assertFalse(q.protected(path), path)
        self.assertFalse(q.safe_changes([{'filename': 'frontend/src/new.ts',
                                         'previous_filename': '.github/old.yml', 'status': 'renamed'}]))
        self.assertFalse(q.safe_changes([{'filename': 'unknown.file', 'status': 'added'}]))

    def test_admission_never_accepts_a_claimed_reuse_flag(self):
        jobs = {'automation': 'success', 'build-jvm': 'success', 'frontend': 'success',
                'verify': 'success', 'browser': 'success'}
        q.job_results('executed', jobs)
        for key in jobs:
            for outcome in ('', 'failure', 'cancelled', 'skipped'):
                bad = {**jobs, key: outcome}
                with self.assertRaises(ValueError): q.job_results('executed', bad)
        skipped = {**jobs, 'build-jvm': 'skipped', 'frontend': 'skipped', 'browser': 'skipped'}
        with self.assertRaises(ValueError): q.job_results('reused', skipped)
        with self.assertRaises(ValueError): q.job_results('unknown', jobs)

    def test_time_window_rejects_future_stale_and_inconsistent(self):
        master = '2026-10-21T12:00:00Z'
        q.time_window('2026-10-21T10:00:00Z', '2026-10-21T11:00:00Z', master)
        for start, end in [('2026-10-20T09:00:00Z', '2026-10-20T11:00:00Z'),
                           ('2026-10-21T13:00:00Z', '2026-10-21T14:00:00Z'),
                           ('2026-10-21T11:00:00Z', '2026-10-21T10:00:00Z')]:
            with self.assertRaises(ValueError): q.time_window(start, end, master)



class NativeReuseContracts(unittest.TestCase):
    def setUp(self):
        import reuse
        self.reuse = reuse
        plan_patch = patch.object(selection, 'plan', side_effect=fixture_plan)
        plan_patch.start()
        self.addCleanup(plan_patch.stop)
        self.master = {'schema': 2, 'repository': q.REPOSITORY, 'repositoryId': q.REPOSITORY_ID,
            'workflowId': q.WORKFLOW_ID, 'sourceSha': 'c'*40, 'tree': 'd'*40, 'parents': ['b'*40],
            'policy': 'a'*64, 'inputs': 'f'*64, 'dirty': False, 'run': '300', 'attempt': '1',
            'event': 'push', 'ref': 'refs/heads/master', 'pr': {}}
        self.pr = {**self.master, 'sourceSha': 'e'*40, 'run': '200', 'event': 'pull_request',
            'ref': 'refs/pull/1/merge', 'parents': ['b'*40, '1'*40],
            'pr': {'number': 1, 'head': '1'*40, 'base': 'b'*40,
                   'headRepositoryId': q.REPOSITORY_ID, 'baseRepositoryId': q.REPOSITORY_ID}}
        self.run = {'id': 200, 'run_attempt': 1, 'repository': {'id': q.REPOSITORY_ID, 'full_name': q.REPOSITORY},
            'head_repository': {'id': q.REPOSITORY_ID, 'fork': False}, 'head_sha': '1'*40,
            'workflow_id': q.WORKFLOW_ID, 'path': '.github/workflows/ci.yml', 'event': 'pull_request',
            'status': 'completed', 'conclusion': 'success',
            'created_at': '2026-10-21T10:00:00Z', 'updated_at': '2026-10-21T11:00:00Z'}
        self.now = '2026-10-21T12:00:00Z'
        self.decision = {'schema': 2, 'kind': 'master-equivalence', 'eligible': True,
            'equivalent': True, 'mode': 'enforce', 'master': self.master, 'pr': self.pr,
            'candidate': '2'*64, 'production': '3'*64, 'environment': '4'*64, 'buildEnvironment': '5'*64,
            'validatedNativeEvidence': True, 'apiValidated': True,
            'prCreated': self.run['created_at'], 'prCompleted': self.run['updated_at'], 'masterCreated': self.now,
            'freshJava21': {'passed': True, 'version': 'openjdk version "21.0.12.1"'},
            'artifacts': [{'id': index+1, 'digest': 'sha256:'+'a'*64,
                'name': name+self.pr['sourceSha'], 'created_at': self.run['created_at'],
                'workflow_run': {'id': 200, 'repository_id': q.REPOSITORY_ID,
                    'head_repository_id': q.REPOSITORY_ID, 'head_sha': '1'*40}}
                for index,name in enumerate(['persefonia-qualification-', 'persefonia-browser-input-'])]}

    def test_first_attempt_successful_same_repository_workflow_only(self):
        self.reuse.validate_run(self.run, self.now)
        for key, values in {'run_attempt': [2, 0], 'workflow_id': [0], 'path': ['.github/workflows/fake.yml'],
                            'event': ['push', 'pull_request_target'], 'status': ['in_progress'],
                            'conclusion': ['failure', 'cancelled', 'skipped']}.items():
            for value in values:
                bad = {**self.run, key: value}
                with self.assertRaises(ValueError): self.reuse.validate_run(bad, self.now)
        for head in [{'id': 0, 'fork': True}, {'id': q.REPOSITORY_ID, 'fork': True}]:
            with self.assertRaises(ValueError): self.reuse.validate_run({**self.run, 'head_repository': head}, self.now)

    def test_every_native_job_is_required_and_carried_over_attempts_are_rejected(self):
        names = q.CI_JOBS
        jobs = {'total_count': len(q.CI_JOBS), 'jobs': [{'name': name, 'run_id': 200, 'run_attempt': 1,
            'status': 'completed', 'conclusion': 'skipped' if name == 'Comparison build' else 'success',
            'started_at': self.run['created_at'], 'completed_at': self.run['updated_at'],
            'steps': [{'conclusion': 'success'}]} for name in names]}
        self.reuse.validate_jobs(jobs, self.run)
        for i, name in enumerate(names):
            if name == 'Comparison build': continue
            for value in ['failure', 'cancelled', 'skipped']:
                changed = copy.deepcopy(jobs); changed['jobs'][i]['conclusion'] = value
                with self.assertRaises(ValueError): self.reuse.validate_jobs(changed, self.run)
        for change in ['duplicate', 'missing', 'attempt']:
            changed = copy.deepcopy(jobs)
            if change == 'duplicate': changed['jobs'][0] = changed['jobs'][1]
            elif change == 'missing': changed['jobs'].pop()
            else: changed['jobs'][0]['run_attempt'] = 2
            with self.assertRaises(ValueError): self.reuse.validate_jobs(changed, self.run)

    def test_enforce_decision_is_source_bound_and_shadow_cannot_authorize_skips(self):
        q.validate_equivalence(self.decision, self.master, '2'*64)
        jobs = {'automation': 'success', 'build-jvm': 'skipped', 'frontend': 'skipped',
                'verify': 'success', 'browser': 'skipped'}
        q.job_results('reused', jobs, self.decision, self.master, '2'*64)
        for key in ['tree', 'inputs', 'policy', 'workflowId', 'run', 'attempt', 'sourceSha']:
            bad = copy.deepcopy(self.decision); bad['master'][key] = 'wrong'
            with self.assertRaises(ValueError): q.validate_equivalence(bad, self.master, '2'*64)
        for key, value in [('candidate', '0'*64), ('validatedNativeEvidence', False), ('apiValidated', False),
                           ('equivalent', False), ('mode', 'unknown'), ('artifacts', [])]:
            with self.assertRaises(ValueError): q.validate_equivalence({**self.decision, key: value}, self.master, '2'*64)
        shadow = {**self.decision, 'mode': 'shadow'}
        q.validate_equivalence(shadow, self.master, '2'*64)
        with self.assertRaises(ValueError): q.job_results('reused', jobs, shadow, self.master, '2'*64)
        for outcome in ['success', 'failure', 'cancelled']:
            with self.assertRaises(ValueError): q.job_results('reused', {**jobs, 'browser': outcome}, self.decision, self.master, '2'*64)

    def test_off_and_non_master_contexts_never_lookup_old_artifacts(self):
        class NoAPI:
            def get(self, path): raise AssertionError('Unexpected API lookup')
        for mode in ['off', 'shadow', 'enforce']:
            contexts = [self.master] if mode == 'off' else [
                {**self.master, 'event': 'pull_request'}, {**self.master, 'event': 'workflow_dispatch'},
                {**self.master, 'ref': 'refs/heads/feature'}, {**self.master, 'attempt': '2'}]
            for source in contexts:
                self.assertFalse(self.reuse.eligible(NoAPI(), source, mode)['eligible'])

    def test_expired_or_forged_api_artifact_never_downloads(self):
        api = self.reuse.GitHub()
        for metadata in [{'expired': True, 'id': 1, 'size_in_bytes': 10},
                         {'expired': False, 'id': 1, 'size_in_bytes': 10,
                          'created_at': self.run['created_at'],
                          'workflow_run': {'id': 201, 'repository_id': q.REPOSITORY_ID,
                                           'head_repository_id': q.REPOSITORY_ID, 'head_sha': '1'*40}}]:
            with self.assertRaises(ValueError): api.artifact(metadata, self.run, self.pr['sourceSha'])

    def test_corrupt_and_symlink_archives_are_rejected_before_extraction(self):
        import stat
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, 'w') as z:
            item = zipfile.ZipInfo('link'); item.create_system = 3
            item.external_attr = (stat.S_IFLNK | 0o777) << 16
            z.writestr(item, '../../outside')
        with self.assertRaises(ValueError): q.archive_members(stream.getvalue())
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, 'w', compression=zipfile.ZIP_STORED) as z: z.writestr('data', b'1234567')
        corrupt = stream.getvalue().replace(b'1234567', b'7654321')
        with self.assertRaises(ValueError): q.archive_members(corrupt)
        with self.assertRaises(ValueError): q.archive_members(stream.getvalue(), total_limit=3)

class EligibilityFlowContracts(unittest.TestCase):
    setUp = NativeReuseContracts.setUp
    def test_complete_controlled_squash_accepts_and_policy_ambiguity_fork_misses(self):
        fixture = GitIdentityFixture()
        self.addCleanup(fixture.close)
        self.master = fixture.identity('squash', shallow=True)
        self.pr = fixture.identity('pr-merge', shallow=True)
        base_sha, head_sha = fixture.graph['normal']['sha'], fixture.graph['head']['sha']
        self.run['head_sha'] = head_sha
        master_run = {**self.run, 'id': 300, 'event': 'push', 'head_branch': 'master',
                      'head_sha': self.master['sourceSha'], 'created_at': self.now}
        pr = {'number': 1, 'merged': True, 'merge_commit_sha': self.master['sourceSha'],
              'base': {'ref': 'master', 'repo': {'id': q.REPOSITORY_ID}},
              'head': {'sha': head_sha, 'repo': {'id': q.REPOSITORY_ID, 'fork': False}}}
        metadata = {'id': 1, 'name': 'persefonia-qualification-'+self.pr['sourceSha'],
            'expired': False, 'size_in_bytes': 100, 'digest': 'sha256:'+'a'*64,
            'created_at': self.run['created_at'], 'expires_at': '2099-01-01T00:00:00Z',
            'workflow_run': {'id': 200, 'repository_id': q.REPOSITORY_ID,
                'head_repository_id': q.REPOSITORY_ID, 'head_sha': head_sha}}
        candidate = {**metadata, 'id': 2, 'name': 'persefonia-browser-input-'+self.pr['sourceSha']}
        names = q.CI_JOBS
        jobs = {'total_count': len(q.CI_JOBS), 'jobs': [{'name': name, 'run_id': 200, 'run_attempt': 1,
            'status': 'completed', 'conclusion': 'skipped' if name == 'Comparison build' else 'success',
            'started_at': self.run['created_at'], 'completed_at': self.run['updated_at'],
            'steps': [{'conclusion': 'success'}]} for name in names]}
        responses = {
            'actions/runs/300': master_run, 'commits/'+self.master['sourceSha']+'/pulls?per_page=100': [{'number': 1}],
            'pulls/1': pr,
            f'actions/workflows/{q.WORKFLOW_ID}/runs?event=pull_request&head_sha={head_sha}&per_page=100': {'total_count': 1, 'workflow_runs': [{'id': 200}]},
            'actions/runs/200': self.run,
            'actions/runs/200/artifacts?per_page=100': {'total_count': 2, 'artifacts': [metadata, candidate]},
            'actions/runs/200/attempts/1/jobs?per_page=100': jobs,
        }
        for source in [self.pr, self.master]:
            responses['git/commits/'+source['sourceSha']] = {'sha': source['sourceSha'],
                'parents': [{'sha': parent} for parent in source['parents']], 'tree': {'sha': source['tree']}}
            responses[f'compare/{base_sha}...{source["sourceSha"]}?per_page=100'] = {
                'status': 'ahead', 'total_commits': 1,
                'files': [{'filename': 'frontend/src/product.ts', 'status': 'modified'}]}
        responses['git/commits/'+base_sha] = {'sha': base_sha, 'tree': {'sha': fixture.graph['normal']['tree']}}
        for revision in [fixture.graph['normal']['tree'], self.master['tree']]:
            responses['git/trees/'+revision+'?recursive=1'] = {'truncated': False,
                'tree': [{'path': 'frontend/src/product.ts', 'mode': '100644'}]}
        record = {'schema': 2, 'status': 'passed', 'lane': 'qualification', 'mode': 'executed', 'identity': self.pr}
        record['candidate'] = '2'*64
        compiler = {key: 'fixture' for key in ['languageVersion', 'vendor', 'runtimeVersion', 'jvmVersion']}
        production = {'app': {'schema': 2, 'sourceInputs': self.pr['inputs'], 'gradle': '9.2.0',
            'compilerToolchain': compiler, 'production': [['Example.class', 'a'*64]],
            'resolvedTestRuntimes': {'test': {**compiler, 'customJvmArguments': 'false', 'hasFilters': 'false',
                'assertions': 'true', 'maxParallelForks': '1', 'maxHeapSize': '1g'}},
            'resolvedTestDependencies': {'test': []}}}
        base = {'schema': 2, 'status': 'passed', 'identity': self.pr}
        java = [{'report': 'test.xml', 'id': 'case', 'status': 'passed'}]
        jvm = {**base, 'lane': 'jvm', 'candidate': record['candidate'], 'javaCases': java,
            'production': production, 'java21': {'passed': True, 'version': 'openjdk version "21"'}}
        frontend = {**base, 'lane': 'frontend', 'unit': {'schema': 2, 'policy': selection.POLICY_HASH, 'selectionPlan': fixture_plan(self.pr), 'physicsProfile': 'ordinary', 'profile': 'ordinary', 'status': 'passed',
            'sourceSha': self.pr['sourceSha'], 'sourceContext': self.pr,
            'runner': {'run': '200', 'attempt': '1'}, 'cases': unit_cases(), 'attempts': [{}]}}
        verify = {**base, 'lane': 'verify', 'candidate': record['candidate'], 'mode': 'executed',
            'producers': {'jvm': q.digest(jvm), 'frontend': q.digest(frontend)}}
        browser = browser_fixture(self.pr, record['candidate'])
        automation = {**base, 'lane': 'automation', 'javaCases': java}
        record.update(verification=verify, browser=browser, automation=q.digest(automation))
        evidence = {'qualification.json': record, 'verification.json': verify, 'jvm.json': jvm,
            'frontend.json': frontend, 'browser-acceptance.json': browser, 'automation.json': automation}
        class API:
            def __init__(self, values): self.values = values
            def get(self, path):
                if path not in self.values: raise AssertionError('Unexpected API path: '+path)
                return self.values[path]
            def artifact(inner, artifact, run, source_sha):
                self.assertEqual(artifact, metadata)
                self.assertEqual(run, self.run)
                self.assertIsNone(source_sha)
                return {name: json.dumps(value).encode() for name,value in evidence.items()}
            def checkout(inner, job):
                self.assertEqual(job, next(job for job in jobs['jobs'] if job['name'] == 'Build + JVM'))
                return self.pr['sourceSha']
        with patch.object(self.reuse, 'OUTPUT', fixture.directory/'output'):
            result = self.reuse.eligible(API(responses), self.master, 'shadow')
            self.assertTrue(result['eligible']); self.assertEqual(result['pr'], self.pr)
            root = fixture.identity('root', shallow=True)
            root_responses = {
                'actions/runs/300': {**master_run, 'head_sha': root['sourceSha']},
                'commits/'+root['sourceSha']+'/pulls?per_page=100': [{'number': 1}],
                'pulls/1': {**pr, 'merge_commit_sha': root['sourceSha']}}
            with self.assertRaisesRegex(ValueError, 'Unsupported master merge history'):
                self.reuse.eligible(API(root_responses), root, 'shadow')
            # Uploaded eligibility contains only explicitly selected provenance, no actor/commit/URLs.
            self.assertEqual(set(result['prRun']), set(self.reuse.compact_run(self.run)))
            self.assertEqual(len(result['artifacts']), 2)
            for mutation in ['fork', 'ambiguous', 'pr-tree', 'final-tree', 'pr-parent-order', 'pr-parent-identity', 'master-parent', 'protected', 'unknown', 'rerun', 'expired', 'missing-candidate']:
                bad = copy.deepcopy(responses)
                if mutation == 'fork': bad['pulls/1']['head']['repo']['fork'] = True
                elif mutation == 'ambiguous': bad['commits/'+self.master['sourceSha']+'/pulls?per_page=100'].append({'number': 2})
                elif mutation in {'pr-tree', 'final-tree'}:
                    sha = self.pr['sourceSha'] if mutation == 'pr-tree' else self.master['sourceSha']
                    bad['git/commits/'+sha]['tree']['sha'] = '0'*40
                elif mutation == 'pr-parent-order':
                    bad['git/commits/'+self.pr['sourceSha']]['parents'].reverse()
                elif mutation in {'pr-parent-identity', 'master-parent'}:
                    sha = self.pr['sourceSha'] if mutation == 'pr-parent-identity' else self.master['sourceSha']
                    bad['git/commits/'+sha]['parents'][0]['sha'] = '0'*40
                elif mutation in {'protected', 'unknown'}:
                    bad[f'compare/{base_sha}...{self.pr["sourceSha"]}?per_page=100']['files'][0]['filename'] = '.github/new.yml' if mutation == 'protected' else 'new-executable.py'
                elif mutation == 'expired': bad['actions/runs/200/artifacts?per_page=100']['artifacts'][1]['expired'] = True
                elif mutation == 'missing-candidate':
                    bad['actions/runs/200/artifacts?per_page=100'] = {'total_count': 1, 'artifacts': [metadata]}
                else: bad['actions/runs/200']['run_attempt'] = 2
                with self.assertRaises(ValueError, msg=mutation): self.reuse.eligible(API(bad), self.master, 'enforce')

    def test_compare_accepts_equal_outputs_and_rejects_each_mismatch(self):
        import tempfile
        from unittest.mock import patch
        raw_jar = b'controlled actual tested candidate'
        checksum = __import__('hashlib').sha256(raw_jar).hexdigest()
        compiler = {'languageVersion': '25', 'vendor': 'fixture vendor', 'runtimeVersion': '25.0.3', 'jvmVersion': '25.0.3'}
        production = {'app': {'schema': 2, 'sourceInputs': self.master['inputs'], 'gradle': '9.2.0',
            'compilerToolchain': compiler, 'java': 'fixture Java25', 'production': [['app/build/classes/java/main/Example.class', 'a'*64]],
            'resolvedTestRuntimes': {'test': {**compiler, 'customJvmArguments': 'false', 'hasFilters': 'false', 'assertions': 'true', 'maxParallelForks': '1', 'maxHeapSize': '1g'}},
            'resolvedTestDependencies': {'test': [['dependencies/example.jar', 'b'*64]]}}}
        fresh = {'candidate': checksum, 'production': production,
                 'environment': {'node': 'v22.22.2', 'runnerImage': {'os': 'ubuntu24', 'version': '20261020.1'}},
                 'java21': {'passed': True, 'version': 'openjdk version \"21.0.12.1\"'}}
        jvm = {'production': fresh['production'], 'environment': fresh['environment'], 'java21': fresh['java21']}
        fresh['environment'].update(os='linux', arch='x64', npm='10.9.4', playwright='1.57.0')
        frontend = {'runner': {'imageOS': 'ubuntu24', 'imageVersion': '20261020.1'}, 'unit': {'runner': {'os': 'linux', 'arch': 'x64', 'node': 'v22.22.2', 'npm': '10.9.4', 'playwright': '1.57.0', 'java': 'fixture Java25'}}}
        environment = {'os': 'linux', 'arch': 'x64', 'node': 'v22.22.2', 'npm': '10.9.4', 'playwright': '1.57.0', 'runnerImage': fresh['environment']['runnerImage'], 'browserRevisions': ['locked'],
                       'ffmpeg': '7.1', 'ffprobe': '7.1', 'browsers': {'firefox': {'sha256': 'a'*64}}}
        browser = {'receipts': [{'runner': {'run': '200', 'attempt': '1', 'java': fresh['java21']['version']}, 'partition': {'project': 'firefox-desktop'}, 'environment': environment}]}
        artifact = {'id': 2, 'digest': 'sha256:'+'a'*64, 'name': 'persefonia-browser-input-'+self.pr['sourceSha'],
                    'workflow_run': {'id': 200, 'repository_id': q.REPOSITORY_ID, 'head_repository_id': q.REPOSITORY_ID, 'head_sha': '1'*40}, 'created_at': self.run['created_at']}
        eligibility = {'eligible': True, 'master': self.master, 'pr': self.pr, 'mode': 'shadow',
            'prRun': self.run, 'artifacts': [artifact], 'qualificationArtifact': {**artifact, 'id': 1, 'name': 'persefonia-qualification-'+self.pr['sourceSha']}, 'masterCreated': self.now, 'prCompleted': self.run['updated_at']}
        class API:
            def artifact(inner, *args): return {'persefonia.jar': raw_jar,
                'persefonia.jar.sha256': (checksum+'  persefonia.jar\n').encode(),
                'source-sha.txt': self.pr['sourceSha'].encode()}
        with tempfile.TemporaryDirectory() as temp, patch.object(self.reuse, 'OUTPUT', Path(temp)), \
                patch.object(q, 'identity', return_value=self.master), \
                patch.object(self.reuse, 'validate_native', return_value=(jvm, frontend, browser)):
            q.write(Path(temp)/'comparison-environment.json', environment)
            self.assertTrue(self.reuse.compare(API(), eligibility, fresh)['equivalent'])
            for subject, key in [(frontend['unit']['runner'], key) for key in ['os', 'arch', 'node', 'npm', 'playwright', 'java']] + [(frontend['runner'], 'imageOS'), (frontend['runner'], 'imageVersion'), (browser['receipts'][0]['runner'], 'java')]:
                before = subject[key]; subject[key] = 'drift'
                with self.assertRaises(ValueError): self.reuse.compare(API(), eligibility, fresh)
                subject[key] = before
            for key in ['candidate', 'production', 'environment', 'java21']:
                bad = copy.deepcopy(fresh)
                bad[key] = '0'*64 if key == 'candidate' else {'wrong': True}
                with self.assertRaises((ValueError, KeyError), msg=key): self.reuse.compare(API(), eligibility, bad)

class DeliveryContracts(unittest.TestCase):
    setUp = NativeReuseContracts.setUp

    def test_delivery_requires_trusted_master_candidate_and_complete_native_jobs(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location('delivery_admission', Path(q.__file__).with_name('delivery-admission.py'))
        delivery = importlib.util.module_from_spec(spec); spec.loader.exec_module(delivery)
        checksum = '2'*64
        browser = browser_fixture(self.master, checksum)
        verify = {'schema': 2, 'status': 'passed', 'lane': 'verify', 'mode': 'executed', 'reusePolicyMode': 'shadow',
                  'identity': self.master, 'candidate': checksum}
        record = {**verify, 'lane': 'qualification', 'verification': verify, 'browser': browser}
        run = {**self.run, 'id': 300, 'event': 'push', 'head_branch': 'master', 'head_sha': self.master['sourceSha']}
        names = q.CI_JOBS
        jobs = {'total_count': len(q.CI_JOBS), 'jobs': [{'name': name, 'run_id': 300, 'run_attempt': 1,
                'status': 'completed', 'conclusion': 'skipped' if name == 'Comparison build' else 'success'} for name in names]}
        delivery.validate(record, run, jobs, checksum, self.master['sourceSha'], '300', '1', expected_source=self.master)
        for key, value in [('event', 'pull_request'), ('workflow_id', 0), ('head_sha', '0'*40),
                            ('conclusion', 'failure'), ('run_attempt', 2)]:
            with self.assertRaises(ValueError): delivery.validate(record, {**run, key: value}, jobs,
                checksum, self.master['sourceSha'], '300', '1', expected_source=self.master)
        for key, value in [('candidate', '0'*64), ('mode', 'claimed-reused')]:
            with self.assertRaises(ValueError): delivery.validate({**record, key: value}, run, jobs,
                checksum, self.master['sourceSha'], '300', '1', expected_source=self.master)
        for key in ['tree', 'policy', 'inputs', 'workflowId']:
            bad_record = copy.deepcopy(record); bad_record['identity'][key] = 'forged'
            with self.assertRaises(ValueError): delivery.validate(bad_record, run, jobs,
                checksum, self.master['sourceSha'], '300', '1', expected_source=self.master)
        bad_jobs = copy.deepcopy(jobs); bad_jobs['jobs'][-1]['conclusion'] = 'skipped'
        with self.assertRaises(ValueError): delivery.validate(record, run, bad_jobs,
                checksum, self.master['sourceSha'], '300', '1', expected_source=self.master)



class NativeCheckoutContracts(unittest.TestCase):
    def test_only_the_api_bound_checkout_step_can_supply_the_tested_merge(self):
        import reuse
        source = 'e'*40
        step = {'name': 'Check out repository', 'conclusion': 'success',
                'started_at': '2026-10-21T10:00:00Z', 'completed_at': '2026-10-21T10:00:02Z'}
        valid = ('2026-10-21T10:00:01.100Z [command]/usr/bin/git log -1 --format=%H\n'
                 '2026-10-21T10:00:01.200Z '+source+'\n').encode()
        self.assertEqual(reuse.checkout_source(valid, {'steps': [step]}), source)
        injected = ('2026-10-21T10:01:00.000Z [command]/usr/bin/git log -1 --format=%H\n'
                    '2026-10-21T10:01:00.100Z '+'f'*40+'\n').encode()
        self.assertEqual(reuse.checkout_source(valid+injected, {'steps': [step]}), source)
        for raw in [injected, valid+valid, valid[:50], b'not a usable native log']:
            with self.assertRaises(ValueError): reuse.checkout_source(raw, {'steps': [step]})

class ExecutedClasspathContracts(unittest.TestCase):
    def test_resolved_test_dependencies_must_match_what_the_jvm_actually_used(self):
        import reuse
        import tempfile
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); folder = root/'app/build/qualification'; folder.mkdir(parents=True)
            reports = root/'app/build/test-results/test'; reports.mkdir(parents=True)
            (reports/'TEST-fixture.xml').write_text('<testsuite/>')
            content = [['app/build/classes/java/main/Example.class', 'a'*64]]
            snapshot = {'schema': 2, 'gradle': '9.2.0', 'compilerToolchain': {}, 'resolvedTestRuntimes': {'test': {}}, 'production': content, 'compiler': 'javac 25', 'java': 'observed Java 25',
                        'resolvedTestDependencies': {'test': [['dependencies/actual.jar', 'b'*64]]}}
            witness = {'schema': 2, 'gradle': '9.2.0', 'compilerToolchain': {}, 'testRuntime': {}, 'production': content, 'compiler': 'javac 25', 'java': 'observed Java 25',
                       'sourceInputs': 'd'*64, 'executed': True,
                       'testDependencies': [['dependencies/actual.jar', 'c'*64]]}
            q.write(folder/'production.json', snapshot); q.write(folder/'test.json', witness)
            with patch.object(reuse.summary, 'ROOT', root), patch.object(q, 'source_inputs', return_value='d'*64):
                with self.assertRaises(ValueError): reuse.summary.production(True)


class ProductionSchemaContracts(unittest.TestCase):
    def test_unknown_or_missing_production_metadata_is_a_reuse_miss(self):
        for value in [{}, {'app': True}, {'app': {'schema': 2}}]:
            with self.assertRaises(ValueError): q.validate_production(value, 'f'*64)




class PolicySchedulingContracts(unittest.TestCase):
    def test_off_shadow_enforce_and_every_unknown_dependency_fail_closed(self):
        for mode in ['off', 'shadow', 'enforce']:
            jobs = {'eligibility': {'result': 'success', 'outputs': {'mode': mode, 'eligible': 'false'}},
                    'compare': {'result': 'skipped'}}
            self.assertEqual(q.admission_policy(jobs), mode)
        for mode in ['shadow', 'enforce']:
            jobs = {'eligibility': {'result': 'success', 'outputs': {'mode': mode, 'eligible': 'true'}},
                    'compare': {'result': 'success', 'outputs': {'reuse': 'false'}}}
            self.assertEqual(q.admission_policy(jobs), mode)
            for outcome in ['failure', 'cancelled', 'skipped', '']:
                with self.assertRaises(ValueError): q.admission_policy({**jobs, 'compare': {'result': outcome}})
            for bad in ['unknown', None, '']:
                changed = copy.deepcopy(jobs);changed['eligibility']['outputs']['mode'] = bad
                with self.assertRaises(ValueError): q.admission_policy(changed)
        with self.assertRaises(ValueError): q.admission_policy({'eligibility': {'result': 'success'}, 'compare': {'result': 'skipped'}})




class BoundedApiContracts(unittest.TestCase):
    def test_budget_size_and_redirect_never_forward_read_token(self):
        import os
        import time
        import reuse
        from urllib.error import HTTPError
        from unittest.mock import patch
        class Response(io.BytesIO):
            pass
        class Opener:
            def __init__(self): self.requests = []
            def open(self, request, timeout):
                self.requests.append(request)
                if len(self.requests) == 1:
                    raise HTTPError(request.full_url, 302, 'redirect',
                        {'Location': 'https://fixture.blob.core.windows.net/data'}, None)
                return Response(b'bounded archive data')
        api = reuse.GitHub(); api.opener = Opener()
        with patch.dict(os.environ, {'GH_TOKEN': 'synthetic-fixture-token'}):
            self.assertEqual(api.request('/repos/'+q.REPOSITORY+'/fixture', archive=True), b'bounded archive data')
        self.assertTrue(api.opener.requests[0].has_header('Authorization'))
        self.assertFalse(api.opener.requests[1].has_header('Authorization'))
        with self.assertRaisesRegex(ValueError, 'limit'): api.bounded_read(Response(b'12345'), 4)
        api.deadline = time.monotonic()-1
        with self.assertRaisesRegex(ValueError, 'budget'): api.bounded_read(Response(b'123'), 4)
        class LateResponse(Response):
            def read1(self, size):
                api.deadline = time.monotonic()-1
                return super().read1(size)
        api.deadline = time.monotonic()+10
        with self.assertRaisesRegex(ValueError, 'budget'): api.bounded_read(LateResponse(b'123'), 4)


if __name__ == '__main__': unittest.main()

class NativePolicyAdmissionContracts(unittest.TestCase):
    setUp = NativeReuseContracts.setUp

    def test_exact_native_inventory_and_first_attempts_reject_each_forgery(self):
        summary = self.reuse.summary
        valid = browser_fixture(self.master, '2'*64)
        summary.validate_browser(valid, self.master, '2'*64)
        for mutation in ['old-schema','missing-engine','extra-engine','duplicate-context','missing-case','extra-case','source','tree','candidate','policy','run','attempt','filter','skip','retry','flaky','cancelled','failed','empty','count','unsupported-engine','missing-decoder']:
            bad = copy.deepcopy(valid); row = bad['receipts'][0]
            if mutation == 'old-schema': bad['schema'] = 1
            elif mutation == 'missing-engine': bad['receipts'].pop()
            elif mutation == 'extra-engine': bad['receipts'].append(copy.deepcopy(row))
            elif mutation == 'duplicate-context': bad['receipts'][1] = copy.deepcopy(row)
            elif mutation == 'missing-case': row['outcomes'].pop()
            elif mutation == 'extra-case': row['outcomes'].append(copy.deepcopy(row['outcomes'][0]))
            elif mutation == 'source': row['identity']['sourceSha'] = '0'*40
            elif mutation == 'tree': row['sourceContext']['tree'] = '0'*40
            elif mutation == 'candidate': row['identity']['artifactSha256'] = '0'*64
            elif mutation == 'policy': row['policy'] = '0'*64
            elif mutation == 'run': row['runner']['run'] = '299'
            elif mutation == 'attempt': row['runner']['attempt'] = '2'
            elif mutation == 'filter': row['partition']['shard'] = '1/2'
            elif mutation == 'skip': row['outcomes'][0]['results'][0]['status'] = 'skipped'
            elif mutation == 'retry': row['outcomes'][0]['results'].append({'status':'passed','retry':1,'duration':1})
            elif mutation == 'flaky': row['outcomes'][0]['status'] = 'flaky'
            elif mutation == 'cancelled': row['outcomes'][0]['results'][0]['status'] = 'interrupted'
            elif mutation == 'failed': row['status'] = 'failed'
            elif mutation == 'empty': row['outcomes'] = []; row['selected'] = []
            elif mutation == 'count': bad['selected'] -= 1
            elif mutation == 'unsupported-engine': row['environment']['browsers'] = {}
            elif mutation == 'missing-decoder': row['environment']['ffprobe'] = ''
            with self.subTest(mutation=mutation), self.assertRaises(ValueError): summary.validate_browser(bad,self.master,'2'*64)

    def test_submitted_plan_cannot_expand_or_narrow_the_fixed_contract(self):
        value=browser_fixture(self.master,'2'*64)
        for required in [[],['ordinary','media'],['ordinary','admin'],['ordinary','physics-live']]:
            bad=copy.deepcopy(value);bad['selectionPlan']['required']=required
            with self.subTest(required=required), self.assertRaises(ValueError):
                self.reuse.summary.validate_browser(bad,self.master,'2'*64)

    def test_ordinary_physics_requires_both_exact_representative_corpora_and_actual_attempts(self):
        plan=fixture_plan(self.master)
        unit={'schema':2,'policy':selection.POLICY_HASH,'status':'passed','profile':'ordinary',
            'physicsProfile':'ordinary','sourceSha':self.master['sourceSha'],'sourceContext':self.master,
            'selectionPlan':plan,'runner':{'run':'300','attempt':'1'},'cases':unit_cases(),'attempts':[{}]}
        self.reuse.summary.validate_unit(unit,self.master)
        for mutation in ['deep','missing','duplicate','cached-only','attempt']:
            bad=copy.deepcopy(unit)
            if mutation=='deep': bad['physicsProfile']='deep';bad['cases']=unit_cases(range(1,101))
            elif mutation=='missing': bad['cases'].pop()
            elif mutation=='duplicate': bad['cases'].append(copy.deepcopy(bad['cases'][0]))
            elif mutation=='cached-only': bad['attempts']=[]
            elif mutation=='attempt': bad['runner']['attempt']='2'
            with self.subTest(mutation=mutation),self.assertRaises(ValueError):
                self.reuse.summary.validate_unit(bad,self.master)

    def test_gate_rejects_forged_raw_reports_even_with_valid_success_summaries(self):
        valid=browser_fixture(self.master,'2'*64)
        for mutation in ['no-raw','focus','parallel','project','retry-policy','runner-error','empty','missing','extra','renamed','failed','retried','stats','forged-id']:
            bad=copy.deepcopy(valid)
            report=bad['native']['execution']
            spec=report['suites'][0]['specs'][0]
            if mutation=='no-raw': bad.pop('native')
            elif mutation=='focus': report['config']['forbidOnly']=False
            elif mutation=='parallel': report['config']['workers']=2
            elif mutation=='project': report['config']['projects'].pop()
            elif mutation=='retry-policy': report['config']['projects'][0]['retries']=1
            elif mutation=='runner-error': report['errors']=[{'message':'crashed'}]
            elif mutation=='empty': report['suites']=[]
            elif mutation=='missing': report['suites'][0]['specs'].pop()
            elif mutation=='extra': report['suites'][0]['specs'].append(copy.deepcopy(spec))
            elif mutation=='renamed': spec['title']='forged'
            elif mutation=='failed': spec['tests'][0]['results'][0]['status']='failed'
            elif mutation=='retried': spec['tests'][0]['results'].append({'status':'passed','retry':1,'duration':1})
            elif mutation=='stats': report['stats']['unexpected']=1
            elif mutation=='forged-id': spec['id']='forged'
            with self.subTest(mutation=mutation),self.assertRaises(ValueError):
                self.reuse.summary.validate_browser(bad,self.master,'2'*64)

    def test_gate_join_reads_actual_report_files_and_rejects_extra_or_incomplete_artifacts(self):
        from browser_evidence import join
        valid=browser_fixture(self.master,'2'*64)
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            for r in valid['receipts']: q.write(root/(r['partition']['name']+'-1of1-receipt.json'),r)
            q.write(root/'native-discovery.json',valid['native']['discovery'])
            q.write(root/'native-results.json',valid['native']['execution'])
            self.assertEqual(join(root,self.master,'2'*64)['selected'],14)
            q.write(root/'extra-receipt.json',valid['receipts'][0])
            with self.assertRaises(ValueError): join(root,self.master,'2'*64)
            (root/'extra-receipt.json').unlink()
            (root/'native-discovery.json').unlink()
            with self.assertRaises(ValueError): join(root,self.master,'2'*64)

    def test_node_admission_accepts_gate_serialization_and_rejects_forged_execution(self):
        with tempfile.TemporaryDirectory() as temp:
            file=Path(temp)/'acceptance.json'
            valid=browser_fixture(self.master,'2'*64)
            q.write(file,valid)
            command=['node','frontend/scripts/verification/browser-admit.mjs',str(file),self.master['sourceSha'],'2'*64]
            good=subprocess.run(command,cwd=q.ROOT,capture_output=True,text=True,timeout=10)
            self.assertEqual(good.returncode,0,good.stderr)
            valid['native']['execution']['suites'][0]['specs'][0]['title']='forged'
            q.write(file,valid)
            bad=subprocess.run(command,cwd=q.ROOT,capture_output=True,text=True,timeout=10)
            self.assertNotEqual(bad.returncode,0)
