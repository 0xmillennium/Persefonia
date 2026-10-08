import { describe, it, expect } from "vitest";
import { FORECAST, GroupForecast, selectRelease } from "../forecast";
import type { ForecastResult } from "../forecast";
import { PhysicsCore } from "../core";
import { polygonProperties } from "../math";
import { canonicalGeometry } from "../geometry";
import selectionFixture from "./regression-release-selection.json";
import { CollisionPermissions } from "../deadlock";
import topologyFixture from "./regression-contact-topology.json";
import currentContactFixture from "./regression-current-contact-batch.json";

const result = (keys: string[], potential: number, safe = true): ForecastResult => ({ keys, potential, safe });
const request = { step: 10, key: "a|b|c", members: ["a", "b", "c"], candidates: ["a/b", "b/c"] };

describe("bounded group counterfactual selection", () => {
  it("preserves normal recovery even if removing contacts would make it faster", () => {
    expect(selectRelease(100, result([], 95), [result(["a/b"], 50)])).toBeNull();
  });
  it("chooses useful progress instead of the first nominated contact", () => {
    expect(selectRelease(100, result([], 99.9), [result(["a/b"], 98), result(["b/c"], 85)])?.keys).toEqual(["b/c"]);
  });
  it("prefers fewer permissions when their benefit is within five percent of the best", () => {
    expect(selectRelease(100, result([], 100), [result(["a/b"], 81), result(["a/b", "b/c"], 80)])?.keys).toEqual(["a/b"]);
  });
  it("selects a jointly necessary release when individual changes cannot help", () => {
    expect(selectRelease(100, result([], 100), [result(["a/b"], 100), result(["b/c"], 100), result(["a/b", "b/c"], 80)])?.keys).toEqual(["a/b", "b/c"]);
  });
  it("rejects unsafe and non-finite alternatives", () => {
    expect(selectRelease(100, result([], 100), [result(["a/b"], 0, false), result(["b/c"], NaN)])).toBeNull();
    expect(selectRelease(NaN, result([], 100), [result(["a/b"], 0)])).toBeNull();
  });
  it("tests joint choices independently of unsuccessful singles and obeys each work budget", () => {
    let calls = 0;
    const search = new GroupForecast(request, keys => {
      let steps = 0;
      return { advance: () => { steps++; calls++; },
        sample: () => ({ potential: keys.length === 2 && steps >= FORECAST.horizonSteps ? 80 : 100, safe: true }) };
    });
    let decision;
    for (let i = 0; i < 100 && !decision; i++) {
      const before = calls; decision = search.advance(3); expect(calls - before).toBeLessThanOrEqual(3);
    }
    expect(decision?.selected).toEqual(["a/b", "b/c"]);
    expect(decision?.trials).toBe(4); expect(decision?.simulatedSteps).toBe(4 * FORECAST.horizonSteps);
  });
  it("stops after the baseline when natural progress is sufficient", () => {
    let created = 0;
    const search = new GroupForecast(request, () => {
      created++; let steps = 0;
      return { advance: () => { steps++; }, sample: () => ({ potential: 100 - steps, safe: true }) };
    });
    let decision = search.advance();
    while (!decision) decision = search.advance();
    expect(created).toBe(1); expect(decision.selected).toEqual([]);
  });
});

const square = [{ x: -20, y: -20 }, { x: 20, y: -20 }, { x: 20, y: 20 }, { x: -20, y: 20 }];
const geometry = ["a", "b", "c"].map((id, i) => ({ id, vertices: square, parts: [square], anchors: square,
  home: { x: (i - 1) * 90, y: 0 }, mass: 1, inertia: polygonProperties(square).inertiaPerMass }));

it("the real solver releases a three-piece contact group concurrently without changing live velocities", () => {
  const core = new PhysicsCore(geometry);
  core.loadFixture([{ id: "a", position: { x: 520, y: 310 } }, { id: "b", position: { x: 480, y: 310 } },
    { id: "c", position: { x: 440, y: 310 } }]);
  let firstRelease = 0;
  for (let i = 0; i < 3000; i++) {
    core.stepFixed();
    expect(core.ledger.permissions).toBe(0);
    if (!firstRelease && core.permissions.events.some(e => e.type === "ghost")) {
      const events = core.permissions.events.filter(e => e.type === "ghost");
      firstRelease = events[0]!.step;
      expect(events.map(e => e.key).sort()).toEqual(["a/b", "b/c"]);
      expect(events.every(e => e.step === firstRelease)).toBe(true);
    }
    if (core.getSnapshot().rest) break;
  }
  expect(firstRelease).toBeGreaterThan(0); expect(firstRelease / 120).toBeLessThan(.25);
  expect(core.getSnapshot().rest).toBe(true); expect(core.permissions.failures).toEqual([]);
});


it("rejects the former seed-2 wrong release despite its pair-local progress veto on the useful contact", () => {
  // Captured immediately before the previous detector chose upper/right-flank.
  // Only this physically disconnected left glyph participates in the fixture.
  const core = new PhysicsCore(canonicalGeometry.filter(g => g.id.startsWith("left-")));
  core.loadFixture(selectionFixture);
  for (let step = 0; step < 120 && !core.permissions.events.length; step++) core.stepFixed();
  expect(core.permissions.events.filter(e => e.type === "ghost").map(e => e.key)).toEqual(["left-cap-upper/left-flank-left"]);
  expect(core.ledger.permissions).toBe(0);
  core.stepFixed(3000);
  expect(core.getSnapshot().rest).toBe(true);
});

it("cancels an unfinished forecast when the navigator changes", () => {
  const core = new PhysicsCore(geometry);
  core.loadFixture([{ id: "a", position: { x: 520, y: 310 } }, { id: "b", position: { x: 480, y: 310 } },
    { id: "c", position: { x: 440, y: 310 } }]);
  core.stepFixed(7);
  expect(core.forecastWork.started).toBe(1); expect(core.permissions.events).toEqual([]);
  core.stepFixed(1, { x: 480, y: 310 });
  expect(core.forecastWork.cancelled).toBe(1); expect(core.permissions.events).toEqual([]);
});

it("cloned permission state preserves latches and cannot mutate its source", () => {
  const source = [{ key: "a/b", a: "a", b: "b", state: "GHOSTED_LATCHED" as const,
    contactSteps: 20, eligible: false, startStep: 10, separatedSteps: 3, rearmMargin: true },
  { key: "b/c", a: "b", b: "c", state: "SOLID" as const, contactSteps: 20, eligible: false,
    startStep: null, separatedSteps: 0, rearmMargin: false }];
  const original = structuredClone(source), copy = CollisionPermissions.forPrediction(1 / 120, source, ["b/c"], 30);
  expect(copy.snapshot()[0]).toEqual(source[0]); expect(copy.ghosted("b/c")).toBe(true);
  for (let step = 31; step <= 38; step++) copy.observe(step, [], source.map(p => ({
    key: p.key, a: p.a, b: p.b, contact: false, overlap: 0, gap: 1, blocking: 0 })));
  expect(copy.ghosted("a/b")).toBe(false); expect(copy.ghosted("b/c")).toBe(false);
  expect(source).toEqual(original);
});

it("does not grant speculative permissions during normal near-contact home recovery", () => {
  const core = new PhysicsCore(geometry.slice(0, 2).map((g, i) => ({ ...g, home: { x: i ? 21 : -21, y: 0 } })));
  core.loadFixture([{ id: "a", position: { x: 456, y: 310 } }, { id: "b", position: { x: 499, y: 310 } }]);
  core.stepFixed(2000);
  expect(core.getSnapshot().rest).toBe(true); expect(core.permissions.events).toEqual([]);
});


it("finishes a group forecast despite fluctuating contact-normal scores in a real stalled island", () => {
  const core = new PhysicsCore(canonicalGeometry);
  core.loadFixture(topologyFixture);
  for (let step = 0; step < 60 && !core.permissions.events.length; step++) core.stepFixed();
  expect(core.permissions.events.some(e => e.type === "ghost")).toBe(true);
  expect(core.forecastWork.cancelled).toBeLessThan(5);
  let failure = false;
  for (let step = 0; step < 2000 && !core.getSnapshot().rest; step++) {
    core.stepFixed();
    const s = core.getSnapshot(); failure ||= !s.finite || !s.contained || s.ledger.permissions !== 0 || !!s.failures.length;
  }
  expect(failure).toBe(false); expect(core.getSnapshot().rest).toBe(true);
});

it("prefers a useful available release over a better speculative set that cannot be committed", () => {
  expect(selectRelease(100, result([], 100), [{ ...result(["a/b"], 85), canCommit: true },
    { ...result(["a/b", "b/c"], 70), canCommit: false }])?.keys).toEqual(["a/b"]);
});

it("always tests the full current-contact batch alongside the speculative batch", () => {
  const plans: string[][] = [];
  const search = new GroupForecast({ ...request, candidates: ["a/b", "b/c", "a/c"], present: ["b/c", "a/c"] }, keys => {
    plans.push([...keys]); return { advance: () => {}, sample: () => ({ potential: 100, safe: true }) };
  });
  while (!search.advance()) { /* deterministic bounded incremental search */ }
  expect(plans).toContainEqual(["a/c", "b/c"]); expect(plans).toContainEqual(["a/b", "a/c", "b/c"]);
});

it("acts on available contacts in a nine-piece island without waiting for merely nearby contacts", () => {
  const core = new PhysicsCore(canonicalGeometry); core.loadFixture(currentContactFixture);
  for (let step = 0; step < 90 && !core.permissions.events.length; step++) core.stepFixed();
  const releases = core.permissions.events.filter(e => e.type === "ghost");
  expect(releases.length).toBeGreaterThan(0);
  expect(releases.every(e => e.step === releases[0]!.step)).toBe(true);
  expect(releases.some(e => e.key === "left-flank-right/right-flank-right" || e.key === "right-cap-lower/right-flank-left")).toBe(false);
  core.stepFixed(2500);
  expect(core.getSnapshot().rest).toBe(true); expect(core.permissions.failures).toEqual([]);
});

it("detects an internal three-piece jam while its center is still travelling quickly", () => {
  const core = new PhysicsCore(geometry);
  core.loadFixture([{ id: "a", position: { x: 520, y: 310 }, velocity: { x: 100, y: 0 } },
    { id: "b", position: { x: 480, y: 310 }, velocity: { x: 100, y: 0 } },
    { id: "c", position: { x: 440, y: 310 }, velocity: { x: 100, y: 0 } }]);
  core.stepFixed(7);
  expect(core.energy()).toBeGreaterThan(1000);
  expect(core.permissions.requests[0]!.members).toEqual(["a", "b", "c"]);
  expect(core.permissions.clusters[0]!.rmsRelativeSpeed).toBeLessThan(.01);
  for (let step = 0; step < 30 && !core.permissions.events.length; step++) core.stepFixed();
  expect(core.permissions.events.filter(e => e.type === "ghost").map(e => e.key).sort()).toEqual(["a/b", "b/c"]);
  expect(core.permissions.events[0]!.step / 120).toBeLessThan(.25);
  expect(core.ledger.permissions).toBe(0);
  core.stepFixed(2500); expect(core.getSnapshot().rest).toBe(true);
});

it("leaves a correctly arranged cluster's common home translation physical", () => {
  const core = new PhysicsCore(geometry.slice(0, 2).map((g, i) => ({ ...g, home: { x: i ? 21 : -21, y: 0 } })));
  core.loadFixture([{ id: "a", position: { x: 479, y: 310 }, velocity: { x: 100, y: 0 } },
    { id: "b", position: { x: 521, y: 310 }, velocity: { x: 100, y: 0 } }]);
  core.stepFixed(2000);
  expect(core.getSnapshot().rest).toBe(true); expect(core.permissions.events).toEqual([]);
  expect(core.forecastWork.started).toBe(0);
});

it("accepts useful response near a weak-torque orientation without requiring one percent of its large absolute potential", () => {
  expect(selectRelease(86_342, result([], 86_343), [result(["a/b"], 86_056)], 17_708)?.keys).toEqual(["a/b"]);
});

it("ranks complete escapes using their projected recovery rather than only immediate displacement", () => {
  const single = { ...result(["a/b"], 86_056), residualBlocks: 0, futurePotential: 11_560 };
  const joint = { ...result(["a/b", "b/c"], 85_816), residualBlocks: 0, futurePotential: 69_727 };
  expect(selectRelease(86_342, result([], 86_343), [single, joint], 17_708)?.keys).toEqual(["a/b"]);
});

it("does not starve a jam while a remote navigator keeps moving", () => {
  const core = new PhysicsCore(geometry);
  core.loadFixture([{ id: "a", position: { x: 520, y: 310 } }, { id: "b", position: { x: 480, y: 310 } },
    { id: "c", position: { x: 440, y: 310 } }]);
  for (let step = 0; step < 240; step++) core.stepFixed(1, { x: 20 + step * .25, y: 20 });
  const ghosts = core.permissions.events.filter(e => e.type === "ghost");
  expect(ghosts.slice(0, 2).map(e => e.key).sort()).toEqual(["a/b", "b/c"]);
  expect(ghosts[0]!.step).toBe(ghosts[1]!.step);
  expect(ghosts[0]!.step / 120).toBeLessThan(.25);
  expect(core.bodies.some(b => b.navigatorContact)).toBe(false);
});
it("cancels a search when relative velocity changes even before poses drift", () => {
  const core = new PhysicsCore(geometry);
  core.loadFixture([{ id: "a", position: { x: 520, y: 310 } }, { id: "b", position: { x: 480, y: 310 } },
    { id: "c", position: { x: 440, y: 310 } }]);
  core.stepFixed(7); expect(core.forecastWork.started).toBe(1);
  core.bodies[0]!.angularVelocity = 4;
  core.stepFixed();
  expect(core.forecastWork.cancelled).toBeGreaterThan(0); expect(core.permissions.events).toEqual([]);
});
it("revalidates the baseline and selected group using twelve fresh shadow steps", () => {
  const core = new PhysicsCore(geometry);
  core.loadFixture([{ id: "a", position: { x: 520, y: 310 } }, { id: "b", position: { x: 480, y: 310 } },
    { id: "c", position: { x: 440, y: 310 } }]);
  core.stepFixed(18); expect(core.permissions.events).toEqual([]);
  expect(core.forecastWork.started).toBe(2);
  const work = core.forecastWork.simulatedSteps;
  core.stepFixed(6);
  expect(core.forecastWork.simulatedSteps - work).toBe(12);
  expect(core.permissions.events.filter(e => e.type === "ghost")).toHaveLength(2);
});

it("gives disconnected stalled islands turns rather than repeatedly preferring the first key", () => {
  const groups = ["a", "b"].flatMap((prefix, group) => geometry.map((g, i) => ({ ...g, id: `${prefix}${i}`,
    home: { x: g.home.x, y: group ? 100 : -100 } })));
  const core = new PhysicsCore(groups);
  core.loadFixture(groups.map((g, i) => ({ id: g.id, position: { x: 520 - i % 3 * 40, y: i < 3 ? 210 : 410 } })));
  core.stepFixed(80);
  const ghosts = core.permissions.events.filter(e => e.type === "ghost");
  expect(ghosts.some(e => e.key.startsWith("a"))).toBe(true);
  expect(ghosts.some(e => e.key.startsWith("b"))).toBe(true);
  expect(core.permissions.failures).toEqual([]);
});

it("invalidates live validation when a nearby outsider gains incoming velocity before contact", () => {
  const outsider = { ...geometry[0]!, id: "d", home: { x: 220, y: 0 } };
  const core = new PhysicsCore([...geometry, outsider]);
  core.loadFixture([{ id: "a", position: { x: 520, y: 310 } }, { id: "b", position: { x: 480, y: 310 } },
    { id: "c", position: { x: 440, y: 310 } }]);
  core.stepFixed(18); expect(core.forecastWork.started).toBe(2);
  const body = core.bodies[3]!, before = { ...body.position };
  body.velocity = { x: -300, y: 0 };
  core.stepFixed(1, undefined, 0);
  expect(Math.hypot(body.position.x - before.x, body.position.y - before.y)).toBeLessThan(3);
  expect(body.contacts).toEqual([]);
  expect(core.forecastWork.cancelled).toBe(1); expect(core.permissions.events).toEqual([]);
});
