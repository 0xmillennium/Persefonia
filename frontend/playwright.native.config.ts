import { defineConfig, devices } from '@playwright/test';
import policy from './scripts/verification/selection-policy.json' with { type: 'json' };
export default defineConfig({
  testDir: './e2e',
  testMatch: 'smoke/*.spec.ts',
  forbidOnly: true,
  fullyParallel: false,
  workers: 1,
  retries: 0,
  repeatEach: 1,
  timeout: 20_000,
  globalTimeout: 210_000,
  expect: { timeout: 5_000 },
  reporter: [
    ['list'],
    ...(process.env.PERSEFONIA_VERIFICATION_REPORT
      ? [['json', { outputFile: process.env.PERSEFONIA_VERIFICATION_REPORT }] as const]
      : []),
  ],
  outputDir: 'test-results/native',
  use: {
    baseURL: process.env.PERSEFONIA_BROWSER_URL ?? 'http://127.0.0.1:18080',
    actionTimeout: 5_000,
    navigationTimeout: 10_000,
    trace: 'off',
    screenshot: 'off',
  },
  projects: policy.native.map((p) => ({
    name: p.project,
    testMatch: [...new Set(p.cases.map((c) => c.file))],
    use: {
      ...devices[
        p.engine === 'firefox'
          ? 'Desktop Firefox'
          : p.engine === 'webkit'
            ? 'Desktop Safari'
            : p.context === 'touch'
              ? 'Pixel 5'
              : 'Desktop Chrome'
      ],
      browserName: p.engine as 'chromium' | 'firefox' | 'webkit',
      ...(p.engine === 'chromium' ? { channel: 'chromium' } : {}),
      viewport: p.context === 'touch' ? { width: 390, height: 850 } : { width: 1440, height: 900 },
    },
  })),
});
