import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { expect, it } from 'vitest';
import { reportTests, sameInventory, validateOutcomes } from '../verification/browser-contract.mjs';

// Exercise the installed runner and inherited configurations, without starting an application.
it.each([
  'playwright.config.ts',
  'playwright.cross-browser.config.ts',
  'playwright.native.config.ts',
])(
  '%s rejects both focus forms in CI and preserves ordinary execution and skips',
  (config) => {
    const directory = mkdtempSync(join(tmpdir(), 'persefonia-admission-'));
    const fixture = join(directory, 'admission.spec.ts');
    const inherited = join(directory, 'config.ts');
    const runner = resolve('node_modules/@playwright/test/cli.js');
    const testApi = resolve('node_modules/@playwright/test/index.mjs');
    writeFileSync(
      inherited,
      `import base from ${JSON.stringify(resolve(config))};
export default { ...base, testDir: ${JSON.stringify(directory)}, testMatch: '**/*.spec.ts',
projects: [{name: 'contract'}], reporter: 'json', outputDir: ${JSON.stringify(join(directory, 'output'))} };`,
    );
    const run = (ci = 'true', discover = false) =>
      spawnSync(
        process.execPath,
        [runner, 'test', '--config', inherited, ...(discover ? ['--list'] : [])],
        {
          encoding: 'utf8',
          env: { ...process.env, CI: ci },
          timeout: 20_000,
        },
      );
    const source = (body: string) =>
      writeFileSync(fixture, `import { test, expect } from ${JSON.stringify(testApi)};\n${body}`);
    try {
      for (const focused of [
        "test.only('focused', () => {});",
        "test.describe.only('focused', () => { test('child', () => {}); });",
      ]) {
        source(focused);
        const result = run('true', true);
        expect(result.status, `${result.error ?? ''}\n${result.stderr}\n${result.stdout}`).toBe(1);
        expect(result.stdout).toContain('forbidOnly');
        expect(run('', true).status).toBe(0);
      }
      source(
        "test('ordinary', () => { expect(1).toBe(1); }); test('intentional', () => { test.skip(true, 'Fixture skip'); });",
      );
      const ordinary = run();
      expect(ordinary.status, ordinary.stderr).toBe(0);
      expect(JSON.parse(ordinary.stdout).stats).toMatchObject({
        expected: 1,
        skipped: 1,
        unexpected: 0,
      });
      const actual = reportTests(JSON.parse(ordinary.stdout));
      const discovery = run('true', true);
      expect(discovery.status, discovery.stderr).toBe(0);
      sameInventory(
        reportTests(JSON.parse(discovery.stdout)).map((row: { id: string }) => row.id),
        actual.map((row: { id: string }) => row.id),
      );
      expect(
        validateOutcomes(actual, [
          {
            file: 'admission.spec.ts',
            title: 'intentional',
            projects: ['contract'],
            reason: 'Fixture skip',
            optional: false,
          },
        ]),
      ).toMatchObject({ passed: 1, skipped: 1 });
      source('');
      expect(run().status).toBe(1);
    } finally {
      rmSync(directory, { recursive: true, force: true });
    }
  },
  90_000,
);
