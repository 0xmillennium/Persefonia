import { afterEach, expect, it, vi } from 'vitest';
import { galleryView } from './view';
import { galleryIndex } from '../contracts/gallery';
const node = () => ({
  hidden: false,
  inert: false,
  dataset: {} as Record<string, string>,
  style: {
    setProperty(name: string, value: string) {
      Reflect.set(this, name, value);
    },
    removeProperty(name: string) {
      Reflect.deleteProperty(this, name);
    },
  } as unknown as CSSStyleDeclaration,
  contains: () => false,
  removeAttribute: vi.fn(),
});
afterEach(() => vi.unstubAllGlobals());
it.each([0, 1, 8])(
  'projects %i cards into reversible transforms, honest counters and reduced/disposed state',
  (count) => {
    const cards = Array.from({ length: count }, node),
      group = node();
    const category = { ...node(), querySelector: () => group, querySelectorAll: () => cards };
    category.dataset.category = 'projects';
    const output = { value: '' },
      space = { clientWidth: 1200 };
    const root = {
      ...node(),
      querySelectorAll: (s: string) => (s === '.gallery-category' ? [category] : []),
      querySelector: (s: string) =>
        s === '[data-gallery-position]' ? output : s === '.gallery-space' ? space : node(),
    };
    vi.stubGlobal('getComputedStyle', () => ({ width: '260px' }));
    const view = galleryView(root as unknown as HTMLElement, new AbortController().signal);
    const selected = view.groups[0]!;
    view.measureCards(selected);
    expect(view.spacing).toBe(count ? 162 : 1.62 / 2.6);
    const render = (index: number, reduced = false) =>
      view.render(selected, index, index, 0, 0, reduced, true);
    const middle = galleryIndex(count ? 4 / (count + 7) : 0, count);
    render(middle);
    expect(output.value).toBe(count ? `1 / ${count}` : '0 / 0');
    expect(root.dataset.galleryEmpty).toBe(String(!count));
    if (count) {
      expect(cards[0]!.dataset.front).toBe('true');
      expect(cards[0]!.style.transform).toContain('translateX(0px)');
      if (count > 1) expect(cards[1]!.style.transform).toContain('translateX(-162px)');
      const first = cards.map((c) => ({ transform: c.style.transform, front: c.dataset.front }));
      render(count + 3);
      expect(root.dataset.galleryEmpty).toBe('true');
      expect(output.value).toBe(`0 / ${count}`);
      render(middle);
      expect(cards.map((c) => ({ transform: c.style.transform, front: c.dataset.front }))).toEqual(
        first,
      );
      render(middle, true);
      expect(cards.every((c) => c.style.transform === '' && c.style.visibility === 'inherit')).toBe(
        true,
      );
    }
    view.dispose();
    expect(root.dataset.galleryEmpty).toBeUndefined();
    expect(
      cards.every((c) => c.dataset.front === undefined && c.style.transform === undefined),
    ).toBe(true);
    expect(category.inert).toBe(false);
  },
);
