import { defineConfig, devices } from '@playwright/test';
import base from './playwright.native.config';

const baseURL = process.env.PERSEFONIA_ADMIN_PREVIEW_URL;
if (!baseURL || new URL(baseURL).hostname !== '127.0.0.1' || new URL(baseURL).protocol !== 'http:')
  throw new Error(
    'Admin browser qualification requires the isolated loopback test-classpath preview',
  );

export default defineConfig({
  ...base,
  testDir: './admin-e2e',
  testMatch: '*.spec.ts',
  globalTimeout: 180_000,
  outputDir: 'test-results/admin-browser',
  use: { ...base.use, baseURL },
  projects: [
    {
      name: 'admin-desktop',
      use: {
        ...devices['Desktop Chrome'],
        channel: 'chromium',
        viewport: { width: 1440, height: 900 },
      },
    },
    {
      name: 'admin-mobile',
      use: { ...devices['Pixel 5'], channel: 'chromium', viewport: { width: 320, height: 850 } },
    },
    {
      name: 'admin-firefox',
      use: { ...devices['Desktop Firefox'], viewport: { width: 1440, height: 900 } },
    },
    {
      name: 'admin-webkit',
      use: { ...devices['Desktop Safari'], viewport: { width: 1440, height: 900 } },
    },
  ],
});
