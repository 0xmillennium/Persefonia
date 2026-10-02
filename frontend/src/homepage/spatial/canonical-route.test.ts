import { expect, test } from "vitest";
import { flattenCanonicalRoute, PROVISIONAL_CANONICAL_ROUTE, sameSpatialPoint, spatialDistance } from "./canonical-route";

const expected = [
  ["threshold", [[0, 0, 0], [.55, 0, .02]]],
  ["work", [[.55, 0, .02], [1.10, -.12, .14], [1.55, .18, .24]]],
  ["writing", [[1.55, .18, .24], [1.78, .68, .16], [1.60, 1.05, .08]]],
  ["research", [[1.60, 1.05, .08], [1.18, 1.32, -.04], [.82, 1.18, -.10]]],
  ["architecture", [[.82, 1.18, -.10], [.45, .90, -.22], [.30, .45, -.28], [.52, .08, -.12]]],
  ["return", [[.52, .08, -.12], [.86, .30, 0], [1.08, .68, .10]]],
  ["person", [[1.08, .68, .10], [.90, .98, .08], [.55, 1.08, .04]]],
  ["final", [[.55, 1.08, .04], [.15, .88, .02], [-.12, .52, 0]]]
] as const;

test("the sole provisional route has exact ordered coordinates and valid open topology", () => {
  const sections = PROVISIONAL_CANONICAL_ROUTE.sections;
  expect(sections).toHaveLength(8);
  expect(sections.map(section => section.region)).toEqual(expected.map(section => section[0]));
  expect(new Set(sections.map(section => section.region)).size).toBe(8);
  sections.forEach((section, index) => {
    expect(section.points.map(point => [point.x, point.y, point.depth])).toEqual(expected[index]![1]);
    expect(section.points.length).toBeGreaterThanOrEqual(2);
    expect(section.points.every(point => [point.x, point.y, point.depth].every(Number.isFinite))).toBe(true);
    const lengths = section.points.slice(1).map((point, pointIndex) => spatialDistance(section.points[pointIndex]!, point));
    expect(lengths.every(length => length > 0 && Number.isFinite(length))).toBe(true);
    expect(lengths.reduce((sum, length) => sum + length, 0)).toBeGreaterThan(0);
    if (index > 0) expect(section.points[0]).toEqual(sections[index - 1]!.points.at(-1));
  });
  expect(sections[0]!.points[0]).not.toEqual(sections.at(-1)!.points.at(-1));
});

test("flattening removes only shared section boundaries", () => {
  const flat = flattenCanonicalRoute(PROVISIONAL_CANONICAL_ROUTE);
  expect(flat).toHaveLength(17);
  expect(flat[0]).toEqual(PROVISIONAL_CANONICAL_ROUTE.sections[0]!.points[0]);
  expect(flat.at(-1)).toEqual(PROVISIONAL_CANONICAL_ROUTE.sections.at(-1)!.points.at(-1));
  expect(flat.slice(1).every((point, index) => !sameSpatialPoint(flat[index]!, point))).toBe(true);
  expect(flat.map(point => [point.x, point.y, point.depth])).toEqual(
    expected.flatMap((section, index) => Array.from(section[1] as readonly (readonly number[])[]).slice(index === 0 ? 0 : 1))
  );
});
