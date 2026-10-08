import { it, expect } from "vitest";
import { createSimulation, applyPath, invariantFailure } from "../scenarios";
import seed26 from "./regression-seed-26.json";
import seed27 from "./regression-seed-27.json";
import seed30 from "./regression-seed-30.json";
import seed45 from "./regression-seed-45.json";
import seed58 from "./regression-seed-58.json";
import seed74 from "./regression-seed-74.json";

it.each([seed26, seed27, seed30, seed45, seed58, seed74])("preserved seed $seed reaches REST", seed => {
  const core = createSimulation(); core.loadFixture(seed.fixture.fragments); applyPath(core, seed.fixture.path);
  let failure: string | null = null;
  for (let step = 0; step < 5400; step++) {
    core.stepFixed(); const s = core.getSnapshot(); failure = invariantFailure(s);
    if (failure || s.rest) break;
  }
  expect(failure, `seed ${seed.seed}`).toBeNull();
  expect(core.getSnapshot().rest, `seed ${seed.seed}`).toBe(true);
});
