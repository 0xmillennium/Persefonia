import { expect, it } from "vitest";
import { PhysicsCore, FIXED_DT } from "../core";
import { CollisionPermissions } from "../deadlock";
import type { GhostState } from "../deadlock";
import { fragmentGeometry } from "../geometry-loader";

const square = [{ x: -20, y: -20 }, { x: 20, y: -20 }, { x: 20, y: 20 }, { x: -20, y: 20 }];
const geometry = ["a", "b", "c"].map((id, i) => fragmentGeometry(id,
  square.map(p => ({ x: p.x + (i - 1) * 80, y: p.y })), [0, 1, 2]));

it.each([0, 7])("live and shadow returns propagate navigator contact through solid neighbours, clearance ticks %i", separatedSteps => {
  const states: GhostState[] = [["a", "b"], ["a", "c"], ["b", "c"]].map(([a, b]) => ({
    key: `${a}/${b}`, a: a!, b: b!, state: a === "a" && b === "b" ? "GHOSTED_LATCHED" : "SOLID",
    contactSteps: 0, eligible: false, startStep: a === "a" && b === "b" ? 0 : null,
    separatedSteps: a === "a" && b === "b" ? separatedSteps : 0, rearmMargin: false,
    ...(a === "a" && b === "b" ? { cohort: "a|b", returnStartStep: 0 } : {})
  }));
  const live = new PhysicsCore(geometry, { deadlock: false });
  // Prepare a captured return state without granting a new speculative release.
  // Exercise the complete core observation pipelines, not identical arrays
  // supplied directly to two permission controllers.
  const permissions = live.permissions as unknown as { pairs: Map<string, GhostState> };
  permissions.pairs = new Map(states.map(p => [p.key, { ...p }]));
  const shadow = new PhysicsCore(geometry, {
    forecastPermissions: CollisionPermissions.forPrediction(FIXED_DT, states, [], 0)
  });
  for (const core of [live, shadow]) {
    core.loadFixture([{ id: "a", position: { x: 360, y: 310 } },
      { id: "b", position: { x: 450, y: 310 } }, { id: "c", position: { x: 490, y: 310 } }]);
    core.navigator.active = true; core.navigator.position = { x: 520, y: 310 };
    core.stepFixed(1, undefined, 0);
  }
  expect(live.bodies.map(b => b.navigatorContact)).toEqual([false, false, true]);
  expect(live.permissions.snapshot().find(p => p.key === "a/b")!.state)
    .toBe(separatedSteps === 7 ? "SOLID" : "GHOSTED_LATCHED");
  expect(shadow.permissions.snapshot()).toEqual(live.permissions.snapshot());
  expect(shadow.bodies).toEqual(live.bodies);
  expect(shadow.ledger).toEqual(live.ledger);
});
