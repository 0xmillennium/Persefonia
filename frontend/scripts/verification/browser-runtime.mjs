import { execFileSync, spawnSync } from 'node:child_process';
import { readFileSync, mkdirSync, writeFileSync, renameSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, join } from 'node:path';
import { createHash } from 'node:crypto';

export const frontend = fileURLToPath(new URL('../../', import.meta.url));
export const repository = resolve(frontend, '..');
export function identity(running = false) {
  const sourceSha = execFileSync('git', ['rev-parse', 'HEAD'], {
    cwd: repository,
    encoding: 'utf8',
  }).trim();
  if (process.env.GITHUB_SHA && process.env.GITHUB_SHA !== sourceSha)
    throw new Error('Checkout SHA mismatch');
  const jar = readFileSync(join(repository, 'app/build/ci/persefonia.jar'));
  const artifactSha256 = createHash('sha256').update(jar).digest('hex');
  const checksum = readFileSync(join(repository, 'app/build/ci/persefonia.jar.sha256'), 'utf8');
  if (checksum.trim() !== `${artifactSha256}  persefonia.jar`)
    throw new Error('BootJar checksum mismatch');
  const source = readFileSync(join(repository, 'app/build/ci/source-sha.txt'), 'utf8').trim();
  if (source !== sourceSha) throw new Error('Artifact source SHA mismatch');
  if (
    running &&
    createHash('sha256')
      .update(readFileSync(join(repository, 'app/build/libs/persefonia.jar')))
      .digest('hex') !== artifactSha256
  )
    throw new Error('Running BootJar differs from qualified input');
  return { sourceSha, artifactSha256 };
}
export function metadata() {
  const version = (tool, args) =>
    execFileSync(tool, args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
  return {
    job: process.env.GITHUB_JOB ?? 'local',
    run: process.env.GITHUB_RUN_ID ?? 'local',
    attempt: process.env.GITHUB_RUN_ATTEMPT ?? '1',
    runner: process.env.RUNNER_NAME ?? 'local',
    os: process.platform,
    arch: process.arch,
    node: process.version,
    npm: version('npm', ['--version']),
    playwright: JSON.parse(
      readFileSync(join(frontend, 'node_modules/@playwright/test/package.json'), 'utf8'),
    ).version,
    java: spawnSync('java', ['-version'], { encoding: 'utf8' }).stderr.trim(),
    dirty: !!execFileSync('git', ['status', '--porcelain'], {
      cwd: repository,
      encoding: 'utf8',
    }).trim(),
  };
}
export function writeJson(path, value) {
  mkdirSync(resolve(path, '..'), { recursive: true });
  writeFileSync(`${path}.tmp`, JSON.stringify(value) + '\n');
  renameSync(`${path}.tmp`, path);
}
export function playwright(suite, selection, destination, discover = false) {
  const started = performance.now();
  const result = spawnSync(
    process.execPath,
    [
      join(frontend, 'node_modules/@playwright/test/cli.js'),
      'test',
      '--config',
      suite === 'cross' ? 'playwright.cross-browser.config.ts' : 'playwright.config.ts',
      '--forbid-only',
      '--fail-on-flaky-tests',
      '--workers=1',
      '--retries=0',
      ...selection,
      ...(discover ? ['--list', '--reporter=json'] : []),
    ],
    {
      cwd: frontend,
      stdio: discover ? ['ignore', 'pipe', 'pipe'] : 'inherit',
      encoding: 'utf8',
      env: {
        ...process.env,
        CI: 'true',
        PLAYWRIGHT_JSON_OUTPUT_NAME: destination,
        PERSEFONIA_VERIFICATION_REPORT: destination,
      },
    },
  );
  if (discover && result.status !== 0) console.error(result.stdout, result.stderr);
  return {
    exitCode: result.status ?? 1,
    signal: result.signal,
    durationMs: performance.now() - started,
  };
}
