import { expect, it } from 'vitest';
import native from '../../playwright.native.config';
import policy from './selection-policy.json';
import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';
import { reportTests } from './browser-contract.mjs';
import { validateNativeSelection } from './selection-policy.mjs';
it('discovers exactly the source manifest with the installed runner, without launching browsers', () => {
  const result = spawnSync(
    process.execPath,
    [
      resolve('node_modules/@playwright/test/cli.js'),
      'test',
      '--config',
      'playwright.native.config.ts',
      '--list',
      '--reporter=json',
    ],
    { encoding: 'utf8', timeout: 15_000, env: { ...process.env, CI: 'true' } },
  );
  expect(result.status, result.stderr).toBe(0);
  const rows = reportTests(JSON.parse(result.stdout));
  expect(rows).toHaveLength(14);
  for (const p of policy.native)
    validateNativeSelection(
      rows.filter((r: { project: string }) => r.project === p.project),
      'ordinary',
      p.project,
    );
});
it('retains all three native engines and touch with bounded isolated first attempts', () => {
  expect(native.projects?.map((p) => p.name)).toEqual(policy.native.map((p) => p.project));
  expect(native.workers).toBe(1);
  expect(native.retries).toBe(0);
  expect(native.fullyParallel).toBe(false);
  expect(native.timeout).toBe(20_000);
  expect(native.globalTimeout).toBe(210_000);
  for (const [i, p] of native.projects!.entries()) {
    expect(p.use?.browserName).toBe(policy.native[i]!.engine);
    expect(p.use?.hasTouch ?? false).toBe(policy.native[i]!.context === 'touch');
    expect(p.grep).toBeUndefined();
    expect(p.grepInvert).toBeUndefined();
  }
});
