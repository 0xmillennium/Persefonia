import { expect, it } from 'vitest';
import { FilmPacing } from './film-pacing';

it('keeps ordinary movement exact and bypasses initial history and direct navigation', () => {
  const pacing = new FilmPacing();
  expect(pacing.sample(600, 0, 5730, 33, 0)).toBe(600);
  expect(pacing.sample(610, 0, 5730, 33, 16)).toBe(610);
  pacing.reset();
  expect(pacing.sample(10000, 0, 5730, 33, 32)).toBe(10000);
  expect(pacing.moving).toBe(false);
});

it('compresses a fast jump, follows reversal and settles within the catch-up deadline', () => {
  const pacing = new FilmPacing();
  pacing.sample(600, 0, 5730, 33, 0);
  const first = pacing.sample(3000, 0, 5730, 33, 16);
  expect(first).toBeGreaterThan(600);
  expect(first).toBeLessThan(800);
  expect(pacing.moving).toBe(true);
  expect(pacing.sample(500, 0, 5730, 33, 32)).toBeLessThan(first);
  let y = 0;
  for (let now = 48; now <= 288; now += 16) y = pacing.sample(500, 0, 5730, 33, now);
  expect(y).toBe(500);
  expect(pacing.moving).toBe(false);
});

it('uses measured decoder time and visits the film boundary before releasing the gallery', () => {
  const fast = new FilmPacing();
  const slow = new FilmPacing();
  fast.sample(1000, 0, 5730, 17, 0);
  slow.sample(1000, 0, 5730, 100, 0);
  expect(slow.sample(1300, 0, 5730, 100, 16)).toBeLessThan(fast.sample(1300, 0, 5730, 17, 16));
  slow.reset();
  slow.sample(5700, 0, 5730, 33, 0);
  const positions: number[] = [];
  for (let now = 16; now <= 272; now += 16) positions.push(slow.sample(10000, 0, 5730, 33, now));
  expect(positions).toContain(5730);
  expect(positions.indexOf(5730)).toBeLessThan(positions.indexOf(10000));
  expect(positions.at(-1)).toBe(10000);
  expect(slow.moving).toBe(false);
});
