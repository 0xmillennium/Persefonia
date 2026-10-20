import { defineConfig, devices } from '@playwright/test';
import base from './playwright.config';

/** Real production pages; no separate demonstration application. */
export default defineConfig({
  ...base,
  testMatch: [
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
  ],
  outputDir: 'test-results/cross-browser',
  projects: [
    {
      name: 'firefox-desktop',
      use: {
        ...devices['Desktop Firefox'],
        browserName: 'firefox',
        viewport: { width: 1440, height: 900 },
      },
    },
    {
      name: 'webkit-desktop',
      use: {
        ...devices['Desktop Safari'],
        browserName: 'webkit',
        viewport: { width: 1440, height: 900 },
      },
    },
  ],
});
