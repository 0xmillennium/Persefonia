import { expect, it } from 'vitest';
import base from '../../playwright.config';
import cross from '../../playwright.cross-browser.config';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

it.each([
  ['playwright.config.ts', 3],
  ['playwright.cross-browser.config.ts', 2],
] as const)(
  '%s resolves the actual engine and channel for every project',
  (config, count) => {
    const directory = mkdtempSync(join(tmpdir(), 'persefonia-project-engines-'));
    try {
      const inherited = join(directory, 'config.ts');
      writeFileSync(
        inherited,
        `import base from ${JSON.stringify(resolve(config))};
export default { ...base, testDir: ${JSON.stringify(directory)}, testMatch: '**/*.spec.ts',
reporter: 'json', outputDir: ${JSON.stringify(join(directory, 'output'))} };`,
      );
      writeFileSync(
        join(directory, 'engines.spec.ts'),
        `import { test, expect } from ${JSON.stringify(resolve('node_modules/@playwright/test/index.mjs'))};
test('uses the intended native engine', async ({ browserName, channel }, testInfo) => {
  const expected = testInfo.project.name.startsWith('firefox') ? 'firefox'
    : testInfo.project.name.startsWith('webkit') ? 'webkit' : 'chromium';
  expect(browserName).toBe(expected);
  expect(channel).toBe(expected === 'chromium' ? 'chromium' : undefined);
});`,
      );
      // Resolve native worker fixtures without requiring installed browser binaries.
      const result = spawnSync(
        process.execPath,
        [resolve('node_modules/@playwright/test/cli.js'), 'test', '--config', inherited],
        { encoding: 'utf8', env: { ...process.env, CI: 'true' }, timeout: 30_000 },
      );
      expect(result.status, `${result.error ?? ''}\n${result.stderr}\n${result.stdout}`).toBe(0);
      expect(JSON.parse(result.stdout).stats).toMatchObject({
        expected: count,
        skipped: 0,
        unexpected: 0,
      });
    } finally {
      rmSync(directory, { recursive: true, force: true });
    }
  },
  45_000,
);

it('retains unfiltered Chromium discovery and every mandatory viewport and engine', () => {
  expect(base.testDir).toBe('./e2e');
  expect(base.testMatch).toBe('**/*.spec.ts');
  expect(base.projects?.map((p) => p.name)).toEqual(
    expect.arrayContaining(['desktop-1440', 'tablet-834', 'mobile-390']),
  );
  expect(cross.projects?.map((p) => p.name)).toEqual(
    expect.arrayContaining(['firefox-desktop', 'webkit-desktop']),
  );
  for (const config of [base, cross]) {
    expect(config.workers).toBe(1);
    expect(config.retries).toBe(0);
    for (const key of ['grep', 'grepInvert', 'testIgnore', 'shard'] as const)
      expect(config[key]).toBeUndefined();
    expect(config.fullyParallel).not.toBe(true);
    for (const project of config.projects ?? []) {
      expect(project.grep).toBeUndefined();
      expect(project.grepInvert).toBeUndefined();
      expect(project.testIgnore).toBeUndefined();
    }
  }
  expect(cross.testMatch).toEqual(
    expect.arrayContaining([
      '**/public/homepage.spec.ts',
      '**/homepage/film/video.spec.ts',
      '**/homepage/loading.spec.ts',
      '**/public/public-shell.spec.ts',
      '**/fallback-visual.spec.ts',
      '**/homepage/gallery/directory.spec.ts',
      '**/homepage/lifecycle.spec.ts',
      '**/public/mermaid.spec.ts',
      '**/identity.spec.ts',
      '**/homepage/film/media-resilience.spec.ts',
      '**/globe.spec.ts',
      '**/homepage/film/hold-repress.spec.ts',
    ]),
  );
});
