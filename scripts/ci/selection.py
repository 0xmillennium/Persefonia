#!/usr/bin/env python3
"""Small ordered source-owned risk policy. Selection is recomputed from Git, never a job output."""
import fnmatch
import hashlib
import json
import re
from pathlib import Path, PurePosixPath
import subprocess
import qualification as q

POLICY_PATH = Path(__file__).resolve().parents[2]/'frontend/scripts/verification/selection-policy.json'
POLICY = q.read(POLICY_PATH)
POLICY_HASH = hashlib.sha256(POLICY_PATH.read_bytes()).hexdigest()
q.require(POLICY.get('schema') == 1 and POLICY.get('version') == 'ordinary-native-v1'
          and POLICY.get('receiptSchema') == 2, 'Unknown selection policy schema/version')
q.require(all(set(rule['require']) <= set(POLICY['deep']) for rule in POLICY['riskRules']),
          'Unknown required deep family in source policy')


def classify(changes, *, truncated=False):
    q.require(not truncated and isinstance(changes, list) and 0 < len(changes) <= 5000,
              'Empty/truncated/oversized changes: explicit classification required')
    required = set()
    owners = []
    seen = set()
    for row in changes:
        q.require(isinstance(row, dict), 'Malformed changed-file row')
        status = row.get('status')
        ordinary = status in {'A', 'M'}
        supported = isinstance(status, str) and (status in {'D', 'T'} or re.fullmatch(r'[RC](?:100|[0-9]{1,2})', status))
        q.require(ordinary or (supported and row in POLICY['classifiedChanges']),
                  'Deleted/renamed/type-changed path requires an exact reviewed classifiedChanges entry')
        candidates = [row.get('path', '')]
        if status and status.startswith(('R', 'C')): candidates.append(row.get('previous', ''))
        for path in candidates:
            q.require(isinstance(path, str) and path, 'Malformed changed path')
            pure = PurePosixPath(path)
            q.require(path == str(pure) and not pure.is_absolute() and '..' not in pure.parts
                      and '\\' not in path and all(32 <= ord(c) < 127 for c in path),
                      'Unsafe/ambiguous changed path: explicit classification required')
            q.require(path not in seen, 'Duplicate/ambiguous changed path')
            seen.add(path)
            # Narrow ordered owners win. Renames retain the union of old and new risks.
            rule = next((r for r in POLICY['riskRules'] if any(fnmatch.fnmatchcase(path, p) for p in r['patterns'])), None)
            q.require(rule is not None, f'Unclassified path: {path}; add a reviewed owner to selection-policy.json')
            required.update(rule['require'])
            owners.append({'path': path, 'owner': rule['owner']})
    return {'schema': 1, 'policy': POLICY_HASH, 'required': ['ordinary', *sorted(required)],
            'changes': q.digest(changes), 'owners': owners}


def changed_paths(source):
    if source['event'] == 'pull_request':
        base = source['pr']['base']
        q.require(q.SHA.fullmatch(base or '') and source['parents'] == [base, source['pr']['head']],
                  'Untrusted PR base-to-tested-tree relationship')
    elif source['event'] == 'push' and source['ref'] == 'refs/heads/master':
        q.require(len(source['parents']) in (1, 2), 'Unknown master history: qualification blocked')
        base = source['parents'][0]
    elif source['event'] == 'local':
        base = 'HEAD'
    else:
        raise ValueError('Unknown change context: explicit full-source qualification plan required')
    args = ['git', '--no-replace-objects', 'diff', '--name-status', '-z', '--find-renames', base]
    if source['event'] != 'local': args.append(source['tree'])
    raw = subprocess.check_output(args, cwd=q.ROOT, timeout=20)
    q.require(len(raw) <= 2*1024*1024, 'Truncated/oversized Git changes')
    fields = raw.decode('utf-8', errors='strict').split('\0')
    q.require(fields.pop() == '', 'Truncated Git path inventory')
    rows = []
    while fields:
        status = fields.pop(0)
        q.require(fields, 'Truncated Git name/status')
        path = fields.pop(0)
        if status.startswith(('R', 'C')):
            q.require(fields, 'Truncated rename')
            rows.append({'status': status, 'previous': path, 'path': fields.pop(0)})
        else: rows.append({'status': status, 'path': path})
    if source['event'] == 'local':
        rows.extend({'status': 'A', 'path': path} for path in q.git('ls-files', '--others', '--exclude-standard').splitlines())
    return rows


def plan(source=None):
    source = source or q.identity()
    value = classify(changed_paths(source))
    return {**value, 'source': source}



if __name__ == '__main__':
    print(json.dumps(plan(), sort_keys=True))
