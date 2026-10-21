import { expect, it } from 'vitest';
import {
  mkdtemp,
  writeFile,
  symlink,
  rm,
  access,
  readdir,
  readFile,
  chmod,
} from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import {
  sourceArguments,
  externalSource,
  withMediaWorkspace,
  requireMediaTools,
} from '../source-input.mjs';

it('requires explicit original paths and rejects repository files through symlinks', async () => {
  expect(() => sourceArguments(['arm', 'tree'], ['--arm', '/original.mp4'])).toThrow('--tree');
  expect(() => sourceArguments(['source'], ['--unknown', 'x'])).toThrow();
  const directory = await mkdtemp(`${tmpdir()}/media-input-test-`);
  try {
    const original = `${directory}/original film.mp4`;
    await writeFile(original, 'external fixture');
    expect(await externalSource(original)).toBe(original);
    const repositoryFile = fileURLToPath(new URL('../../../package.json', import.meta.url));
    await symlink(repositoryFile, `${directory}/linked-original.mp4`);
    await expect(externalSource(`${directory}/linked-original.mp4`)).rejects.toThrow('outside');
    await expect(externalSource(directory)).rejects.toThrow('regular file');
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

it('removes owned media intermediates after both completion and failure', async () => {
  for (const fail of [false, true]) {
    let workspace = '';
    const result = withMediaWorkspace(async (directory: string) => {
      workspace = directory;
      await writeFile(`${directory}/intermediate.png`, 'fixture');
      if (fail) throw new Error('preparation failed');
      return 'published';
    });
    if (fail) await expect(result).rejects.toThrow('preparation failed');
    else expect(await result).toBe('published');
    await expect(access(workspace)).rejects.toThrow();
  }
});

it('rejects invalid original video and cleans its workspace before touching publication', async () => {
  const directory = await mkdtemp(`${tmpdir()}/media-rejection-test-`);
  const manifest = new URL('../../../public/home-videos/manifest.json', import.meta.url);
  const before = await readFile(manifest);
  try {
    const original = `${directory}/invalid.mp4`;
    await writeFile(original, 'not a video');
    const result = spawnSync(
      process.execPath,
      [
        fileURLToPath(new URL('../publish-home-media.mjs', import.meta.url)),
        '--arm',
        original,
        '--tree',
        original,
      ],
      { env: { ...process.env, TMPDIR: directory }, encoding: 'utf8' },
    );
    expect(result.status).not.toBe(0);
    expect(result.stderr).toContain('Invalid data found when processing input');
    expect(result.stderr).not.toContain('Missing media prerequisite');
    expect((await readdir(directory)).sort()).toEqual(['invalid.mp4']);
    expect(await readFile(manifest)).toEqual(before);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

it('reports missing encoding tools as a prerequisite failure, not invalid media', () => {
  expect(() => requireMediaTools({ PATH: '/nonexistent-media-tool-path' })).toThrow(
    'Missing media prerequisite: ffprobe',
  );
});

it('checks each media executable independently with a PATH isolated from host tools', async () => {
  const directory = await mkdtemp(`${tmpdir()}/media-tools-test-`);
  const log = `${directory}/calls`;
  try {
    for (const missing of ['ffprobe', 'ffmpeg', 'both', 'neither']) {
      await writeFile(log, '');
      for (const tool of ['ffprobe', 'ffmpeg']) {
        const path = `${directory}/${tool}`;
        await rm(path, { force: true });
        if (missing === tool || missing === 'both') continue;
        await writeFile(
          path,
          `#!/bin/sh\nprintf '%s\\n' '${tool} -version' >> "$MEDIA_FIXTURE_LOG"\nprintf '%s\\n' '${tool} version fixture'\n`,
        );
        await chmod(path, 0o755);
      }
      const check = () => requireMediaTools({ PATH: directory, MEDIA_FIXTURE_LOG: log });
      if (missing === 'neither') {
        expect(check).not.toThrow();
        expect(await readFile(log, 'utf8')).toBe('ffprobe -version\nffmpeg -version\n');
      } else {
        expect(check).toThrow(
          `Missing media prerequisite: ${missing === 'both' ? 'ffprobe' : missing}`,
        );
      }
    }
    await writeFile(`${directory}/ffmpeg`, '#!/bin/sh\nexit 7\n');
    expect(() => requireMediaTools({ PATH: directory })).toThrow(
      'Missing media prerequisite: ffmpeg',
    );
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
