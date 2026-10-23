import { afterEach, expect, it, vi } from 'vitest';
import { firstWriting, writingPull } from './first';
import { secondWriting } from './second';
import { laterWriting } from './later';
import { WritingEntrance } from './entrance';

// A write-only DOM double: this verifies controller ownership, never CSS paint.
class NodeDouble {
  style = Object.assign({} as Record<string, string>, {
    removeProperty(name: string) {
      Reflect.deleteProperty(
        this,
        name.replace(/-([a-z])/g, (_, c: string) => c.toUpperCase()),
      );
    },
  });
  dataset: Record<string, string> = {};
  attributes: Record<string, string> = {};
  children: NodeDouble[] = [];
  hidden = false;
  removed = false;
  id = '';
  textContent = '';
  parentNode: NodeDouble | null = null;
  firstChild: { nodeType: number; textContent: string } | null = null;
  childNodes: unknown[] = [];
  querySelector = (_: string): unknown => null;
  querySelectorAll = (_: string): unknown[] => [];
  setAttribute(name: string, value: string) {
    this.attributes[name] = value;
  }
  removeAttribute(name: string) {
    delete this.attributes[name];
  }
  append(...nodes: NodeDouble[]) {
    this.children.push(...nodes);
    for (const node of nodes) node.parentNode = this;
  }
  prepend(node: NodeDouble) {
    this.children.unshift(node);
  }
  before = vi.fn();
  remove() {
    this.removed = true;
  }
  replaceChildren(...nodes: NodeDouble[]) {
    this.children = nodes;
  }
  getBoundingClientRect() {
    return { left: 0, top: 0, width: 200, height: 60 };
  }
  animate = vi.fn((_frames: unknown, _options: { delay?: number }) => ({
    pause: vi.fn(),
    cancel: vi.fn(),
    currentTime: 0,
  }));
}
afterEach(() => vi.unstubAllGlobals());
function documentDouble() {
  const created: NodeDouble[] = [];
  const create = () => {
    const node = new NodeDouble();
    created.push(node);
    return node;
  };
  vi.stubGlobal('document', {
    createElement: create,
    createElementNS: create,
    createRange: () => ({
      setStart() {},
      setEnd() {},
      getBoundingClientRect: () => ({ left: 0, top: 0, width: 10 }),
    }),
  });
  vi.stubGlobal('getComputedStyle', () => ({ fontSize: '60px' }));
  vi.stubGlobal(
    'Node',
    class {
      static TEXT_NODE = 3;
    },
  );
  const disconnect = vi.fn();
  vi.stubGlobal(
    'MutationObserver',
    class {
      observe() {}
      disconnect = disconnect;
    },
  );
  return { created, disconnect };
}
it('preserves original first-writing character pull, reversal, semantic fallback and cleanup', () => {
  const env = documentDouble();
  const rows = Array.from({ length: 2 }, () => {
    const slot = new NodeDouble(),
      text = new NodeDouble();
    text.textContent = 'ABC';
    text.firstChild = { nodeType: 3, textContent: 'ABC' };
    text.childNodes = [text.firstChild];
    text.parentNode = slot;
    slot.querySelector = () => text;
    return { slot, text };
  });
  const root = { querySelectorAll: () => rows.map((r) => r.slot) } as unknown as HTMLElement;
  const owner = firstWriting(root);
  owner.measure();
  owner.update(0.5, false);
  const snapshot = () =>
    rows.map((r) => r.slot.children[0]!.children.map((n) => n.style.transform));
  const partial = snapshot();
  expect(partial[0]![0]).not.toBe(partial[0]![2]);
  expect(partial[0]![0]).not.toBe(partial[1]![0]);
  owner.update(0.8, false);
  expect(snapshot()).not.toEqual(partial);
  owner.update(0.5, false);
  expect(snapshot()).toEqual(partial);
  expect(writingPull(0.5, 0, 0)).toBeGreaterThan(writingPull(0.5, 1, 0));
  // Foreign owners may wrap or split semantic text. Decoration must yield safely.
  rows[0]!.text.childNodes = [{}, {}];
  owner.measure();
  owner.update(0.5, false);
  expect(rows[0]!.text.style.opacity).toBe('');
  expect(rows[0]!.slot.children[0]!.hidden).toBe(true);
  owner.update(0.5, true);
  expect(rows.every((r) => r.text.style.opacity === '')).toBe(true);
  owner.dispose();
  expect(env.disconnect).toHaveBeenCalledOnce();
  expect(rows.every((r) => r.slot.children[0]!.removed)).toBe(true);
});
it('projects Writing B word goo and clipped exits through interruption, reverse, reduced motion and disposal', () => {
  const env = documentDouble(),
    paragraph = new NodeDouble();
  const words = Array.from({ length: 6 }, () => {
    const word = new NodeDouble(),
      text = new NodeDouble();
    word.querySelector = () => text;
    return { word, text };
  });
  paragraph.querySelectorAll = () => words.map((r) => r.word);
  const root = { querySelector: () => paragraph } as unknown as HTMLElement;
  const owner = secondWriting(root),
    entrance = new WritingEntrance(1000);
  owner.measure();
  owner.update(0.1, 0, false);
  expect(words[0]!.word.style.filter).toContain('url(#second-writing-');
  entrance.sample(0.1, 0, 0);
  owner.update(entrance.sample(0.1, 0, 1100), 0, false);
  expect(words.every((r) => r.word.style.filter === '')).toBe(true);
  owner.update(entrance.sample(1, 0.65, 1200), 0.65, false);
  const exit = words.map((r) => ({ ...r.text.style }));
  expect(words[0]!.text.style.opacity).toBe('0');
  expect(words.at(-1)!.text.style.opacity).toBe('');
  entrance.suspend();
  owner.update(entrance.sample(1, 0.65, 50000), 0.65, false);
  expect(words.map((r) => ({ ...r.text.style }))).toEqual(exit);
  owner.update(entrance.sample(1, 0.2, 50100), 0.2, false);
  expect(words.map((r) => ({ ...r.text.style }))).not.toEqual(exit);
  owner.update(1, 0.65, false);
  expect(words.map((r) => ({ ...r.text.style }))).toEqual(exit);
  owner.update(1, 0, true);
  expect(words.every((r) => r.text.style.transform === undefined)).toBe(true);
  owner.dispose();
  expect(env.created[0]!.removed).toBe(true);
  expect(
    words.every((r) => r.word.style.filter === undefined && r.text.style.willChange === undefined),
  ).toBe(true);
});
it('completes the original eight logo slots, prioritizes authored exits and releases animations', () => {
  const env = documentDouble(),
    svg = new NodeDouble();
  const polygons = Array.from({ length: 8 }, (_, i) =>
    Object.assign(new NodeDouble(), {
      getBBox: () => ({ y: i * 10, height: 20 + i }),
      transform: { baseVal: { consolidate: () => ({ matrix: { e: i, f: 0 } }) } },
    }),
  );
  svg.querySelectorAll = () => polygons;
  const third = [new NodeDouble(), new NodeDouble()],
    fourth = [new NodeDouble(), new NodeDouble()];
  const root = {
    querySelector: () => svg,
    querySelectorAll: (s: string) => (s.startsWith('.capabilities') ? third : fourth),
  } as unknown as HTMLElement;
  const owner = laterWriting(root);
  owner.fourth(0.2, 0, 0.2, false);
  const rises = env.created.filter((n) => n.animate.mock.calls.length > 0);
  expect(rises).toHaveLength(8);
  expect(new Set(rises.map((n) => n.animate.mock.calls[0]![1]?.delay)).size).toBe(8);
  owner.fourth(1, 0, 1, false);
  expect(owner.settled).toBe(true);
  expect(rises.every((n) => n.style.transform === undefined)).toBe(true);
  owner.fourth(1, 0.6, 1, false);
  const exit = rises.map((n) => n.style.transform);
  expect(exit.every((v) => v?.startsWith('translateY('))).toBe(true);
  owner.fourth(1, 0.2, 1, false);
  expect(rises.map((n) => n.style.transform)).not.toEqual(exit);
  owner.fourth(1, 0.6, 1, false);
  expect(rises.map((n) => n.style.transform)).toEqual(exit);
  owner.third(0.2, 0, false);
  expect(third[0]!.style.transform).not.toBe(third[1]!.style.transform);
  owner.fourth(1, 0, 1, true);
  expect(rises.every((n) => n.style.transform === undefined)).toBe(true);
  owner.dispose();
  expect(polygons.every((p) => p.before.mock.calls.length === 1)).toBe(true);
  expect(third.every((n) => n.style.transform === undefined)).toBe(true);
});
