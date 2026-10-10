import { describe, expect, it } from "vitest";
import { fragmentGeometry } from "../geometry-loader";
import { canonicalGeometry } from "../geometry";
import { PhysicsCore, PHYSICS } from "../core";
import { CollisionPermissions } from "../deadlock";
import { GroupForecast } from "../forecast";
import { circleContact, isConvex, polygonProperties, transform } from "../math";

const square = [{ x: -20, y: -20 }, { x: 20, y: -20 }, { x: 20, y: 20 }, { x: -20, y: 20 }];

describe("validated geometry boundary", () => {
  it.each([
    ["too few vertices", square.slice(0, 2), [0, 1, 2]],
    ["non-finite vertex", [...square.slice(0, 3), { x: NaN, y: 20 }], [0, 1, 2]],
    ["repeated vertex", [square[0]!, square[0]!, ...square.slice(1)], [0, 2, 3]],
    ["crossing with nonzero signed area", [{ x: 0, y: 0 }, { x: 40, y: 0 }, { x: 0, y: 30 }, { x: 30, y: 40 }], [0, 1, 2]],
    ["backtracking edge", [{ x: 0, y: 0 }, { x: 20, y: 0 }, { x: 10, y: 0 }, { x: 10, y: 20 }, { x: 0, y: 20 }], [0, 1, 3]],
    ["duplicated anchor", square, [0, 0, 2]],
    ["fractional anchor", square, [0, 1.5, 2]],
    ["missing anchor", square, [0, 1, 4]],
    ["collinear anchors", [{ x: 0, y: 0 }, { x: 10, y: 0 }, { x: 20, y: 0 }, { x: 20, y: 20 }, { x: 0, y: 20 }], [0, 1, 2]]
  ] as const)("rejects %s before decomposition", (_label, points, indices) => {
    expect(() => fragmentGeometry("piece", points, indices)).toThrow();
  });
  it("rejects IDs that collide with pair and cluster key delimiters", () => {
    for (const id of ["", "a/b", "a|b", '"piece"']) expect(() => fragmentGeometry(id, square, [0, 1, 2])).toThrow();
  });
  it("normalizes clockwise contours while retaining selected world anchors and mass properties", () => {
    const world = [...square].reverse(), geometry = fragmentGeometry("piece", world, [0, 1, 2]);
    expect(isConvex(geometry.vertices)).toBe(true);
    expect(geometry.parts).toHaveLength(1);
    expect(geometry.mass).toBeCloseTo(polygonProperties(square).area / 3500, 12);
    geometry.anchors.forEach((a, i) => expect({ x: a.x + geometry.home.x, y: a.y + geometry.home.y }).toEqual(world[i]));
    expect(Object.isFrozen(geometry)).toBe(true);
    expect(geometry.vertices.every(Object.isFrozen)).toBe(true);
  });
});

describe("simulation input contracts", () => {
  it("rejects invalid observation timesteps and fractional or unbounded forecast budgets", () => {
    for (const dt of [0, -1, NaN, Infinity]) expect(() => new CollisionPermissions(dt)).toThrow();
    const search = new GroupForecast({ step: 0, key: "a|b", members: ["a", "b"], candidates: ["a/b"] }, () => {
      throw new Error("Invalid budget must not start a branch");
    });
    for (const budget of [-1, .5, NaN, Infinity]) expect(() => search.advance(budget)).toThrow("Invalid forecast budget");
    expect(search.advance(0)).toBeNull(); expect(search.simulatedSteps).toBe(0);
  });
  it("rejects invalid dimensions, duplicate identities, and invalid inverse-mass inputs", () => {
    for (const width of [0, -1, Infinity, NaN]) expect(() => new PhysicsCore(canonicalGeometry, { width })).toThrow();
    expect(() => new PhysicsCore([canonicalGeometry[0]!, canonicalGeometry[0]!])).toThrow();
    for (const value of [0, -1, Infinity, NaN]) for (const key of ["mass", "inertia"] as const)
      expect(() => new PhysicsCore([{ ...canonicalGeometry[0]!, [key]: value }])).toThrow();
  });
  it("does not partially mutate the world when a later fixture is invalid", () => {
    const core = new PhysicsCore(canonicalGeometry), before = core.getSnapshot();
    expect(() => core.loadFixture([{ id: canonicalGeometry[0]!.id, angle: 1 },
      { id: canonicalGeometry[1]!.id, velocity: { x: NaN, y: 0 } }])).toThrow();
    expect(core.getSnapshot()).toEqual(before);
    expect(() => core.loadFixture([{ id: canonicalGeometry[0]!.id, angle: 1 }, { id: "unknown" }])).toThrow();
    expect(core.getSnapshot()).toEqual(before);
    expect(() => core.loadFixture([{ id: canonicalGeometry[0]!.id }, { id: canonicalGeometry[0]!.id }])).toThrow();
    expect(core.getSnapshot()).toEqual(before);
  });
  it("rejects an invalid observation interval before moving or activating the navigator", () => {
    const core = new PhysicsCore(canonicalGeometry), before = core.getSnapshot();
    for (const elapsedSeconds of [0, -1, NaN, Infinity]) {
      expect(() => core.stepFixed(1, { x: 480, y: 310, elapsedSeconds })).toThrow();
      expect(core.getSnapshot()).toEqual(before);
    }
  });
});

it("keeps tangent and interior circle contacts in the exact narrow phase", () => {
  expect(circleContact(square, { x: 30, y: 0 }, 10)?.depth).toBe(0);
  expect(circleContact(square, { x: 30.0001, y: 0 }, 10)).toBeNull();
  expect(circleContact(square, { x: 0, y: 0 }, 10)?.depth).toBe(30);
  expect(circleContact(square, { x: 27.1, y: 27.1 }, 10)).toBeNull();
});

it("transforms shared contour points once while preserving every decomposed part's exact numeric pose", () => {
  const core = new PhysicsCore(canonicalGeometry);
  core.loadFixture(canonicalGeometry.map((g, i) => ({ id: g.id, angle: (i - 4) * .37,
    position: { x: 300 + i * 7, y: 250 - i * 9 } })));
  for (const body of core.bodies) {
    const vertices = core.vertices(body), parts = core.parts(body);
    expect(parts).toEqual(body.geometry.parts.map(part => transform(part, body.position, body.angle)));
    expect(parts.every(part => part.every(point => vertices.includes(point)))).toBe(true);
  }
});
it("preserves independently authored convex parts without assuming shared point identities", () => {
  const geometry = fragmentGeometry("piece", square, [0, 1, 2]);
  const independent = { ...geometry, parts: geometry.parts.map(part => part.map(p => ({ ...p }))) };
  const core = new PhysicsCore([independent]), body = core.bodies[0]!;
  core.loadFixture([{ id: "piece", angle: .73 }]);
  expect(core.parts(body)).toEqual(independent.parts.map(part => transform(part, body.position, body.angle)));
});

it("rejects illegal solid penetration in shadow safety while allowing a deliberately latched relationship", () => {
  const geometry = ["a", "b"].map(id => fragmentGeometry(id, square, [0, 1, 2]));
  const core = new PhysicsCore(geometry);
  // Exercise the shadow branch's safety predicate directly: ordinary stepping
  // would first correct this overlap and conceal the missing guard.
  expect(core.getSnapshot().pairs[0]!.overlap).toBeGreaterThan(PHYSICS.solidTolerance);
  expect(core["forecastSafe"]()).toBe(false);
  const states = [{ key: "a/b", a: "a", b: "b", state: "SOLID" as const, contactSteps: 0,
    eligible: false, startStep: null, separatedSteps: 0, rearmMargin: false }];
  const prediction = new PhysicsCore(geometry, {
    forecastPermissions: CollisionPermissions.forPrediction(1 / 120, states, ["a/b"], 0)
  });
  expect(prediction["forecastSafe"]()).toBe(true);
});
