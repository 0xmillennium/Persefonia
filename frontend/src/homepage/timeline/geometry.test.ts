import { expect, it } from 'vitest';
import { measureRegions, type SceneRegion } from './geometry';
it('captures immutable actual geometry independently of later layout and owner mutations', () => {
  let height = 1800;
  const region = {
    name: 'gallery',
    top: 0,
    height: 0,
    node: {
      getBoundingClientRect: () => ({ top: 250, height }),
    },
  } as unknown as SceneRegion;
  const original = measureRegions([region], 600);
  height = 2400;
  const resized = measureRegions([region], 800);
  expect(original.gallery).toEqual({ top: 850, height: 1800 });
  expect(resized.gallery).toEqual({ top: 1050, height: 2400 });
  expect(Object.isFrozen(original)).toBe(true);
  expect(Object.isFrozen(original.gallery)).toBe(true);
});
