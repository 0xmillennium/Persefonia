import { expect, it } from 'vitest';
import { GlobeHighlight } from '../highlight';

it('keeps hover feedback consistent across refresh rates and settles to idle', () => {
  const strengths = [60, 120, 144].map((rate) => {
    const highlight = new GlobeHighlight();
    for (let i = 0; i < rate / 2; i++) highlight.step([1, 0, 0], 1 / rate, false);
    const strength = highlight.strength;
    expect(strength).toBeGreaterThan(0.99);
    for (let i = 0; i < rate * 2; i++) highlight.step(null, 1 / rate, false);
    expect(highlight.strength).toBe(0);
    expect(highlight.moving).toBe(false);
    expect(highlight.point).toEqual([1, 0, 0]);
    return strength;
  });
  expect(Math.max(...strengths) - Math.min(...strengths)).toBeLessThan(0.001);
});

it('uses immediate static feedback with reduced motion', () => {
  const highlight = new GlobeHighlight();
  highlight.step([0, 1, 0], 0, true);
  expect(highlight.strength).toBe(1);
  expect(highlight.moving).toBe(false);
  highlight.step(null, 0, true);
  expect(highlight.strength).toBe(0);
  expect(highlight.moving).toBe(false);
});
