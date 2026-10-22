import { defineConfig, devices } from '@playwright/test';
import base from './playwright.config';
import policy from './scripts/verification/selection-policy.json' with { type: 'json' };
const baseURL = process.env.PERSEFONIA_ADMIN_PREVIEW_URL;
if (!baseURL || new URL(baseURL).hostname !== '127.0.0.1' || new URL(baseURL).protocol !== 'http:')
  throw new Error(
    'Admin native qualification requires the guarded loopback test-classpath preview',
  );
export default defineConfig({
  ...base,
  testDir: './admin-e2e',
  testMatch: policy.adminNative[0]!.cases.map((c) => `**/${c.file}`),
  outputDir: 'test-results/admin-required',
  reporter: [
    ['list'],
    ...(process.env.PERSEFONIA_VERIFICATION_REPORT
      ? [['json', { outputFile: process.env.PERSEFONIA_VERIFICATION_REPORT }] as const]
      : []),
  ],
  use: { ...base.use, baseURL, trace: 'off' },
  projects: [
    {
      name: 'admin-desktop',
      use: {
        ...devices['Desktop Chrome'],
        browserName: 'chromium',
        channel: 'chromium',
        viewport: { width: 1440, height: 900 },
      },
    },
  ],
});
