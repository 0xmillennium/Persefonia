import { expect, it } from 'vitest';
import { startupState } from './state';

it('does not complete startup because a poster exists while the video is preparing', () => {
  expect(startupState(true, 'ready', { state: 'loading', progress: 0 })).toEqual({
    phase: 'loading',
    progress: 25,
  });
  expect(startupState(true, 'ready', { state: 'loading', progress: 0.9 }).progress).toBeLessThan(
    100,
  );
  expect(startupState(true, 'ready', { state: 'video', progress: 1 })).toEqual({
    phase: 'ready',
    progress: 100,
  });
});
it('waits for critical layout resources and intentionally accepts reduced-motion static readiness', () => {
  expect(startupState(false, 'ready', { state: 'video', progress: 1 }).phase).toBe('loading');
  expect(startupState(true, 'pending', { state: 'video', progress: 1 }).phase).toBe('loading');
  expect(startupState(true, 'ready', { state: 'static', progress: 1 })).toEqual({
    phase: 'static',
    progress: 100,
  });
});
it('distinguishes degraded startup, the absolute escape deadline, and later recovery', () => {
  const pending = { state: 'loading', progress: 0.5 } as const;
  expect(startupState(true, 'ready', pending, true).phase).toBe('fallback');
  expect(startupState(true, 'pending', pending, true).phase).toBe('unavailable');
  expect(startupState(true, 'ready', { state: 'fallback', progress: 0 }).phase).toBe('fallback');
  expect(startupState(true, 'ready', { state: 'video', progress: 1 }, true).phase).toBe('ready');
});

it.each(['video', 'static', 'fallback'] as const)(
  'settles failed images with usable %s as degraded, never successful',
  (state) => {
    expect(startupState(true, 'failed', { state, progress: 1 })).toEqual({
      phase: 'fallback',
      progress: 85,
    });
    expect(startupState(false, 'failed', { state, progress: 1 }).phase).toBe('loading');
  },
);
it('keeps pending preparation bounded and separates terminal unavailable media', () => {
  expect(startupState(true, 'failed', { state: 'loading', progress: 0 }).phase).toBe('loading');
  expect(startupState(true, 'failed', { state: 'loading', progress: 0 }, true).phase).toBe(
    'unavailable',
  );
  expect(startupState(true, 'failed', { state: 'unavailable', progress: 0 }).phase).toBe(
    'unavailable',
  );
  expect(startupState(true, 'ready', { state: 'video', progress: 1 }, true).phase).toBe('ready');
});
