import { afterEach, expect, it, vi } from 'vitest';
import { storyViewport } from './viewport';
afterEach(() => vi.unstubAllGlobals());
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
  vi.stubGlobal('document', { createElement: () => probe });
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
  expect(values.size).toBe(0);
});
