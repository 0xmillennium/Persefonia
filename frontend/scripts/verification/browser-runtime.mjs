import { runProcess } from './process.mjs';
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
  const java = spawnSync('java', ['-version'], { encoding: 'utf8' });
  if (java.status !== 0 || typeof java.stderr !== 'string')
    throw new Error('Java metadata prerequisite unavailable');
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
    java: java.stderr.trim(),
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
export async function playwright(suite, selection, destination, discover = false) {
  if (suite !== 'ordinary' || selection.length)
    throw new Error('Mandatory native execution accepts no filters');
  const started = performance.now();
  const result = await runProcess(
    process.execPath,
    [
      join(frontend, 'node_modules/@playwright/test/cli.js'),
      'test',
      '--config',
      'playwright.native.config.ts',
      '--forbid-only',
      '--fail-on-flaky-tests',
      '--workers=1',
      '--retries=0',
      ...selection,
      ...(discover ? ['--list', '--reporter=json'] : ['--global-timeout=210000']),
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
    discover ? 30_000 : 215_000,
  );
  if (discover && result.exitCode !== 0) console.error(result.stdout, result.stderr);
  return {
    exitCode: result.exitCode,
    signal: result.signal,
    durationMs: performance.now() - started,
  };
}

export function sourceContext() {
  return JSON.parse(
    execFileSync('python3', ['-B', join(repository, 'scripts/ci/qualification.py')], {
      cwd: repository,
      encoding: 'utf8',
      maxBuffer: 1024 * 1024,
    }),
  );
}

export function verificationPlan() {
  return JSON.parse(
    execFileSync('python3', ['-B', join(repository, 'scripts/ci/selection.py')], {
      cwd: repository,
      encoding: 'utf8',
      maxBuffer: 2 * 1024 * 1024,
    }),
  );
}

/** Bounded unique-key JSON parsing is shared with artifact admission, without executing data. */
export function readEvidence(path) {
  return JSON.parse(
    execFileSync(
      'python3',
      [
        '-B',
        '-c',
        'import sys,json; sys.path.insert(0,sys.argv[1]); import qualification as q; print(json.dumps(q.read(sys.argv[2])))',
        join(repository, 'scripts/ci'),
        resolve(path),
      ],
      { cwd: repository, encoding: 'utf8', maxBuffer: 32 * 1024 * 1024 },
    ),
  );
}
