import { expect, it } from 'vitest';
import base from '../../playwright.config';
import cross from '../../playwright.cross-browser.config';

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
