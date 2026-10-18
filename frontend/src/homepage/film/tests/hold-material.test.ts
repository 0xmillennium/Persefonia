import { expect, it } from 'vitest';
import { Hold } from '../hold/state';
import { HoldMaterial } from '../hold/material';
import { radiusFor } from '../rendering/shaders';

function advance(hold: Hold, material: HoldMaterial, frames: number) {
  for (let frame = 0; frame < frames; frame++) {
    hold.step(1 / 60, false, material.coverage);
    material.step(hold);
  }
}

function outsideAperture(hold: Hold, material: HoldMaterial) {
  const radius = radiusFor(
    hold.expansion ** 1.6,
    hold.center,
    material.stageWidth,
    material.stageHeight,
  );
  // Conservative envelope includes the old material's permitted noise and a cell of AA.
  const limit =
    radius * 1.12 +
    Math.hypot(material.stageWidth, material.stageHeight) * 0.045 +
    material.stageWidth / material.width;
  let outside = 0;
  for (let y = 0; y < material.height; y++)
    for (let x = 0; x < material.width; x++) {
      if (material.pixels[(y * material.width + x) * 4]! <= 127) continue;
      const distance = Math.hypot(
        ((x + 0.5) / material.width) * material.stageWidth - hold.center.x,
        ((y + 0.5) / material.height) * material.stageHeight - hold.center.y,
      );
      if (distance > limit) outside++;
    }
  return outside;
}

it.each([
  [1440, 900, 100, 400, 1320, 500],
  [1440, 900, 720, 100, 720, 790],
  [1440, 900, 150, 740, 1290, 150],
  [390, 850, 30, 750, 350, 120],
])('bounds interrupted growth when retargeting across %i×%i', (width, height, ax, ay, bx, by) => {
  const hold = new Hold(),
    material = new HoldMaterial(width, height, 96);
  hold.press({ x: ax, y: ay }, 'mouse');
  advance(hold, material, 90);
  hold.release();
  advance(hold, material, 5);
  const progress = hold.progress,
    expansion = hold.expansion;
  hold.press({ x: bx, y: by }, 'mouse');
  expect(hold.progress).toBe(progress);
  expect(hold.expansion).toBe(expansion);
  const violations: number[] = [];
  for (let frame = 0; frame < 30; frame++) {
    advance(hold, material, 1);
    violations.push(outsideAperture(hold, material));
  }
  expect(Math.max(...violations)).toBe(0);
  expect(material.generations).toBe(1);
});

it('has the same local shape at the same pose regardless of the path through screen edges', () => {
  const render = (path: readonly { x: number; y: number }[]) => {
    const hold = new Hold(),
      material = new HoldMaterial(1200, 800, 96);
    hold.press({ x: 120, y: 680 }, 'mouse');
    hold.expansion = 0.42;
    material.step(hold);
    for (const point of path) {
      hold.center = { ...point };
      material.step(hold);
    }
    hold.center = { x: 700, y: 400 };
    material.step(hold);
    return material.distances;
  };
  expect(
    render([
      { x: 1180, y: 10 },
      { x: -200, y: 1000 },
      { x: 400, y: -200 },
    ]),
  ).toEqual(render([]));
});

it('a later gesture restores its local material instead of inheriting transported terrain', () => {
  const hold = new Hold(),
    material = new HoldMaterial(1200, 800, 96);
  const seed = { x: 120, y: 680 };
  hold.press(seed, 'mouse');
  hold.expansion = 0.42;
  material.step(hold);
  const first = material.distances.slice();
  for (const point of [
    { x: 1180, y: 10 },
    { x: -200, y: 1000 },
  ]) {
    hold.center = point;
    material.step(hold);
  }
  hold.reset();
  material.step(hold);
  hold.press(seed, 'mouse');
  hold.expansion = 0.42;
  material.step(hold);
  expect(material.distances).toEqual(first);
  expect(material.generations).toBe(2);
});

it('keeps one bounded material through repeated corner and offscreen interruptions', () => {
  const hold = new Hold(),
    material = new HoldMaterial(1200, 800, 96);
  hold.press({ x: 600, y: 400 }, 'mouse');
  advance(hold, material, 72);
  for (let cycle = 0; cycle < 12; cycle++) {
    hold.release();
    advance(hold, material, 5);
    const point = [
      { x: 10, y: 10 },
      { x: 1190, y: 790 },
      { x: -200, y: 900 },
      { x: 1000, y: -100 },
    ][cycle % 4]!;
    hold.press(point, 'mouse');
    for (let frame = 0; frame < 15; frame++) {
      advance(hold, material, 1);
      expect(outsideAperture(hold, material)).toBe(0);
    }
  }
  expect(material.generations).toBe(1);
  expect(hold.cycles).toBe(0);
  expect(hold.phase).toBe('GROWING');
});

it('recognizes a new gesture even when reset and re-press happen between rendered frames', () => {
  const hold = new Hold(),
    material = new HoldMaterial(1200, 800, 96);
  hold.press({ x: 100, y: 300 }, 'mouse');
  hold.expansion = 0.3;
  material.step(hold);
  const generation = hold.generation;
  hold.reset();
  hold.press({ x: 900, y: 500 }, 'mouse');
  hold.expansion = 0.3;
  material.step(hold);
  expect(hold.generation).toBe(generation + 1);
  expect(material.generations).toBe(2);
  const freshHold = new Hold(),
    freshMaterial = new HoldMaterial(1200, 800, 96);
  freshHold.press({ x: 900, y: 500 }, 'mouse');
  freshHold.expansion = 0.3;
  freshMaterial.step(freshHold);
  expect(material.distances).toEqual(freshMaterial.distances);
});
