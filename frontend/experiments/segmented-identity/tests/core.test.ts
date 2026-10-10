import { describe, it, expect } from "vitest";
import { PhysicsCore, navigatorTransfer } from "../core";
import { polygonProperties, decompose, polygonContact, transform } from "../math";
import type { FragmentGeometry } from "../core";
import { CollisionPermissions } from "../deadlock";

const square = [{ x: -20, y: -20 }, { x: 20, y: -20 }, { x: 20, y: 20 }, { x: -20, y: 20 }];
const g = (id: string, x: number): FragmentGeometry => ({ id, vertices: square, parts: [square],
  anchors: square, home: { x, y: 0 }, mass: 1, inertia: polygonProperties(square).inertiaPerMass });

const blockedBodies = () => ["a", "b", "c"].map(id => ({ id, mass: 1, inertia: 1, speed: 0,
  angularSpeed: 0, energy: 0, error: 20, homeRequest: 12, navigatorContact: false }));
const blockedPairs = () => [{ key: "a/b", a: "a", b: "b", contact: true, overlap: 0, gap: 0, blocking: 12 },
  { key: "b/c", a: "b", b: "c", contact: true, overlap: 0, gap: 0, blocking: 6 }];

const approve = (permissions: CollisionPermissions, step: number, selected?: readonly string[]) => {
  const request = permissions.requests[0];
  if (request) permissions.acceptPrediction(step, { request, selected: selected ?? request.candidates,
    baselineReduction: .001, selectedReduction: .1, trials: 2, simulatedSteps: 12 });
};

describe("predictive permission boundary", () => {
  it.each(["chain", "triangle"])("prepares the whole %s and commits its selected contacts atomically", shape => {
    const p = new CollisionPermissions(1 / 120), pairs = blockedPairs();
    if (shape === "triangle") pairs.push({ key: "a/c", a: "a", b: "c", contact: true, overlap: 0, gap: 0, blocking: 3 });
    for (let step = 1; step <= 7; step++) p.observe(step, blockedBodies(), pairs);
    expect(p.events).toEqual([]); // Observation alone is never release authority.
    expect(p.requests[0]!.candidates).toHaveLength(pairs.length);
    approve(p, 7);
    expect(p.events.map(e => e.key).sort()).toEqual(pairs.map(p => p.key).sort());
    expect(p.events.every(e => e.step === 7)).toBe(true);
  });
  it("rejects a partial permission for an indivisible return cohort", () => {
    const p = new CollisionPermissions(1 / 120);
    for (let step = 1; step <= 7; step++) p.observe(step, blockedBodies(), blockedPairs());
    approve(p, 7, ["b/c"]);
    expect(p.ghosted("b/c")).toBe(false); expect(p.ghosted("a/b")).toBe(false);
  });
  it("keeps a moving contact group solid without evidence of suppressed progress", () => {
    const p = new CollisionPermissions(1 / 120);
    for (let step = 1; step <= 20; step++) p.observe(step,
      blockedBodies().map(b => b.id === "c" ? { ...b, speed: 20, energy: 200 } : b), blockedPairs());
    expect(p.requests).toEqual([]);
    approve(p, 20); expect(p.ghosted("b/c")).toBe(false);
  });
  it("keeps a calm contact island nominated across brief relative-speed spikes", () => {
    const p = new CollisionPermissions(1 / 120);
    for (let step = 1; step <= 30; step++) {
      p.observe(step, blockedBodies().map(b => {
        const vx = b.id === "c" && step % 7 === 0 ? 12.1 : 0;
        return { ...b, speed: vx, velocityX: vx, velocityY: 0, energy: .5 * vx ** 2 };
      }), blockedPairs());
      if (step >= 7) {
        expect(p.requests[0]!.members).toEqual(["a", "b", "c"]);
        expect([...p.requests[0]!.candidates].sort()).toEqual(["a/b", "b/c"]);
      }
    }
    expect(p.events).toEqual([]);
  });
  it("blocks release during sustained new motion without a progress observation", () => {
    const p = new CollisionPermissions(1 / 120);
    for (let step = 1; step <= 7; step++) p.observe(step, blockedBodies(), blockedPairs());
    for (let step = 8; step <= 20; step++) {
      p.observe(step, blockedBodies().map(b => b.id === "c" ? { ...b, speed: 20, energy: 200 } : b), blockedPairs());
      expect(p.requests.every(r => !r.members.includes("c"))).toBe(true);
    }
    expect(p.requests).toEqual([]);
    expect(p.events).toEqual([]);
  });
  it("retains supporting contacts as candidates without automatically exempting them", () => {
    const p = new CollisionPermissions(1 / 120), pairs = blockedPairs(); pairs[1]!.blocking = -6;
    for (let step = 1; step <= 20; step++) p.observe(step, blockedBodies(), pairs);
    expect([...p.requests[0]!.candidates].sort()).toEqual(["a/b", "b/c"]);
    approve(p, 20, ["a/b"]); expect(p.ghosted("b/c")).toBe(false);
  });
  it("rejects a brief turning point in moving bodies", () => {
    const p = new CollisionPermissions(1 / 120);
    for (let step = 1; step <= 180; step++) {
      p.observe(step, blockedBodies().map(b => step % 60 ? { ...b, speed: 15, energy: 112.5 } : b), blockedPairs());
      expect(p.requests).toEqual([]);
    }
  });
  it.each(["lost blocking", "navigator"])("rejects a completed decision after %s invalidates its group", reason => {
    const p = new CollisionPermissions(1 / 120), pairs = blockedPairs();
    for (let step = 1; step <= 7; step++) p.observe(step, blockedBodies(), pairs);
    const request = p.requests[0]!;
    p.observe(8, blockedBodies().map(b => ({ ...b, navigatorContact: reason === "navigator" && b.id === "a" })),
      pairs.map(p => ({ ...p, blocking: reason === "lost blocking" ? 0 : p.blocking })));
    p.acceptPrediction(8, { request, selected: ["a/b", "b/c"], baselineReduction: 0, selectedReduction: .1, trials: 2, simulatedSteps: 12 });
    expect(p.events).toEqual([]);
  });
  it("does not add a merely approaching outsider to an existing return cohort", () => {
    const p = new CollisionPermissions(1 / 120), pairs = blockedPairs();
    pairs[1] = { ...pairs[1]!, contact: false, gap: 1, blocking: 0 };
    for (let step = 1; step <= 7; step++) p.observe(step, blockedBodies(), pairs);
    expect(p.requests[0]!.members).toEqual(["a", "b"]); approve(p, 7);
    expect(p.ghosted("a/b")).toBe(true); expect(p.ghosted("b/c")).toBe(false);
    for (let step = 8; step <= 10; step++) p.observe(step, blockedBodies(), blockedPairs());
    approve(p, 10); expect(p.events.map(e => e.key)).toEqual(["a/b", "b/c"]);
    expect(p.snapshot().every(pair => pair.cohort === "a|b|c")).toBe(true);
  });
  it("adds a new touching edge without another long per-pair history", () => {
    const p = new CollisionPermissions(1 / 120);
    for (let step = 1; step <= 20; step++) p.observe(step, blockedBodies(), blockedPairs().slice(0, 1));
    for (let step = 21; step <= 23; step++) p.observe(step, blockedBodies(), blockedPairs());
    approve(p, 23); expect(p.events.map(e => e.step)).toEqual([23, 23]);
  });
  it("propagates the navigator veto through an existing ghost overlap", () => {
    const p = new CollisionPermissions(1 / 120);
    for (let step = 1; step <= 7; step++) p.observe(step, blockedBodies(), blockedPairs().slice(0, 1));
    approve(p, 7);
    for (let step = 8; step <= 180; step++) p.observe(step,
      blockedBodies().map(b => ({ ...b, navigatorContact: b.id === "a" })), blockedPairs());
    expect(p.events.map(e => e.key)).toEqual(["a/b"]); expect(p.requests).toEqual([]);
  });
  it("preserves the navigator veto through a persistent solid contact group", () => {
    const p = new CollisionPermissions(1 / 120);
    for (let step = 1; step <= 180; step++) p.observe(step,
      blockedBodies().map(b => ({ ...b, navigatorContact: b.id === "a" })), blockedPairs());
    expect(p.events).toEqual([]); expect(p.requests).toEqual([]);
  });
  it("propagates a new navigator contact immediately through an already stalled neighbour", () => {
    const p = new CollisionPermissions(1 / 120);
    for (let step = 1; step <= 7; step++) p.observe(step, blockedBodies(), blockedPairs().slice(1));
    expect(p.requests).toHaveLength(1);
    p.observe(8, blockedBodies().map(b => ({ ...b, navigatorContact: b.id === "a" })), blockedPairs());
    expect(p.requests).toEqual([]); expect(p.events).toEqual([]);
  });
  it("rejects a prediction whose measured benefit does not justify its permissions", () => {
    const p = new CollisionPermissions(1 / 120);
    for (let step = 1; step <= 7; step++) p.observe(step, blockedBodies(), blockedPairs());
    p.acceptPrediction(7, { request: p.requests[0]!, selected: ["a/b"], baselineReduction: 0,
      selectedReduction: .001, trials: 2, simulatedSteps: 12 });
    expect(p.events).toEqual([]);
  });
  it("requires fresh physical contact evidence after separation and return", () => {
    const p = new CollisionPermissions(1 / 120), pairs = blockedPairs().slice(0, 1);
    for (let step = 1; step <= 7; step++) p.observe(step, blockedBodies(), pairs);
    approve(p, 7);
    for (let step = 8; step <= 15; step++) p.observe(step, blockedBodies().map(b => ({ ...b, error: 0 })), [{ ...pairs[0]!, contact: false, gap: 1 }]);
    p.observe(16, blockedBodies(), pairs); approve(p, 16);
    expect(p.events.filter(e => e.type === "ghost")).toHaveLength(1);
    p.observe(17, blockedBodies(), pairs); p.observe(18, blockedBodies(), pairs); approve(p, 18);
    expect(p.events.filter(e => e.type === "ghost").map(e => e.step)).toEqual([7, 18]);
  });
});

describe("deterministic isolated physics", () => {
  it("tolerates brief contact jitter only inside sustained calm motion and energy limits", () => {
    const calm = new CollisionPermissions(1 / 120), moving = new CollisionPermissions(1 / 120);
    const bodies = ["a", "b", "c", "d"].map(id => ({ id, mass: 1.25, inertia: 1000,
      speed: 1, angularSpeed: .02, energy: .825, error: 10, homeRequest: 12, navigatorContact: false }));
    const pairs = [["a", "b"], ["b", "c"], ["c", "d"]].map(([a, b]) => ({
      key: `${a}/${b}`, a: a!, b: b!, contact: true, overlap: 0, gap: 0, blocking: 12 }));
    for (let step = 1; step < 300; step++) {
      const observations = bodies.map(b => b.id === "a" && step % 12 === 0
        ? { ...b, speed: 7, angularSpeed: .24, energy: 59.425 } : b);
      calm.observe(step, observations, pairs); approve(calm, step);
      moving.observe(step, bodies.map(b => ({ ...b, speed: 10, energy: 62.7 })), pairs);
    }
    expect(calm.events.some(e => e.type === "ghost")).toBe(true);
    expect(moving.events).toEqual([]);
  });
  it("uses the same calm motion criterion across polygon mass scales", () => {
    for (const scale of [1, 1000]) {
      const permissions = new CollisionPermissions(1 / 120);
      const bodies = ["a", "b"].map(id => ({ id, mass: scale, inertia: 100 * scale,
        speed: 1, angularSpeed: .02, energy: .52 * scale,
        error: 10, homeRequest: 12, navigatorContact: false }));
      const pair = { key: "a/b", a: "a", b: "b", contact: true, overlap: 0, gap: 0, blocking: 12 };
      for (let step = 1; step < 260; step++) permissions.observe(step, bodies, [pair]);
      approve(permissions, 259);
      expect(permissions.ghosted("a/b"), `mass scale ${scale}`).toBe(true);
    }
  });
  it("recognizes rotational home motion blocked between solid neighbors and walls", () => {
    const core = new PhysicsCore([g("a", -23), g("b", 23)], { width: 92, height: 60 });
    core.loadFixture([{ id: "a", angle: Math.PI / 2 }, { id: "b", angle: Math.PI / 2 }]);
    core.stepFixed(3600);
    expect(core.getSnapshot().rest, JSON.stringify(core.getSnapshot().clusters)).toBe(true);
  });
  it("includes angular spring acceleration in the contact blocking observation", () => {
    const core = new PhysicsCore([g("a", -21), g("b", 21)]);
    core.loadFixture([{ id: "a", angle: 1.2 }, { id: "b", angle: 1.2 }]);
    expect(Math.hypot(...Object.values(core.homeForce(core.bodies[0]!).acceleration))).toBeLessThan(1e-10);
    expect(core.getSnapshot().pairs[0]!.blocking).toBeGreaterThan(100);
  });
  it("keeps navigator collision strict at an exact contour vertex", () => {
    const core = new PhysicsCore([g("a", 0)]);
    core.stepFixed(1, { x: 460, y: 290 });
    expect(core.getSnapshot().fragments[0]!.navigatorOverlap).toBeLessThan(.02);
  });
  it("reports solid pair permissions before the first tick", () => {
    const core = new PhysicsCore([g("a", -70), g("b", 70)]);
    expect(core.getSnapshot().pairs[0]!.state).toBe("SOLID");
    expect(core.getSnapshot().pairs[0]!.eligible).toBe(false);
  });
  it("returns detached read-only telemetry rather than collider references", () => {
    const core = new PhysicsCore([g("a", 0)]);
    const snapshot = core.getSnapshot();
    const point = snapshot.fragments[0]!.vertices[0]!;
    (point as { x: number }).x = 1e9;
    snapshot.fragments[0]!.position.x = 1e9;
    expect(core.getSnapshot().rest).toBe(true);
    expect(core.getSnapshot().fragments[0]!.vertices[0]!.x).not.toBe(1e9);
  });
  it("has exact zero activity at canonical rest", () => {
    const core = new PhysicsCore([g("a", -70), g("b", 70)]);
    core.stepFixed(1200);
    expect(core.getSnapshot().rest).toBe(true);
    expect(core.energy()).toBe(0);
    expect(core.permissions.events).toEqual([]);
  });
  it("bounds navigator transfer smoothly", () => {
    expect(navigatorTransfer(100)).toBeLessThan(navigatorTransfer(400));
    expect(navigatorTransfer(400)).toBeLessThan(navigatorTransfer(1600));
    expect(navigatorTransfer(1e12)).toBe(720);
    expect(navigatorTransfer(3200) - navigatorTransfer(1600)).toBeLessThan(navigatorTransfer(800) - navigatorTransfer(400));
  });
  it("rotates during travel using distributed anchors", () => {
    const core = new PhysicsCore([g("a", 0)]);
    core.loadFixture([{ id: "a", position: { x: 620, y: 410 }, angle: 1 }]);
    core.stepFixed(60);
    expect(Math.abs(core.bodies[0]!.angle)).toBeLessThan(1);
    expect(core.homeError(core.bodies[0]!)).toBeGreaterThan(1);
    core.stepFixed(3000);
    expect(core.getSnapshot().rest).toBe(true);
  });
  it("reproduces a genuine contact deadlock before enabling the remedy", () => {
    const core = new PhysicsCore([g("a", -70), g("b", 70)], { deadlock: false });
    core.loadFixture([{ id: "a", position: { x: 530, y: 310 } }, { id: "b", position: { x: 430, y: 310 } }]);
    core.stepFixed(3000);
    const s = core.getSnapshot();
    expect(s.rest).toBe(false);
    expect(s.energy).toBeLessThan(8);
    expect(s.clusters.some(c => c.deadlocked)).toBe(true);
  });
  it("resolves that same deadlock through latched permission only", () => {
    const core = new PhysicsCore([g("a", -70), g("b", 70)]);
    core.loadFixture([{ id: "a", position: { x: 530, y: 310 } }, { id: "b", position: { x: 430, y: 310 } }]);
    let overlappingGhost = false;
    for (let i = 0; i < 3000; i++) {
      core.stepFixed();
      const s = core.getSnapshot();
      expect(s.ledger.permissions).toBe(0);
      if (s.pairs.some(p => p.overlap > .1 && p.state === "GHOSTED_LATCHED")) overlappingGhost = true;
      for (const event of s.events.filter(e => e.step === s.step && e.type === "rearm")) {
        expect(event.overlap).toBe(0);
        expect(event.gap).toBeGreaterThanOrEqual(.65);
        expect(event.separatedSteps).toBeGreaterThanOrEqual(8);
      }
    }
    expect(overlappingGhost).toBe(true);
    expect(core.permissions.events.map(e => e.type)).toEqual(["ghost", "rearm"]);
    expect(core.getSnapshot().rest).toBe(true);
    expect(core.permissions.failures).toEqual([]);
  });
  it("conserves exact contour area under decomposition", () => {
    const concave = [{ x: 0, y: 0 }, { x: 40, y: 0 }, { x: 40, y: 10 },
      { x: 10, y: 10 }, { x: 10, y: 40 }, { x: 0, y: 40 }];
    const parts = decompose(concave);
    expect(parts.reduce((sum, p) => sum + polygonProperties(p).area, 0)).toBeCloseTo(polygonProperties(concave).area, 8);
    const small = square.map(p => ({ x: p.x / 4 + 25, y: p.y / 4 + 25 }));
    expect(polygonContact(concave, small, parts, [small])).toBeNull();
    const moved = transform(small, { x: -20, y: -20 }, 0);
    expect(polygonContact(concave, moved, parts, [moved])).not.toBeNull();
  });
  it("keeps the latch through overlap, timeout and navigator activity", () => {
    const p = new CollisionPermissions(1 / 120);
    const bodies = ["a", "b"].map(id => Object.freeze({ id, mass: 1, inertia: 1, speed: 0, angularSpeed: 0,
      energy: 0, error: 10, homeRequest: 12, navigatorContact: false }));
    const pair = { key: "a/b", a: "a", b: "b", contact: true, overlap: 0, gap: 0, blocking: 12 };
    for (let step = 1; step < 260; step++) p.observe(step, bodies, [pair]);
    approve(p, 259);
    expect(p.ghosted("a/b")).toBe(true);
    for (let step = 260; step < 3500; step++) p.observe(step,
      bodies.map(b => Object.freeze({ ...b, navigatorContact: step > 3300 })), [{ ...pair, overlap: 10 }]);
    expect(p.ghosted("a/b")).toBe(true);
    expect(p.failures).toHaveLength(1);
    for (let step = 3500; step < 3507; step++) p.observe(step, bodies.map(b => ({ ...b, error: 0 })), [{ ...pair, contact: false, overlap: 0, gap: 1 }]);
    expect(p.ghosted("a/b")).toBe(true);
    p.observe(3507, bodies.map(b => ({ ...b, error: 0 })), [{ ...pair, contact: false, overlap: 0, gap: 1 }]);
    expect(p.ghosted("a/b")).toBe(false);
  });
  it("does not let any observation bypass the forecast decision", () => {
    const permissions = new CollisionPermissions(1 / 120);
    for (let step = 1; step <= 300; step++) permissions.observe(step, blockedBodies(), blockedPairs());
    expect(permissions.requests).toHaveLength(1); expect(permissions.events).toEqual([]);
  });
});

it("bounds long-session event storage while preserving cumulative transitions and opt-in replay logs", () => {
  for (const limit of [512, Infinity]) {
    const p = new CollisionPermissions(1 / 120, true, false, limit);
    let step = 0;
    for (let cycle = 0; cycle < 300; cycle++) {
      for (let i = 0; i < 7; i++) p.observe(++step, blockedBodies(), blockedPairs());
      approve(p, step);
      for (let i = 0; i < 8; i++) p.observe(++step, blockedBodies().map(b => ({ ...b, error: 0 })), blockedPairs().map(pair => ({ ...pair, contact: false, gap: 1 })));
    }
    expect(p.eventCount).toBe(1200);
    expect(p.events).toHaveLength(limit === Infinity ? 1200 : 512);
    expect(p.events.at(-1)!.step).toBe(step);
    expect(p.snapshot().every(pair => pair.state === "SOLID")).toBe(true);
  }
});
