"""Gate-owned native evidence validation. Never imports Playwright or installs npm packages."""
import hashlib
import json
import math
from pathlib import Path
import qualification as q
import selection
ROOT = q.ROOT


def native_browser_rows(report):
    config = report.get('config', {})
    q.require(config.get('forbidOnly') is True and config.get('fullyParallel') is False
              and config.get('workers') == 1, 'Unsafe native scheduling')
    projects = config.get('projects', [])
    q.require(len(projects) == len(selection.POLICY['native'])
              and {p.get('name') for p in projects} == {p['project'] for p in selection.POLICY['native']}
              and all(p.get('retries') == 0 and p.get('repeatEach') == 1 for p in projects), 'Filtered/retried native projects')
    q.require(report.get('errors') == [], 'Native runner errors')
    rows = []
    def visit(suites):
        q.require(isinstance(suites, list), 'Malformed native suites')
        for suite in suites:
            for spec in suite.get('specs', []):
                q.require(all(isinstance(spec.get(k), str) and spec[k] for k in ['id','file','title']), 'Malformed native identity')
                for test in spec.get('tests', []):
                    q.require(isinstance(test.get('projectName'), str) and isinstance(test.get('results'), list)
                              and isinstance(test.get('annotations'), list), 'Malformed native outcome')
                    rows.append({'id': test['projectName']+':'+spec['id'], 'project':test['projectName'],
                        'file':spec['file'], 'title':spec['title'], 'expectedStatus':test.get('expectedStatus'),
                        'status':test.get('status'), 'annotations':test['annotations'],
                        'results':[{k:r[k] for k in ['status','duration','retry','startTime'] if k in r} for r in test['results']]})
            visit(suite.get('suites', []))
    visit(report.get('suites'))
    q.require(rows and len({r['id'] for r in rows}) == len(rows), 'Empty/duplicate native report')
    return rows


def validate_browser(value, source, checksum):
    plan = selection.plan(source)
    profiles = ['ordinary']
    q.require(value.get('schema') == 3 and value.get('policy') == selection.POLICY_HASH
              and value.get('status') == 'passed' and value.get('sourceContext') == source
              and value.get('selectionPlan') == plan and value.get('profiles') == profiles
              and value.get('identity') == {'sourceSha': source['sourceSha'], 'artifactSha256': checksum}
              and str(value.get('runner', {}).get('run')) == source['run']
              and str(value.get('runner', {}).get('attempt')) == source['attempt'], 'Invalid browser acceptance')
    expected = {}
    for profile in profiles:
        for project in selection.POLICY['native']:
            key = profile+'-'+project['name']+'-1of1'
            expected[key] = (profile, project)
    receipts = value.get('receipts', [])
    q.require(isinstance(receipts, list) and value.get('partitions') == len(receipts) == len(expected), 'Incomplete native receipts')
    observed = set()
    all_ids = {}
    for receipt in receipts:
        partition = receipt['partition']
        key = partition['name']+'-'+partition['shard'].replace('/', 'of')
        q.require(key in expected and key not in observed, 'Missing/extra/duplicate native engine/context')
        observed.add(key)
        profile, project = expected[key]
        q.require(partition == {'name': profile+'-'+project['name'], 'suite': profile, 'project': project['project'], 'shard': '1/1'}
                  and receipt.get('schema') == 3 and receipt.get('policy') == selection.POLICY_HASH
                  and receipt.get('status') == 'passed' and receipt.get('exitCode') == 0 and receipt.get('errors') == []
                  and receipt.get('identity') == value['identity'] and receipt.get('sourceContext') == source
                  and receipt.get('selectionPlan') == plan
                  and receipt.get('runner', {}).get('run') == source['run']
                  and receipt.get('runner', {}).get('attempt') == source['attempt'], 'Wrong native profile/source/run/attempt/candidate')
        environment = receipt.get('environment', {})
        engine = project['engine']
        q.require(set(environment.get('browsers', {})) == {engine}
                  and q.HASH.fullmatch(environment['browsers'][engine].get('sha256', ''))
                  and all(isinstance(environment.get(tool), str) and environment[tool] for tool in ['ffmpeg', 'ffprobe']),
                  'Missing required native engine/decoder prerequisite')
        outcomes = receipt.get('outcomes', [])
        ids = [r['id'] for r in outcomes]
        q.require(ids and len(ids) == len(set(ids)) and sorted(ids) == sorted(receipt.get('selected', [])), 'Missing/duplicate native cases')
        fingerprint = hashlib.sha256(json.dumps(sorted(ids), separators=(',', ':'), ensure_ascii=False).encode()).hexdigest()
        q.require(receipt.get('selectionHash') == fingerprint, 'Corrupt native selection')
        if profile == 'ordinary':
            wanted = {(c['file'], c['title']) for c in project['cases']}
            q.require(len(outcomes) == len(wanted) and {(r['file'], r['title']) for r in outcomes} == wanted,
                      'Ordinary native cases differ from source policy')
        for row in outcomes:
            q.require(row['project'] == project['project'] and len(row.get('results', [])) == 1, 'Wrong context or repeated native attempt')
            result = row['results'][0]
            q.require(result.get('retry') == 0 and isinstance(result.get('duration'), (int, float)) and not isinstance(result['duration'], bool) and math.isfinite(result['duration']) and result['duration'] >= 0,
                      'Invalid native first attempt')
            q.require(result.get('status') == 'passed' and row.get('status') == 'expected' and row.get('expectedStatus') == 'passed',
                      'Failed/skipped/cancelled/unsupported ordinary native contract')
        all_ids.setdefault(profile, []).extend(ids)
    inventory = value.get('inventory', {})
    q.require(set(inventory) == set(profiles), 'Wrong independent discovery profile')
    for profile, ids in all_ids.items():
        rows = inventory[profile]
        q.require(len(ids) == len(set(ids)) and sorted(ids) == sorted(r['id'] for r in rows), 'Discovery/receipt case mismatch')
        actual = {(r['id'], r['file'], r['title'], r['project']) for r in rows}
        executed = {(r['id'], r['file'], r['title'], r['project']) for receipt in receipts if receipt['partition']['suite'] == profile for r in receipt['outcomes']}
        q.require(actual == executed, 'Forged discovery identity')
    q.require(value.get('selected') == sum(len(ids) for ids in all_ids.values()), 'Forged native counts')
    # Producer success is insufficient: the trusted Gate parses both raw native reports.
    native = value.get('native', {})
    q.require(set(native) == {'discovery', 'execution'}, 'Missing independent native reports')
    discovery = native_browser_rows(native['discovery'])
    execution = native_browser_rows(native['execution'])
    stats = native['execution'].get('stats', {})
    q.require(stats.get('expected') == len(execution) and all(stats.get(k) == 0 for k in ['unexpected','flaky','skipped']), 'Failed or forged native runner counts')
    compact = lambda rows: sorted(rows, key=lambda r: r['id'])
    q.require(compact(discovery) == compact(inventory['ordinary']), 'Forged discovery report')
    q.require(compact(execution) == compact([row for r in receipts for row in r['outcomes']]), 'Forged native execution report')
    return value



def join(directory, source, checksum):
    directory = Path(directory)
    expected = {'ordinary-'+p['name']+'-1of1-receipt.json' for p in selection.POLICY['native']}
    q.require({p.name for p in directory.glob('*.json')} == expected | {'native-discovery.json','native-results.json'},
              'Missing/extra native evidence files')
    receipts = [q.read(directory/name) for name in sorted(expected)]
    native = {'discovery':q.read(directory/'native-discovery.json'), 'execution':q.read(directory/'native-results.json')}
    value = {'schema':3, 'policy':selection.POLICY_HASH, 'profiles':['ordinary'], 'status':'passed',
        'sourceContext':source, 'selectionPlan':selection.plan(source),
        'identity':{'sourceSha':source['sourceSha'], 'artifactSha256':checksum},
        'runner':{'run':source['run'],'attempt':source['attempt']},
        'partitions':len(receipts), 'selected':sum(len(r['selected']) for r in receipts),
        'inventory':{'ordinary':native_browser_rows(native['discovery'])}, 'receipts':receipts, 'native':native}
    return validate_browser(value, source, checksum)
