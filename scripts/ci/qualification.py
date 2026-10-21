"""Trusted, bounded CI evidence primitives. Only repository-owned callers may authorize reuse."""
from datetime import datetime
import fnmatch
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import subprocess
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[2]
REPOSITORY = '0xmillennium/Persefonia'
REPOSITORY_ID = 1358514219
WORKFLOW_ID = 351133355
SCHEMA = 2
SHA = re.compile(r'[0-9a-f]{40}')
HASH = re.compile(r'[0-9a-f]{64}')
PARTITIONS = ['desktop-1of2', 'desktop-2of2', 'tablet-1of2', 'tablet-2of2',
              'mobile-1of2', 'mobile-2of2', 'firefox-1of1', 'webkit-1of1']


def require(condition, message):
    if not condition: raise ValueError(message)


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def file_hash(path):
    with Path(path).open('rb') as source: return hashlib.file_digest(source, 'sha256').hexdigest()


def load_json(raw):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, 'Duplicate JSON field')
            result[key] = value
        return result
    require(len(raw) <= 32 * 1024 * 1024, 'JSON exceeds evidence limit')
    try:
        return json.loads(raw, object_pairs_hook=unique, parse_constant=lambda _: require(False, 'Nonfinite JSON'))
    except (RecursionError, UnicodeDecodeError, TypeError) as error:
        raise ValueError('Malformed JSON evidence') from error


def read(path):
    path = Path(path)
    require(path.is_file() and not path.is_symlink(), 'Missing or unsafe evidence file')
    return load_json(path.read_bytes())


def write(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(data, sort_keys=True, separators=(',', ':')) + '\n')
    temporary.replace(path)


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()


def commit_parents(source_sha):
    require(SHA.fullmatch(source_sha), 'Malformed source SHA')
    raw = subprocess.check_output(['git', '--no-replace-objects', 'cat-file', 'commit', source_sha], cwd=ROOT)
    headers, separator, _ = raw.partition(b'\n\n')
    lines = headers.split(b'\n')
    require(separator and lines[0].startswith(b'tree ') and
            SHA.fullmatch(lines[0][5:].decode('ascii')), 'Malformed commit headers')
    parents = []
    for line in lines[1:]:
        # Continuations (including signatures) and message bytes are not headers.
        if line.startswith((b' ', b'\t')): continue
        if line == b'parent' or line.startswith((b'parent ', b'parent\t')):
            parent = line[7:].decode('ascii')
            require(line.startswith(b'parent ') and SHA.fullmatch(parent), 'Malformed commit parent')
            parents.append(parent)
    return parents


def reuse_mode(value):
    mode = 'shadow' if value is None or value == '' else value
    require(mode in ('off', 'shadow', 'enforce'), 'Unknown CI_QUALIFICATION_REUSE mode')
    return mode


# Closed allow-list for ordinary source. Everything else is control policy until classified.
# This includes new tools/config, executable files, renamed and deleted policy, support/fixtures,
# all Gradle/Node/TS/build/media/release/deploy/runtime declarations, and the classifier itself.
def protected(path):
    p = PurePosixPath(path)
    if p.is_absolute() or '..' in p.parts or '\\' in path: return True
    name = p.name.lower()
    if any(part in {'testsupport', 'support', 'fixtures', 'global-setup', 'buildsrc', 'build-logic'}
           for part in (part.lower() for part in p.parts)): return True
    if any(word in name for word in ('config', 'lock', 'package', 'gradle', 'tsconfig', 'tool-version')): return True
    if name.startswith('application') and p.suffix in {'.yml', '.yaml', '.properties'}: return True
    if path.startswith(('docs/',)) and p.suffix == '.md': return False
    if path.startswith('frontend/src/') and p.suffix in {'.ts', '.css', '.svg', '.json'}: return False
    if path.startswith('frontend/e2e/') and name.endswith('.spec.ts'): return False
    if path.startswith('frontend/public/') and p.suffix in {'.mp4', '.webp', '.json', '.svg', '.ico', '.txt'}: return False
    if re.fullmatch(r'[^/.]+/src/(main|test|integrationTest|migrationTest)/.+\.(java|jte|sql|properties|yml|yaml|json|txt|xml|webp|svg)', path):
        return False
    return True


def safe_changes(files):
    return bool(files) and len(files) < 300 and all(
        not protected(f['filename']) and not protected(f.get('previous_filename', f['filename']))
        and f['status'] in {'added', 'modified', 'removed', 'renamed'} for f in files)


def policy(include_untracked=True):
    entries = []
    for row in git('ls-files', '--stage').splitlines():
        mode, blob, stage, path = row.split(maxsplit=3)
        require(stage == '0', 'Unmerged policy')
        if protected(path):
            require(mode == '100644' or mode == '100755', 'Unsafe policy type')
            entries.append([path, mode, file_hash(ROOT / path)])
    # Include untracked new verifier files in local receipts; hosted checkouts have none.
    for path in git('ls-files', '--others', '--exclude-standard').splitlines() if include_untracked else []:
        if protected(path): entries.append([path, '100755' if os.access(ROOT / path, os.X_OK) else '100644', file_hash(ROOT / path)])
    return digest(sorted(entries))


def source_inputs(include_untracked=True):
    rows = []
    tracked = git('ls-files').splitlines()
    extra = git('ls-files', '--others', '--exclude-standard').splitlines() if include_untracked else []
    for name in sorted(set(tracked + extra)):
        path = ROOT / name
        if not path.exists():
            rows.append([name, 'removed'])
            continue
        require(path.is_file() and not path.is_symlink(), 'Unsafe source input')
        rows.append([name, '100755' if os.access(path, os.X_OK) else '100644', file_hash(path)])
    return digest(rows)


def identity(source_only=False):
    require(os.environ.get('GITHUB_REPOSITORY', REPOSITORY) == REPOSITORY and
            os.environ.get('GITHUB_REPOSITORY_ID', str(REPOSITORY_ID)) == str(REPOSITORY_ID),
            'Untrusted execution repository')
    sha = git('rev-parse', 'HEAD')
    require(SHA.fullmatch(sha), 'Malformed source SHA')
    require(os.environ.get('GITHUB_SHA', sha) == sha, 'Checkout SHA mismatch')
    event = read(os.environ['GITHUB_EVENT_PATH']) if os.environ.get('GITHUB_EVENT_PATH') else {}
    pr = event.get('pull_request', {})
    return {'schema': SCHEMA, 'repository': REPOSITORY, 'repositoryId': REPOSITORY_ID,
            'workflowId': WORKFLOW_ID, 'sourceSha': sha, 'tree': git('rev-parse', f'{sha}^{{tree}}'),
            'inputs': source_inputs(not source_only), 'dirty': bool(git('status', '--porcelain', '--untracked-files=no') if source_only else git('status', '--porcelain')),
            'parents': commit_parents(sha), 'policy': policy(not source_only),
            'run': os.environ.get('GITHUB_RUN_ID', 'local'),
            'attempt': os.environ.get('GITHUB_RUN_ATTEMPT', '1'),
            'event': os.environ.get('GITHUB_EVENT_NAME', 'local'),
            'ref': os.environ.get('GITHUB_REF', 'local'),
            'pr': {'number': pr.get('number'), 'head': pr.get('head', {}).get('sha'),
                   'base': pr.get('base', {}).get('sha'),
                   'headRepositoryId': pr.get('head', {}).get('repo', {}).get('id'),
                   'baseRepositoryId': pr.get('base', {}).get('repo', {}).get('id')}}


def validate_identity(actual, expected):
    require(isinstance(actual, dict) and actual == expected, 'Evidence source/run/attempt/policy identity mismatch')
    require(actual['schema'] == SCHEMA and SHA.fullmatch(actual['sourceSha']) and
            SHA.fullmatch(actual['tree']) and HASH.fullmatch(actual['policy']), 'Malformed source identity')
    require(actual['repositoryId'] == REPOSITORY_ID and actual['repository'] == REPOSITORY and
            actual['workflowId'] == WORKFLOW_ID, 'Wrong repository/workflow identity')


def time_window(start, end, master):
    a, b, c = [datetime.fromisoformat(value.replace('Z', '+00:00')) for value in (start, end, master)]
    require(a.tzinfo and b.tzinfo and c.tzinfo and a <= b <= c and 0 <= (c-b).total_seconds() <= 86400,
            'Stale, future or inconsistent qualification timestamps')


def archive_members(raw, *, maximum=256*1024*1024, total_limit=384*1024*1024):
    require(len(raw) <= maximum, 'Archive exceeds compressed limit')
    try:
        with zipfile.ZipFile(io.BytesIO(raw)) as archive:
            infos = archive.infolist()
            require(0 < len(infos) <= 10000, 'Archive member count exceeds limit')
            seen = set()
            total = 0
            for item in infos:
                path = PurePosixPath(item.filename)
                kind = stat.S_IFMT(item.external_attr >> 16)
                require(re.fullmatch(r'[A-Za-z0-9_.][A-Za-z0-9._/-]{0,300}', item.filename) and not path.is_absolute() and '..' not in path.parts and '\\' not in item.filename
                        and item.orig_filename == item.filename and item.filename == str(path) and item.filename not in seen and
                        kind in (0, stat.S_IFREG) and item.compress_type in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED) and not item.is_dir() and not (item.flag_bits & 1),
                        'Unsafe or duplicate archive member')
                require(item.file_size <= maximum, 'Archive member exceeds limit')
                total += item.file_size
                require(total <= total_limit, 'Archive expansion exceeds limit')
                seen.add(item.filename)
            require(archive.testzip() is None, 'Archive CRC failure')
            return {item.filename: archive.read(item) for item in infos}
    except (zipfile.BadZipFile, RuntimeError, EOFError, zlib.error) as error:
        raise ValueError('Unreadable ZIP evidence') from error


def validate_production(values, inputs, strict_environment=True):
    require(isinstance(values, dict) and 'app' in values, 'Missing production module inventory')
    def entries(rows):
        require(isinstance(rows, list), 'Missing production/dependency entries')
        names = []
        for row in rows:
            require(isinstance(row, list) and len(row) == 2 and isinstance(row[0], str)
                    and isinstance(row[1], str) and HASH.fullmatch(row[1]), 'Malformed production digest')
            path = PurePosixPath(row[0])
            require(not path.is_absolute() and '..' not in path.parts and '\\' not in row[0]
                    and str(path) == row[0], 'Unsafe production inventory path')
            names.append(row[0])
        require(len(names) == len(set(names)), 'Duplicate production output')
    fields = {'languageVersion', 'vendor', 'runtimeVersion', 'jvmVersion'}
    for module in values.values():
        require(isinstance(module, dict) and module.get('schema') == SCHEMA
                and module.get('sourceInputs') == inputs and isinstance(module.get('gradle'), str)
                and 0 < len(module['gradle']) < 128, 'Unknown production source/toolchain identity')
        compiler = module.get('compilerToolchain')
        require(isinstance(compiler, dict) and fields <= compiler.keys()
                and all(isinstance(compiler[key], str) and 0 < len(compiler[key]) < 512 for key in fields),
                'Missing resolved compiler metadata')
        entries(module.get('production'))
        runtimes, dependencies = module.get('resolvedTestRuntimes'), module.get('resolvedTestDependencies')
        require(isinstance(runtimes, dict) and runtimes and isinstance(dependencies, dict)
                and runtimes.keys() == dependencies.keys(), 'Missing resolved JVM test environment')
        for name, runtime in runtimes.items():
            require(isinstance(runtime, dict) and fields <= runtime.keys()
                    and all(isinstance(runtime[key], str) and 0 < len(runtime[key]) < 512 for key in fields)
                    and runtime.get('customJvmArguments') in ({'false'} if strict_environment else {'false', 'true'})
                    and runtime.get('hasFilters') == 'false' and runtime.get('assertions') == 'true'
                    and re.fullmatch(r'[1-9][0-9]*', runtime.get('maxParallelForks', ''))
                    and isinstance(runtime.get('maxHeapSize'), str) and 0 < len(runtime['maxHeapSize']) < 64, 'Unknown or narrowed JVM execution policy')
            entries(dependencies[name])
    require(values['app']['production'], 'Missing app production bytes')


def receipt(value, lane, expected, candidate=None):
    require(isinstance(value, dict) and value.get('schema') == SCHEMA and value.get('status') == 'passed' and value.get('lane') == lane,
            'Missing, failed or wrong producer receipt')
    validate_identity(value.get('identity'), expected)
    if candidate is not None: require(value.get('candidate') == candidate, 'Wrong candidate')
    return value


def candidate(directory):
    directory = Path(directory)
    jar = directory / 'persefonia.jar'
    checksum = file_hash(jar)
    require((directory/'persefonia.jar.sha256').read_text().strip() == f'{checksum}  persefonia.jar', 'Candidate checksum mismatch')
    require((directory/'source-sha.txt').read_text().strip() == identity()['sourceSha'], 'Candidate source mismatch')
    return checksum


def validate_equivalence(decision, expected, fresh_candidate):
    require(decision.get('schema') == SCHEMA and decision.get('kind') == 'master-equivalence'
            and decision.get('eligible') is True and decision.get('equivalent') is True
            and decision.get('mode') in {'shadow', 'enforce'}, 'Missing trusted equivalence decision')
    validate_identity(decision.get('master'), expected)
    require(expected['event'] == 'push' and expected['ref'] == 'refs/heads/master'
            and expected['attempt'] == '1', 'Reuse requires a first-attempt master push')
    require(decision.get('candidate') == fresh_candidate and HASH.fullmatch(fresh_candidate), 'Wrong equivalence candidate')
    pr = decision.get('pr', {})
    require(pr.get('event') == 'pull_request' and pr.get('attempt') == '1'
            and pr.get('tree') == expected['tree'] and pr.get('inputs') == expected['inputs'] and pr.get('policy') == expected['policy']
            and pr.get('repositoryId') == REPOSITORY_ID and pr.get('workflowId') == WORKFLOW_ID
            and pr.get('pr', {}).get('headRepositoryId') == REPOSITORY_ID
            and pr.get('pr', {}).get('baseRepositoryId') == REPOSITORY_ID,
            'Invalid original PR identity')
    require(decision.get('validatedNativeEvidence') is True and decision.get('apiValidated') is True
            and HASH.fullmatch(decision.get('production', ''))
            and HASH.fullmatch(decision.get('environment', '')) and HASH.fullmatch(decision.get('buildEnvironment', ''))
            and isinstance(decision.get('artifacts'), list) and len(decision['artifacts']) >= 2,
            'Incomplete comparison provenance')
    validate_identity(pr, pr)
    relation = pr['pr']
    require(isinstance(relation.get('number'), int) and relation['number'] > 0
            and SHA.fullmatch(relation.get('head', '')) and SHA.fullmatch(relation.get('base', ''))
            and pr.get('parents') == [relation['base'], relation['head']]
            and expected.get('parents') == [relation['base']]
            and pr.get('ref') == f"refs/pull/{relation['number']}/merge"
            and expected.get('dirty') is False and pr.get('dirty') is False
            and str(pr.get('run', '')).isdigit() and str(expected.get('run', '')).isdigit()
            and pr['run'] != expected['run'], 'Unsupported equivalence source relationship')
    time_window(decision['prCreated'], decision['prCompleted'], decision['masterCreated'])
    runtime = decision.get('freshJava21', {})
    require(runtime.get('passed') is True and isinstance(runtime.get('version'), str)
            and runtime['version'].startswith('openjdk version "21'), 'Missing fresh master runtime evidence')
    artifacts = decision['artifacts']
    require(len(artifacts) == 2 and len({artifact.get('id') for artifact in artifacts}) == 2,
            'Duplicate or unexpected equivalence artifacts')
    names = set()
    for artifact in artifacts:
        require(isinstance(artifact.get('id'), int) and artifact['id'] > 0
                and re.fullmatch(r'sha256:[0-9a-f]{64}', artifact.get('digest', '')),
                'Malformed artifact provenance')
        api = artifact.get('workflow_run', {})
        require(api.get('id') == int(pr['run']) and api.get('repository_id') == REPOSITORY_ID
                and api.get('head_repository_id') == REPOSITORY_ID and api.get('head_sha') == relation['head'],
                'Original artifact run/source provenance mismatch')
        time_window(decision['prCreated'], artifact['created_at'], decision['prCompleted'])
        names.add(artifact.get('name'))
    require(names == {'persefonia-qualification-'+pr['sourceSha'], 'persefonia-browser-input-'+pr['sourceSha']},
            'Wrong original qualification/candidate artifact')
    return decision


def admission_policy(jobs):
    eligibility = jobs['eligibility']
    require(eligibility.get('result') == 'success', 'Failed eligibility producer')
    outputs = eligibility.get('outputs', {})
    mode, eligible = outputs.get('mode'), outputs.get('eligible')
    require(mode in {'off', 'shadow', 'enforce'} and eligible in {'true', 'false'},
            'Missing or unknown qualification policy outputs')
    require(mode != 'off' or eligible == 'false', 'Off mode cannot compare old evidence')
    comparison = jobs['compare']
    require(comparison.get('result') == ('success' if eligible == 'true' else 'skipped'),
            'Failed, cancelled or unexpected comparison scheduling')
    if eligible == 'true':
        require(comparison.get('outputs', {}).get('reuse') in {'true', 'false'}, 'Missing comparison branch output')
    return mode


def job_results(mode, jobs, decision=None, expected=None, fresh_candidate=None):
    require(mode in {'executed', 'reused'}, 'Unknown admission mode')
    required = {'automation', 'build-jvm', 'frontend', 'verify', 'browser', 'browser-receipts'}
    require(set(jobs) == required, 'Missing or unexpected required jobs')
    if mode == 'reused':
        require(decision is not None and expected is not None, 'Claimed reused flag is not admission')
        validate_equivalence(decision, expected, fresh_candidate)
        require(decision['mode'] == 'enforce', 'Shadow cannot authorize skipped work')
    for job in required:
        wanted = 'skipped' if mode == 'reused' and job in {'build-jvm', 'frontend', 'browser'} else 'success'
        require(jobs[job] == wanted, f'Required job {job}: expected {wanted}, got {jobs[job]}')


if __name__ == '__main__': print(json.dumps(identity(), sort_keys=True))
