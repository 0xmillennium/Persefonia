import type { NarrativeState } from "../narrative/state";
import type { MentalWorldRegionId } from "../narrative/regions";
import { PROVISIONAL_CANONICAL_ROUTE, sameSpatialPoint, spatialDistance, type CanonicalRoute, type RouteSection, type SpatialPoint, type SpatialVector } from "./canonical-route";

export type RouteSample = {
  readonly region: MentalWorldRegionId;
  readonly regionProgress: number;
  readonly position: SpatialPoint;
  readonly tangent: SpatialVector;
};

function sectionFor(state: NarrativeState, route: CanonicalRoute): RouteSection {
  const section = route.sections.find(candidate => candidate.region === state.activeRegion);
  if (!section) throw new Error(`Canonical route has no section for ${state.activeRegion}`);
  return section;
}

function segmentTangent(start: SpatialPoint, end: SpatialPoint, length: number): SpatialVector {
  return { x: (end.x - start.x) / length, y: (end.y - start.y) / length, depth: (end.depth - start.depth) / length };
}

export function sampleCanonicalRoute(state: NarrativeState, route: CanonicalRoute = PROVISIONAL_CANONICAL_ROUTE): RouteSample {
  const section = sectionFor(state, route);
  const lengths = section.points.slice(1).map((point, index) => spatialDistance(section.points[index]!, point));
  const total = lengths.reduce((sum, length) => sum + length, 0);
  if (!Number.isFinite(total) || total <= 0 || lengths.some(length => length <= 0)) throw new RangeError("Canonical route section must have positive finite segments");
  const target = state.activeRegionProgress * total;
  let elapsed = 0;
  for (let index = 0; index < lengths.length; index++) {
    const length = lengths[index]!;
    const start = section.points[index]!;
    const end = section.points[index + 1]!;
    const boundaryTolerance = Number.EPSILON * Math.max(1, total) * 8;
    if (target < elapsed + length - boundaryTolerance || index === lengths.length - 1) {
      const fraction = Math.min(1, Math.max(0, (target - elapsed) / length));
      return {
        region: state.activeRegion,
        regionProgress: state.activeRegionProgress,
        position: fraction === 0 ? start : fraction === 1 ? end : {
          x: start.x + (end.x - start.x) * fraction,
          y: start.y + (end.y - start.y) * fraction,
          depth: start.depth + (end.depth - start.depth) * fraction
        },
        tangent: segmentTangent(start, end, length)
      };
    }
    elapsed += length;
  }
  throw new Error("Canonical route section cannot be sampled");
}

export function buildTraversedRoutePrefix(state: NarrativeState, route: CanonicalRoute = PROVISIONAL_CANONICAL_ROUTE): readonly SpatialPoint[] {
  const sample = sampleCanonicalRoute(state, route);
  const prefix: SpatialPoint[] = [];
  const append = (point: SpatialPoint): void => {
    if (prefix.length === 0 || !sameSpatialPoint(prefix[prefix.length - 1]!, point)) prefix.push(point);
  };
  for (const section of route.sections) {
    if (section.region === state.activeRegion) {
      append(section.points[0]!);
      let elapsed = 0;
      const total = section.points.slice(1).reduce((sum, point, index) => sum + spatialDistance(section.points[index]!, point), 0);
      const target = state.activeRegionProgress * total;
      for (let index = 1; index < section.points.length; index++) {
        elapsed += spatialDistance(section.points[index - 1]!, section.points[index]!);
        if (elapsed <= target) append(section.points[index]!);
      }
      append(sample.position);
      return prefix;
    }
    section.points.forEach(append);
  }
  throw new Error(`Canonical route has no section for ${state.activeRegion}`);
}
