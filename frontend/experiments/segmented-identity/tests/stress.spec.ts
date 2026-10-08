import { test, expect } from "@playwright/test";
import type {} from "../test-support";

const seeds = process.env.IDENTITY_SEED ? [Number(process.env.IDENTITY_SEED)] : Array.from({ length: 100 }, (_, i) => i + 1);
for (const seed of seeds) {
  test(`seed ${seed}: browser stress reaches REST`, async ({ page }, info) => {
    await page.goto("/?test=1"); await page.waitForFunction(() => !!window.__identityTest);
    const result = await page.evaluate(seed => {
      const result = window.__identityTest.runSeed(seed);
      return { seed, passed: result.passed, failure: result.failure, time: result.snapshot.time,
        maximumEnergy: result.maximumEnergy, events: result.snapshot.events,
        finalError: Math.max(...result.snapshot.fragments.map(b => b.homeAnchorError)),
        failureFixture: result.passed ? undefined : window.__identityTest.seedFixture(seed),
        failureSnapshot: result.passed ? undefined : result.snapshot };
    }, seed);
    await info.attach(`seed-${seed}.json`, { body: JSON.stringify(result, null, 2), contentType: "application/json" });
    expect(result.passed, `seed ${seed}: ${result.failure}`).toBe(true);
  });
}
