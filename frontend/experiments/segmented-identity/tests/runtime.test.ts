import { it, expect } from "vitest";
import { PointerPath, POINTER_PATH } from "../pointer-path";
import { FixedScheduler, FRAME } from "../scheduler";
import { PhysicsCore, FIXED_DT } from "../core";
import { canonicalGeometry } from "../geometry";
import { orientationBias, homeResponse } from "../home-response";

it("retains out-and-back corners with bounded travel and original observation time", () => {
  const path = new PointerPath();
  path.push({ x: 600, y: 300, elapsedSeconds: .1 });
  path.push({ x: 300, y: 300, elapsedSeconds: .2 });
  let origin = { x: 300, y: 300 }, elapsed = 0;
  const points = [origin];
  while (path.pending) {
    const sample = path.take(origin, true)!;
    expect(Math.hypot(sample.x - origin.x, sample.y - origin.y)).toBeLessThanOrEqual(POINTER_PATH.travelPerTick + 1e-9);
    origin = { x: sample.x, y: sample.y }; elapsed += sample.elapsedSeconds!; points.push(origin);
  }
  expect(points.some(p => p.x === 600)).toBe(true);
  expect(origin).toEqual({ x: 300, y: 300 }); expect(elapsed).toBeCloseTo(.3, 12);
});
it("bounds overflow, preserves endpoints/time, and clears cancelled strokes", () => {
  const path = new PointerPath();
  for (let i = 0; i < 512; i++) path.push({ x: i, y: i % 2, elapsedSeconds: .001 });
  expect(path.size).toBe(128); expect(path.dropped).toBe(384);
  let elapsed = 0, sample = path.take(); expect(sample!.x).toBe(0);
  while (sample) { elapsed += sample.elapsedSeconds!; const next = path.take(); if (!next) expect(sample.x).toBe(511); sample = next; }
  expect(elapsed).toBeCloseTo(.512, 12);
  path.push({ x: 100, y: 200 }); path.push({ x: -100, y: -100, active: false });
  expect(path.size).toBe(1); expect(path.take()!.active).toBe(false);
});
it("does not retry a wall-constrained endpoint indefinitely", () => {
  const path = new PointerPath(); path.push({ x: 960, y: 310, elapsedSeconds: .1 });
  let ticks = 0;
  while (path.pending && ticks < 100) { path.take({ x: 900, y: 310 }, true); ticks++; }
  expect(ticks).toBe(2); expect(path.pending).toBe(false);
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
  const core = new PhysicsCore(canonicalGeometry), body = core.bodies.find(b => b.geometry.id === "left-center")!;
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
it("confines the deterministic orientation remedy to its documented angular band", () => {
  for (const angle of [-2.4, -1, 0, 1, 2.4, Math.PI - .011]) expect(orientationBias(angle, 100, 12)).toBe(0);
  expect(orientationBias(Math.PI, 100, 12)).toBeLessThan(0);
  expect(orientationBias(-Math.PI, 100, 12)).toBeGreaterThan(0);
});

it("merges high-rate straight samples without losing time or flattening a reversal", () => {
  const path = new PointerPath();
  for (let i = 0; i < 1000; i++) path.push({ x: i, y: 20, elapsedSeconds: .001 });
  expect(path.size).toBe(2); expect(path.dropped).toBe(0);
  expect(path.take()!.x).toBe(0);
  const end = path.take()!; expect(end.x).toBe(999); expect(end.elapsedSeconds).toBeCloseTo(.999, 12);
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
  expect(finished).toBe(true); expect(search.simulatedSteps).toBe(72);
});
