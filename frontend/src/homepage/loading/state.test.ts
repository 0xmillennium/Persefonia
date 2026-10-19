import { expect, it } from 'vitest';
import { startupState } from './state';

it('does not complete startup because a poster exists while the video is preparing', () => {
  expect(startupState(true, true, { state: 'loading', progress: 0 })).toEqual({
    phase: 'loading',
    progress: 25,
  });
  expect(startupState(true, true, { state: 'loading', progress: 0.9 }).progress).toBeLessThan(100);
  expect(startupState(true, true, { state: 'video', progress: 1 })).toEqual({
    phase: 'ready',
    progress: 100,
  });
});
it('waits for critical layout resources and intentionally accepts reduced-motion static readiness', () => {
  expect(startupState(false, true, { state: 'video', progress: 1 }).phase).toBe('loading');
  expect(startupState(true, false, { state: 'video', progress: 1 }).phase).toBe('loading');
  expect(startupState(true, true, { state: 'static', progress: 1 })).toEqual({
    phase: 'static',
    progress: 100,
  });
});
it('distinguishes degraded startup, the absolute escape deadline, and later recovery', () => {
  const pending = { state: 'loading', progress: 0.5 } as const;
  expect(startupState(true, true, pending, true).phase).toBe('fallback');
  expect(startupState(true, false, pending, true).phase).toBe('unavailable');
  expect(startupState(true, true, { state: 'fallback', progress: 0 }).phase).toBe('fallback');
  expect(startupState(true, true, { state: 'video', progress: 1 }, true).phase).toBe('ready');
});
