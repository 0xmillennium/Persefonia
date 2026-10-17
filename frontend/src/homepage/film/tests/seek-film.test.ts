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
  get currentTime() {
    return this.time;
  }
  set currentTime(value: number) {
    this.time = value;
    this.seeking = true;
  }
  style = {};
  setAttribute() {}
  removeAttribute() {}
  load() {}
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
it('bounds a stalled request despite continuing scroll targets, and explicitly retries', () => {
  const films = new SeekFilm(parent, 960, vi.fn());
  films.request('tree', 0);
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT - 1);
  films.request('tree', 90);
  vi.advanceTimersByTime(1);
  expect(films.failed('tree')).toBe(true);
  videos[0]!.settle(90);
  expect(films.get('tree', 90)).toBeUndefined();
  films.retry();
  expect(videos[0]!.src).toContain('?retry=1');
  videos[0]!.settle(90);
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
