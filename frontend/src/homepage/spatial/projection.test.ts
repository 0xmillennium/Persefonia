import { expect, test } from "vitest";
import { flattenCanonicalRoute, PROVISIONAL_CANONICAL_ROUTE } from "./canonical-route";
import { deriveViewportProjection, projectSpatialPointToPlane, projectSpatialPointToViewport } from "./projection";
import { sampleCanonicalRoute } from "./route-sampler";
import { deriveNarrativeState } from "../narrative/derive-narrative-state";

const route = flattenCanonicalRoute(PROVISIONAL_CANONICAL_ROUTE);

test("oblique projection uses the exact provisional coefficients", () => {
  expect(projectSpatialPointToPlane({ x: 2, y: 3, depth: 4 })).toEqual({ x: 3.4, y: 2.2 });
});

test("one centered uniform transform fits the route and current position with padding", () => {
  const original = structuredClone(route);
  const projection = deriveViewportProjection(route, 640, 360);
  expect(projection).toEqual(deriveViewportProjection(route, 640, 360));
  const points = route.map(point => projectSpatialPointToViewport(point, projection));
  for (const point of points) {
    expect(Number.isFinite(point.x) && Number.isFinite(point.y)).toBe(true);
    expect(point.x).toBeGreaterThanOrEqual(64 - 1e-9);
    expect(point.x).toBeLessThanOrEqual(576 + 1e-9);
    expect(point.y).toBeGreaterThanOrEqual(36 - 1e-9);
    expect(point.y).toBeLessThanOrEqual(324 + 1e-9);
  }
  const planeA = projectSpatialPointToPlane(route[0]!);
  const planeB = projectSpatialPointToPlane(route[1]!);
  expect(points[1]!.x - points[0]!.x).toBeCloseTo((planeB.x - planeA.x) * projection.scale);
  expect(points[1]!.y - points[0]!.y).toBeCloseTo((planeB.y - planeA.y) * projection.scale);
  const navigator = sampleCanonicalRoute(deriveNarrativeState(.437)).position;
  expect(projectSpatialPointToViewport(navigator, projection)).toEqual(projectSpatialPointToViewport(navigator, projection));
  expect(route).toEqual(original);
});

test("invalid viewports and padding are rejected", () => {
  for (const [width, height, padding] of [[0, 360, .1], [640, Infinity, .1], [NaN, 360, .1], [640, 360, -.1], [640, 360, .5]]) {
    expect(() => deriveViewportProjection(route, width!, height!, padding!)).toThrow(RangeError);
  }
  expect(() => deriveViewportProjection([], 640, 360)).toThrow(RangeError);
});
