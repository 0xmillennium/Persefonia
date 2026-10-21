import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { chromium, firefox, webkit } from 'playwright';
import { metadata, writeJson } from './browser-runtime.mjs';

export function environment(engines = []) {
  const version = (tool, args) =>
    execFileSync(tool, args, {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
    }).trim();
  const base = metadata();
  const revisions = JSON.parse(
    readFileSync(
      new URL('../../node_modules/playwright-core/browsers.json', import.meta.url),
      'utf8',
    ),
  );
  return {
    os: base.os,
    arch: base.arch,
    node: base.node,
    npm: base.npm,
    playwright: base.playwright,
    runnerImage: {
      os: process.env.ImageOS ?? 'local',
      version: process.env.ImageVersion ?? 'local',
    },
    ffmpeg: version('ffmpeg', ['-version']).split('\n')[0],
    ffprobe: version('ffprobe', ['-version']).split('\n')[0],
    browserRevisions: revisions.browsers.map(
      ({ name, revision, browserVersion, revisionOverrides }) => ({
        name,
        revision,
        browserVersion: browserVersion ?? null,
        revisionOverrides: revisionOverrides ?? null,
      }),
    ),
    // These are installed executable identities; comparing them does not claim master execution.
    browsers: Object.fromEntries(
      Object.entries({ chromium, firefox, webkit })
        .filter(([name]) => engines.includes(name))
        .map(([name, type]) => [
          name,
          {
            sha256: createHash('sha256').update(readFileSync(type.executablePath())).digest('hex'),
          },
        ]),
    ),
  };
}
if (process.argv[1]?.endsWith('/environment.mjs'))
  writeJson(
    process.argv[2],
    environment(process.argv.includes('--browsers') ? ['chromium', 'firefox', 'webkit'] : []),
  );
