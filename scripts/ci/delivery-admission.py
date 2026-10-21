#!/usr/bin/env python3
"""Admit only a successful trusted master CI qualification and its authoritative candidate."""
import os
from pathlib import Path
import qualification as q
from reuse import GitHub, summary


def validate(record, run, jobs, checksum, source_sha, run_id, attempt, *, expected_source):
    source = record['identity']
    q.validate_identity(source, expected_source)
    q.require(run['id'] == int(run_id) and run['run_attempt'] == int(attempt)
              and run['repository']['id'] == q.REPOSITORY_ID and run['head_repository']['id'] == q.REPOSITORY_ID
              and not run['head_repository']['fork'] and run['workflow_id'] == q.WORKFLOW_ID
              and run['path'] == '.github/workflows/ci.yml' and run['event'] == 'push'
              and run['head_branch'] == 'master' and run['head_sha'] == source_sha
              and run['status'] == 'completed' and run['conclusion'] == 'success', 'Untrusted Delivery CI source')
    q.receipt(record, 'qualification', source, checksum)
    q.require(source['event'] == 'push' and source['ref'] == 'refs/heads/master'
              and source['sourceSha'] == source_sha and source['run'] == run_id
              and source['attempt'] == attempt, 'Delivery record source/attempt mismatch')
    names = [job['name'] for job in jobs['jobs']]
    q.require(jobs['total_count'] == len(names) == 16 and len(set(names)) == 16, 'Incomplete Delivery job inventory')
    required = {'Automation', 'Reuse eligibility', 'Comparison build', 'Build + JVM', 'Frontend verification',
                'Verify', 'Browser receipts', 'Gate', *['Browsers ('+p+')' for p in q.PARTITIONS]}
    q.require(set(names) == required, 'Unknown Delivery CI job policy')
    for job in jobs['jobs']:
        name = job['name']
        wanted = 'skipped' if record['mode'] == 'reused' and (name in {'Build + JVM', 'Frontend verification'} or name.startswith('Browsers (')) else 'success'
        allowed = {'success', 'skipped'} if name == 'Comparison build' and record['mode'] == 'executed' else {wanted}
        q.require(job['run_id'] == run['id'] and job['run_attempt'] == int(attempt)
                  and job['status'] == 'completed' and job['conclusion'] in allowed, 'Invalid Delivery job outcome/attempt')
    verify = record['verification']
    q.receipt(verify, 'verify', source, checksum)
    q.require(verify['mode'] == record['mode'] and record.get('reusePolicyMode') in {'off', 'shadow', 'enforce'}
              and verify.get('reusePolicyMode') == record['reusePolicyMode'], 'Inconsistent Delivery qualification mode')
    if record['mode'] == 'reused':
        q.validate_equivalence(verify['equivalence'], source, checksum)
        q.require(record['reusePolicyMode'] == 'enforce' and verify['equivalence']['mode'] == 'enforce', 'Shadow cannot authorize Delivery reuse')
        q.require(record['browser'].get('mode') == 'reused' and record['browser'].get('identity') == source
                  and record['browser'].get('equivalence') == q.digest(verify['equivalence']),
                  'Inconsistent reused browser evidence in Delivery')
    else:
        q.require(record['mode'] == 'executed', 'Unknown Delivery admission mode')
        summary.validate_browser(record['browser'], source, checksum)
    return record


def main():
    directory = Path('delivery-input')
    record = q.read(directory/'qualification.json')
    sha, run_id, attempt = [os.environ[key] for key in ['EXPECTED_SOURCE_SHA', 'EXPECTED_CI_RUN', 'EXPECTED_CI_ATTEMPT']]
    q.require(q.git('rev-parse', 'HEAD') == sha, 'Delivery checkout mismatch')
    checksum = q.file_hash(directory/'persefonia.jar')
    q.require((directory/'persefonia.jar.sha256').read_text().strip() == checksum+'  persefonia.jar', 'Delivery candidate checksum mismatch')
    api = GitHub()
    run = api.get('actions/runs/'+run_id)
    jobs = api.get(f'actions/runs/{run_id}/attempts/{attempt}/jobs?per_page=100')
    checked_out = q.identity(source_only=True)
    expected = {**checked_out, 'run': run_id, 'attempt': attempt,
                'event': 'push', 'ref': 'refs/heads/master'}
    validate(record, run, jobs, checksum, sha, run_id, attempt, expected_source=expected)
    print('Validated '+record['mode']+' master qualification and authoritative BootJar')


if __name__ == '__main__':
    try: main()
    except (ValueError, KeyError, OSError): raise SystemExit('Delivery qualification admission failed') from None
