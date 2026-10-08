import { it, expect, afterAll } from "vitest";
import { stressRun, seedFixture } from "../scenarios";
import { mkdirSync, writeFileSync } from "node:fs";

const seeds = process.env.IDENTITY_SEED ? [Number(process.env.IDENTITY_SEED)] : Array.from({ length: 100 }, (_, i) => i + 1);
const results: { seed: number; passed: boolean; failure: string | null; seconds: number; maximumEnergy: number; ghosts: number }[] = [];
afterAll(() => {
  mkdirSync("test-results/segmented-identity", { recursive: true });
  writeFileSync("test-results/segmented-identity/unit-stress-summary.json", JSON.stringify(results, null, 2));
});
it.each(seeds)("seed %i converges without invariant failures", seed => {
  const result = stressRun(seed);
  results.push({ seed, passed: result.passed, failure: result.failure, seconds: result.snapshot.time,
    maximumEnergy: result.maximumEnergy, ghosts: result.snapshot.events.filter(e => e.type === "ghost").length });
  if (!result.passed) {
    mkdirSync("test-results/segmented-identity/seeds", { recursive: true });
    writeFileSync(`test-results/segmented-identity/seeds/${seed}.json`, JSON.stringify({ fixture: seedFixture(seed), ...result }, null, 2));
  }
  expect(result.passed, `seed ${seed}: ${result.failure}; error ${Math.max(...result.snapshot.fragments.map(b => b.homeAnchorError))}; energy ${result.snapshot.energy}`).toBe(true);
});
