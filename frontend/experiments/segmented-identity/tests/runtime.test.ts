import { it, expect } from "vitest";
import { PointerPath, POINTER_PATH } from "../pointer-path";
import { FixedScheduler, FRAME } from "../scheduler";
import { PhysicsCore, FIXED_DT } from "../core";
import { canonicalGeometry } from "../geometry";
import { legacyGeometry } from "./legacy-geometry";
import { orientationBias, homeResponse } from "../home-response";

it("delivers out-and-back corners and original observation time in one batch", () => {
  const path = new PointerPath();
  path.push({ x: 600, y: 300, elapsedSeconds: .1 });
  path.push({ x: 300, y: 300, elapsedSeconds: .2 });
  expect(path.take()).toEqual([{ x: 600, y: 300, elapsedSeconds: .1 }, { x: 300, y: 300, elapsedSeconds: .2 }]);
  expect(path.pending).toBe(false); expect(path.take()).toBeUndefined();
});
it("bounds overflow, preserves endpoints/time, and clears cancelled strokes", () => {
  const path = new PointerPath();
  for (let i = 0; i < 512; i++) path.push({ x: i, y: i % 2, elapsedSeconds: .001 });
  expect(path.size).toBe(POINTER_PATH.capacity); expect(path.dropped).toBe(384);
  const batch = path.take()!, elapsed = batch.reduce((sum, p) => sum + p.elapsedSeconds!, 0);
  expect(batch[0]!.x).toBe(0); expect(batch.at(-1)!.x).toBe(511);
  expect(elapsed).toBeCloseTo(.512, 12);
  path.push({ x: 100, y: 200 }); path.push({ x: -100, y: -100, active: false });
  expect(path.size).toBe(1); expect(path.take()![0]!.active).toBe(false);
});
it("does not retain or retry a consumed endpoint", () => {
  const path = new PointerPath(); path.push({ x: 960, y: 310, elapsedSeconds: .1 });
  expect(path.take()![0]!.x).toBe(960); expect(path.pending).toBe(false); expect(path.take()).toBeUndefined();
});
it("preserves the first fresh observation after cancellation through merging and overflow", () => {
  const path = new PointerPath();
  path.push({ x: -100, y: -100, active: false });
  path.push({ x: 280, y: 280, elapsedSeconds: .001 });
  path.push({ x: 600, y: 600, elapsedSeconds: .002 });
  expect(path.size).toBe(3);
  expect(path.take()!.map(p => p.x)).toEqual([-100, 280, 600]);
  path.push({ x: -100, y: -100, active: false });
  for (let i = 0; i < 512; i++) path.push({ x: i, y: i % 2, elapsedSeconds: .001 });
  const batch = path.take()!;
  expect(batch).toHaveLength(POINTER_PATH.capacity);
  expect(batch[0]!.active).toBe(false); expect(batch[1]!.x).toBe(0); expect(batch.at(-1)!.x).toBe(511);
  expect(batch.slice(1).reduce((sum, p) => sum + p.elapsedSeconds!, 0)).toBeCloseTo(.512, 12);
});
it.each([60, 90, 120, 144])("schedules fixed 120 Hz physics at %i Hz without changing dt", hz => {
  const scheduler = new FixedScheduler(); let ticks = 0;
  for (let frame = 0; frame <= hz; frame++) scheduler.advance(frame * 1000 / hz, () => { ticks++; return 0; }, () => 0);
  expect(ticks).toBe(120);
});
it("limits catch-up ticks, prediction work, and wall-clock work between ticks", () => {
  const scheduler = new FixedScheduler(), budgets: number[] = [];
  scheduler.advance(0, () => 0, () => 0);
  expect(scheduler.advance(5000, budget => { budgets.push(budget); return 2; }, () => 0)).toBe(FRAME.ticksLimit);
  expect(budgets).toEqual([4, 2]);
  scheduler.reset(); scheduler.advance(0, () => 0, () => 0);
  let time = 0;
  expect(scheduler.advance(50, () => { time += 11; return 0; }, () => time)).toBe(1);
});
it.each([Math.PI, -Math.PI, Math.PI - .001])("recovers the isolated inverted center at angle %f through the normal home field and contact solver", angle => {
  const core = new PhysicsCore(legacyGeometry), body = core.bodies.find(b => b.geometry.id === "left-center")!;
  const mean = body.geometry.anchors.reduce((sum, a) => ({ x: sum.x + a.x / body.geometry.anchors.length,
    y: sum.y + a.y / body.geometry.anchors.length }), { x: 0, y: 0 });
  const rotated = { x: mean.x * Math.cos(angle) - mean.y * Math.sin(angle), y: mean.x * Math.sin(angle) + mean.y * Math.cos(angle) };
  const home = core.home(body);
  core.loadFixture([{ id: body.geometry.id, angle, position: { x: home.x + mean.x - rotated.x, y: home.y + mean.y - rotated.y } }]);
  expect(core.getSnapshot().pairs.some(p => p.contact)).toBe(false);
  const response = homeResponse(body, home, 12);
  expect(response.torque).toBeCloseTo(core.homeForce(body).torque, 8);
  for (let step = 0; step < 45 / FIXED_DT && !core.isRest(); step++) core.stepFixed();
  expect(core.isRest()).toBe(true);
  expect(core.permissions.failures).toEqual([]);
  expect(core.ledger.permissions).toBe(0);
});
it.each(canonicalGeometry.map(g => [g.id, g] as const))("recovers the current %s fragment from an isolated half-turn", (_id, geometry) => {
  const core = new PhysicsCore([geometry]), body = core.bodies[0]!;
  const mean = geometry.anchors.reduce((sum, a) => ({ x: sum.x + a.x / geometry.anchors.length,
    y: sum.y + a.y / geometry.anchors.length }), { x: 0, y: 0 });
  const home = core.home(body);
  core.loadFixture([{ id: geometry.id, angle: Math.PI, position: { x: home.x + 2 * mean.x, y: home.y + 2 * mean.y } }]);
  expect(homeResponse(body, home, 12).torque).toBeCloseTo(core.homeForce(body).torque, 8);
  for (let step = 0; step < 45 / FIXED_DT && !core.isRest(); step++) core.stepFixed();
  expect(core.isRest()).toBe(true); expect(core.permissions.failures).toEqual([]);
  expect(core.ledger.permissions).toBe(0);
});
it("confines the deterministic orientation remedy to its documented angular band", () => {
  for (const angle of [-2.4, -1, 0, 1, 2.4, Math.PI - .011]) expect(orientationBias(angle, 100, 12)).toBe(0);
  expect(orientationBias(Math.PI, 100, 12)).toBeLessThan(0);
  expect(orientationBias(-Math.PI, 100, 12)).toBeGreaterThan(0);
});

it("merges high-rate straight samples without losing time or flattening a reversal", () => {
  const path = new PointerPath();
  for (let i = 0; i < 1000; i++) path.push({ x: i, y: 20, elapsedSeconds: .001 });
  expect(path.size).toBe(2); expect(path.dropped).toBe(0);
  const batch = path.take()!;
  expect(batch[0]!.x).toBe(0);
  const end = batch.at(-1)!; expect(end.x).toBe(999); expect(end.elapsedSeconds).toBeCloseTo(.999, 12);
});


it("accepts unidentified trusted hover without admitting touch or ambiguous contact", async () => {
  const { acceptsHover } = await import("../input");
  const hover = { pointerType: "", isPrimary: true, isTrusted: true, buttons: 0, pressure: 0, width: 1, height: 1 };
  expect(acceptsHover(hover)).toBe(true);
  expect(acceptsHover({ ...hover, pointerType: "touch" })).toBe(false);
  expect(acceptsHover({ ...hover, pressure: .5 })).toBe(false);
  expect(acceptsHover({ ...hover, buttons: 1 })).toBe(false);
  expect(acceptsHover({ ...hover, width: 10 })).toBe(false);
  expect(acceptsHover({ ...hover, isPrimary: false })).toBe(false);
  expect(acceptsHover({ ...hover, isTrusted: false })).toBe(false);
});

it("finishes the widest supported forecast before its age limit at 60 Hz", async () => {
  const { GroupForecast, FORECAST } = await import("../forecast");
  const candidates = Array.from({ length: 6 }, (_, i) => `${i}/${i + 1}`);
  const search = new GroupForecast({ step: 0, key: "wide", members: ["a", "b"],
    candidates, present: candidates.slice(0, 5) }, keys => {
    let ticks = 0;
    return { advance: () => { ticks++; }, sample: () => ({ potential: ticks && keys.length ? 50 : 100, safe: true }) };
  });
  const scheduler = new FixedScheduler(); let steps = 0, finished = false;
  for (let frame = 0; frame <= 60 && !finished; frame++) scheduler.advance(frame * 1000 / 60, budget => {
    steps++;
    expect(steps).toBeLessThanOrEqual(FORECAST.maximumAgeSteps);
    const before = search.simulatedSteps;
    finished = search.advance(Math.min(FORECAST.workPerTick, budget)) !== null;
    return search.simulatedSteps - before;
  }, () => 0);
  expect(finished).toBe(true); expect(search.simulatedSteps).toBe(12);
});

it("checks live failures without building a full diagnostic snapshot", () => {
  const core = new PhysicsCore(canonicalGeometry);
  core.stepFixed(); expect(core.runtimeFailure()).toBeNull();
  core.permissions.failures.push("Return exceeded its deadline");
  expect(core.runtimeFailure()).toBe("Return exceeded its deadline");
  core.permissions.failures.length = 0;
  core.bodies[0]!.velocity = { x: NaN, y: 0 };
  expect(core.runtimeFailure()).toBe("Non-finite simulation state");
});
it("guards energy, containment and navigator clearance independently", () => {
  const core = new PhysicsCore(canonicalGeometry);
  core.stepFixed(); core.ledger.fragments = 1;
  expect(core.runtimeFailure()).toBe("Simulation energy invariant failed");
  core.ledger.fragments = 0; core.bodies[0]!.position = { x: -100, y: 310 };
  expect(core.runtimeFailure()).toBe("Fragment left the stage");
  core.bodies[0]!.position = { x: 480, y: 310 };
  core.navigator.active = true; core.navigator.position = { ...core.bodies[0]!.position };
  // Use a contour vertex: this is guaranteed to touch even for a concave body.
  core.navigator.position = core.getSnapshot().fragments[0]!.vertices[0]!;
  expect(core.runtimeFailure()).toBe("Navigator clearance invariant failed");
});
it("diagnostic reads preserve subsequent physics, permission decisions and energy accounting", () => {
  const observed = new PhysicsCore(canonicalGeometry), silent = new PhysicsCore(canonicalGeometry);
  const stroke = [{ x: 280, y: 310 }, { x: 550, y: 320, elapsedSeconds: .01 }];
  observed.stepStroke(stroke); silent.stepStroke(stroke);
  for (let step = 0; step < 180; step++) {
    const snapshot = observed.getSnapshot();
    const ledgerSum = snapshot.ledger.before + snapshot.ledger.home + snapshot.ledger.damping
      + snapshot.ledger.fragments + snapshot.ledger.navigator + snapshot.ledger.boundaries + snapshot.ledger.permissions;
    expect(ledgerSum).toBeCloseTo(snapshot.energy, 7);
    observed.stepFixed(); silent.stepFixed();
    expect(observed.runtimeFailure()).toBeNull();
    expect(observed.bodies).toEqual(silent.bodies);
    expect(observed.permissions.snapshot()).toEqual(silent.permissions.snapshot());
    expect(observed.ledger).toEqual(silent.ledger);
  }
  expect(observed.getSnapshot()).toEqual(silent.getSnapshot());
});
