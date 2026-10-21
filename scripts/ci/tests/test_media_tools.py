"""Media preparation contracts use an isolated PATH, never host apt, sudo or FFmpeg."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

HELPER = Path(__file__).resolve().parents[1]/'prepare-media-tools.py'


class MediaPreparationContracts(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(dir=os.environ.get('RUNNER_TEMP'))
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.bin = self.root/'bin'
        self.bin.mkdir()
        self.reports = self.root/'reports'
        self.events = self.root/'events'
        self.environment = {'PATH': str(self.bin), 'FIXTURE_BIN': str(self.bin),
                            'FIXTURE_EVENTS': str(self.events)}
        spec = importlib.util.spec_from_file_location('media_tools', HELPER)
        self.media = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.media)
        self.executable('apt-get', 'echo "apt $*" >> "$FIXTURE_EVENTS"\nexit 99\n')
        self.executable('sudo', 'echo "sudo $*" >> "$FIXTURE_EVENTS"\nexit 98\n')

    def executable(self, name, body, executable=True):
        path = self.bin/name
        path.write_text('#!/bin/sh\n'+body)
        path.chmod(0o755 if executable else 0o644)
        return path

    def healthy(self, tool):
        self.executable(tool, 'echo "'+tool+' $*" >> "$FIXTURE_EVENTS"\necho "'+tool+' version fixture-real-output"\n')

    def invoke(self, mode='check', uid=0, system='linux', distro='ubuntu'):
        with patch.dict(os.environ, self.environment, clear=True), \
                patch.object(self.media.sys, 'platform', system), \
                patch.object(self.media.platform, 'freedesktop_os_release', return_value={'ID': distro}), \
                patch.object(self.media.os, 'geteuid', return_value=uid, create=True), \
                contextlib.redirect_stderr(io.StringIO()):
            status = self.media.prepare(mode, self.reports)
        records = sorted(self.reports.glob('invocation-*/setup.json'), key=lambda p: p.stat().st_mtime_ns)
        self.assertTrue(records)
        record = json.loads(records[-1].read_text())
        self.assertEqual(status == 0, record['status'] == 'passed')
        self.assertTrue(record['phase'])
        self.assertGreaterEqual(record['durationSeconds'], 0)
        return status, record, records[-1]

    def lines(self):
        return self.events.read_text().splitlines() if self.events.exists() else []

    def installer(self, exit_code=0, tools=('ffmpeg', 'ffprobe'), update_code=0):
        scripts = ''
        for tool in tools:
            scripts += ('/bin/cat > "$FIXTURE_BIN/'+tool+'" <<\'TOOL\'\n#!/bin/sh\n'
                        'echo "'+tool+' $*" >> "$FIXTURE_EVENTS"\necho "'+tool+' version installed-output"\nTOOL\n'
                        '/bin/chmod +x "$FIXTURE_BIN/'+tool+'"\n')
        self.executable('apt-get', 'echo "apt $*" >> "$FIXTURE_EVENTS"\n'
            'if [ "$1" = update ]; then exit '+str(update_code)+'; fi\n'+scripts+'exit '+str(exit_code)+'\n')
        self.executable('dpkg-query', 'echo "6.1.1-distro-fixture"\n')

    def test_healthy_tools_recheck_without_installing_or_overwriting_evidence(self):
        for tool in ('ffmpeg', 'ffprobe'): self.healthy(tool)
        before = None
        for mode in ('check', 'install', 'install'):
            status, record, path = self.invoke(mode)
            self.assertEqual(status, 0)
            self.assertEqual(record['checks'][0]['tools']['ffmpeg']['status'], 'passed')
            self.assertIn('version fixture-real-output', record['checks'][0]['tools']['ffprobe']['version'])
            if before is None: before = (path, path.read_bytes())
        self.assertEqual(before[0].read_bytes(), before[1])
        self.assertEqual(len(list(self.reports.glob('invocation-*'))), 3)
        self.assertEqual(self.lines(), ['ffmpeg -version', 'ffprobe -version']*3)

    def test_each_missing_tool_and_both_missing_fail_check_without_package_operations(self):
        for missing in [('ffmpeg',), ('ffprobe',), ('ffmpeg', 'ffprobe')]:
            for tool in ('ffmpeg', 'ffprobe'):
                self.healthy(tool)
                if tool in missing: (self.bin/tool).unlink()
            with self.subTest(missing=missing):
                status, record, _ = self.invoke()
                self.assertNotEqual(status, 0)
                for tool in missing: self.assertIn(tool, record['failureReason'])
                self.assertEqual(record['checks'][0]['phase'], 'initial-check')
        self.assertFalse(any(line.startswith(('apt ', 'sudo ')) for line in self.lines()))

    def test_non_executable_broken_and_loader_failure_are_not_success(self):
        self.healthy('ffprobe')
        for body, executable in [('echo unused\n', False), ('exit 7\n', True), ('exit 0\n', True)]:
            self.executable('ffmpeg', body, executable)
            if body == 'exit 0\n': (self.bin/'ffmpeg').write_text('#!/missing-fixture-loader\n')
            with self.subTest(body=body, executable=executable):
                status, record, _ = self.invoke()
                self.assertNotEqual(status, 0)
                self.assertIn('ffmpeg', record['failureReason'])
                self.assertEqual(record['checks'][0]['tools']['ffprobe']['status'], 'passed')

    def test_install_orders_update_then_install_and_rechecks_both(self):
        self.installer()
        status, record, _ = self.invoke('install')
        self.assertEqual(status, 0)
        self.assertEqual(self.lines(), ['apt update', 'apt install -y --no-install-recommends ffmpeg',
                                       'ffmpeg -version', 'ffprobe -version'])
        self.assertEqual(record['packageVersion'], '6.1.1-distro-fixture')
        self.assertEqual([row['phase'] for row in record['checks']], ['initial-check', 'post-install-check'])
        self.assertEqual([row['timeoutSeconds'] for row in record['commands'][:2]], [180, 300])
        # A second actual check must not repeat package preparation.
        self.assertEqual(self.invoke('install')[0], 0)
        self.assertEqual(sum(line.startswith('apt ') for line in self.lines()), 2)

    def test_update_failure_prevents_install_and_retains_postcheck(self):
        self.installer(update_code=12)
        status, record, _ = self.invoke('install')
        self.assertNotEqual(status, 0)
        self.assertIn('apt-update', record['failureReason'])
        self.assertEqual(self.lines(), ['apt update'])
        self.assertEqual(len(record['checks']), 2)
        self.assertEqual(set(record['checks'][1]['tools']), {'ffmpeg', 'ffprobe'})

    def test_install_failure_stays_failed_even_if_tools_became_usable(self):
        self.installer(exit_code=13)
        status, record, _ = self.invoke('install')
        self.assertNotEqual(status, 0)
        self.assertIn('apt-install', record['failureReason'])
        self.assertTrue(all(tool['status'] == 'passed' for tool in record['checks'][1]['tools'].values()))

    def test_nominal_install_success_cannot_hide_a_missing_postinstall_tool(self):
        self.installer(tools=('ffmpeg',))
        status, record, _ = self.invoke('install')
        self.assertNotEqual(status, 0)
        self.assertIn('post-install-check', record['failureReason'])
        self.assertIn('ffprobe', record['failureReason'])

    def test_shadowing_broken_binary_is_not_replaced_or_path_rewritten(self):
        shadow = self.root/'shadow'
        shadow.mkdir()
        broken = shadow/'ffmpeg'
        broken.write_text('#!/bin/sh\nexit 8\n'); broken.chmod(0o755)
        self.environment['PATH'] = str(shadow)+os.pathsep+str(self.bin)
        self.installer()
        status, record, _ = self.invoke('install')
        self.assertNotEqual(status, 0)
        self.assertEqual(record['checks'][1]['tools']['ffmpeg']['path'], str(broken))
        self.assertEqual(broken.read_text(), '#!/bin/sh\nexit 8\n')

    def test_unsupported_platform_missing_apt_and_unavailable_privilege_fail_without_fallback(self):
        for subject in ('platform', 'distro', 'apt', 'privilege'):
            if subject == 'apt': (self.bin/'apt-get').unlink()
            if subject == 'privilege': (self.bin/'sudo').unlink()
            with self.subTest(subject=subject):
                status, record, _ = self.invoke('install', system='darwin' if subject == 'platform' else 'linux',
                    distro='other' if subject == 'distro' else 'ubuntu', uid=1000 if subject == 'privilege' else 0)
                self.assertNotEqual(status, 0)
                self.assertIn('install-policy', record['failureReason'])
            if subject == 'apt': self.executable('apt-get', 'exit 99\n')
        self.assertEqual(self.lines(), [])
        for tool in ('ffmpeg', 'ffprobe'): self.healthy(tool)
        self.assertEqual(self.invoke(system='darwin')[0], 0)

    def test_noninteractive_sudo_refusal_has_no_retry_or_password_prompt(self):
        self.installer()
        status, record, _ = self.invoke('install', uid=1000)
        self.assertNotEqual(status, 0)
        self.assertTrue(self.lines()[0].startswith('sudo -n '))
        self.assertEqual(len(self.lines()), 1)
        self.assertIn('apt-update', record['failureReason'])

    def test_timeout_records_phase_and_both_checks_use_ten_second_bounds(self):
        for tool in ('ffmpeg', 'ffprobe'): self.healthy(tool)
        real_run = subprocess.run
        def run(command, **kwargs):
            if command[0] == str(self.bin/'ffmpeg'):
                self.assertEqual(kwargs['timeout'], 10)
                raise subprocess.TimeoutExpired(command, 10)
            self.assertEqual(kwargs['timeout'], 10)
            return real_run(command, **kwargs)
        with patch.object(self.media.subprocess, 'run', side_effect=run):
            status, record, _ = self.invoke()
        self.assertNotEqual(status, 0)
        self.assertIn('timeout', record['failureReason'])
        self.assertTrue(record['commands'][0]['timedOut'])
        self.assertEqual(record['checks'][0]['tools']['ffprobe']['status'], 'passed')

    def test_package_timeouts_retain_original_failure_and_validate_both_tools(self):
        self.installer()
        real_run = subprocess.run
        for phase, bound in [('update', 180), ('install', 300)]:
            def run(command, **kwargs):
                if command[0] == str(self.bin/'apt-get') and command[1] == phase:
                    self.assertEqual(kwargs['timeout'], bound)
                    raise subprocess.TimeoutExpired(command, bound)
                return real_run(command, **kwargs)
            with self.subTest(phase=phase), patch.object(self.media.subprocess, 'run', side_effect=run):
                status, record, _ = self.invoke('install')
                self.assertNotEqual(status, 0)
                self.assertIn('apt-'+phase, record['failureReason'])
                self.assertIn('timeout', record['failureReason'])
                self.assertEqual(set(record['checks'][-1]['tools']), {'ffmpeg', 'ffprobe'})

    def test_receipt_exists_before_probes_and_finalization_retains_primary_error(self):
        real_which = self.media.shutil.which
        def which(tool):
            self.assertTrue(list(self.reports.glob('invocation-*/setup.json')))
            return real_which(tool)
        with patch.object(self.media.shutil, 'which', side_effect=which):
            self.assertNotEqual(self.invoke()[0], 0)
        original_write = self.media.write_record
        def write(path, value):
            if value['status'] == 'failed': raise OSError('fixture finalization failure')
            original_write(path, value)
        stderr = io.StringIO()
        with patch.dict(os.environ, self.environment, clear=True), \
                patch.object(self.media, 'write_record', side_effect=write), contextlib.redirect_stderr(stderr):
            self.assertNotEqual(self.media.prepare('check', self.reports), 0)
        self.assertIn('ffmpeg', stderr.getvalue())
        self.assertIn('finalization', stderr.getvalue())

    def test_intermediate_diagnostic_write_failure_does_not_replace_the_tool_failure(self):
        self.executable('ffmpeg', 'exit 17\n')
        self.healthy('ffprobe')
        original_write = self.media.write_record
        def write(path, value):
            if value['status'] == 'running' and any(row['status'] == 'failed' for row in value['commands']):
                raise OSError('fixture intermediate diagnostic failure')
            original_write(path, value)
        with patch.object(self.media, 'write_record', side_effect=write):
            status, record, _ = self.invoke()
        self.assertNotEqual(status, 0)
        self.assertIn('exit 17', record['failureReason'])
        self.assertIn('diagnosticErrors', record)
