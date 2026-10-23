#!/usr/bin/env python3
"""Native verification receipts and strict non-browser/final admission; one evidence owner."""
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import xml.etree.ElementTree as ET
import qualification as q
import selection as selection

ROOT = q.ROOT
OUTPUT = ROOT / 'app/build/ci'


def native_java(automation=False):
    cases = []
    for report in sorted(ROOT.glob('*/build/test-results/*/TEST-*.xml')):
        if (report.parts[-5] == 'automation-tests') != automation: continue
        for case in ET.parse(report).getroot().iter('testcase'):
            status = 'failed' if case.find('failure') is not None or case.find('error') is not None else 'skipped' if case.find('skipped') is not None else 'passed'
            cases.append({'report': str(report.relative_to(ROOT)), 'id': case.attrib['classname']+'#'+case.attrib['name'],
                          'status': status, 'seconds': float(case.attrib.get('time', '0'))})
    validate_java(cases)
    return cases


def validate_java(cases):
    q.require(isinstance(cases, list) and cases and all(c['status'] == 'passed' for c in cases),
              'Missing, failed or skipped mandatory Java verification')
    keys = [(c['report'], c['id']) for c in cases]
    q.require(len(keys) == len(set(keys)), 'Duplicate Java evidence')


def production(executed=False):
    values = {}
    for path in sorted(ROOT.glob('*/build/qualification/production.json')):
        value = q.read(path)
        q.require(value['schema'] == q.SCHEMA and isinstance(value['production'], list), 'Missing production bytes')
        values[path.parts[-4]] = value
    q.require('app' in values and values['app']['production'], 'Missing app production manifest')
    if executed:
        for report_dir in ROOT.glob('*/build/test-results/*'):
            if report_dir.parts[-4] == 'automation-tests' or not list(report_dir.glob('TEST-*.xml')): continue
            project = report_dir.parts[-4]
            witness = q.read(ROOT/project/'build/qualification'/f'{report_dir.name}.json')
            q.require(witness['testRuntime'] == values[project]['resolvedTestRuntimes'][report_dir.name]
                      and witness['gradle'] == values[project]['gradle'] and witness['compilerToolchain'] == values[project]['compilerToolchain']
                      and witness['testDependencies'] == values[project]['resolvedTestDependencies'][report_dir.name]
                      and witness['sourceInputs'] == q.source_inputs() and witness['executed'] is True and witness['production'] == values[project]['production']
                      and witness['compiler'] == values[project]['compiler'] and witness['java'] == values[project]['java'],
                      'Final producer outputs differ from actual JVM test inputs')
    return values


def timings():
    paths = [ROOT/'app/build/ci/timings.jsonl', ROOT/'build/ci/task-timings.jsonl']
    return {str(path.relative_to(ROOT)): [q.load_json(line.encode()) for line in path.read_text().splitlines() if line]
            for path in paths if path.exists()}


def base(lane, source):
    return {'schema': q.SCHEMA, 'status': 'passed', 'lane': lane, 'identity': source,
            'runner': {'os': platform.system(), 'arch': platform.machine(),
                       'imageOS': os.environ.get('ImageOS', 'local'),
                       'imageVersion': os.environ.get('ImageVersion', 'local')}}


def validate_unit(unit, source):
    q.require(unit.get('schema') == 2 and unit.get('policy') == selection.POLICY_HASH and unit.get('profile') == 'ordinary' and unit.get('status') == 'passed' and unit.get('sourceSha') == source['sourceSha'], 'Invalid unit source receipt')
    q.validate_identity(unit.get('sourceContext'), source)
    expected_plan = selection.plan(source)
    q.require(unit.get('selectionPlan') == expected_plan, 'Wrong fixed frontend plan')
    expected_physics = 'ordinary'
    q.require(unit.get('physicsProfile') == expected_physics,
              'Wrong ordinary physics corpus')
    runner = unit['runner']
    q.require(str(runner['run']) == source['run'] and str(runner['attempt']) == source['attempt'], 'Wrong unit attempt')
    q.require(unit['cases'] and all(c['status'] == 'passed' for c in unit['cases']), 'Incomplete frontend cases')
    q.require(unit['attempts'], 'Missing first-attempt unit evidence')
    seeds = selection.POLICY['ordinaryPhysicsSeeds']
    for file, title in [('stress.test.ts', 'seed {} converges without invariant failures'),
                        ('live-stress.test.ts', 'live seed {}: coalesced fast strokes preserve invariants and recover')]:
        actual = [case.get('fullName') for case in unit['cases']
                  if case.get('file', '').replace('\\', '/').endswith('/identity/physics/tests/'+file)]
        q.require(sorted(actual) == sorted(title.format(seed) for seed in seeds),
                  'Missing/duplicate/extra required scalar/live physics corpus')


def validate_nonbrowser(directory, source, checksum):
    directory = Path(directory)
    jvm = q.receipt(q.read(directory/'jvm.json'), 'jvm', source, checksum)
    frontend = q.receipt(q.read(directory/'frontend.json'), 'frontend', source)
    validate_java(jvm['javaCases'])
    q.validate_production(jvm['production'], source['inputs'], strict_environment=False)
    validate_unit(frontend['unit'], source)
    q.require(jvm['production'] and jvm['java21']['passed'] is True and jvm['java21']['version'].startswith('openjdk version "21'), 'Missing production/runtime evidence')
    return jvm, frontend


from browser_evidence import validate_browser, join as join_browser


def aggregate(source):
    jobs = q.load_json(os.environ['VERIFICATION_NEEDS'].encode())
    q.require(set(jobs) == {'automation', 'eligibility', 'compare', 'build-jvm', 'frontend'}, 'Incomplete Verify dependencies')
    q.require(jobs['automation']['result'] == 'success' and jobs['eligibility']['result'] == 'success', 'Automation/eligibility failed')
    policy_mode = q.admission_policy(jobs)
    checksum = q.candidate(OUTPUT)
    result = {**base('verify', source), 'reusePolicyMode': policy_mode}
    decision_path = OUTPUT/'equivalence.json'
    decision = q.read(decision_path) if jobs['compare']['result'] == 'success' else None
    if jobs['compare']['result'] == 'success':
        q.require(decision and decision.get('schema') == q.SCHEMA and decision.get('kind') == 'master-equivalence'
                  and decision.get('mode') == policy_mode and decision.get('eligible') is True
                  and isinstance(decision.get('equivalent'), bool), 'Missing or inconsistent comparison decision')
        q.validate_identity(decision['master'], source)
        if decision['equivalent'] is True: q.validate_equivalence(decision, source, decision['candidate'])
    reused = bool(decision and policy_mode == 'enforce' and decision.get('equivalent') is True)
    if decision:
        expected_flag = 'true' if reused else 'false'
        q.require(jobs['compare'].get('outputs', {}).get('reuse') == expected_flag, 'Claimed branch differs from trusted decision')
    if reused:
        q.validate_equivalence(decision, source, checksum)
        q.require(jobs['build-jvm']['result'] == 'skipped' and jobs['frontend']['result'] == 'skipped', 'Unexpected reuse scheduling')
        result.update(mode='reused', equivalence=decision)
    else:
        q.require(jobs['compare']['result'] in {'success', 'skipped'}, 'Master comparison build/runtime failure')
        q.require(jobs['build-jvm']['result'] == 'success' and jobs['frontend']['result'] == 'success', 'Required producer failed/cancelled/skipped')
        jvm, frontend = validate_nonbrowser(OUTPUT, source, checksum)
        result.update(mode='executed', producers={'jvm': q.digest(jvm), 'frontend': q.digest(frontend)})
        if decision:
            result['reuseEvaluation'] = {'decision': q.digest(decision), 'equivalent': decision['equivalent'],
                'finalCandidateMatches': decision.get('candidate') == checksum,
                'finalProducerMatches': decision.get('production') == q.digest(jvm['production'])
                    and decision.get('buildEnvironment') == q.digest(jvm['environment'])
                    and decision.get('freshJava21') == jvm['java21']}
    result['candidate'] = checksum
    q.write(OUTPUT/'verification.json', result)


def gate(source):
    verify = q.receipt(q.read(OUTPUT/'verification.json'), 'verify', source)
    checksum = q.candidate(OUTPUT)
    q.require(verify['candidate'] == checksum, 'Gate candidate mismatch')
    jobs = q.load_json(os.environ['QUALIFICATION_NEEDS'].encode())
    q.require(set(jobs) == {'automation', 'eligibility', 'compare', 'build-jvm', 'frontend', 'verify', 'browser'}
              and jobs['eligibility']['result'] == 'success' and jobs['compare']['result'] in {'success', 'skipped'},
              'Incomplete/failed eligibility or comparison dependency')
    results = {key: jobs[key]['result'] for key in ['automation', 'build-jvm', 'frontend', 'verify', 'browser']}
    q.require(q.admission_policy(jobs) == verify.get('reusePolicyMode'), 'Gate qualification policy differs from Verify')
    decision = verify.get('equivalence')
    q.job_results(verify['mode'], results, decision, source, checksum)
    if verify['mode'] == 'reused':
        q.require(jobs['compare']['result'] == 'success', 'Missing trusted master comparison job')
    else:
        jvm, frontend = validate_nonbrowser(OUTPUT, source, checksum)
        q.require(verify['producers'] == {'jvm': q.digest(jvm), 'frontend': q.digest(frontend)}, 'Forged producer references')
    automation = q.receipt(q.read(OUTPUT/'automation.json'), 'automation', source)
    validate_java(automation['javaCases'])
    if verify['mode'] == 'executed':
        browser = join_browser(ROOT/'app/build/browser/verification', source, checksum)
        q.write(OUTPUT/'browser-acceptance.json', browser)
        subprocess.run(['node', 'frontend/scripts/verification/browser-admit.mjs', str(OUTPUT/'browser-acceptance.json'),
                        source['sourceSha'], checksum], cwd=ROOT, check=True)
    else:
        subprocess.run(['python3', '-B', 'scripts/ci/reuse.py', 'browser-admission'], cwd=ROOT, check=True)
        browser = q.read(OUTPUT/'browser-acceptance.json')
        q.require(browser.get('mode') == 'reused' and browser.get('equivalence') == q.digest(decision)
                  and browser.get('identity') == source, 'Invalid reused browser admission')
    result = base('qualification', source)
    result.update(mode=verify['mode'], reusePolicyMode=verify['reusePolicyMode'], candidate=checksum, verification=verify,
                  browser=browser, automation=q.digest(automation))
    q.write(OUTPUT/'qualification.json', result)


def main():
    scope = sys.argv[1] if len(sys.argv) == 2 else 'application'
    source = q.identity()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    if scope == 'automation':
        result = base('automation', source)
        result['javaCases'] = native_java(True)
        q.write(ROOT/'automation-tests/build/verification.json', result)
    elif scope in {'jvm', 'application'}:
        checksum = q.candidate(OUTPUT)
        result = base('jvm', source)
        result.update(candidate=checksum, javaCases=native_java(), production=production(True),
                      environment=q.read(OUTPUT/'environment.json'), java21=q.read(OUTPUT/'java21.json'), timings=timings())
        q.write(OUTPUT/'jvm.json', result)
        if scope == 'application':
            unit = q.read(ROOT/'frontend/test-results/unit-receipt.json'); validate_unit(unit, source)
            q.write(OUTPUT/'verification.json', {**result, 'frontendCases': len(unit['cases'])})
    elif scope == 'frontend':
        unit = q.read(ROOT/'frontend/test-results/unit-receipt.json'); validate_unit(unit, source)
        native = {key: q.read(ROOT/'frontend/test-results'/name) for key, name in {
            'discovery': 'unit-discovery.json', 'report': 'unit.json', 'attempts': 'unit-attempts.json', 'phases': 'unit-phases.json',
            'scalar': 'segmented-identity/unit-stress-summary.json', 'live': 'segmented-identity/live-stress-summary.json'}.items()}
        q.write(OUTPUT/'frontend.json', {**base('frontend', source), 'unit': unit, 'native': native})
        subprocess.run(['node', 'frontend/scripts/verification/unit-validate.mjs', str(OUTPUT/'frontend.json')], cwd=ROOT, check=True)
    elif scope == 'production':
        q.write(OUTPUT/'production.json', {'schema': q.SCHEMA, 'identity': source,
            'candidate': q.candidate(OUTPUT), 'production': production(),
            'environment': q.read(OUTPUT/'environment.json'), 'java21': q.read(OUTPUT/'java21.json'), 'timings': timings()})
    elif scope == 'verify': aggregate(source)
    elif scope == 'gate': gate(source)
    else: raise ValueError('Unknown verification scope')


if __name__ == '__main__':
    try: main()
    except (ValueError, KeyError, OSError, ET.ParseError) as error: raise SystemExit(str(error)) from error
