import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['src/**/*.test.ts', 'scripts/**/*.test.ts'],
    environment: 'node',
    isolate: false,
    maxWorkers: 2,
    testTimeout: process.env.PERSEFONIA_PHYSICS_PROFILE === 'deep' ? 30_000 : 10_000,
  },
});
