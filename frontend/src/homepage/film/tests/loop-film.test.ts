import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { LoopFilm } from '../loop-film';
import { FILM_READINESS_TIMEOUT } from '../delivery';
vi.mock('virtual:home-media', () => ({ loopVersion: 'qualified' }));
vi.mock('../loader', () => ({ loadBitmap: () => new Promise(() => {}) }));
class Media extends EventTarget {
  className = '';
  loop = false;
  muted = false;
  playsInline = false;
  preload = '';
  hidden = false;
  src = '';
  poster = '';
  readyState = 0;
  seeking = false;
  currentTime = 0;
  duration = 8;
  paused = true;
  style = {};
  dataset: Record<string, string> = {};
  alt = '';
  setAttribute() {}
  removeAttribute() {}
  load() {
    this.readyState = 0;
  }
  remove() {}
  pause() {
    this.paused = true;
  }
  async play() {
    this.paused = false;
  }
}
let video: Media;
beforeEach(() => {
  vi.useFakeTimers();
  video = new Media();
  vi.stubGlobal('window', globalThis);
  vi.stubGlobal('document', { createElement: () => video });
  vi.stubGlobal('Image', Media);
});
afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});
const parent = { prepend() {} } as unknown as HTMLElement;
it('accepts actual decode readiness when loadeddata is delayed', () => {
  const loop = new LoopFilm(parent, 1280, vi.fn());
  loop.prepare();
  video.readyState = 4;
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT);
  expect(loop.failed).toBe(false);
  expect(loop.image).toBe(video);
  loop.dispose();
  expect(vi.getTimerCount()).toBe(0);
});
it('distinguishes continued native playback from a real stall despite delayed timeupdate', async () => {
  const loop = new LoopFilm(parent, 1280, vi.fn());
  loop.prepare();
  video.readyState = 4;
  video.dispatchEvent(new Event('loadeddata'));
  loop.setPlayback(true, false);
  await Promise.resolve();
  video.dispatchEvent(new Event('waiting'));
  video.currentTime = 2;
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT);
  expect(loop.failed).toBe(false);
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT);
  expect(loop.failed).toBe(true);
  expect(video.paused).toBe(true);
  loop.dispose();
  expect(vi.getTimerCount()).toBe(0);
});
