import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { LoopFilm } from '../loop-film';
import { FILM_READINESS_TIMEOUT } from '../delivery';
import { loadBitmap } from '../loader';
vi.mock('virtual:home-media', () => ({ loopVersion: 'qualified' }));
vi.mock('../loader', () => ({ loadBitmap: vi.fn(() => new Promise(() => {})) }));
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
  buffered = { length: 1, start: () => 0, end: () => 8 };
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
it('accepts a prepared still while covered without falsely qualifying unbuffered visible playback', async () => {
  vi.mocked(loadBitmap).mockResolvedValueOnce({ close: vi.fn() } as unknown as ImageBitmap);
  const loop = new LoopFilm(parent, 1280, vi.fn());
  loop.prepare();
  loop.setPlayback(false, false);
  video.readyState = 4;
  video.buffered.length = 0;
  await Promise.resolve();
  expect(video.paused).toBe(true);
  expect(loop.readiness).toEqual({ state: 'static', progress: 1 });
  loop.setPlayback(true, false);
  await Promise.resolve();
  expect(loop.readiness.state).toBe('loading');
  video.buffered.length = 1;
  expect(loop.readiness.state).toBe('video');
  loop.dispose();
  expect(vi.getTimerCount()).toBe(0);
});
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
it.each([true, false])(
  'announces late native buffering with playback allowed=%s and stops checking once ready',
  async (allowed) => {
    const changed = vi.fn();
    const loop = new LoopFilm(parent, 1280, changed);
    loop.prepare();
    video.buffered.length = 0;
    loop.setPlayback(allowed, false);
    video.readyState = 4;
    video.dispatchEvent(new Event('loadeddata'));
    await Promise.resolve();
    expect(loop.readiness.state).toBe('loading');
    changed.mockClear();
    // A native engine can expose this range after the last progress/playing event;
    // a covered, paused loop does not supply subsequent timeupdate events either.
    video.buffered.length = 1;
    video.currentTime = 0.5;
    vi.advanceTimersByTime(250);
    expect(loop.readiness.state).toBe('video');
    expect(changed).toHaveBeenCalledTimes(1);
    video.currentTime = 1;
    vi.advanceTimersByTime(FILM_READINESS_TIMEOUT * 2);
    expect(changed).toHaveBeenCalledTimes(1);
    loop.dispose();
    expect(vi.getTimerCount()).toBe(0);
  },
);
it('bounds silent buffering checks even when the owner repeatedly reconciles paused playback', () => {
  const loop = new LoopFilm(parent, 1280, vi.fn());
  loop.prepare();
  video.buffered.length = 0;
  loop.setPlayback(false, false);
  video.readyState = 4;
  video.dispatchEvent(new Event('loadeddata'));
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT + 250);
  expect(vi.getTimerCount()).toBe(0);
  for (let i = 0; i < 5; i++) loop.setPlayback(false, false);
  expect(vi.getTimerCount()).toBe(0);
  loop.suspend(true);
  loop.suspend(false);
  expect(vi.getTimerCount()).toBeGreaterThan(0);
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
  video.dispatchEvent(new Event('pause'));
  expect(loop.failed).toBe(true);
  loop.dispose();
  expect(vi.getTimerCount()).toBe(0);
});

it('loads only the poster for reduced motion and lazily creates video after the preference changes', () => {
  const loop = new LoopFilm(parent, 1280, vi.fn());
  const create = vi.spyOn(document, 'createElement');
  loop.prepare(0, true);
  expect(create).not.toHaveBeenCalled();
  expect(loop.prepared).toBe(true);
  loop.prepare(0, false);
  expect(create).toHaveBeenCalledTimes(1);
  loop.dispose();
});
it('accepts buffered decode behind a covering scene without starting playback', async () => {
  const loop = new LoopFilm(parent, 1280, vi.fn());
  loop.prepare();
  loop.setPlayback(false, false);
  expect(loop.readiness.state).toBe('loading');
  video.readyState = 4;
  expect(video.paused).toBe(true);
  expect(loop.readiness.state).toBe('video');
  loop.setPlayback(true, false);
  expect(loop.readiness.state).toBe('loading');
  await Promise.resolve();
  expect(video.paused).toBe(false);
  expect(loop.readiness.state).toBe('video');
  loop.dispose();
  expect(vi.getTimerCount()).toBe(0);
});
it('accepts a healthy video that decodes after the idle timeout without reload', () => {
  const loop = new LoopFilm(parent, 1280, vi.fn());
  loop.prepare();
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT);
  expect(loop.failed).toBe(true);
  video.readyState = 4;
  video.dispatchEvent(new Event('loadeddata'));
  expect(loop.failed).toBe(false);
  expect(loop.image).toBe(video);
  loop.dispose();
});
