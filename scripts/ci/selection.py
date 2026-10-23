#!/usr/bin/env python3
"""Fixed ordinary verification contract, with Git provenance and no automatic diagnostics."""
import hashlib
import json
import re
from pathlib import Path, PurePosixPath
import subprocess
import qualification as q

POLICY_PATH = Path(__file__).resolve().parents[2]/'frontend/scripts/verification/selection-policy.json'
POLICY = q.read(POLICY_PATH)
POLICY_HASH = hashlib.sha256(POLICY_PATH.read_bytes()).hexdigest()
q.require(POLICY.get('schema') == 2 and POLICY.get('version') == 'lean-native-v2'
          and POLICY.get('receiptSchema') == 3, 'Unknown selection policy schema/version')


def classify(changes, *, truncated=False):
    q.require(not truncated and isinstance(changes, list) and len(changes) <= 5000,
              'Truncated/oversized changed-file inventory')
    seen = set()
    for row in changes:
        q.require(isinstance(row, dict), 'Malformed changed-file row')
        status = row.get('status')
        q.require(isinstance(status, str) and (status in {'A', 'M', 'D', 'T'}
                  or re.fullmatch(r'[RC](?:100|[0-9]{1,2})', status)), 'Malformed change status')
        paths = [row.get('path', '')]
        if status.startswith(('R', 'C')): paths.append(row.get('previous', ''))
        for path in paths:
            q.require(isinstance(path, str) and path, 'Malformed changed path')
            pure = PurePosixPath(path)
            q.require(path == str(pure) and not pure.is_absolute() and '..' not in pure.parts
                      and '\\' not in path and all(32 <= ord(c) < 127 for c in path), 'Unsafe changed path')
            q.require(path not in seen, 'Duplicate changed path')
            seen.add(path)
    # Unknown, renamed and deleted source gets every ordinary check, never less coverage.
    return {'schema': 2, 'policy': POLICY_HASH, 'required': ['ordinary'], 'changes': q.digest(changes)}


def changed_paths(source):
    if source['event'] == 'pull_request':
        base = source['pr']['base']
        q.require(q.SHA.fullmatch(base or '') and source['parents'] == [base, source['pr']['head']],
                  'Untrusted PR base-to-tested-tree relationship')
    elif source['event'] == 'push' and source['ref'] == 'refs/heads/master':
        q.require(len(source['parents']) in (1, 2), 'Unknown master history: qualification blocked')
        base = source['parents'][0]
    elif source['event'] == 'workflow_dispatch':
        q.require(q.SHA.fullmatch(source.get('sourceSha', '')), 'Malformed dispatch source')
        base = source['sourceSha']
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
