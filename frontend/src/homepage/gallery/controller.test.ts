import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { mountGallery } from './controller';
import { galleryProgress } from '../contracts/gallery';
const f = vi.hoisted(() => ({
  view: undefined as any,
  input: undefined as any,
  callbacks: undefined as any,
  tune: undefined as any,
  finish: vi.fn(),
  tuner: { update: vi.fn(), active: vi.fn(), dispose: vi.fn(), select: vi.fn(), wheel: vi.fn() },
}));
vi.mock('./view', () => ({ galleryView: () => f.view }));
vi.mock('./input', () => ({
  galleryInput: (
    _root: unknown,
    _space: unknown,
    _signal: unknown,
    state: () => unknown,
    callbacks: unknown,
  ) => {
    f.input = state;
    f.callbacks = callbacks;
    return { finish: f.finish, press: null };
  },
}));
vi.mock('./tuner', () => ({
  collectionTuner: (_directory: unknown, select: unknown) => {
    f.tune = select;
    return f.tuner;
  },
}));
beforeEach(() => {
  vi.clearAllMocks();
  vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener() {} }));
  vi.stubGlobal('history', { state: { homepageV2: { category: 'articles' } } });
  vi.stubGlobal('window', new EventTarget());
  vi.stubGlobal('document', Object.assign(new EventTarget(), { hidden: false }));
  vi.stubGlobal('requestAnimationFrame', () => 1);
  vi.stubGlobal('cancelAnimationFrame', vi.fn());
  vi.stubGlobal('scrollY', 0);
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect() {}
    },
  );
  f.view = {
    groups: ['projects', 'articles', 'notes'].map((category, i) => ({
      node: { dataset: { category } },
      group: null,
      cards: Array(i + 2).fill({}),
    })),
    radios: [],
    directory: {},
    space: {},
    spacing: 162,
    measureCards: vi.fn(),
    render: vi.fn(),
    select: vi.fn(() => vi.fn()),
    cancelTransitions: vi.fn(),
    dispose: vi.fn(),
  };
});
afterEach(() => vi.unstubAllGlobals());
it('restores only an eligible category and centers new selections without advancing page travel', () => {
  const position = vi.fn(),
    tune = vi.fn();
  const owner = mountGallery({
    root: { dataset: {} } as HTMLElement,
    position,
    tune,
    restoreCategory: true,
  });
  expect(owner.category).toBe('articles');
  expect(owner.count).toBe(3);
  owner.active(true);
  f.tune('notes', 1);
  expect(owner.category).toBe('notes');
  expect(owner.count).toBe(4);
  expect(f.view.render.mock.calls.at(-1)?.slice(1, 3)).toEqual([0, 0]);
  expect(tune).toHaveBeenCalledOnce();
  expect(position).not.toHaveBeenCalled();
  owner.select('missing');
  expect(owner.category).toBe('notes');
  owner.active(false);
  owner.update(galleryProgress(2, 4));
  expect(f.view.render.mock.calls.at(-1)?.slice(1, 3)).toEqual([2, 2]);
  owner.dispose();
  expect(f.tuner.dispose).toHaveBeenCalledOnce();
  expect(f.view.dispose).toHaveBeenCalledOnce();
});
it('focus and wheel interrupts stop pending motion; a fresh visit uses Projects', () => {
  const owner = mountGallery({ root: { dataset: {} } as HTMLElement, position: vi.fn() });
  expect(owner.category).toBe('projects');
  f.callbacks.focus(1);
  expect(f.input().index).toBe(1);
  owner.wheel();
  expect(f.finish).toHaveBeenLastCalledWith(false, true, expect.any(Number));
  owner.dispose();
  expect(cancelAnimationFrame).toHaveBeenCalled();
});
