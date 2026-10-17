import { expect, it } from 'vitest';
import { pointerScale } from './pointer-scale';
it('keeps hover/press response consistent at 60, 120 and 144 Hz', () => {
  const results = [60, 120, 144].map((rate) => {
    let scale = 1;
    for (let frame = 0; frame < rate / 2; frame++) scale = pointerScale(scale, 1.5, 1 / rate);
    for (let frame = 0; frame < rate / 2; frame++) scale = pointerScale(scale, 0.7, 1 / rate);
    return scale;
  });
  expect(results[0]).toBeCloseTo(results[1]!, 12);
  expect(results[0]).toBeCloseTo(results[2]!, 12);
  expect(results[0]).toBeGreaterThan(0.7);
  expect(results[0]).toBeLessThan(1);
});
