import { defineConfig } from '@playwright/test';
import base from './playwright.native.config';
// Explicit unique native diagnostics; these never issue ordinary qualification receipts.
const files = [
  'homepage/timeline/wheel-roundtrip.spec.ts',
  'homepage/film/motion.spec.ts',
  'homepage/film/gpu-delivery.spec.ts',
  'homepage/film/media-resilience.spec.ts',
  'homepage/film/loop.spec.ts',
  'homepage/globe.spec.ts',
  'homepage/layout.spec.ts',
];
export default defineConfig({
  ...base,
  testMatch: files,
  timeout: 30_000,
  globalTimeout: 600_000,
  outputDir: 'test-results/diagnostics',
  projects: base
    .projects!.filter((p) => p.name !== 'mobile-390')
    .map((p) => ({ ...p, testMatch: files })),
});
