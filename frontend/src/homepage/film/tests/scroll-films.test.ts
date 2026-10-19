import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ScrollFilms } from '../scroll-films';
import { Hold, HOLD } from '../hold/state';
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
it('healthy seeking retains an existing video without requesting posters', () => {
  native.hasVisual.mockReturnValue(true);
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
it('retains the usable poster through timeout and adopts a healthy late preferred resolution', async () => {
  native.failed.mockReturnValue(true);
  const films = new ScrollFilms(parent, 960, vi.fn());
  films.request('tree', 50, 'final');
  images[0]!.resolve();
  await Promise.resolve();
  films.setTier(1600);
  films.request('tree', 50, 'final');
  expect(films.presentation('tree', 50, 'final')?.source).toBe(images[0]);
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT);
  expect(films.presentation('tree', 50, 'final')?.source).toBe(images[0]);
  images[1]!.resolve();
  await Promise.resolve();
  expect(films.presentation('tree', 50, 'final')?.source).toBe(images[1]);
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

it('shows a cold-start poster and replaces it when the video decodes without a reload', async () => {
  const films = new ScrollFilms(parent, 960, vi.fn());
  films.request('robotic-arm', 0, 'opening');
  images[0]!.resolve();
  await Promise.resolve();
  expect(films.presentation('robotic-arm', 0, 'opening')?.kind).toBe('poster');
  native.get.mockReturnValue({} as HTMLVideoElement);
  expect(films.presentation('robotic-arm', 0, 'opening')?.kind).toBe('video');
  films.dispose();
});

it('keeps a cold poster below normal readiness but recognizes intentional static and error fallbacks', async () => {
  const films = new ScrollFilms(parent, 960, vi.fn());
  films.request('tree', 0, 'opening');
  images[0]!.resolve();
  await Promise.resolve();
  expect(films.readiness('tree', 0, 'opening').state).toBe('loading');
  native.failed.mockReturnValue(true);
  expect(films.readiness('tree', 0, 'opening').state).toBe('fallback');
  films.setReduced(true);
  expect(films.readiness('tree', 0, 'opening').state).toBe('static');
  films.dispose();
});

it('recovers the same timed-out reduced-motion entry without a download or retry', async () => {
  const changed = vi.fn(),
    films = new ScrollFilms(parent, 960, changed);
  films.setReduced(true);
  films.request('tree', 0, 'opening');
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT);
  expect(films.readiness('tree', 0, 'opening').state).toBe('unavailable');
  for (let i = 0; i < 4; i++) films.request('tree', 0, 'opening');
  images[0]!.resolve();
  await Promise.resolve();
  expect(films.readiness('tree', 0, 'opening').state).toBe('static');
  expect(films.hasError).toBe(false);
  expect(images).toHaveLength(1);
  expect(changed).toHaveBeenCalledTimes(2);
  films.dispose();
});
it('ignores obsolete retry completions and does not replace the selected source or preferred tier', async () => {
  const changed = vi.fn(),
    films = new ScrollFilms(parent, 960, changed);
  films.setReduced(true);
  films.request('tree', 0, 'opening');
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT);
  films.retry();
  films.request('tree', 0, 'opening');
  changed.mockClear();
  images[0]!.resolve();
  await Promise.resolve();
  expect(changed).not.toHaveBeenCalled();
  expect(films.presentation('tree', 0, 'opening')).toBeUndefined();
  films.setTier(1600);
  films.request('tree', 0, 'opening');
  images[2]!.resolve();
  await Promise.resolve();
  images[1]!.resolve();
  await Promise.resolve();
  expect(films.presentation('tree', 0, 'opening')?.source).toBe(images[2]);
  films.request('robotic-arm', 0, 'opening');
  films.show('robotic-arm', 0, 'opening', [0, 0, 100, 100], 1);
  expect(images[1]!.hidden).toBe(true);
  expect(images[2]!.hidden).toBe(true);
  films.dispose();
});
it('keeps permanent rejection terminal and retains the lower tier through a preferred failure', async () => {
  const films = new ScrollFilms(parent, 960, vi.fn());
  films.setReduced(true);
  films.request('tree', 0, 'opening');
  images[0]!.resolve();
  await Promise.resolve();
  films.setTier(1600);
  films.request('tree', 0, 'opening');
  images[1]!.reject();
  await Promise.resolve();
  await Promise.resolve();
  vi.advanceTimersByTime(FILM_READINESS_TIMEOUT * 2);
  expect(films.presentation('tree', 0, 'opening')?.source).toBe(images[0]);
  expect(images).toHaveLength(2);
  films.dispose();
});

it.each(['cancelled', 'expired'] as const)(
  'late incoming readiness cannot commit a %s hold',
  async (intent) => {
    const films = new ScrollFilms(parent, 960, vi.fn()),
      hold = new Hold();
    films.setReduced(true);
    films.request('tree', 0, 'opening');
    hold.select({ x: 0, y: 0 });
    vi.advanceTimersByTime(FILM_READINESS_TIMEOUT);
    if (intent === 'cancelled') hold.cancel();
    else hold.step(HOLD.readinessTimeout + 1, false, 1, true);
    images[0]!.resolve();
    await Promise.resolve();
    expect(films.readiness('tree', 0, 'opening').state).toBe('static');
    for (let i = 0; i < 200; i++) expect(hold.step(1 / 60, true, 1, true)).toBe(false);
    expect(hold.phase).toBe('IDLE');
    films.dispose();
  },
);

it('ignores a late obsolete higher tier but retains one that was already displayed during resize', async () => {
  const films = new ScrollFilms(parent, 1600, vi.fn());
  films.setReduced(true);
  films.request('tree', 0, 'opening');
  films.setTier(960);
  films.request('tree', 0, 'opening');
  images[0]!.resolve();
  await Promise.resolve();
  expect(films.presentation('tree', 0, 'opening')).toBeUndefined();
  images[1]!.resolve();
  await Promise.resolve();
  expect(films.presentation('tree', 0, 'opening')?.source).toBe(images[1]);
  films.setTier(1600);
  films.show('tree', 0, 'opening', [0, 0, 100, 100], 1);
  films.setTier(960);
  expect(films.presentation('tree', 0, 'opening')?.source).toBe(images[1]);
  films.dispose();
  const retained = new ScrollFilms(parent, 1600, vi.fn());
  retained.setReduced(true);
  retained.request('tree', 0, 'opening');
  images[2]!.resolve();
  await Promise.resolve();
  retained.show('tree', 0, 'opening', [0, 0, 100, 100], 1);
  retained.setTier(960);
  retained.request('tree', 0, 'opening');
  expect(retained.presentation('tree', 0, 'opening')?.source).toBe(images[2]);
  retained.dispose();
});
