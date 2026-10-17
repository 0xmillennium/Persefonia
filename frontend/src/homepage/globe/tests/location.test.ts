import { describe, expect, it } from 'vitest';
import { cameraFor } from '../geometry';
import { ISTANBUL, LOCATIONS, LocationProximity, projectLocation } from '../location';

describe('one geographically attached location', () => {
  it('configures only Istanbul and projects with globe inclination and spin', () => {
    expect(LOCATIONS).toHaveLength(1);
    expect(ISTANBUL.name).toBe('Istanbul, Türkiye');
    expect(ISTANBUL.latitude).toBe(41.0082);
    expect(ISTANBUL.longitude).toBe(28.9784);
    const camera = cameraFor(852, 778);
    const front = projectLocation(
      ISTANBUL,
      { pitch: 0.3, spin: (-ISTANBUL.longitude * Math.PI) / 180 },
      camera,
    );
    expect(front.x).toBeCloseTo(426);
    expect(front.facing).toBeGreaterThan(0);
    const rear = projectLocation(
      ISTANBUL,
      { pitch: 0.3, spin: Math.PI - (ISTANBUL.longitude * Math.PI) / 180 },
      camera,
    );
    expect(rear.facing).toBeLessThan(0);
    expect(projectLocation(ISTANBUL, { pitch: -0.3, spin: 0 }, camera).y).not.toBe(front.y);
  });
  it('uses distance and horizon hysteresis without chattering', () => {
    const p = new LocationProximity();
    expect(p.update(1, 40, true, false)).toBe(false);
    expect(p.update(1, 30, true, false)).toBe(true);
    for (const distance of [33, 31, 39, 50]) expect(p.update(1, distance, true, false)).toBe(true);
    expect(p.update(1, 53, true, false)).toBe(false);
    expect(p.update(0.15, 20, true, false)).toBe(true);
    expect(p.update(0.05, 20, true, false)).toBe(false);
    expect(p.update(0.15, 20, true, false)).toBe(false);
    expect(p.update(0.23, 20, true, false)).toBe(true);
  });
  it('withdraws on rotating away, sphere miss, press or far-side pin', () => {
    const p = new LocationProximity();
    for (const [facing, distance, hit, pressed, pinned] of [
      [1, 60, true, false, false],
      [1, 20, false, false, false],
      [1, 20, true, true, true],
      [-1, 0, true, false, true],
    ] as const) {
      p.update(1, 20, true, false);
      expect(p.update(facing, distance, hit, pressed, pinned)).toBe(false);
    }
    expect(p.update(1, Infinity, false, false, true)).toBe(true);
  });
});
