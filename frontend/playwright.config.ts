import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  forbidOnly: !!process.env.CI,
  testDir: './e2e',
  testMatch: '**/*.spec.ts',
  workers: 1,
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 8_000 },
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ...(process.env.PERSEFONIA_VERIFICATION_REPORT
      ? [['json', { outputFile: process.env.PERSEFONIA_VERIFICATION_REPORT }] as const]
      : []),
  ],
  // Browser output is disposable; source media and qualification evidence live outside it.
  outputDir: 'test-results/browser',
  use: {
    baseURL: process.env.PERSEFONIA_BROWSER_URL ?? 'http://127.0.0.1:18080',
    trace: 'off',
    screenshot: 'off',
  },
  projects: [
    {
      name: 'desktop-1440',
      use: {
        ...devices['Desktop Chrome'],
        browserName: 'chromium',
        channel: 'chromium',
        viewport: { width: 1440, height: 900 },
      },
    },
    {
      name: 'tablet-834',
      use: {
        ...devices['Desktop Chrome'],
        browserName: 'chromium',
        channel: 'chromium',
        viewport: { width: 834, height: 900 },
      },
    },
    {
      name: 'mobile-390',
      use: {
        ...devices['Pixel 5'],
        browserName: 'chromium',
        channel: 'chromium',
        viewport: { width: 390, height: 850 },
      },
    },
  ],
});
