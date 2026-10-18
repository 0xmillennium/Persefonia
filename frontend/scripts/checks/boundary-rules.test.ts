import { expect, it } from 'vitest';
import { featureEdgeAllowed, importCycles } from './boundary-rules.mjs';
it('keeps feature controllers private while admitting narrow shared model contracts', () => {
  expect(featureEdgeAllowed('homepage/film/controller.ts', 'homepage/gallery/controller.ts')).toBe(
    false,
  );
  expect(featureEdgeAllowed('homepage/gallery/controller.ts', 'homepage/film/gestures.ts')).toBe(
    false,
  );
  expect(featureEdgeAllowed('homepage/gallery/motion.ts', 'homepage/timeline/pacing.ts')).toBe(
    true,
  );
  expect(featureEdgeAllowed('homepage/home-experience.ts', 'homepage/film/controller.ts')).toBe(
    true,
  );
});
it('reports a concrete cycle and accepts a shared acyclic dependency', () => {
  expect(
    importCycles(
      new Map([
        ['a', ['b']],
        ['b', ['c']],
        ['c', ['a']],
      ]),
    ),
  ).toEqual([['a', 'b', 'c', 'a']]);
  expect(
    importCycles(
      new Map([
        ['a', ['c']],
        ['b', ['c']],
        ['c', []],
      ]),
    ),
  ).toEqual([]);
});
