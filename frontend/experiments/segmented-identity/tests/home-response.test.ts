import { it, expect } from "vitest";
import { PhysicsCore, PHYSICS, FIXED_DT } from "../core";
import { canonicalGeometry } from "../geometry";
import { homeResponse, projectHomeOnly } from "../home-response";

it("the aggregated forecast spring matches the existing distributed-anchor force and torque", () => {
  const core = new PhysicsCore(canonicalGeometry);
  core.loadFixture(canonicalGeometry.map((g, i) => ({ id: g.id, position: { x: 410 + i * 12, y: 280 + i * 5 },
    angle: i * .7 - 2.4 })));
  for (const body of core.bodies) {
    const exact = core.homeForce(body), aggregated = homeResponse(body, core.home(body), PHYSICS.homeStiffness);
    expect(aggregated.acceleration.x).toBeCloseTo(exact.acceleration.x, 8);
    expect(aggregated.acceleration.y).toBeCloseTo(exact.acceleration.y, 8);
    expect(aggregated.torque).toBeCloseTo(exact.torque, 8);
  }
});

it("home-only ranking copies cannot change source poses or velocities", () => {
  const core = new PhysicsCore(canonicalGeometry);
  core.loadFixture([{ id: "left-center", angle: 1.4 }]);
  const before = core.getSnapshot();
  const projected = projectHomeOnly(core.bodies, core.bodies.map(b => core.home(b)), { steps: 120, dt: FIXED_DT,
    stiffness: PHYSICS.homeStiffness, damping: PHYSICS.linearDamping, angularDamping: PHYSICS.angularDamping,
    width: core.width, height: core.height, navigator: { active: false, position: { x: -100, y: -100 }, radius: 10 } });
  expect(projected).not.toBeNull(); expect(projected![4]!.angle).not.toBe(core.bodies[4]!.angle);
  expect(core.getSnapshot()).toEqual(before);
});

it("declines an unconstrained ranking estimate that could meet a wall", () => {
  const core = new PhysicsCore(canonicalGeometry);
  core.loadFixture([{ id: "left-center", position: { x: 10, y: 310 } }]);
  expect(projectHomeOnly(core.bodies, core.bodies.map(b => core.home(b)), { steps: 120, dt: FIXED_DT,
    stiffness: PHYSICS.homeStiffness, damping: PHYSICS.linearDamping, angularDamping: PHYSICS.angularDamping,
    width: core.width, height: core.height, navigator: { active: false, position: { x: -100, y: -100 }, radius: 10 } })).toBeNull();
});
