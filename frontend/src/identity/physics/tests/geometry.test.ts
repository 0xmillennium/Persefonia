import { describe, it, expect } from 'vitest';
import { canonicalGeometry, canonicalContours, validateGeometry } from '../geometry';
import source from '../geometry.json';
import { polygonProperties, closestOnSegment, sub, length, inside } from '../math';
import { createSimulation, stressRun } from './scenarios';
import { readFileSync } from 'node:fs';

describe('OpenDesign canonical geometry', () => {
  it('preserves eight three-corner contours and exact decomposition', () => {
    validateGeometry();
    expect(canonicalGeometry).toHaveLength(8);
    expect(canonicalGeometry.filter((g) => g.parts.length > 1)).toHaveLength(4);
    const core = createSimulation();
    expect(core.getSnapshot().rest).toBe(true);
    expect(core.getSnapshot().pairs.every((p) => p.overlap === 0 && p.gap > 0.65)).toBe(true);
    core.stepFixed(600);
    expect(core.getSnapshot().rest).toBe(true);
    expect(core.energy()).toBe(0);
  });
  it('keeps the no-JS SVG coupled to the canonical contour', () => {
    const svg = readFileSync(
      new URL(
        '../../../../../app/src/main/jte/site/components/physicalIdentity.jte',
        import.meta.url,
      ),
      'utf8',
    );
    for (const g of canonicalGeometry) {
      const points = g.vertices.map((p) => `${p.x},${p.y}`).join(' ');
      expect(svg).toContain(`points="${points}"`);
    }
  });
  it('reuses shared boundaries to close into the parent rectangle by translation only', () => {
    expect(canonicalContours.every((c) => c.corners.length === 3)).toBe(true);
    expect(
      canonicalContours.reduce((sum, c) => sum + polygonProperties(c.points).area, 0),
    ).toBeCloseTo(116 * 160, 8);
    // Every interior probe belongs to exactly one closed fragment. Offset the
    // grid to avoid shared edges; area alone would not rule out holes/overlaps.
    for (let y = -79.73; y < 80; y += 2.31)
      for (let x = -57.81; x < 58; x += 1.97)
        expect(canonicalContours.filter((c) => inside({ x, y }, c.points))).toHaveLength(1);
    for (const c of canonicalContours) {
      const g = canonicalGeometry.find((g) => g.id === c.id)!;
      c.points.forEach((p, i) => {
        expect(g.vertices[i]!.x + g.home.x).toBeCloseTo(p.x + c.translation.x, 10);
        expect(g.vertices[i]!.y + g.home.y).toBeCloseTo(p.y + c.translation.y, 10);
      });
    }
  });
  it('bounds the rendered and collision curve approximation and preserves the approved x width', () => {
    const corner = canonicalContours.find((c) => c.id === 'corner-top-right')!;
    for (const curve of source.quarterCurves)
      for (let i = 0; i <= 1000; i++) {
        const t = i / 1000,
          w = [(1 - t) ** 3, 3 * (1 - t) ** 2 * t, 3 * (1 - t) * t ** 2, t ** 3];
        const p = {
          x: curve.reduce((sum, v, j) => sum + v[0]! * w[j]!, 0),
          y: curve.reduce((sum, v, j) => sum + v[1]! * w[j]!, 0),
        };
        const distance = Math.min(
          ...corner.points.map((a, j) =>
            length(sub(p, closestOnSegment(p, a, corner.points[(j + 1) % corner.points.length]!))),
          ),
        );
        expect(distance).toBeLessThanOrEqual(source.curveTolerance);
      }
    const alpha = (57 * Math.PI) / 180;
    expect(
      2 * canonicalContours.find((c) => c.id === 'inner-right')!.translation.x * Math.sin(alpha),
    ).toBeCloseTo(15.5, 10);
    expect(
      -2 * canonicalContours.find((c) => c.id === 'inner-top')!.translation.y * Math.cos(alpha),
    ).toBeCloseTo(15.5, 10);
  });
  it('replays a seed identically', () => {
    const first = stressRun(1),
      second = stressRun(1);
    expect(first).toEqual(second);
  });
});
