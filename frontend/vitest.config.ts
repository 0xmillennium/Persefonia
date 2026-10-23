import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['src/**/*.test.ts', 'scripts/**/*.test.ts'],
    environment: 'node',
    isolate: false,
    // CPU-bound physics replays share the ordinary ten-second case budget.
    maxWorkers: 1,
    testTimeout: process.env.PERSEFONIA_PHYSICS_PROFILE === 'deep' ? 30_000 : 10_000,
  },
});
