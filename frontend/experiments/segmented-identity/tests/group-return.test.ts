import { it, expect } from "vitest";
import { CollisionPermissions, pairKey } from "../deadlock";
import { PhysicsCore } from "../core";
import { polygonProperties } from "../math";
import { canonicalGeometry } from "../geometry";
import { seedFixture, invariantFailure } from "../scenarios";
import reinteractionContact from "./reinteraction-contact.json";

const bodies = () => ["a", "b", "c"].map(id => ({ id, mass: 1, inertia: 100,
  speed: 200, angularSpeed: 4, energy: 20_800, error: 50, homeRequest: 20, navigatorContact: false }));
const pairs = () => [["a", "b"], ["a", "c"], ["b", "c"]].map(([a, b], i) => ({
  key: pairKey(a!, b!), a: a!, b: b!, contact: i !== 1, overlap: 0, gap: i === 1 ? 40 : 0, blocking: 12 }));
const suppressed = () => ({ actual: 0, expected: 10 });
function grant(p: CollisionPermissions, step: number) {
  const request = p.requests[0]!;
  expect(p.acceptPrediction(step, { request, selected: request.candidates,
    baselineReduction: 0, selectedReduction: .1, trials: 2, simulatedSteps: 12 })).toBe(true);
}

it("nominates suppressed home work despite fast translation and angular motion", () => {
  const p = new CollisionPermissions(1 / 120);
  p.observe(1, bodies(), pairs(), suppressed); p.observe(2, bodies(), pairs(), suppressed);
  expect(p.requests).toEqual([]);
  p.observe(3, bodies(), pairs(), suppressed);
  expect(p.requests[0]!.candidates).toEqual(["a/b", "a/c", "b/c"]);
  expect(p.events).toEqual([]); grant(p, 3);
  expect(p.snapshot().every(pair => pair.cohort === "a|b|c")).toBe(true);
  expect(p.events.every(e => e.step === 3)).toBe(true);
});

it.each([{ actual: -10, expected: -8 }, { actual: 4, expected: 5 }, { actual: 0, expected: NaN }])(
  "does not mistake free deceleration or invalid observations for suppression: %j", progress => {
    const p = new CollisionPermissions(1 / 120);
    for (let step = 1; step <= 120; step++) p.observe(step, bodies(), pairs(), () => progress);
    expect(p.requests).toEqual([]); expect(p.events).toEqual([]);
  });

it("requires consecutive suppression and vetoes the whole group on navigator contact", () => {
  const p = new CollisionPermissions(1 / 120);
  for (let step = 1; step <= 30; step++) p.observe(step, bodies(), pairs(), () =>
    step % 3 ? suppressed() : { actual: 10, expected: 10 });
  expect(p.requests).toEqual([]);
  for (let step = 31; step <= 40; step++) p.observe(step, bodies().map(b => ({ ...b,
    navigatorContact: b.id === "c" })), pairs(), suppressed);
  expect(p.requests).toEqual([]); expect(p.events).toEqual([]);
});

it("keeps every cohort permission through separation, motion and incomplete return, then rearms atomically", () => {
  const p = new CollisionPermissions(1 / 120);
  for (let step = 1; step <= 3; step++) p.observe(step, bodies(), pairs(), suppressed);
  grant(p, 3);
  const clear = pairs().map(pair => ({ ...pair, contact: false, gap: 1 }));
  const home = bodies().map(b => ({ ...b, error: 0, speed: 0, angularSpeed: 0, energy: 0 }));
  for (let step = 4; step <= 20; step++) p.observe(step, bodies(), clear, suppressed);
  expect(p.snapshot().every(pair => p.ghosted(pair.key))).toBe(true);
  for (let step = 21; step <= 30; step++) p.observe(step, home, clear.map(pair => pair.key === "a/c" ? { ...pair, gap: .1 } : pair), suppressed);
  expect(p.snapshot().every(pair => p.ghosted(pair.key))).toBe(true);
  for (let step = 31; step <= 37; step++) p.observe(step, home, clear, suppressed);
  expect(p.snapshot().every(pair => p.ghosted(pair.key))).toBe(true);
  p.observe(38, home, clear, suppressed);
  expect(p.snapshot().every(pair => !p.ghosted(pair.key))).toBe(true);
  expect(p.events.filter(e => e.type === "rearm").map(e => e.step)).toEqual([38, 38, 38]);
});

it("ends broad return permissions on renewed interaction without restoring overlapping colliders", () => {
  const p = new CollisionPermissions(1 / 120);
  for (let step = 1; step <= 3; step++) p.observe(step, bodies(), pairs(), suppressed);
  grant(p, 3);
  const mixed = pairs().map(pair => ({ ...pair, contact: pair.key === "a/b",
    overlap: pair.key === "a/b" ? 5 : 0, gap: pair.key === "a/b" ? 0 : 2 }));
  p.observe(4, bodies().map(b => ({ ...b, navigatorContact: b.id === "a" })), mixed, suppressed);
  const shadow = CollisionPermissions.forPrediction(1 / 120, p.snapshot(), [], 4);
  // One touch permanently interrupts the automatic return. Mouse departure
  // must not restore broad immunity, or require a fast/moving group to settle.
  for (let step = 5; step <= 10; step++) {
    p.observe(step, bodies(), mixed, suppressed); shadow.observe(step, bodies(), mixed);
  }
  expect(p.snapshot().every(pair => p.ghosted(pair.key))).toBe(true);
  p.observe(11, bodies(), mixed, suppressed); shadow.observe(11, bodies(), mixed);
  expect(p.ghosted("a/b")).toBe(true);
  expect(p.ghosted("a/c")).toBe(false); expect(p.ghosted("b/c")).toBe(false);
  expect(shadow.snapshot()).toEqual(p.snapshot());
  expect(p.events.filter(e => e.type === "rearm").map(e => e.key)).toEqual(["a/c", "b/c"]);
  // A late overlap on the remaining relationship resets its clearance clock.
  for (let step = 12; step <= 18; step++) p.observe(step, bodies(), mixed.map(pair => ({ ...pair,
    contact: false, overlap: 0, gap: 2 })), suppressed);
  p.observe(19, bodies(), mixed, suppressed);
  for (let step = 20; step <= 26; step++) p.observe(step, bodies(), mixed.map(pair => ({ ...pair,
    contact: false, overlap: 0, gap: 2 })), suppressed);
  expect(p.ghosted("a/b")).toBe(true);
  p.observe(27, bodies(), mixed.map(pair => ({ ...pair, contact: false, overlap: 0, gap: 2 })), suppressed);
  expect(p.snapshot().every(pair => !p.ghosted(pair.key))).toBe(true);
  expect(p.events.filter(e => e.type === "rearm").every(e => e.overlap === 0 && e.gap >= .65 && e.separatedSteps >= 8)).toBe(true);
});

it.each([4, 12])("restores physical collisions during repeated navigator strikes at %i radians/second", rate => {
  const core = new PhysicsCore(canonicalGeometry), fixture = seedFixture(83);
  core.loadFixture(fixture.fragments);
  for (const point of fixture.path) core.stepFixed(1, point);
  for (let tick = 0; tick < 1200 && !core.permissions.events.length; tick++) core.stepFixed();
  expect(core.permissions.snapshot().some(p => p.state === "GHOSTED_LATCHED")).toBe(true);
  let restoredWhileMoving = false;
  for (let tick = 0; tick < 720; tick++) {
    core.stepStroke(Array.from({ length: 3 }, (_, i) => {
      const angle = (tick + (i + 1) / 3) * rate / 120;
      return { x: 480 + 100 * Math.cos(angle), y: 310 + 140 * Math.sin(angle), elapsedSeconds: 1 / 360 };
    }));
    const s = core.getSnapshot();
    expect(invariantFailure(s)).toBeNull();
    restoredWhileMoving ||= s.events.some(e => e.type === "rearm" && e.step === s.step
      && s.fragments.some(b => e.key.split("/").includes(b.id) && b.homeAnchorError > 1));
    if (tick === 120) expect(s.pairs.filter(p => p.state === "GHOSTED_LATCHED").length).toBeLessThanOrEqual(1);
  }
  expect(restoredWhileMoving).toBe(true);
  expect(core.permissions.snapshot().every(p => p.state === "SOLID")).toBe(true);
  core.stepStroke([{ x: 0, y: 0, active: false }]);
  for (let tick = 0; tick < 5400 && !core.isRest(); tick++) {
    core.stepFixed(); expect(invariantFailure(core.getSnapshot())).toBeNull();
  }
  expect(core.isRest()).toBe(true);
});

it("finishes residual correction of a solid three-corner contact captured during renewed interaction", () => {
  const core = new PhysicsCore(canonicalGeometry);
  core.loadFixture(reinteractionContact.fragments);
  Object.assign(core.navigator, reinteractionContact.navigator);
  core.stepStroke(Array.from({ length: 3 }, (_, i) => {
    const angle = (reinteractionContact.tick + (i + 1) / 3) * 12 / 120;
    return { x: 480 + 100 * Math.cos(angle), y: 310 + 140 * Math.sin(angle), elapsedSeconds: 1 / 360 };
  }));
  expect(invariantFailure(core.getSnapshot())).toBeNull();
  expect(core.permissions.events).toEqual([]);
});

const square = [{ x: -20, y: -20 }, { x: 20, y: -20 }, { x: 20, y: 20 }, { x: -20, y: 20 }];
const geometry = ["a", "b", "c", "d"].map((id, i) => ({ id, vertices: square, parts: [square], anchors: square,
  home: { x: i === 3 ? 300 : (i - 1) * 90, y: i === 3 ? -150 : 0 }, mass: 1, inertia: polygonProperties(square).inertiaPerMass }));
it("releases a travelling jam far from home while keeping outsiders solid", () => {
  const core = new PhysicsCore(geometry);
  core.loadFixture(["a", "b", "c"].map((id, i) => ({ id,
    position: { x: 520 - 40 * i, y: 460 }, velocity: { x: 0, y: -300 } })));
  for (let i = 0; i < 30 && !core.permissions.events.length; i++) core.stepFixed();
  expect(core.permissions.events.map(e => e.key)).toEqual(["a/b", "a/c", "b/c"]);
  expect(core.step / 120).toBeLessThan(.2);
  expect(Math.min(...core.bodies.slice(0, 3).map(b => core.homeError(b)))).toBeGreaterThan(100);
  expect(core.permissions.snapshot().filter(p => p.a === "d" || p.b === "d").every(p => p.state === "SOLID")).toBe(true);
  expect(core.forecastWork.simulatedSteps).toBe(24);
  expect(core.ledger.permissions).toBe(0);
  core.stepFixed(3000); expect(core.isRest()).toBe(true); expect(core.permissions.failures).toEqual([]);
});

it("matches finite-difference home-work derivatives for rotating, off-center anchor groups", () => {
  const shifted = square.map(p => ({ x: p.x + 7, y: p.y - 11 }));
  const core = new PhysicsCore(geometry.slice(0, 3).map((g, i) => ({ ...g,
    anchors: i % 2 ? square : shifted, mass: i + 1, inertia: g.inertia * (i + 1) })));
  core.loadFixture(core.bodies.map((b, i) => ({ id: b.geometry.id,
    position: { x: 300 + i * 71, y: 200 + i * 39 }, angle: .4 + i * .7,
    velocity: { x: 120 - i * 24, y: -80 + i * 17 }, angularVelocity: -1.3 + i * 1.8 })));
  // Observe the private scoring calculation without making it a product API.
  const metrics = core as unknown as { homeMetrics(ids: readonly string[], progress: boolean): { potential: number; power: number; powerDerivative: number } };
  const ids = core.bodies.map(b => b.geometry.id), before = metrics.homeMetrics(ids, true), dt = 1e-7;
  for (const body of core.bodies) {
    const response = core.homeForce(body), velocity = { ...body.velocity }, angular = body.angularVelocity;
    body.position = { x: body.position.x + velocity.x * dt, y: body.position.y + velocity.y * dt }; body.angle += angular * dt;
    body.velocity = { x: velocity.x + (response.acceleration.x - 3.2 * velocity.x) * dt,
      y: velocity.y + (response.acceleration.y - 3.2 * velocity.y) * dt };
    body.angularVelocity += (response.torque / body.geometry.inertia - 3.6 * angular) * dt;
  }
  const after = metrics.homeMetrics(ids, true);
  expect(Math.abs((before.potential - after.potential) / dt - before.power) / Math.abs(before.power)).toBeLessThan(1e-5);
  expect(Math.abs((after.power - before.power) / dt - before.powerDerivative) / Math.abs(before.powerDerivative)).toBeLessThan(1e-5);
});

it("does not charge prolonged user interaction against the cohort return deadline", () => {
  const p = new CollisionPermissions(1 / 120);
  for (let step = 1; step <= 3; step++) p.observe(step, bodies(), pairs(), suppressed);
  grant(p, 3);
  const occupied = pairs().map(pair => ({ ...pair, contact: true, overlap: 1, gap: 0 }));
  for (let step = 4; step <= 3200; step++) p.observe(step,
    bodies().map(b => ({ ...b, navigatorContact: b.id === "a" })), occupied, suppressed);
  expect(p.failures).toEqual([]); expect(p.snapshot().every(pair => p.ghosted(pair.key))).toBe(true);
  for (let step = 3201; step <= 6200; step++) p.observe(step, bodies(), occupied, suppressed);
  expect(p.failures).toEqual([]);
  p.observe(6201, bodies(), occupied, suppressed);
  expect(p.failures).toHaveLength(3); // Real uninterrupted failure is still visible.
  expect(p.snapshot().every(pair => p.ghosted(pair.key))).toBe(true);
});
