import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { homepageReadiness } from './readiness';
import { STARTUP_DEADLINE } from './loading/state';

vi.mock('../identity/compact/controller', () => ({
  compactIdentity: () => ({ dispose: vi.fn() }),
}));
vi.mock('../shared/motion/staircase', () => ({ exitStaircase: async () => {} }));
vi.mock('./loading/view', () => ({
  loadingView: () => ({
    progress: vi.fn(),
    phase: vi.fn(),
    hint: vi.fn(),
    exit: async () => {},
    dispose: vi.fn(),
  }),
}));
const deferred = () => {
  let resolve!: () => void, reject!: () => void;
  const promise = new Promise<void>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
};
const flush = async () => {
  for (let i = 0; i < 6; i++) await Promise.resolve();
};
beforeEach(() => {
  vi.useFakeTimers();
  vi.stubGlobal('window', globalThis);
  vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn() }));
});
afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});
function setup(fontSetReady?: Promise<void>) {
  const fonts = deferred(),
    image = deferred();
  const faces = [
    { status: 'loading', loaded: fonts.promise },
    { status: 'unloaded', loaded: new Promise<void>(() => {}) },
  ];
  vi.stubGlobal('document', {
    fonts: Object.assign(faces, { ready: fontSetReady ?? fonts.promise }),
  });
  const root = { dataset: {}, getBoundingClientRect: vi.fn() } as unknown as HTMLElement;
  const loader = {
    hidden: false,
    querySelector: () => ({}),
    querySelectorAll: () => [],
  } as unknown as HTMLElement;
  const onReady = vi.fn();
  const owner = homepageReadiness(
    root,
    loader,
    false,
    { matches: true } as MediaQueryList,
    onReady,
  );
  owner.track({ decode: () => image.promise } as HTMLImageElement);
  return { owner, root, loader, onReady, fonts, image };
}
it('accepts requested fonts and a usable poster while document font readiness waits on unrelated loading', async () => {
  const s = setup(new Promise<void>(() => {}));
  s.owner.media({ state: 'fallback', progress: 0 });
  s.image.resolve();
  s.fonts.resolve();
  await flush();
  expect(s.root.dataset.startupState).toBe('fallback');
  expect(s.root.dataset.ready).toBe('true');
  expect(s.onReady).toHaveBeenCalledTimes(1);
  expect(s.loader.hidden).toBe(true);
  s.owner.dispose();
});
it.each(['video', 'static'] as const)(
  'preserves delayed successful %s startup and font ordering',
  async (state) => {
    const s = setup();
    s.owner.media({ state, progress: 1 });
    vi.advanceTimersByTime(1500);
    s.image.resolve();
    await flush();
    expect(s.onReady).not.toHaveBeenCalled();
    s.fonts.resolve();
    await flush();
    expect(s.root.dataset.startupState).toBe(state === 'video' ? 'ready' : 'static');
    expect(s.root.dataset.ready).toBe('true');
    expect(s.onReady).toHaveBeenCalledTimes(1);
    s.owner.dispose();
  },
);
it.each(['video', 'static', 'fallback'] as const)(
  'settles image rejection with usable %s without false readiness or reopening',
  async (state) => {
    const s = setup();
    s.fonts.resolve();
    s.image.reject();
    await flush();
    expect(s.onReady).not.toHaveBeenCalled();
    s.owner.media({ state, progress: 1 });
    await flush();
    expect(s.root.dataset.startupState).toBe('fallback');
    expect(s.root.dataset.ready).toBeUndefined();
    expect(s.loader.hidden).toBe(true);
    s.owner.media({ state: 'video', progress: 1 });
    vi.advanceTimersByTime(STARTUP_DEADLINE);
    await flush();
    expect(s.onReady).toHaveBeenCalledTimes(1);
    expect(s.loader.hidden).toBe(true);
    s.owner.dispose();
  },
);
it('bounds pending images and recovers a delayed success after deadline without reopening', async () => {
  const s = setup();
  s.fonts.resolve();
  s.owner.media({ state: 'video', progress: 1 });
  await flush();
  expect(s.onReady).not.toHaveBeenCalled();
  vi.advanceTimersByTime(STARTUP_DEADLINE);
  await flush();
  expect(s.root.dataset.startupState).toBe('unavailable');
  s.image.resolve();
  await flush();
  expect(s.root.dataset.startupState).toBe('ready');
  expect(s.onReady).toHaveBeenCalledTimes(1);
  expect(s.loader.hidden).toBe(true);
  s.owner.dispose();
});
it('settles unavailable alternatives honestly and ignores all completions after disposal', async () => {
  const s = setup();
  s.fonts.resolve();
  s.image.reject();
  await flush();
  s.owner.media({ state: 'unavailable', progress: 0 });
  await flush();
  expect(s.root.dataset.startupState).toBe('unavailable');
  expect(s.root.dataset.ready).toBeUndefined();
  s.owner.dispose();
  const pending = setup();
  pending.owner.dispose();
  pending.fonts.resolve();
  pending.image.resolve();
  pending.owner.media({ state: 'video', progress: 1 });
  vi.advanceTimersByTime(STARTUP_DEADLINE);
  await flush();
  expect(pending.root.dataset).toEqual({});
  expect(pending.onReady).not.toHaveBeenCalled();
  expect(vi.getTimerCount()).toBe(0);
});
