import type { MentalWorldRegionId } from "../narrative/regions";

export type SpatialPoint = { readonly x: number; readonly y: number; readonly depth: number };
export type SpatialVector = { readonly x: number; readonly y: number; readonly depth: number };
export type RouteSection = { readonly region: MentalWorldRegionId; readonly points: readonly SpatialPoint[] };
export type CanonicalRoute = { readonly sections: readonly RouteSection[] };

// This geometry is Phase 2 provisional topology scaffolding, not final spatial composition, pacing, camera, Map, or Mark geometry.
export const PROVISIONAL_CANONICAL_ROUTE = {
  sections: [
    { region: "threshold", points: [{ x: 0, y: 0, depth: 0 }, { x: 0.55, y: 0, depth: 0.02 }] },
    { region: "work", points: [{ x: 0.55, y: 0, depth: 0.02 }, { x: 1.10, y: -0.12, depth: 0.14 }, { x: 1.55, y: 0.18, depth: 0.24 }] },
    { region: "writing", points: [{ x: 1.55, y: 0.18, depth: 0.24 }, { x: 1.78, y: 0.68, depth: 0.16 }, { x: 1.60, y: 1.05, depth: 0.08 }] },
    { region: "research", points: [{ x: 1.60, y: 1.05, depth: 0.08 }, { x: 1.18, y: 1.32, depth: -0.04 }, { x: 0.82, y: 1.18, depth: -0.10 }] },
    { region: "architecture", points: [{ x: 0.82, y: 1.18, depth: -0.10 }, { x: 0.45, y: 0.90, depth: -0.22 }, { x: 0.30, y: 0.45, depth: -0.28 }, { x: 0.52, y: 0.08, depth: -0.12 }] },
    { region: "return", points: [{ x: 0.52, y: 0.08, depth: -0.12 }, { x: 0.86, y: 0.30, depth: 0 }, { x: 1.08, y: 0.68, depth: 0.10 }] },
    { region: "person", points: [{ x: 1.08, y: 0.68, depth: 0.10 }, { x: 0.90, y: 0.98, depth: 0.08 }, { x: 0.55, y: 1.08, depth: 0.04 }] },
    { region: "final", points: [{ x: 0.55, y: 1.08, depth: 0.04 }, { x: 0.15, y: 0.88, depth: 0.02 }, { x: -0.12, y: 0.52, depth: 0 }] }
  ]
} as const satisfies CanonicalRoute;

export function sameSpatialPoint(a: SpatialPoint, b: SpatialPoint): boolean {
  return a.x === b.x && a.y === b.y && a.depth === b.depth;
}

export function spatialDistance(a: SpatialPoint, b: SpatialPoint): number {
  return Math.hypot(b.x - a.x, b.y - a.y, b.depth - a.depth);
}

export function flattenCanonicalRoute(route: CanonicalRoute): readonly SpatialPoint[] {
  const points: SpatialPoint[] = [];
  for (const section of route.sections) {
    for (const [index, point] of section.points.entries()) {
      if (index === 0 && points.length > 0 && sameSpatialPoint(points[points.length - 1]!, point)) continue;
      points.push(point);
    }
  }
  return points;
}
