import { describe, it, expect } from 'vitest';
import { absorbedPigment, illuminatedPigment, pigmentBands } from '../rendering/pigment';
describe('Pigment material', () => {
  it('absorbs a pale film instead of adding clipped fluorescent light', () => {
    for (const source of [0, 0.04, 0.5, 1])
      for (const pigment of [0.005, 0.08, 0.25, 0.85])
        for (const absorption of [0.35, 0.72, 0.65]) {
          const target = absorbedPigment(source, pigment, absorption);
          expect(target).toBeGreaterThanOrEqual(0);
          expect(target).toBeLessThan(1);
        }
    expect(absorbedPigment(1, 0.85, 0.72)).toBeLessThan(1);
    expect(absorbedPigment(0, 0.85, 0.72)).toBeGreaterThan(0.5);
  });
  it('seeds spatial color variation along a stroke rather than identical transverse stripes', () => {
    const a = pigmentBands(200, 350, 0),
      b = pigmentBands(600, 350, 0);
    expect(a).not.toEqual(b);
    for (const value of [...a, ...b]) {
      expect(value).toBeGreaterThanOrEqual(0);
      expect(value).toBeLessThanOrEqual(1);
    }
  });
  it('illuminates dark and bright imagery without clipping channel detail', () => {
    for (const source of [0, 0.04, 0.5, 0.9, 1])
      for (const pigment of [0, 0.1, 0.5, 0.95, 1])
        for (const absorption of [0.35, 0.72, 0.65])
          for (const light of [0.4, 0.65, 0.9]) {
            const color = illuminatedPigment(source, pigment, absorption, light);
            expect(color).toBeGreaterThanOrEqual(0);
            expect(color).toBeLessThanOrEqual(1);
            expect(color).toBeGreaterThanOrEqual(absorbedPigment(source, pigment, absorption));
          }
    expect(illuminatedPigment(0.9, 0.5, 0.72, 0.8)).toBeLessThan(
      illuminatedPigment(1, 0.5, 0.72, 0.8),
    );
    expect(illuminatedPigment(0, 0.95, 0.72, 0.8)).toBeGreaterThan(0.8);
  });
});
