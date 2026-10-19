import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { SeekFilm } from '../seek-film';
import { FILM_READINESS_TIMEOUT } from '../delivery';
vi.mock('virtual:home-media', () => ({ videoVersion: 'qualified' }));
class Video extends EventTarget {
  className = '';
  muted = false;
  playsInline = false;
  preload = '';
  hidden = false;
  src = '';
  readyState = 0;
  seeking = false;
  time = 0;
  removed = false;
  error: { code: number } | null = null;
  seeks: number[] = [];
  loads = 0;
  get currentTime() {
    return this.time;
  }
  set currentTime(value: number) {
    this.time = value;
    this.seeks.push(Math.floor(value * 30));
    this.seeking = true;
  }
  style = {};
  setAttribute() {}
  removeAttribute() {}
  load() {
    this.loads++;
    this.error = null;
    this.seeking = false;
    this.readyState = 0;
  }
  remove() {
    this.removed = true;
  }
  settle(index: number, notify = true) {
    this.time = (index + 0.25) / 30;
    this.readyState = 2;
    this.seeking = false;
    if (notify) this.dispatchEvent(new Event('seeked'));
  }
}
let videos: Video[];
const parent = { prepend: (video: Video) => videos.push(video) } as unknown as HTMLElement;
beforeEach(() => {
  videos = [];
  vi.useFakeTimers();
  vi.stubGlobal('window', globalThis);
  vi.stubGlobal('document', { createElement: () => new Video() });
});
afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});
it('falls back after a stalled request and automatically accepts healthy late readiness', () => {
  const films = new SeekFilm(parent, 960, vi.fn());
  films.request('tree', 0);
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT - 1);
  films.request('tree', 90);
  vi.advanceTimersByTime(1);
  expect(films.failed('tree')).toBe(true);
  videos[0]!.settle(90);
  expect(videos[0]!.loads).toBe(0);
  expect(films.get('tree', 90)).toBe(videos[0]);
  films.dispose();
  expect(vi.getTimerCount()).toBe(0);
});
it('keeps the old resolution until replacement reaches the current pose; failed upgrade cannot discard it', () => {
  const films = new SeekFilm(parent, 960, vi.fn());
  films.request('tree', 50);
  videos[0]!.settle(50);
  films.setTier(1600);
  films.request('tree', 50);
  expect(videos).toHaveLength(2);
  expect(films.get('tree', 50)).toBe(videos[0]);
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT);
  expect(films.failed('tree')).toBe(false);
  expect(films.get('tree', 50)).toBe(videos[0]);
  films.retry();
  videos[1]!.settle(50);
  expect(films.get('tree', 50)).toBe(videos[1]);
  expect(videos[0]!.removed).toBe(true);
  films.dispose();
});
it('suspension cancels readiness deadlines and resume grants a fresh bounded attempt', () => {
  const films = new SeekFilm(parent, 960, vi.fn());
  films.request('robotic-arm', 0);
  films.suspend(true);
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT * 2);
  expect(films.failed('robotic-arm')).toBe(false);
  films.suspend(false);
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT);
  expect(films.failed('robotic-arm')).toBe(true);
  films.dispose();
});

it('uses settled native state even when the seeked event is delayed or absent', () => {
  const changed = vi.fn();
  const films = new SeekFilm(parent, 960, changed);
  films.request('tree', 50);
  videos[0]!.settle(50, false);
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT);
  expect(changed).toHaveBeenCalledOnce();
  expect(films.failed('tree')).toBe(false);
  expect(films.get('tree', 50)).toBe(videos[0]);
  films.setTier(1600);
  films.request('tree', 50);
  videos[1]!.settle(50, false);
  expect(films.get('tree', 50)).toBe(videos[1]);
  expect(videos[0]!.removed).toBe(true);
  films.dispose();
});

it('serializes seeks and coalesces fast reversals into the latest destination', () => {
  const films = new SeekFilm(parent, 960, vi.fn());
  films.request('tree', 10);
  const video = videos[0]!;
  video.readyState = 1;
  video.dispatchEvent(new Event('loadedmetadata'));
  films.request('tree', 20);
  films.request('tree', 80);
  expect(video.seeks).toEqual([10]);
  video.settle(10);
  expect(video.seeks).toEqual([10, 80]);
  films.request('tree', 5);
  expect(video.seeks).toEqual([10, 80]);
  video.settle(80);
  expect(video.seeks).toEqual([10, 80, 5]);
  video.settle(5);
  expect(films.get('tree', 5)).toBe(video);
  films.dispose();
});
it('estimates decoder time from settled seeks within bounded frame budgets', () => {
  const films = new SeekFilm(parent, 960, vi.fn());
  films.request('tree', 10);
  const video = videos[0]!;
  video.readyState = 1;
  video.dispatchEvent(new Event('loadedmetadata'));
  vi.advanceTimersByTime(80);
  video.settle(10);
  expect(films.frameInterval('tree')).toBeGreaterThan(1000 / 30);
  films.request('tree', 20);
  vi.advanceTimersByTime(1000);
  video.settle(20);
  expect(films.frameInterval('tree')).toBeLessThanOrEqual(100);
  films.dispose();
});
it('preserves progressing cold downloads and falls back only after progress stalls', () => {
  const films = new SeekFilm(parent, 960, vi.fn());
  films.request('tree', 0);
  for (let i = 0; i < 5; i++) {
    vi.advanceTimersByTime(FILM_READINESS_TIMEOUT - 1);
    videos[0]!.dispatchEvent(new Event('progress'));
    expect(films.failed('tree')).toBe(false);
  }
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT);
  expect(films.failed('tree')).toBe(true);
  videos[0]!.settle(0);
  expect(films.get('tree', 0)).toBe(videos[0]);
  films.dispose();
});
it('retries transient network failure once and keeps explicit recovery available', () => {
  const films = new SeekFilm(parent, 960, vi.fn());
  films.request('tree', 0);
  const video = videos[0]!;
  video.error = { code: 2 };
  video.dispatchEvent(new Event('error'));
  vi.advanceTimersByTime(250);
  expect(video.loads).toBe(1);
  video.error = { code: 2 };
  video.dispatchEvent(new Event('error'));
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT * 2);
  expect(video.loads).toBe(1);
  films.retry();
  expect(video.loads).toBe(2);
  video.settle(0);
  expect(films.get('tree', 0)).toBe(video);
  films.dispose();
  expect(vi.getTimerCount()).toBe(0);
});

it('retries a persistently idle request once while renewed progress postpones recovery', () => {
  const films = new SeekFilm(parent, 960, vi.fn());
  films.request('tree', 0);
  const video = videos[0]!;
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT);
  expect(films.failed('tree')).toBe(true);
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT - 1);
  video.dispatchEvent(new Event('progress'));
  vi.advanceTimersByTime(1);
  expect(video.loads).toBe(0);
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT - 1);
  expect(video.loads).toBe(1);
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT * 3);
  expect(video.loads).toBe(1);
  video.settle(0);
  expect(films.get('tree', 0)).toBe(video);
  films.dispose();
  expect(vi.getTimerCount()).toBe(0);
});
