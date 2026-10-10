import { expect, it, vi } from "vitest";
import { PhysicsCore, FIXED_DT, PHYSICS } from "../core";
import { canonicalGeometry } from "../geometry";
import { fragmentGeometry } from "../geometry-loader";
import { circleCast, circleContact, add, sub, mul } from "../math";
import { PointerStroke, POINTER_STROKE } from "../pointer-stroke";
import { FixedScheduler } from "../scheduler";
import { PointerPath } from "../pointer-path";
import { invariantFailure } from "../scenarios";

const square = [{ x: -20, y: -20 }, { x: 20, y: -20 }, { x: 20, y: 20 }, { x: -20, y: 20 }];
const cavity = [{ x: -40, y: -40 }, { x: 40, y: -40 }, { x: 40, y: 40 }, { x: 20, y: 40 },
  { x: 20, y: -20 }, { x: -20, y: -20 }, { x: -20, y: 40 }, { x: -40, y: 40 }];

it.each([-1, 1])("casts a long circle stroke against the first actual face, direction %i", direction => {
  const hit = circleCast(square, { x: -100 * direction, y: 0 }, { x: 100 * direction, y: 0 }, 10)!;
  expect(hit.fraction).toBeCloseTo(.35, 12);
  expect(hit.normal.x).toBe(-direction); expect(hit.normal.y).toBeCloseTo(0, 12);
  expect(hit.point).toEqual({ x: -20 * direction, y: 0 });
});
it("casts against rounded vertex offsets and does not extend finite edges", () => {
  const hit = circleCast(square, { x: -100, y: -100 }, { x: 0, y: 0 }, 10)!;
  expect(hit.fraction).toBeCloseTo((80 - 10 / Math.sqrt(2)) / 100, 12);
  expect(hit.point).toEqual({ x: -20, y: -20 });
  expect(circleCast(square, { x: -100, y: -31 }, { x: 100, y: -31 }, 10)).toBeNull();
});
it("preserves concave free space and detects its actual inner boundary", () => {
  expect(circleCast(cavity, { x: 0, y: 10 }, { x: 0, y: 80 }, 5)).toBeNull();
  expect(circleCast(cavity, { x: 0, y: 80 }, { x: 0, y: -80 }, 5)?.fraction).toBeCloseTo(95 / 160, 12);
});
it("handles initial overlap, motion away from a tangent, zero travel and endpoint contact", () => {
  expect(circleCast(square, { x: 0, y: 0 }, { x: 100, y: 0 }, 10)?.fraction).toBe(0);
  expect(circleCast(square, { x: -30, y: 0 }, { x: -100, y: 0 }, 10)).toBeNull();
  expect(circleCast(square, { x: -100, y: 0 }, { x: -100, y: 0 }, 10)).toBeNull();
  expect(circleCast(square, { x: -100, y: 0 }, { x: -30, y: 0 }, 10)?.fraction).toBe(1);
});
it("agrees with a dense contour-distance oracle for varied directions, including concave contours", () => {
  for (const polygon of [square, cavity, ...canonicalGeometry.map(g => g.vertices)]) for (let i = 0; i < 16; i++) {
    const angle = i * .39, start = { x: -150 * Math.cos(angle), y: -150 * Math.sin(angle) };
    const end = { x: 150 * Math.cos(angle) + 17, y: 150 * Math.sin(angle) + 9 }, delta = sub(end, start);
    const hit = circleCast(polygon, start, end, 10);
    let sampled: number | undefined;
    for (let n = 0; n <= 1500; n++) if (circleContact(polygon, add(start, mul(delta, n / 1500)), 10)) {
      sampled = n / 1500; break;
    }
    if (sampled === undefined) expect(hit).toBeNull();
    else {
      expect(hit).not.toBeNull(); expect(hit!.fraction).toBeLessThanOrEqual(sampled + 1e-10);
      expect(sampled - hit!.fraction).toBeLessThanOrEqual(1 / 1500 + 1e-10);
    }
  }
});
it("retains every corner, observed velocity and exact final point across bounded subdivisions", () => {
  const stroke = new PointerStroke({ x: 50, y: 100 }, [{ x: 910, y: 100, elapsedSeconds: .005 },
    { x: 50, y: 100, elapsedSeconds: .01 }, { x: 50, y: 600, elapsedSeconds: .002 }], 960, 620, FIXED_DT);
  let origin = stroke.points[0]!;
  const ends = [], velocities = [];
  for (let s = 1; s <= PHYSICS.microsteps; s++) {
    const legs = stroke.take(s === PHYSICS.microsteps ? Infinity : stroke.distance * s / PHYSICS.microsteps, origin);
    for (const leg of legs) { ends.push(leg.end); velocities.push(leg.delta.x / leg.elapsedSeconds); origin = leg.end; }
  }
  expect(ends).toContainEqual({ x: 910, y: 100 }); expect(ends).toContainEqual({ x: 50, y: 100 });
  expect(origin).toEqual({ x: 50, y: 600 }); expect(velocities).toContain(860 / .005); expect(velocities).toContain(-860 / .01);
  expect(stroke.take(Infinity, origin)).toEqual([]);
});
it("consumes fresh input on the first awake frame without altering subsequent 120 Hz scheduling", () => {
  const scheduler = new FixedScheduler(); let ticks = 0;
  scheduler.advance(0, () => { ticks++; return 0; }, () => 0, true);
  expect(ticks).toBe(1);
  for (let frame = 1; frame <= 60; frame++) scheduler.advance(frame * 1000 / 60, () => { ticks++; return 0; }, () => 0, true);
  expect(ticks).toBe(121);
});
it("bounds body integration independently of stroke distance and catches every crossed body", () => {
  const geometry = [-100, 0, 100].map((x, i) => fragmentGeometry(`piece-${i}`, square.map(p => ({ x: p.x + x, y: p.y })), [0, 1, 2]));
  const core = new PhysicsCore(geometry);
  core.stepStroke([{ x: 50, y: 310 }]);
  const force = vi.spyOn(core, "homeForce");
  core.stepStroke([{ x: 910, y: 310, elapsedSeconds: .005 }]);
  expect(core.navigator.position).toEqual({ x: 910, y: 310 });
  // One force evaluation per body/microstep, plus pair inspection and the
  // permission observation. No distance-dependent integration loop remains.
  expect(force.mock.calls.length).toBeLessThanOrEqual(geometry.length * (PHYSICS.microsteps + 2));
  expect(core.bodies.every(b => b.navigatorContact && b.velocity.x > 0)).toBe(true);
  expect(core.bodies.every(b => b.velocity.x <= PHYSICS.navigatorSpeedLimit * (1 + PHYSICS.restitution))).toBe(true);
  expect(invariantFailure(core.getSnapshot())).toBeNull();
});
it("detects an out-and-back stroke even when its endpoint never changes", () => {
  const core = new PhysicsCore([fragmentGeometry("piece", square, [0, 1, 2])]);
  core.stepStroke([{ x: 280, y: 310 }]);
  core.stepStroke([{ x: 680, y: 310, elapsedSeconds: .005 }, { x: 280, y: 310, elapsedSeconds: .005 }]);
  expect(core.navigator.position).toEqual({ x: 280, y: 310 });
  expect(core.bodies[0]!.navigatorContact).toBe(true); expect(core.homeError(core.bodies[0]!)).toBeGreaterThan(.1);
  expect(invariantFailure(core.getSnapshot())).toBeNull();
});
it("consumes a capacity-sized burst without losing its final point or leaving work queued", () => {
  const core = new PhysicsCore(canonicalGeometry);
  core.stepStroke([{ x: 50, y: 100 }]);
  const samples = Array.from({ length: POINTER_STROKE.capacity }, (_, i) => ({
    x: i % 2 ? 50 : 910, y: i === POINTER_STROKE.capacity - 1 ? 100 : 310, elapsedSeconds: .001
  }));
  core.stepStroke(samples);
  expect(core.navigator.position).toEqual({ x: 50, y: 100 });
  expect(invariantFailure(core.getSnapshot())).toBeNull();
  core.stepFixed(); expect(core.navigator.position).toEqual({ x: 50, y: 100 });
});
it("rejects an invalid batch atomically and clears presence without replaying cancelled motion", () => {
  const core = new PhysicsCore(canonicalGeometry), before = core.getSnapshot();
  for (const batch of [[{ x: 280, y: 310 }, { x: NaN, y: 310 }], [{ x: 280, y: 310 }, { x: 480, y: 310, elapsedSeconds: 0 }],
    [{ x: 280, y: 310 }, { x: -100, y: -100, active: false }], Array.from({ length: 129 }, () => ({ x: 280, y: 310 }))]) {
    expect(() => core.stepStroke(batch)).toThrow(); expect(core.getSnapshot()).toEqual(before);
  }
  core.stepStroke([{ x: 280, y: 310 }]); core.stepStroke([{ x: -100, y: -100, active: false }]);
  expect(core.navigator.active).toBe(false); expect(core.energy()).toBe(0);
});
it("re-entry in the cancellation frame starts fresh without striking across the cancelled gap", () => {
  const core = new PhysicsCore(canonicalGeometry), path = new PointerPath();
  core.stepStroke([{ x: 280, y: 310 }]);
  path.push({ x: 480, y: 310 });
  path.push({ x: -100, y: -100, active: false });
  path.push({ x: 680, y: 310 });
  core.stepStroke(path.take()!);
  expect(core.navigator.active).toBe(true); expect(core.navigator.position).toEqual({ x: 680, y: 310 });
  expect(core.energy()).toBe(0); expect(core.bodies.every(b => !b.navigatorContact)).toBe(true);
  expect(path.pending).toBe(false);
  core.stepStroke([{ x: 280, y: 310, elapsedSeconds: .005 }]);
  expect(core.bodies.some(b => b.navigatorContact)).toBe(true); expect(core.energy()).toBeGreaterThan(0);
});
it("repeated final contact inspection replaces diagnostics instead of accumulating duplicate neighbours", () => {
  const geometry = [0, 40].map((x, i) => fragmentGeometry(`piece-${i}`, square.map(p => ({ x: p.x + x, y: p.y })), [0, 1, 2]));
  const core = new PhysicsCore(geometry);
  core["inspectPairs"](); core["inspectPairs"]();
  expect(core.getSnapshot().fragments.map(b => b.contacts)).toEqual([["piece-1"], ["piece-0"]]);
});
it.each([1, 2, 3, 4, 5])("live fast strokes preserve strict contacts and recover, path %i", seed => {
  const core = new PhysicsCore(canonicalGeometry);
  core.stepStroke([{ x: 50, y: 100 }]);
  for (let i = 0; i < 24; i++) {
    core.stepStroke([{ x: 480 + 420 * Math.sin(i * (seed + .41)), y: 310 + 220 * Math.cos(i * (seed + .17)), elapsedSeconds: .008 },
      { x: 480 + 420 * Math.sin((i + 1) * (seed + .41)), y: 310 + 220 * Math.cos((i + 1) * (seed + .17)), elapsedSeconds: .008 }]);
    expect(invariantFailure(core.getSnapshot())).toBeNull();
  }
  core.stepStroke([{ x: 0, y: 0, active: false }]);
  for (let s = 0; s < 45 / FIXED_DT && !core.isRest(); s++) {
    core.stepFixed(); expect(invariantFailure(core.getSnapshot())).toBeNull();
  }
  expect(core.isRest()).toBe(true);
});
