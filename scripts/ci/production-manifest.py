#!/usr/bin/env python3
"""Snapshot production bytes from the classpath in the actual JVM-test workspace."""
import argparse
import json
import os
from pathlib import Path
import platform
import subprocess
import qualification as q


def version(command):
    result = subprocess.run(command, check=True, capture_output=True, text=True)
    return (result.stdout + result.stderr).strip()


def manifest(paths):
    rows = []
    for entry in sorted(set(paths)):
        path = Path(entry)
        if not path.exists(): continue  # Absent resource directories are not classpath content.
        q.require(not path.is_symlink(), 'Symlink in production classpath')
        if path.is_dir():
            for file in sorted(path.rglob('*')):
                q.require(not file.is_symlink(), 'Symlink in production output')
                if file.is_file(): rows.append([str(file.relative_to(q.ROOT)), q.file_hash(file)])
        else:
            # Dependency hashes are independent of Gradle cache or runner absolute paths.
            name = str(path.relative_to(q.ROOT)) if path.is_relative_to(q.ROOT) else 'dependencies/' + path.name
            rows.append([name, q.file_hash(path)])
    names = [row[0] for row in rows]
    q.require(len(names) == len(set(names)), 'Duplicate production inventory')
    return sorted(rows)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('destination')
    parser.add_argument('--classpath', required=True)
    parser.add_argument('--executed-classpath')
    parser.add_argument('--task', required=True)
    parser.add_argument('--resolved-test-classpaths')
    parser.add_argument('--gradle-version', required=True)
    parser.add_argument('--compiler-metadata', required=True)
    parser.add_argument('--test-runtime')
    parser.add_argument('--resolved-test-runtimes')
    args = parser.parse_args()
    paths = args.classpath.split(os.pathsep)
    if args.executed_classpath:
        actual = set(args.executed_classpath.split(os.pathsep))
        q.require(all(path in actual for path in paths if Path(path).exists()),
                  'Production output differs from actual test classpath: ' + ', '.join(Path(path).name for path in paths if Path(path).exists() and path not in actual))
    q.write(args.destination, {'schema': q.SCHEMA, 'task': args.task, 'sourceInputs': q.source_inputs(),
        'production': manifest(paths), 'executed': bool(args.executed_classpath),
        'testDependencies': manifest([path for path in actual if Path(path).suffix == '.jar']) if args.executed_classpath else None,
        'resolvedTestDependencies': {name: manifest([path for path in classpath.split(os.pathsep) if Path(path).suffix == '.jar'])
            for name, classpath in q.load_json(args.resolved_test_classpaths.encode()).items()} if args.resolved_test_classpaths else None,
        'compiler': version(['javac', '-version']), 'java': version(['java', '-version']),
        'gradle': args.gradle_version, 'compilerToolchain': q.load_json(args.compiler_metadata.encode()),
        'testRuntime': q.load_json(args.test_runtime.encode()) if args.test_runtime else None,
        'resolvedTestRuntimes': q.load_json(args.resolved_test_runtimes.encode()) if args.resolved_test_runtimes else None})


if __name__ == '__main__': main()
