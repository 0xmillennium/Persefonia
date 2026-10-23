import { afterEach, expect, it, vi } from 'vitest';
import { storyViewport } from './viewport';
afterEach(() => vi.unstubAllGlobals());
it('owns a stable large composition while the small story and visible aperture are independent', () => {
  const small = {
    className: '',
    clientWidth: 390,
    clientHeight: 850,
    setAttribute() {},
    remove() {},
  };
  const large = { ...small, clientHeight: 950 };
  const probes = [small, large];
  const values = new Map<string, string>();
  const root = {
    append() {},
    style: {
      setProperty: (k: string, v: string) => values.set(k, v),
      removeProperty: (k: string) => values.delete(k),
    },
  } as unknown as HTMLElement;
  vi.stubGlobal('document', { createElement: () => probes.shift() });
  const viewport = storyViewport(root);
  expect(values.get('--composition-viewport-height')).toBe('950px');
  large.clientHeight = 1050;
  expect(viewport.refresh()).toBe(false);
  expect(values.get('--composition-viewport-height')).toBe('950px');
  small.clientWidth = 850;
  small.clientHeight = 390;
  large.clientHeight = 430;
  expect(viewport.refresh()).toBe(true);
  expect(values.get('--composition-viewport-height')).toBe('430px');
  viewport.dispose();
  expect(values.size).toBe(0);
});
it('keeps story geometry stable during chrome changes and updates real layout changes', () => {
  const probe = {
    className: '',
    clientWidth: 390,
    clientHeight: 720,
    setAttribute() {},
    remove: vi.fn(),
  };
  const values = new Map<string, string>();
  const root = {
    append: vi.fn(),
    style: {
      setProperty: (k: string, v: string) => values.set(k, v),
      removeProperty: (k: string) => values.delete(k),
    },
  } as unknown as HTMLElement;
  const composition = { ...probe, remove: vi.fn() };
  let created = 0;
  vi.stubGlobal('document', { createElement: () => (created++ === 0 ? probe : composition) });
  vi.stubGlobal('innerHeight', 720);
  const viewport = storyViewport(root);
  vi.stubGlobal('innerHeight', 850);
  expect(viewport.refresh()).toBe(false);
  expect(viewport.height).toBe(720);
  probe.clientWidth = 850;
  probe.clientHeight = 390;
  expect(viewport.refresh()).toBe(true);
  expect(viewport.width).toBe(850);
  expect(values.get('--story-viewport-height')).toBe('390px');
  viewport.dispose();
  expect(probe.remove).toHaveBeenCalledOnce();
  expect(composition.remove).toHaveBeenCalledOnce();
  expect(values.size).toBe(0);
});
