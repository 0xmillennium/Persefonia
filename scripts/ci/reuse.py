#!/usr/bin/env python3
"""Trusted master-only PR eligibility and rebuild/compare; misses always select full execution."""
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, build_opener, HTTPRedirectHandler
import qualification as q
import importlib.util

_spec = importlib.util.spec_from_file_location('verification_summary', Path(__file__).with_name('verification-summary.py'))
summary = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(summary)
OUTPUT = q.ROOT/'app/build/ci'


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl): return None


class GitHub:
    def __init__(self):
        self.deadline = time.monotonic() + 120
        self.opener = build_opener(NoRedirect)

    def remaining(self):
        remaining = self.deadline - time.monotonic()
        q.require(remaining > 0, 'Reuse API lookup budget exhausted')
        return min(30, remaining)

    def bounded_read(self, response, limit):
        chunks, size = [], 0
        while True:
            self.remaining()
            chunk = response.read1(min(1024*1024, limit-size+1))
            self.remaining()
            if not chunk: return b''.join(chunks)
            size += len(chunk)
            q.require(size <= limit, 'API response exceeds download limit')
            chunks.append(chunk)

    def request(self, path, *, archive=False, max_bytes=None):
        q.require(path.startswith('/repos/'+q.REPOSITORY+'/') and '\n' not in path, 'Untrusted API path')
        request = Request('https://api.github.com'+path, headers={
            'Accept': 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28',
            'Authorization': 'Bearer '+os.environ['GH_TOKEN']})
        limit = max_bytes or (256 if archive else 32)*1024*1024
        try:
            with self.opener.open(request, timeout=self.remaining()) as response:
                raw = self.bounded_read(response, limit)
                return raw if archive else q.load_json(raw)
        except HTTPError as error:
            if not archive or error.code != 302: raise ValueError(f'GitHub metadata unavailable (HTTP {error.code})') from None
            location = error.headers['Location']
        url = urlparse(location)
        q.require(url.scheme == 'https' and url.hostname and not url.username and
                  any(url.hostname.endswith(suffix) for suffix in ('.blob.core.windows.net', '.actions.githubusercontent.com', '.githubusercontent.com')),
                  'Untrusted artifact origin')
        # New request/opener: the read token is never sent to the signed download origin.
        try:
            with self.opener.open(Request(location), timeout=self.remaining()) as response:
                raw = self.bounded_read(response, limit)
            return raw
        except (HTTPError, URLError): raise ValueError('Artifact download unavailable') from None

    def get(self, path): return self.request('/repos/'+q.REPOSITORY+'/'+path)

    def checkout(self, job):
        raw = self.request(f'/repos/{q.REPOSITORY}/actions/jobs/{job["id"]}/logs', archive=True, max_bytes=16*1024*1024)
        return checkout_source(raw, job)

    def artifact(self, metadata, run, source_sha):
        validate_artifact_metadata(metadata, run)
        raw = self.request(f'/repos/{q.REPOSITORY}/actions/artifacts/{metadata["id"]}/zip', archive=True)
        checksum = hashlib.sha256(raw).hexdigest()
        q.require(metadata.get('digest') == 'sha256:'+checksum and len(raw) == metadata['size_in_bytes'], 'Artifact API digest/size mismatch')
        return q.archive_members(raw)


def validate_artifact_metadata(metadata, run):
    q.require(metadata['expired'] is False and isinstance(metadata['id'], int)
              and 0 < metadata['size_in_bytes'] <= 256*1024*1024, 'Expired or oversized artifact')
    q.time_window(run['created_at'], metadata['created_at'], run['updated_at'])
    provenance = metadata['workflow_run']
    q.require(provenance['id'] == run['id'] and provenance['repository_id'] == q.REPOSITORY_ID
              and provenance['head_repository_id'] == q.REPOSITORY_ID
              and provenance['head_sha'] == run['head_sha'], 'Artifact API provenance mismatch')
    q.require(q.HASH.fullmatch(metadata.get('digest', '').removeprefix('sha256:'))
              and metadata['digest'].startswith('sha256:'), 'Missing artifact API digest')
    expires = datetime.fromisoformat(metadata['expires_at'].replace('Z', '+00:00'))
    q.require(expires > datetime.now(timezone.utc), 'Expired artifact timestamp')


def compact_artifact(value):
    result = {key: value[key] for key in ['id', 'name', 'size_in_bytes', 'digest', 'expired', 'created_at', 'expires_at']}
    result['workflow_run'] = {key: value['workflow_run'][key] for key in ['id', 'repository_id', 'head_repository_id', 'head_sha']}
    return result


def compact_run(value):
    result = {key: value[key] for key in ['id', 'head_sha', 'run_attempt', 'created_at', 'updated_at', 'event', 'path', 'status', 'conclusion', 'workflow_id']}
    result['repository'] = {key: value['repository'][key] for key in ['id', 'full_name']}
    result['head_repository'] = {key: value['head_repository'][key] for key in ['id', 'fork']}
    return result


def validate_changes(api, base, revision):
    comparison = api.get(f'compare/{base}...{revision}?per_page=100')
    q.require(comparison.get('status') == 'ahead' and comparison.get('total_commits', 301) < 100
              and q.safe_changes(comparison.get('files', [])), 'Protected, unknown or truncated source change')


def checkout_source(raw, job):
    """Bind the tested merge to the API-confirmed producer's native checkout step."""
    q.require(isinstance(raw, bytes) and len(raw) <= 16*1024*1024, 'Missing or oversized producer log')
    steps = [step for step in job['steps'] if step.get('name') == 'Check out repository']
    q.require(len(steps) == 1 and steps[0]['conclusion'] == 'success', 'Missing native checkout step')
    step = steps[0]
    start = datetime.fromisoformat(step['started_at'].replace('Z', '+00:00'))
    end = datetime.fromisoformat(step['completed_at'].replace('Z', '+00:00'))
    text = raw.decode('utf-8', errors='strict')
    text = re.sub(r'\x1b(?:\[[0-?]*[ -/]*[@-~]|\][^\x07]*(?:\x07|\x1b\\))', '', text)
    checkout = []
    for line in text.splitlines():
        match = re.match(r'^(\d{4}-\d{2}-\d{2}T[0-9:.]+Z) (.*)$', line)
        if not match: continue
        stamp = datetime.fromisoformat(match[1].replace('Z', '+00:00'))
        # API step times have second precision. Source code executes in later setup/build steps.
        if start <= stamp and (stamp-end).total_seconds() < 1: checkout.append(match[2].strip())
    matches = []
    for index, line in enumerate(checkout[:-1]):
        if re.fullmatch(r'(?:\[command])?[^\r\n]*[/\\]git log -1 --format=%H', line):
            value = checkout[index+1]
            if q.SHA.fullmatch(value): matches.append(value)
    q.require(len(matches) == 1, 'Missing, duplicate or malformed native checkout SHA')
    return matches[0]


def validate_run(run, master_created):
    q.require(run['repository']['id'] == q.REPOSITORY_ID and run['repository']['full_name'] == q.REPOSITORY
              and run['head_repository']['id'] == q.REPOSITORY_ID and not run['head_repository']['fork']
              and run['workflow_id'] == q.WORKFLOW_ID and run['path'] == '.github/workflows/ci.yml'
              and run['run_attempt'] == 1 and run['event'] == 'pull_request'
              and run['status'] == 'completed' and run['conclusion'] == 'success', 'Untrusted or rerun PR CI')
    q.time_window(run['created_at'], run['updated_at'], master_created)


def validate_jobs(jobs, run):
    expected = ['Automation', 'Reuse eligibility', 'Comparison build', 'Build + JVM', 'Frontend verification',
                'Verify', 'Browser receipts', 'Gate'] + [f'Browsers ({p})' for p in q.PARTITIONS]
    q.require(jobs.get('total_count') == len(expected) and len(jobs['jobs']) == len(expected), 'Incomplete native job inventory')
    names = [job['name'] for job in jobs['jobs']]
    q.require(sorted(names) == sorted(expected), 'Missing, duplicate or unexpected native jobs')
    for job in jobs['jobs']:
        wanted = 'skipped' if job['name'] == 'Comparison build' else 'success'
        q.require(job['run_id'] == run['id'] and job['run_attempt'] == 1 and job['status'] == 'completed'
                  and job['conclusion'] == wanted, 'Failed/cancelled/skipped/rerun native job')
        if wanted == 'success':
            q.require(job['started_at'] and job['completed_at'] and job.get('steps')
                      and all(step['conclusion'] in {'success', 'skipped'} for step in job['steps']), 'Incomplete native execution')
            q.time_window(run['created_at'], job['completed_at'], run['updated_at'])


def eligible(api, source, mode):
    result = {'schema': q.SCHEMA, 'kind': 'reuse-eligibility', 'mode': mode, 'master': source,
              'eligible': False, 'reason': 'Full verification context'}
    if mode == 'off': result['reason'] = 'Operational off switch'; return result
    if source.get('dirty') is True: return result
    if source['event'] != 'push' or source['ref'] != 'refs/heads/master' or source['attempt'] != '1': return result
    master_run = api.get('actions/runs/'+source['run'])
    q.require(master_run['repository']['id'] == q.REPOSITORY_ID and master_run['head_repository']['id'] == q.REPOSITORY_ID
              and not master_run['head_repository']['fork'] and master_run['path'] == '.github/workflows/ci.yml'
              and master_run['workflow_id'] == q.WORKFLOW_ID and master_run['event'] == 'push'
              and master_run['head_branch'] == 'master' and master_run['head_sha'] == source['sourceSha']
              and master_run['run_attempt'] == 1, 'Untrusted master run')
    associated = api.get('commits/'+source['sourceSha']+'/pulls?per_page=100')
    q.require(isinstance(associated, list) and len(associated) == 1, 'Ambiguous or absent merged PR')
    pr = api.get('pulls/'+str(associated[0]['number']))
    q.require(pr['merged'] is True and pr['merge_commit_sha'] == source['sourceSha']
              and pr['base']['ref'] == 'master' and pr['base']['repo']['id'] == q.REPOSITORY_ID
              and pr['head']['repo']['id'] == q.REPOSITORY_ID and not pr['head']['repo']['fork'], 'Fork or unsupported merge relationship')
    # Initial policy supports a single-parent squash; rebases/merge commits safely execute in full.
    q.require(len(source['parents']) == 1, 'Unsupported master merge history')
    validate_changes(api, source['parents'][0], source['sourceSha'])
    runs = api.get(f'actions/workflows/{q.WORKFLOW_ID}/runs?event=pull_request&head_sha={pr["head"]["sha"]}&per_page=100')
    q.require(runs['total_count'] == 1 and len(runs['workflow_runs']) == 1, 'Ambiguous or missing PR CI history')
    run = api.get('actions/runs/'+str(runs['workflow_runs'][0]['id']))
    validate_run(run, master_run['created_at'])
    q.require(run['head_sha'] == pr['head']['sha'], 'PR head drift')
    artifacts = api.get(f'actions/runs/{run["id"]}/artifacts?per_page=100')
    q.require(artifacts['total_count'] == len(artifacts['artifacts']) < 100, 'Unbounded artifact inventory')
    qualification = [a for a in artifacts['artifacts'] if a['name'].startswith('persefonia-qualification-')]
    q.require(len(qualification) == 1, 'Missing or duplicate native qualification artifact')
    validate_artifact_metadata(qualification[0], run)
    inputs = [a for a in artifacts['artifacts'] if a['name'].startswith('persefonia-browser-input-')]
    q.require(len(inputs) == 1, 'Missing or duplicate tested PR candidate')
    validate_artifact_metadata(inputs[0], run)
    members = api.artifact(qualification[0], run, None)
    required = {'qualification.json', 'verification.json', 'jvm.json', 'frontend.json', 'browser-acceptance.json', 'automation.json'}
    q.require(required <= members.keys() <= required | {'equivalence.json'}, 'Incomplete native qualification archive')
    record = q.load_json(members['qualification.json'])
    tested = record['identity']
    q.validate_identity(tested, tested)
    q.require(record['schema'] == q.SCHEMA and record['status'] == 'passed' and record['lane'] == 'qualification'
              and record['mode'] == 'executed' and tested['event'] == 'pull_request' and tested['attempt'] == '1'
              and tested['run'] == str(run['id']) and tested['pr']['number'] == pr['number']
              and tested['pr']['head'] == pr['head']['sha'] and tested['pr']['headRepositoryId'] == q.REPOSITORY_ID
              and tested['pr']['baseRepositoryId'] == q.REPOSITORY_ID, 'Qualification PR identity mismatch')
    q.require(qualification[0]['name'] == 'persefonia-qualification-'+tested['sourceSha']
              and inputs[0]['name'] == 'persefonia-browser-input-'+tested['sourceSha'], 'Wrong qualification artifact name')
    q.require(tested['parents'] == [tested['pr']['base'], tested['pr']['head']]
              and tested['pr']['base'] == source['parents'][0], 'Unsupported tested merge/base history')
    commit = api.get('git/commits/'+tested['sourceSha'])
    final = api.get('git/commits/'+source['sourceSha'])
    q.require(commit['sha'] == tested['sourceSha'] and final['sha'] == source['sourceSha']
              and [p['sha'] for p in commit['parents']] == tested['parents']
              and [p['sha'] for p in final['parents']] == source['parents']
              and commit['tree']['sha'] == tested['tree'] == final['tree']['sha'] == source['tree'], 'Wrong tested/final tree or parents')
    q.require(tested['policy'] == source['policy'] and tested['inputs'] == source['inputs'] and tested['dirty'] is False, 'Control policy/input digest drift')
    # Both comparisons include deletions and old/new rename names. API truncation is a miss.
    validate_changes(api, tested['pr']['base'], tested['sourceSha'])
    base_commit = api.get('git/commits/'+tested['pr']['base'])
    q.require(base_commit['sha'] == tested['pr']['base'], 'Recorded base commit mismatch')
    for tree_sha in (base_commit['tree']['sha'], source['tree']):
        tree = api.get('git/trees/'+tree_sha+'?recursive=1')
        q.require(tree.get('truncated') is False, 'Truncated control input tree')
        q.require(all(entry['mode'] in {'100644', '040000'} or q.protected(entry['path'])
                      for entry in tree['tree']), 'Unclassified executable or symlink input')
    jobs = api.get(f'actions/runs/{run["id"]}/attempts/1/jobs?per_page=100')
    validate_jobs(jobs, run)
    producer = next(job for job in jobs['jobs'] if job['name'] == 'Build + JVM')
    q.require(api.checkout(producer) == tested['sourceSha'], 'Native producer checkout differs from claimed tested merge')
    completed = max(job['completed_at'] for job in jobs['jobs'] if job['conclusion'] == 'success')
    q.time_window(run['created_at'], completed, master_run['created_at'])
    result.update(eligible=True, reason='Eligible first-attempt same-tree same-repository PR', pr=tested,
                  prRun=compact_run(run), prCompleted=completed, masterCreated=master_run['created_at'],
                  qualificationArtifact=compact_artifact(qualification[0]),
                  artifacts=[compact_artifact(a) for a in [qualification[0], inputs[0]]])
    # Keep untrusted bytes as bounded data; no scripts are extracted or imported.
    for name, data in members.items():
        q.require(name in {'qualification.json', 'verification.json', 'jvm.json', 'frontend.json', 'browser-acceptance.json', 'automation.json', 'equivalence.json'}, 'Unexpected qualification member')
        q.write(OUTPUT/'pr-evidence'/name, q.load_json(data))
    validate_native(OUTPUT/'pr-evidence', tested, record['candidate'], rediscover=False)
    return result


def validate_native(directory, tested, checksum, rediscover=True):
    record = q.receipt(q.read(directory/'qualification.json'), 'qualification', tested, checksum)
    q.require(record['mode'] == 'executed', 'Recursive qualification reuse is unsupported')
    jvm, frontend = summary.validate_nonbrowser(directory, tested, checksum)
    verify = q.receipt(q.read(directory/'verification.json'), 'verify', tested, checksum)
    q.require(verify['mode'] == 'executed' and verify['producers'] == {'jvm': q.digest(jvm), 'frontend': q.digest(frontend)}
              and record['verification'] == verify, 'Forged/missing producer evidence')
    browser = summary.validate_browser(q.read(directory/'browser-acceptance.json'), tested, checksum)
    q.require(record['browser'] == browser, 'Browser record mismatch')
    automation = q.receipt(q.read(directory/'automation.json'), 'automation', tested)
    summary.validate_java(automation['javaCases'])
    q.require(record['automation'] == q.digest(automation), 'Automation receipt mismatch')
    if rediscover:
        subprocess.run(['node', 'frontend/scripts/verification/unit-validate.mjs', str(directory/'frontend.json')], cwd=q.ROOT, check=True)
        subprocess.run(['node', 'frontend/scripts/verification/browser-validate.mjs', str(directory/'browser-acceptance.json')], cwd=q.ROOT, check=True)
    return jvm, frontend, browser


def compare(api, eligibility, fresh):
    q.require(eligibility['eligible'] is True, 'Comparison requires eligibility')
    source = q.identity()
    q.validate_identity(eligibility['master'], source)
    tested = eligibility['pr']
    checksum = fresh['candidate']
    jvm, frontend, browser = validate_native(OUTPUT/'pr-evidence', tested, checksum)
    # Compare content, production used by JVM tests, and observed producer/runtime metadata.
    q.validate_production(fresh['production'], source['inputs'])
    q.validate_production(jvm['production'], tested['inputs'])
    q.require(fresh['production'] == jvm['production'], 'Production output/compiler/test-classpath mismatch')
    q.require(fresh['environment'] == jvm['environment'], 'Build/runtime/runner environment drift')
    q.require(fresh['java21'] == jvm['java21'] and fresh['java21']['passed'] is True, 'Java 21 runtime drift')
    q.require(all(frontend['unit']['runner'][key] == fresh['environment'][key]
                  for key in ['os', 'arch', 'node', 'npm', 'playwright']), 'Frontend toolchain drift')
    q.require(frontend['runner']['imageOS'] == fresh['environment']['runnerImage']['os']
              and frontend['runner']['imageVersion'] == fresh['environment']['runnerImage']['version']
              and frontend['unit']['runner']['java'] == fresh['production']['app']['java'],
              'Frontend runner image/Java metadata drift')
    q.require(fresh['environment']['runnerImage']['version'] != 'local', 'Unknown hosted runner image')
    current_browsers = q.read(OUTPUT/'comparison-environment.json')
    for receipt in browser['receipts']:
        q.require(receipt['runner']['run'] == tested['run'] and receipt['runner']['attempt'] == '1', 'Mixed browser attempts')
        q.require(receipt['runner']['java'] == fresh['java21']['version'], 'Browser application Java runtime drift')
        environment = receipt['environment']
        project = receipt['partition']['project']
        engine = 'firefox' if project == 'firefox-desktop' else 'webkit' if project == 'webkit-desktop' else 'chromium'
        q.require(set(environment.get('browsers', {})) == {engine}, 'Missing actual browser executable identity')
        q.require(all(environment.get(key) == current_browsers.get(key) and environment.get(key)
                      for key in ['os', 'arch', 'node', 'npm', 'playwright']), 'Browser runtime/toolchain drift')
        q.require(environment['runnerImage'] == fresh['environment']['runnerImage']
                  and environment['browserRevisions'] == current_browsers['browserRevisions']
                  and environment['ffmpeg'] == current_browsers['ffmpeg']
                  and environment['ffprobe'] == current_browsers['ffprobe'], 'Browser/decoder/runner drift')
        for engine, executable in environment['browsers'].items():
            q.require(current_browsers['browsers'].get(engine) == executable, 'Browser executable revision drift')
    run = eligibility['prRun']
    inputs = [a for a in eligibility['artifacts'] if a['name'] == 'persefonia-browser-input-'+tested['sourceSha']]
    q.require(len(inputs) == 1, 'Missing/duplicate tested PR candidate')
    members = api.artifact(inputs[0], run, tested['sourceSha'])
    q.require(set(members) == {'persefonia.jar', 'persefonia.jar.sha256', 'source-sha.txt'}, 'Malformed candidate archive')
    q.require(hashlib.sha256(members['persefonia.jar']).hexdigest() == checksum
              and members['persefonia.jar.sha256'].decode().strip() == checksum+'  persefonia.jar'
              and members['source-sha.txt'].decode().strip() == tested['sourceSha'], 'Inner tested PR BootJar mismatch')
    decision = {'schema': q.SCHEMA, 'kind': 'master-equivalence', 'mode': eligibility['mode'],
        'eligible': True, 'equivalent': True, 'master': source, 'pr': tested,
        'candidate': checksum, 'production': q.digest(fresh['production']),
        'environment': q.digest(current_browsers), 'buildEnvironment': q.digest(fresh['environment']), 'apiValidated': True, 'validatedNativeEvidence': True,
        'artifacts': [{k: a[k] for k in ['id', 'digest', 'name', 'workflow_run', 'created_at']}
                      for a in [eligibility['qualificationArtifact'], inputs[0]]],
        'masterCreated': eligibility['masterCreated'], 'prCreated': run['created_at'], 'prCompleted': eligibility['prCompleted'], 'freshJava21': fresh['java21'],
        'preliminaryTimings': fresh.get('timings', {}),
        'reason': 'Fresh master output equals API-bound first-attempt PR evidence'}
    q.validate_equivalence(decision, source, checksum)
    return decision


def main():
    command = sys.argv[1]
    started = time.monotonic()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    if command == 'eligibility':
        mode = q.reuse_mode(os.environ.get('CI_QUALIFICATION_REUSE'))
        try: result = eligible(GitHub(), q.identity(), mode)
        except (ValueError, KeyError, TypeError, AttributeError, OSError, URLError) as error:
            # Details are bounded data; never print HTTP exceptions containing signed URLs.
            result = {'schema': q.SCHEMA, 'kind': 'reuse-eligibility', 'mode': mode,
                      'master': q.identity(), 'eligible': False, 'reason': str(error)[:300] if isinstance(error, ValueError) else type(error).__name__}
        q.write(OUTPUT/'eligibility.json', result)
        output = {'eligible': str(result['eligible']).lower(), 'mode': mode}
    elif command == 'compare':
        eligibility = q.read(OUTPUT/'eligibility.json')
        fresh = q.read(OUTPUT/'production.json')
        # Build/runtime failures occur before this command and must fail the comparison job.
        try: result = compare(GitHub(), eligibility, fresh)
        except (ValueError, KeyError, TypeError, AttributeError, OSError, URLError, subprocess.CalledProcessError) as error:
            reason = str(error) if isinstance(error, ValueError) else type(error).__name__
            result = {'schema': q.SCHEMA, 'kind': 'master-equivalence', 'mode': eligibility['mode'],
                      'master': q.identity(), 'eligible': True, 'equivalent': False, 'reason': reason[:300]}
        result['comparisonSeconds'] = time.monotonic()-started
        q.write(OUTPUT/'equivalence.json', result)
        output = {'reuse': str(result.get('equivalent') is True and eligibility['mode'] == 'enforce').lower()}
    elif command == 'browser-admission':
        source = q.identity()
        decision = q.read(OUTPUT/'equivalence.json')
        q.validate_equivalence(decision, source, q.candidate(OUTPUT))
        q.require(decision['mode'] == 'enforce', 'Shadow cannot admit skipped browsers')
        q.write(OUTPUT/'browser-acceptance.json', {'schema': q.SCHEMA, 'mode': 'reused',
                'identity': source, 'equivalence': q.digest(decision)})
        output = {}
    else: raise ValueError('Unknown reuse operation')
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a') as file:
            for key, value in output.items(): file.write(key+'='+value+'\n')
    print(json.dumps({'operation': command, **output, 'seconds': time.monotonic()-started}))


if __name__ == '__main__':
    try: main()
    except (ValueError, KeyError, OSError) as error: raise SystemExit(type(error).__name__+': invalid reuse inputs') from None
