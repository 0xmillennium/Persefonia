import { expect, it } from 'vitest';
import { frameAt, FRAME_COUNT } from '../model';
it('maps finite progress to the authored frame endpoints', () => {
  expect(frameAt(0)).toBe(0);
  expect(frameAt(1)).toBe(191);
  expect(frameAt(-2)).toBe(0);
  expect(frameAt(2)).toBe(191);
  expect(frameAt(NaN)).toBe(0);
});
it('maps every pose reversibly without accumulating state', () => {
  const path = Array.from({ length: FRAME_COUNT }, (_, i) => i / (FRAME_COUNT - 1));
  expect(path.map(frameAt)).toEqual(Array.from({ length: FRAME_COUNT }, (_, i) => i));
  expect(path.reverse().map(frameAt)).toEqual(
    Array.from({ length: FRAME_COUNT }, (_, i) => FRAME_COUNT - 1 - i),
  );
  for (let n = 0; n < 100; n++) expect(frameAt(0.35)).toBe(67);
});
