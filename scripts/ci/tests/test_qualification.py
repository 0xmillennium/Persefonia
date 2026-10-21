"""Adversarial contracts for trusted CI evidence; no GitHub credentials or network."""
import copy
import io
import json
from pathlib import Path
import sys
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import qualification as q


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
                'verify': 'success', 'browser': 'success', 'browser-receipts': 'success'}
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
        names = ['Automation', 'Reuse eligibility', 'Comparison build', 'Build + JVM', 'Frontend verification',
                 'Verify', 'Browser receipts', 'Gate'] + [f'Browsers ({p})' for p in q.PARTITIONS]
        jobs = {'total_count': 16, 'jobs': [{'name': name, 'run_id': 200, 'run_attempt': 1,
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
                'verify': 'success', 'browser': 'skipped', 'browser-receipts': 'success'}
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
        import tempfile
        from unittest.mock import patch
        master_run = {**self.run, 'id': 300, 'event': 'push', 'head_branch': 'master',
                      'head_sha': self.master['sourceSha'], 'created_at': self.now}
        pr = {'number': 1, 'merged': True, 'merge_commit_sha': self.master['sourceSha'],
              'base': {'ref': 'master', 'repo': {'id': q.REPOSITORY_ID}},
              'head': {'sha': '1'*40, 'repo': {'id': q.REPOSITORY_ID, 'fork': False}}}
        metadata = {'id': 1, 'name': 'persefonia-qualification-'+self.pr['sourceSha'],
            'expired': False, 'size_in_bytes': 100, 'digest': 'sha256:'+'a'*64,
            'created_at': self.run['created_at'], 'expires_at': '2099-01-01T00:00:00Z',
            'workflow_run': {'id': 200, 'repository_id': q.REPOSITORY_ID,
                'head_repository_id': q.REPOSITORY_ID, 'head_sha': '1'*40}}
        candidate = {**metadata, 'id': 2, 'name': 'persefonia-browser-input-'+self.pr['sourceSha']}
        names = ['Automation', 'Reuse eligibility', 'Comparison build', 'Build + JVM', 'Frontend verification',
                 'Verify', 'Browser receipts', 'Gate'] + [f'Browsers ({p})' for p in q.PARTITIONS]
        jobs = {'total_count': 16, 'jobs': [{'name': name, 'run_id': 200, 'run_attempt': 1,
            'status': 'completed', 'conclusion': 'skipped' if name == 'Comparison build' else 'success',
            'started_at': self.run['created_at'], 'completed_at': self.run['updated_at'],
            'steps': [{'conclusion': 'success'}]} for name in names]}
        responses = {
            'actions/runs/300': master_run, 'commits/'+self.master['sourceSha']+'/pulls?per_page=100': [{'number': 1}],
            'pulls/1': pr,
            f'actions/workflows/{q.WORKFLOW_ID}/runs?event=pull_request&head_sha={"1"*40}&per_page=100': {'total_count': 1, 'workflow_runs': [{'id': 200}]},
            'actions/runs/200': self.run,
            'actions/runs/200/artifacts?per_page=100': {'total_count': 2, 'artifacts': [metadata, candidate]},
            'actions/runs/200/attempts/1/jobs?per_page=100': jobs,
        }
        for source in [self.pr, self.master]:
            responses['git/commits/'+source['sourceSha']] = {'sha': source['sourceSha'],
                'parents': [{'sha': parent} for parent in source['parents']], 'tree': {'sha': source['tree']}}
            responses[f'compare/{"b"*40}...{source["sourceSha"]}?per_page=100'] = {
                'status': 'ahead', 'total_commits': 1,
                'files': [{'filename': 'frontend/src/product.ts', 'status': 'modified'}]}
        responses['git/commits/'+ 'b'*40] = {'sha': 'b'*40, 'tree': {'sha': '9'*40}}
        for revision in ['9'*40, self.master['tree']]:
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
        frontend = {**base, 'lane': 'frontend', 'unit': {'schema': 1, 'status': 'passed',
            'sourceSha': self.pr['sourceSha'], 'sourceContext': self.pr,
            'runner': {'run': '200', 'attempt': '1'}, 'cases': [{'status': 'passed'}], 'attempts': [{}]}}
        verify = {**base, 'lane': 'verify', 'candidate': record['candidate'], 'mode': 'executed',
            'producers': {'jvm': q.digest(jvm), 'frontend': q.digest(frontend)}}
        browser = {'schema': 1, 'status': 'passed', 'sourceContext': self.pr,
            'identity': {'sourceSha': self.pr['sourceSha'], 'artifactSha256': record['candidate']},
            'runner': {'run': '200', 'attempt': '1'}, 'partitions': 8, 'selected': 1, 'receipts': [{}]*8}
        automation = {**base, 'lane': 'automation', 'javaCases': java}
        record.update(verification=verify, browser=browser, automation=q.digest(automation))
        evidence = {'qualification.json': record, 'verification.json': verify, 'jvm.json': jvm,
            'frontend.json': frontend, 'browser-acceptance.json': browser, 'automation.json': automation}
        class API:
            def __init__(self, values): self.values = values
            def get(self, path): return self.values[path]
            def artifact(self, *args): return {name: json.dumps(value).encode() for name,value in evidence.items()}
            def checkout(inner, job): return self.pr['sourceSha']
        with tempfile.TemporaryDirectory() as temp, patch.object(self.reuse, 'OUTPUT', Path(temp)):
            result = self.reuse.eligible(API(responses), self.master, 'shadow')
            self.assertTrue(result['eligible']); self.assertEqual(result['pr'], self.pr)
            # Uploaded eligibility contains only explicitly selected provenance, no actor/commit/URLs.
            self.assertEqual(set(result['prRun']), set(self.reuse.compact_run(self.run)))
            self.assertEqual(len(result['artifacts']), 2)
            for mutation in ['fork', 'ambiguous', 'wrong-tree', 'protected', 'unknown', 'rerun', 'expired', 'missing-candidate']:
                bad = copy.deepcopy(responses)
                if mutation == 'fork': bad['pulls/1']['head']['repo']['fork'] = True
                elif mutation == 'ambiguous': bad['commits/'+self.master['sourceSha']+'/pulls?per_page=100'].append({'number': 2})
                elif mutation == 'wrong-tree': bad['git/commits/'+self.pr['sourceSha']]['tree']['sha'] = '0'*40
                elif mutation in {'protected', 'unknown'}:
                    bad[f'compare/{"b"*40}...{self.pr["sourceSha"]}?per_page=100']['files'][0]['filename'] = '.github/new.yml' if mutation == 'protected' else 'new-executable.py'
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
        browser = {'schema': 1, 'status': 'passed', 'sourceContext': self.master, 'identity': {'sourceSha': self.master['sourceSha'],
                   'artifactSha256': checksum}, 'runner': {'run': '300', 'attempt': '1'},
                   'partitions': 8, 'selected': 10, 'receipts': [{} for _ in range(8)]}
        verify = {'schema': 2, 'status': 'passed', 'lane': 'verify', 'mode': 'executed', 'reusePolicyMode': 'shadow',
                  'identity': self.master, 'candidate': checksum}
        record = {**verify, 'lane': 'qualification', 'verification': verify, 'browser': browser}
        run = {**self.run, 'id': 300, 'event': 'push', 'head_branch': 'master', 'head_sha': self.master['sourceSha']}
        names = ['Automation', 'Reuse eligibility', 'Comparison build', 'Build + JVM', 'Frontend verification',
                 'Verify', 'Browser receipts', 'Gate'] + [f'Browsers ({p})' for p in q.PARTITIONS]
        jobs = {'total_count': 16, 'jobs': [{'name': name, 'run_id': 300, 'run_attempt': 1,
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
