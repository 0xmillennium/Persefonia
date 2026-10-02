import { expect, test } from "vitest";
import { deriveNarrativeState } from "../narrative/derive-narrative-state";
import { flattenCanonicalRoute, PROVISIONAL_CANONICAL_ROUTE, sameSpatialPoint, spatialDistance, type CanonicalRoute } from "./canonical-route";
import { buildTraversedRoutePrefix, sampleCanonicalRoute } from "./route-sampler";

const route = PROVISIONAL_CANONICAL_ROUTE;

test("sampling uses narrative section ownership and exact endpoints", () => {
  const start = sampleCanonicalRoute(deriveNarrativeState(0));
  expect(start.region).toBe("threshold");
  expect(start.position).toEqual(route.sections[0]!.points[0]);
  const boundary = sampleCanonicalRoute(deriveNarrativeState(.125));
  expect(boundary.region).toBe("work");
  expect(boundary.regionProgress).toBe(0);
  expect(boundary.position).toEqual(route.sections[1]!.points[0]);
  const outgoing = route.sections[1]!.points;
  const length = spatialDistance(outgoing[0]!, outgoing[1]!);
  expect(boundary.tangent.x).toBeCloseTo((outgoing[1]!.x - outgoing[0]!.x) / length);
  const end = sampleCanonicalRoute(deriveNarrativeState(1));
  expect(end.region).toBe("final");
  expect(end.position).toEqual(route.sections.at(-1)!.points.at(-1));
  const finalPoints = route.sections.at(-1)!.points;
  const finalLength = spatialDistance(finalPoints.at(-2)!, finalPoints.at(-1)!);
  expect(end.tangent.y).toBeCloseTo((finalPoints.at(-1)!.y - finalPoints.at(-2)!.y) / finalLength);
});

test("unequal segments sample by 3D arc length and internal boundaries use outgoing tangents", () => {
  const custom: CanonicalRoute = { sections: [{ region: "work", points: [
    { x: 0, y: 0, depth: 0 }, { x: 1, y: 0, depth: 0 }, { x: 1, y: 3, depth: 0 }
  ] }] };
  const state = { ...deriveNarrativeState(.125), activeRegionProgress: .5 };
  expect(sampleCanonicalRoute(state, custom).position).toEqual({ x: 1, y: 1, depth: 0 });
  expect(sampleCanonicalRoute({ ...state, activeRegionProgress: .25 }, custom)).toMatchObject({
    position: { x: 1, y: 0, depth: 0 }, tangent: { x: 0, y: 1, depth: 0 }
  });
  expect(sampleCanonicalRoute({ ...state, activeRegionProgress: 0 }, custom).tangent).toEqual({ x: 1, y: 0, depth: 0 });
  expect(sampleCanonicalRoute({ ...state, activeRegionProgress: 1 }, custom).tangent).toEqual({ x: 0, y: 1, depth: 0 });
});

test("representative samples have finite unit tangents", () => {
  for (const progress of [0, .18, .28, .437, .58, .81, 1]) {
    const sample = sampleCanonicalRoute(deriveNarrativeState(progress));
    expect(Object.values(sample.tangent).every(Number.isFinite)).toBe(true);
    expect(Math.hypot(sample.tangent.x, sample.tangent.y, sample.tangent.depth)).toBeCloseTo(1, 12);
  }
});

test("Ariadne prefix begins at origin, ends at its sample, and contains no future geometry", () => {
  const flat = flattenCanonicalRoute(route);
  expect(buildTraversedRoutePrefix(deriveNarrativeState(0))).toEqual([flat[0]]);
  for (const progress of [.18, .28, .437, .58, .81]) {
    const state = deriveNarrativeState(progress);
    const prefix = buildTraversedRoutePrefix(state);
    expect(prefix[0]).toEqual(flat[0]);
    expect(prefix.at(-1)).toEqual(sampleCanonicalRoute(state).position);
    expect(prefix.length).toBeLessThan(flat.length);
    expect(prefix.slice(1).every((point, index) => !sameSpatialPoint(prefix[index]!, point))).toBe(true);
    expect(buildTraversedRoutePrefix(state)).toEqual(prefix);
  }
  const research = buildTraversedRoutePrefix(deriveNarrativeState(.437));
  expect(research).toContainEqual(route.sections[2]!.points.at(-1));
  expect(research).not.toContainEqual(route.sections[3]!.points.at(-1));
  const pastControlPoint = buildTraversedRoutePrefix(deriveNarrativeState(.48));
  expect(pastControlPoint).toContainEqual(route.sections[3]!.points[1]);
  expect(pastControlPoint).not.toContainEqual(route.sections[3]!.points[2]);
  const sectionBoundary = buildTraversedRoutePrefix(deriveNarrativeState(.125));
  expect(sectionBoundary.at(-1)).toEqual(route.sections[1]!.points[0]);
  expect(sectionBoundary).toEqual(route.sections[0]!.points);
  expect(buildTraversedRoutePrefix(deriveNarrativeState(1))).toEqual(flat);
});

test("backscroll re-derives and shortens the prefix", () => {
  buildTraversedRoutePrefix(deriveNarrativeState(0));
  const later = buildTraversedRoutePrefix(deriveNarrativeState(.82));
  const earlier = buildTraversedRoutePrefix(deriveNarrativeState(.28));
  expect(earlier).toEqual(buildTraversedRoutePrefix(deriveNarrativeState(.28)));
  expect(earlier.length).toBeLessThan(later.length);
  expect(earlier).not.toContainEqual(later.at(-1));
});
