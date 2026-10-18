import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { expect, it } from 'vitest';
import { surfaceSamples } from '../sampling.mjs';

it('retains pinned geographic source and real land/ocean classification fixtures', () => {
  const source = readFileSync(new URL('../assets/ne_110m_land.geojson', import.meta.url));
  expect(createHash('sha256').update(source).digest('hex')).toBe(
    '9e0729ee253ca7d7a5c4ae9395fb1902264c5377c52e224d13dd85010e2835d9',
  );
  const mask = readFileSync(new URL('../assets/land-mask.bin', import.meta.url));
  const samples = surfaceSamples();
  expect(mask.length).toBe(Math.ceil(samples.length / 8));
  const retained = { land: 0, water: 0 };
  samples.forEach((point, i) => {
    const land = !!((mask[i >> 3]! >> (i & 7)) & 1);
    if ((point.surface === 'land') === land) retained[land ? 'land' : 'water']++;
  });
  expect(retained.land).toBeGreaterThan(8000);
  expect(retained.land).toBeLessThan(10000);
  expect(retained.water).toBeGreaterThan(5000);
  expect(retained.water).toBeLessThan(7000);
  expect(retained.water).toBeLessThan(retained.land);
  for (const [latitude, longitude, land] of [
    [23, 15, 1],
    [40, -100, 1],
    [-25, 135, 1],
    [0, -130, 0],
    [0, -30, 0],
  ] as const) {
    let nearest = 0,
      distance = Infinity;
    samples.forEach((p, i) => {
      const candidate = (p.latitude - latitude) ** 2 + (p.longitude - longitude) ** 2;
      if (candidate < distance) {
        nearest = i;
        distance = candidate;
      }
    });
    expect((mask[nearest >> 3]! >> (nearest & 7)) & 1).toBe(land);
  }
});
