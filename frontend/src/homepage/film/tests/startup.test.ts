import { expect, it } from 'vitest';
import { videoReadiness } from '../startup';

function media(ahead: number, readyState = 2, currentTime = 0) {
  return {
    readyState,
    currentTime,
    duration: 8,
    buffered: { length: 1, start: () => currentTime, end: () => currentTime + ahead },
  } as unknown as HTMLVideoElement;
}
it('requires an actually decoded frame and a short buffer, rather than metadata alone', () => {
  expect(videoReadiness(media(0.05), true).state).toBe('loading');
  expect(videoReadiness(media(1), false).state).toBe('loading');
  expect(videoReadiness(media(0.25), true)).toEqual({ state: 'video', progress: 1 });
});
it('requires active loop playback and permits the shorter remaining buffer at the film end', () => {
  expect(videoReadiness(media(1), true, false).state).toBe('loading');
  expect(videoReadiness(media(0.1, 2, 7.9), true).state).toBe('video');
});
