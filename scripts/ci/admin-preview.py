#!/usr/bin/env python3
"""Run admin native only from the JVM producer's own guarded test classpath."""
import os
from pathlib import Path
import re
import struct
import sys
import zipfile
import qualification as q

CLASSES = ['AdminBrowserPreview.class', 'AdminBrowserPreview$PreviewConfiguration.class',
           'AdminBrowserPreview$PreviewConfiguration$1.class']


def validate_runtime(payload, root, inputs=None, witness=None, fresh=False):
    root=Path(root).resolve()
    rows=payload.get('classpath')
    q.require(payload.get('schema') == 1 and isinstance(rows,list) and 0 < len(rows) <= 512
              and all(isinstance(p,str) and p and '\n' not in p and os.pathsep not in p for p in rows)
              and len(rows)==len(set(rows)), 'Missing/duplicate/unsafe preview classpath')
    paths=[Path(p) for p in rows]
    tests=root/'app/build/classes/java/test'
    main=root/'app/build/classes/java/main'
    jte=root/'app/build/generated/jte-classes'
    q.require(all(p.is_absolute() and p==p.resolve() and not p.is_symlink() for p in paths)
              and all(p in paths and p.is_dir() for p in [tests,main,jte]), 'Foreign or missing preview class directories')
    for p in paths:
        if p.suffix=='.jar': q.require(p.is_file(), 'Missing preview dependency')
        else:
            q.require(p.is_relative_to(root) and '/build/' in str(p.relative_to(root))
                      and ('/classes/' in str(p) or '/resources/' in str(p) or p==jte),
                      'Imported/foreign compiled preview output')
    for name in CLASSES:
        file=tests/'dev/persefonia/app/webadmin/preview'/name
        q.require(file.is_file() and not file.is_symlink() and file.resolve().is_relative_to(tests.resolve()), 'Missing test-only preview class')
        with file.open('rb') as stream: header=stream.read(8)
        q.require(len(header)==8 and header[:4]==bytes.fromhex('cafebabe') and struct.unpack('>H',header[6:])[0]<=65,
                  'Preview classes must preserve Java 21 compatibility')
    q.require(any(re.fullmatch(r'spring-boot-test-[0-9].*\.jar',p.name) for p in paths), 'Missing actual Spring test runtime')
    if fresh:
        q.require(isinstance(witness,dict) and witness.get('executed') is True and witness.get('sourceInputs')==inputs
                  and witness.get('testRuntime',{}).get('hasFilters')=='false', 'Stale/filtered/unexecuted JVM preview owner')
        dependencies=dict(witness.get('testDependencies',[]))
        for p in paths:
            if p.suffix=='.jar':
                key=str(p.relative_to(root)) if p.is_relative_to(root) else 'dependencies/'+p.name
                q.require(dependencies.get(key)==q.file_hash(p), 'Preview dependency differs from actual JVM tests')
    return os.pathsep.join(rows)


def isolate_fixture(payload, root):
    root=Path(root).resolve()
    tests=root/'app/build/classes/java/test'
    package=Path('dev/persefonia/app/webadmin/preview')
    target=root/'app/build/admin-preview/fixture.jar'
    target.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(target,'w',compression=zipfile.ZIP_DEFLATED) as jar:
        for name in CLASSES: jar.writestr(str(package/name),(tests/package/name).read_bytes())
    # Other test configurations and resources must never affect the real application's preview.
    excluded={str(tests),str(root/'app/build/resources/test')}
    return os.pathsep.join([p for p in payload['classpath'] if p not in excluded]+[str(target)])


def main():
    action=sys.argv[1] if len(sys.argv)==2 else ''
    if action=='classpath':
        q.require(not os.environ.get('CI'), 'Admin native diagnostics are explicit local operations')
        ci=False
        payload=q.read(q.ROOT/'app/build/admin-preview/classpath.json')
        source=q.identity() if ci else None
        witness=q.read(q.ROOT/'app/build/qualification/test.json') if ci else None
        validate_runtime(payload,q.ROOT,source['inputs'] if ci else None,witness,ci)
        print(isolate_fixture(payload,q.ROOT))
    else: raise ValueError('Expected classpath for explicit local diagnostics')


if __name__=='__main__':
    try: main()
    except (ValueError,KeyError,OSError): raise SystemExit('Guarded admin preview prerequisite failed; qualification is blocked') from None
