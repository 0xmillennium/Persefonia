import { describe, expect, it } from 'vitest';
import {
  cameraFor,
  dot,
  geographicPoint,
  orientationMatrix,
  projectPoint,
  sphereIntersection,
  transformPoint,
} from '../geometry';
import { LAND_CANDIDATES, WATER_CANDIDATES, surfaceSamples } from '../sampling.mjs';

describe('geographic surface and camera', () => {
  it('uses east-positive longitude and a north pole', () => {
    expect(geographicPoint(0, 0)).toEqual([0, 0, 1]);
    expect(geographicPoint(0, 90)[0]).toBeCloseTo(1);
    expect(geographicPoint(90, 0)[1]).toBe(1);
    const istanbul = geographicPoint(41.0082, 28.9784);
    expect(istanbul[0]).toBeCloseTo(0.3654, 3);
    expect(istanbul[1]).toBeCloseTo(0.6562, 3);
    expect(dot(istanbul, istanbul)).toBeCloseTo(1);
  });
  it('projects the front intersection back to the exact pointer across aspect ratios', () => {
    for (const [w, h] of [
      [852, 778],
      [390, 356],
      [1200, 600],
    ]) {
      const camera = cameraFor(w!, h!);
      for (const [dx, dy] of [
        [0, 0],
        [0.7, 0.2],
        [-0.3, 0.8],
        [0.99, 0],
      ]) {
        const x = w! / 2 + dx! * camera.radius,
          y = h! / 2 + dy! * camera.radius;
        const hit = sphereIntersection(x, y, camera)!;
        expect(dot(hit, hit)).toBeCloseTo(1);
        expect(projectPoint(hit, camera).x).toBeCloseTo(x);
        expect(projectPoint(hit, camera).y).toBeCloseTo(y);
        expect(projectPoint(hit, camera).facing).toBeGreaterThan(0);
      }
      expect(sphereIntersection(w! / 2 + camera.radius + 1, h! / 2, camera)).toBeNull();
    }
  });
  it('classifies the camera-facing hemisphere with perspective horizon', () => {
    const camera = cameraFor(852, 778);
    expect(projectPoint([0, 0, 1], camera).facing).toBeGreaterThan(0);
    expect(projectPoint([0, 0, -1], camera).facing).toBeLessThan(0);
    expect(projectPoint([1, 0, 0], camera).facing).toBeLessThan(0);
  });
  it('shares shader matrix and CPU transforms, retaining the intrinsic pole', () => {
    for (const pitch of [-1, 0, 0.7]) {
      const pole = transformPoint([0, 1, 0], { pitch, spin: 0 });
      for (const spin of [-9, 0.4, 20]) {
        const spunPole = transformPoint([0, 1, 0], { pitch, spin });
        for (let axis = 0; axis < 3; axis++) expect(spunPole[axis]).toBeCloseTo(pole[axis]!, 12);
        const input = geographicPoint(41.0082, 28.9784);
        const matrix = orientationMatrix({ pitch, spin });
        const expected = transformPoint(input, { pitch, spin });
        for (let row = 0; row < 3; row++) {
          expect(
            matrix[row]! * input[0] + matrix[row + 3]! * input[1] + matrix[row + 6]! * input[2],
          ).toBeCloseTo(expected[row]!, 6);
        }
      }
    }
  });
  it('uses independent equal-area land and ocean candidates without latitude bands', () => {
    const samples = surfaceSamples();
    expect(samples).toHaveLength(LAND_CANDIDATES + WATER_CANDIDATES);
    expect(
      samples.every(
        (p) => p.latitude > -90 && p.latitude < 90 && p.longitude >= -180 && p.longitude < 180,
      ),
    ).toBe(true);
    for (const [surface, count] of [
      ['land', LAND_CANDIDATES],
      ['water', WATER_CANDIDATES],
    ] as const) {
      const points = samples.filter((p) => p.surface === surface);
      expect(points).toHaveLength(count);
      expect(new Set(points.map((p) => p.latitude)).size).toBe(count);
      // Equal-height bands on a unit sphere have equal area.
      const bands = new Array<number>(10).fill(0);
      for (const point of points) {
        const height = (Math.sin((point.latitude * Math.PI) / 180) + 1) / 2;
        bands[Math.min(9, Math.floor(height * 10))]!++;
      }
      expect(Math.max(...bands) - Math.min(...bands)).toBeLessThanOrEqual(1);
    }
    expect(surfaceSamples()).toEqual(samples);
  });
});
