import { afterEach, expect, it, vi } from 'vitest';
import { mountFooter } from './controller';

const node = () => ({
  childNodes: [] as unknown[],
  inert: false,
  style: {
    transform: '',
    opacity: '',
    removeProperty(name: string) {
      Reflect.deleteProperty(this, name);
    },
  },
  classList: { add: vi.fn(), remove: vi.fn() },
  append(...children: unknown[]) {
    this.childNodes.push(...children);
  },
  replaceChildren(...children: unknown[]) {
    this.childNodes = children;
  },
  contains: () => false,
});
afterEach(() => vi.unstubAllGlobals());
it('composes staggered footer row writes, reverse ownership, reduced motion and disposal', () => {
  const menu = [node(), node()],
    details = [node()],
    globe = node(),
    strip = node(),
    rises: ReturnType<typeof node>[] = [];
  vi.stubGlobal('document', {
    activeElement: null,
    createElement: () => {
      const n = node();
      rises.push(n);
      return n;
    },
  });
  const root = {
    querySelectorAll: (s: string) => (s === '.location-links nav a' ? menu : details),
    querySelector: (s: string) => (s === '.location-system' ? globe : strip),
  } as unknown as HTMLElement;
  const controller = mountFooter(root);
  controller.update(0, false, 0);
  expect(rises[0]!.style).toMatchObject({ transform: 'translateY(100%)', opacity: '0' });
  expect([...menu, ...details].every((n) => n.inert)).toBe(true);
  controller.update(1, false, 1);
  expect(
    rises.every((n) => n.style.transform === 'translateY(0%)' && n.style.opacity === '1'),
  ).toBe(true);
  expect([...menu, ...details, globe, strip].every((n) => !n.inert)).toBe(true);
  controller.update(0.4, false, 0.2);
  expect(rises[0]!.style.transform).not.toBe('translateY(0%)');
  expect(rises[0]!.style.transform).not.toBe(rises[1]!.style.transform);
  expect(menu[0]!.inert).toBe(true);
  expect(globe.inert).toBe(true);
  controller.update(0, true, 0);
  expect([...menu, ...details, globe, strip].every((n) => !n.inert)).toBe(true);
  expect(rises.every((n) => n.style.transform === undefined)).toBe(true);
  controller.dispose();
  expect(
    [...menu, ...details].every((n) => !n.inert && n.classList.remove.mock.calls.length === 1),
  ).toBe(true);
});
