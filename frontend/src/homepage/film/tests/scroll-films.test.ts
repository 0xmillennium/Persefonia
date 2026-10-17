import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ScrollFilms } from '../scroll-films';
import { FILM_READINESS_TIMEOUT } from '../delivery';
const native = vi.hoisted(() => ({
  request: vi.fn(),
  setTier: vi.fn(),
  suspend: vi.fn(),
  dispose: vi.fn(),
  show: vi.fn(),
  retry: vi.fn(),
  get: vi.fn(),
  hasVisual: vi.fn(),
  failed: vi.fn(),
  hasError: false,
}));
vi.mock('../seek-film', () => ({
  SeekFilm: class {
    constructor() {
      return native;
    }
  },
}));
vi.mock('virtual:home-media', () => ({ posterVersion: 'qualified' }));
class Poster {
  src = '';
  className = '';
  alt = '';
  hidden = true;
  decoding = '';
  dataset = {};
  style = {};
  removed = false;
  resolve!: () => void;
  reject!: () => void;
  promise = new Promise<void>((resolve, reject) => {
    this.resolve = resolve;
    this.reject = reject;
  });
  decode() {
    return this.promise;
  }
  removeAttribute() {
    this.src = '';
  }
  remove() {
    this.removed = true;
  }
}
let images: Poster[];
const parent = {
  prepend(image: Poster) {
    images.push(image);
  },
} as unknown as HTMLElement;
beforeEach(() => {
  images = [];
  vi.useFakeTimers();
  vi.clearAllMocks();
  native.failed.mockReturnValue(false);
  native.get.mockReturnValue(undefined);
  native.hasVisual.mockReturnValue(false);
  vi.stubGlobal('window', globalThis);
  vi.stubGlobal('Image', Poster);
});
afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});
it('healthy seeking does not request posters', () => {
  const films = new ScrollFilms(parent, 960, vi.fn());
  films.request('tree', 10, 'final');
  expect(native.request).toHaveBeenCalledWith('tree', 10);
  expect(images).toHaveLength(0);
  films.dispose();
});
it('uses an explicit static pose after failure, and retains it during another pose load', async () => {
  native.failed.mockReturnValue(true);
  const changed = vi.fn(),
    films = new ScrollFilms(parent, 960, changed);
  films.request('tree', 0, 'opening');
  images[0]!.resolve();
  await Promise.resolve();
  expect(films.presentation('tree', 0, 'opening')?.key).toBe('tree:opening');
  films.request('tree', 100, 'final');
  expect(films.presentation('tree', 100, 'final')?.key).toBe('tree:opening');
  images[1]!.resolve();
  await Promise.resolve();
  expect(films.presentation('tree', 100, 'final')?.key).toBe('tree:final');
  expect(changed).toHaveBeenCalledTimes(2);
  films.dispose();
  expect(vi.getTimerCount()).toBe(0);
});
it('retains the usable poster while a higher resolution prepares or fails', async () => {
  native.failed.mockReturnValue(true);
  const films = new ScrollFilms(parent, 960, vi.fn());
  films.request('tree', 50, 'final');
  images[0]!.resolve();
  await Promise.resolve();
  films.setTier(1600);
  films.request('tree', 50, 'final');
  expect(films.presentation('tree', 50, 'final')?.source).toBe(images[0]);
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT);
  images[1]!.resolve();
  await Promise.resolve();
  expect(films.presentation('tree', 50, 'final')?.source).toBe(images[0]);
  films.dispose();
});
it('bounds stalled posters and retries only on explicit demand', async () => {
  native.failed.mockReturnValue(true);
  const films = new ScrollFilms(parent, 960, vi.fn());
  films.request('tree', 0, 'opening');
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT);
  for (let i = 0; i < 10; i++) films.request('tree', i, 'opening');
  expect(images).toHaveLength(1);
  expect(films.presentation('tree', 0, 'opening')).toBeUndefined();
  films.retry();
  films.request('tree', 0, 'opening');
  expect(images).toHaveLength(2);
  expect(images[1]!.src).toContain('?retry=1');
  images[1]!.resolve();
  await Promise.resolve();
  expect(films.presentation('tree', 0, 'opening')?.kind).toBe('poster');
  films.dispose();
});
it('reduced motion presents a poster without starting native media', async () => {
  const films = new ScrollFilms(parent, 960, vi.fn());
  films.setReduced(true);
  films.request('robotic-arm', 0, 'opening');
  expect(native.request).not.toHaveBeenCalled();
  images[0]!.resolve();
  await Promise.resolve();
  expect(films.presentation('robotic-arm', 0, 'opening')?.key).toBe('robotic-arm:opening');
  films.setReduced(false);
  const source = {} as HTMLVideoElement;
  native.get.mockReturnValue(source);
  expect(films.presentation('robotic-arm', 77, 'final')).toMatchObject({
    kind: 'video',
    key: 'robotic-arm:77',
    source,
  });
  films.dispose();
});
it('suspends pending poster deadlines and ignores late disposal completions', async () => {
  native.failed.mockReturnValue(true);
  const changed = vi.fn(),
    films = new ScrollFilms(parent, 960, changed);
  films.request('tree', 0, 'opening');
  films.suspend(true);
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT * 2);
  expect(changed).not.toHaveBeenCalled();
  films.suspend(false);
  films.dispose();
  images[0]!.resolve();
  await Promise.resolve();
  expect(changed).not.toHaveBeenCalled();
  expect(images[0]!.removed).toBe(true);
  expect(vi.getTimerCount()).toBe(0);
});

it('keeps a usable poster during an explicit native retry and then promotes decoded video', async () => {
  native.failed.mockReturnValue(true);
  const films = new ScrollFilms(parent, 960, vi.fn());
  films.request('tree', 50, 'final');
  images[0]!.resolve();
  await Promise.resolve();
  films.retry();
  native.failed.mockReturnValue(false);
  expect(films.presentation('tree', 50, 'final')?.kind).toBe('poster');
  const source = {} as HTMLVideoElement;
  native.get.mockReturnValue(source);
  expect(films.presentation('tree', 50, 'final')).toMatchObject({
    kind: 'video',
    key: 'tree:50',
    source,
  });
  films.dispose();
});

it('reports terminal reduced-motion poster failure without opening a video decoder', async () => {
  const films = new ScrollFilms(parent, 960, vi.fn());
  films.setReduced(true);
  films.request('tree', 0, 'opening');
  images[0]!.reject();
  await Promise.resolve();
  await Promise.resolve();
  expect(films.unavailable('tree', 'opening')).toBe(true);
  expect(native.request).not.toHaveBeenCalled();
  films.dispose();
});
