#!/usr/bin/env python3
"""Classify finalized Playwright traces without masking the original test result."""
from pathlib import Path
import sys
import zipfile
import qualification as q


def validate(directory):
    results = []
    for path in sorted(Path(directory).rglob('trace.zip')):
        result = {'path': str(path), 'sha256': q.file_hash(path), 'bytes': path.stat().st_size}
        try:
            q.require(path.stat().st_size <= 64*1024*1024, 'Trace exceeds diagnostic limit')
            with zipfile.ZipFile(path) as archive:
                infos = archive.infolist()
                q.require(len(infos) < 10000 and sum(i.file_size for i in infos) <= 256*1024*1024,
                          'Trace expansion exceeds diagnostic limit')
                q.require(archive.testzip() is None and any(i.filename.endswith('.trace') for i in infos),
                          'Trace CRC or event stream missing')
                for name in [i.filename for i in infos if i.filename.endswith('.trace')]:
                    for line in archive.read(name).splitlines(): q.load_json(line)
            result['readable'] = True
        except (ValueError, OSError, zipfile.BadZipFile, EOFError):
            result.update(readable=False, diagnosticError='Trace finalization/validation failed')
        results.append(result)
    return results


if __name__ == '__main__':
    q.write(sys.argv[2], {'traces': validate(sys.argv[1])})
