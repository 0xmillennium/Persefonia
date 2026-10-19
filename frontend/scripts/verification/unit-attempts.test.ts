import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { expect, it } from 'vitest';
import { validateUnitAttempts } from './unit-contract.mjs';

it('observes and rejects a native successful retry that JSON alone could accept', () => {
  const directory = mkdtempSync(join(tmpdir(), 'persefonia-unit-attempts-'));
  const config = join(directory, 'config.mjs'),
    report = join(directory, 'results.json'),
    attempts = join(directory, 'attempts.json');
  writeFileSync(
    config,
    `export default {test: {root: ${JSON.stringify(directory)}, include: ['fixture.test.mjs'],
    maxWorkers: 1, retry: 0, reporters: ['json', ${JSON.stringify(resolve('scripts/verification/unit-attempts.ts'))}],
    outputFile: ${JSON.stringify(report)} }};`,
  );
  writeFileSync(
    join(directory, 'fixture.test.mjs'),
    `import {test, expect} from ${JSON.stringify(resolve('node_modules/vitest/dist/index.js'))};
    let calls = 0; test('retry', {retry: 1}, () => { expect(++calls).toBe(2); });
    test.each([1, 2])('same parameterized title', () => {});`,
  );
  try {
    const result = spawnSync(
      process.execPath,
      [resolve('node_modules/vitest/vitest.mjs'), 'run', '--config', config],
      {
        encoding: 'utf8',
        env: { ...process.env, PERSEFONIA_UNIT_ATTEMPTS: attempts },
        timeout: 30_000,
      },
    );
    expect(result.status, result.stdout + result.stderr).toBe(0);
    const json = JSON.parse(readFileSync(report, 'utf8'));
    const diagnostics = JSON.parse(readFileSync(attempts, 'utf8'));
    const cases = json.testResults.flatMap((file: { name: string; assertionResults: object[] }) =>
      file.assertionResults.map((test) => ({ file: file.name, ...test })),
    );
    expect(json.success).toBe(true);
    const retried = diagnostics.find((test: { retryCount: number }) => test.retryCount === 1);
    expect(retried).toBeDefined();
    expect(() => validateUnitAttempts(cases, diagnostics)).toThrow('first attempt');
    retried.retryCount = 0;
    expect(() => validateUnitAttempts(cases, diagnostics)).not.toThrow();
    expect(diagnostics[1].id).toBe(diagnostics[2].id);
    const duplicate = structuredClone(diagnostics);
    duplicate[2].nativeId = duplicate[1].nativeId;
    expect(() => validateUnitAttempts(cases, duplicate)).toThrow('unit attempts');
    expect(() => validateUnitAttempts(cases, diagnostics.slice(1))).toThrow('unit attempts');
    expect(() => validateUnitAttempts(cases, [...diagnostics, diagnostics[0]])).toThrow(
      'unit attempts',
    );
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}, 40_000);
