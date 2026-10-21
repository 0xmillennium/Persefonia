import { expect, it } from 'vitest';
import { Hold } from '../hold/state';
import { HoldMaterial } from '../hold/material';
import { radiusFor } from '../rendering/shaders';

it('anchors aperture geometry independently at a 3-4-5 corner and at the stage center', () => {
  expect(radiusFor(0, { x: 0, y: 0 }, 3, 4)).toBe(-1);
  expect(radiusFor(1, { x: 0, y: 0 }, 3, 4)).toBeCloseTo(5.9, 12);
  expect(radiusFor(1, { x: 1.5, y: 2 }, 3, 4)).toBeCloseTo(3.4, 12);
});

it('anchors interrupted progress, generation retention, local bounds and complete cancellation', () => {
  const hold = new Hold();
  const material = new HoldMaterial(1440, 900);
  hold.press({ x: 202, y: 558 }, 'mouse');
  hold.advance(1.5);
  expect(hold.progress).toBeCloseTo(0.5, 12);
  material.step(hold);
  hold.release();
  hold.advance(0.08);
  expect(hold.progress).toBeCloseTo(0.4424, 12);
  const generation = hold.generation;
  hold.press({ x: 1267, y: 288 }, 'mouse');
  expect(hold.generation).toBe(generation);
  for (let i = 0; i < 10; i++) {
    hold.advance(0.016);
    material.step(hold);
  }
  // Independent conservative envelope: <700px at this specific pose/time, no radiusFor/HOLD oracle.
  let open = 0;
  for (let y = 0; y < material.height; y++)
    for (let x = 0; x < material.width; x++) {
      if (material.pixels[(y * material.width + x) * 4]! <= 127) continue;
      open++;
      expect(
        Math.hypot(
          ((x + 0.5) / material.width) * 1440 - hold.center.x,
          ((y + 0.5) / material.height) * 900 - hold.center.y,
        ),
      ).toBeLessThan(700);
    }
  expect(open).toBeGreaterThan(0);
  hold.release();
  for (let i = 0; i < 120; i++) {
    hold.advance(0.016);
    material.step(hold);
  }
  expect(hold.phase).toBe('IDLE');
  expect(hold.progress).toBe(0);
  expect(material.pixels.some((value, i) => i % 4 === 0 && value > 0)).toBe(false);
});
