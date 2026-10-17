import { defineConfig, devices } from '@playwright/test';
import base from './playwright.config';

/** Real production pages; no separate demonstration application. */
export default defineConfig({
  ...base,
  testMatch: [
    'homepage.spec.ts',
    '**/homepage-film-video.spec.ts',
    '**/public-shell.spec.ts',
    '**/fallback-visual.spec.ts',
    '**/homepage-gallery-directory.spec.ts',
    '**/homepage-lifecycle.spec.ts',
    '**/mermaid.spec.ts',
    '**/identity.spec.ts',
    '**/media-resilience.spec.ts',
    '**/globe.spec.ts',
    '**/hold-repress.spec.ts',
  ],
  outputDir: 'test-results/cross-browser',
  projects: [
    {
      name: 'firefox-desktop',
      use: {
        ...devices['Desktop Firefox'],
        channel: undefined,
        viewport: { width: 1440, height: 900 },
      },
    },
    {
      name: 'webkit-desktop',
      use: {
        ...devices['Desktop Safari'],
        channel: undefined,
        viewport: { width: 1440, height: 900 },
      },
    },
  ],
});
