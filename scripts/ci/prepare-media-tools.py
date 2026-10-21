#!/usr/bin/env python3
"""Prepare system media prerequisites explicitly; diagnostics are not qualification receipts."""
import argparse
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import tempfile
import time


def write_record(path, record):
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(record, indent=2)+'\n', encoding='utf-8')
    temporary.replace(path)


def prepare(mode, report_dir):
    started = time.monotonic()
    record = {'schema': 1, 'kind': 'media-prerequisites', 'mode': mode, 'phase': 'initializing',
              'status': 'running', 'checks': [], 'commands': [], 'failureReason': None}
    failure = None
    try:
        root = Path(report_dir)
        root.mkdir(parents=True, exist_ok=True)
        directory = Path(tempfile.mkdtemp(prefix='invocation-', dir=root))
        receipt = directory/'setup.json'
        write_record(receipt, record)
    except OSError as error:
        print(f'Media diagnostics initialization failed: {error}', file=sys.stderr)
        return 1

    def persist():
        try:
            write_record(receipt, record)
        except OSError as error:
            record.setdefault('diagnosticErrors', []).append(str(error))
            print(f'Media diagnostics update failed: {error}', file=sys.stderr)

    def command(label, arguments, timeout):
        row = {'phase': label, 'status': 'running', 'exitCode': None, 'timedOut': False,
               'timeoutSeconds': timeout, 'command': arguments, 'durationSeconds': 0}
        prefix = f'{len(record["commands"])+1:02d}-{label}'
        stdout, stderr = directory/(prefix+'.stdout.log'), directory/(prefix+'.stderr.log')
        row.update(stdout=stdout.name, stderr=stderr.name)
        record['phase'] = label
        record['commands'].append(row)
        persist()
        begin = time.monotonic()
        with stdout.open('wb') as out, stderr.open('wb') as err:
            try:
                result = subprocess.run(arguments, stdin=subprocess.DEVNULL, stdout=out, stderr=err,
                                        timeout=timeout, check=False)
                row.update(exitCode=result.returncode, status='passed' if result.returncode == 0 else 'failed')
            except subprocess.TimeoutExpired:
                row.update(status='failed', timedOut=True, error=f'timeout after {timeout} seconds')
            except OSError as error:
                row.update(status='failed', error=str(error))
        row['durationSeconds'] = round(time.monotonic()-begin, 6)
        if row['status'] != 'passed':
            row['failureReason'] = f'{label}: '+row.get('error', f'exit {row["exitCode"]}')
        persist()
        return row, stdout.read_text(errors='replace').strip(), stderr.read_text(errors='replace').strip()

    def probe(phase):
        result = {'phase': phase, 'tools': {}}
        record['phase'] = phase
        record['checks'].append(result)
        for tool in ('ffmpeg', 'ffprobe'):
            path = shutil.which(tool)
            tool_result = {'path': path, 'status': 'failed', 'version': None,
                           'exitCode': None, 'durationSeconds': 0, 'timedOut': False}
            result['tools'][tool] = tool_result
            if path is None:
                tool_result['failureReason'] = f'{phase}: {tool} missing or not executable on PATH'
                continue
            row, stdout, stderr = command(phase+'-'+tool, [path, '-version'], 10)
            tool_result.update(status=row['status'], exitCode=row['exitCode'],
                               durationSeconds=row['durationSeconds'], timedOut=row['timedOut'])
            if row['status'] == 'passed' and (stdout or stderr):
                tool_result['version'] = stdout or stderr
            else:
                tool_result['status'] = 'failed'
                tool_result['failureReason'] = row.get('failureReason', f'{phase}: {tool} returned no version output')
        persist()
        return '; '.join(tool['failureReason'] for tool in result['tools'].values() if tool['status'] != 'passed')

    try:
        if mode not in ('check', 'install'): raise ValueError('Unsupported media preparation mode')
        failure = probe('initial-check') or None
        if failure and mode == 'install':
            record['phase'] = 'install-policy'
            if sys.platform != 'linux' or platform.freedesktop_os_release().get('ID') != 'ubuntu':
                raise ValueError('install-policy: automatic media installation requires Ubuntu Linux; prepare ffmpeg and ffprobe explicitly')
            apt = shutil.which('apt-get')
            if not apt: raise ValueError('install-policy: apt-get unavailable for ffmpeg preparation')
            privilege = []
            if os.geteuid() != 0:
                sudo = shutil.which('sudo')
                if not sudo: raise ValueError('install-policy: root or noninteractive sudo privilege is required')
                privilege = [sudo, '-n']
            # Use the runner's configured signed distribution repositories, without changing sources.
            update, _, _ = command('apt-update', [*privilege, apt, 'update'], 180)
            package_failure = update.get('failureReason')
            if package_failure is None:
                install, _, _ = command('apt-install', [*privilege, apt, 'install', '-y',
                                       '--no-install-recommends', 'ffmpeg'], 300)
                package_failure = install.get('failureReason')
            post_failure = probe('post-install-check')
            failure = package_failure or post_failure or None
            if package_failure and privilege:
                failure += '; noninteractive sudo must permit the package operation'
        # Package metadata is diagnostic only; executable readiness remains authoritative.
        dpkg = shutil.which('dpkg-query')
        if dpkg and sys.platform == 'linux':
            row, output, _ = command('package-version', [dpkg, '-W', '-f=${Version}', 'ffmpeg'], 10)
            if row['status'] == 'passed': record['packageVersion'] = output
        record['phase'] = 'complete'
    except (OSError, ValueError) as error:
        failure = f'{record["phase"]}: {error}'
    finally:
        if failure is None and record.get('diagnosticErrors'):
            failure = 'Media diagnostics update failed'
        record.update(status='failed' if failure else 'passed', failureReason=failure,
                      durationSeconds=round(time.monotonic()-started, 6))
        try:
            write_record(receipt, record)
        except OSError as error:
            print(f'Media diagnostics finalization failed: {error}', file=sys.stderr)
            if failure is None: failure = 'Media diagnostics finalization failed'
        if failure: print(f'Media preparation failed: {failure}', file=sys.stderr)
    return 1 if failure else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mode', choices=('check', 'install'), required=True)
    parser.add_argument('--report-dir', type=Path, required=True)
    args = parser.parse_args()
    return prepare(args.mode, args.report_dir)


if __name__ == '__main__':
    sys.exit(main())
