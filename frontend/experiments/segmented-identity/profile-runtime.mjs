import { chromium, firefox, webkit } from "@playwright/test";
import { mkdir, writeFile } from "node:fs/promises";

// Run against an already running experiment without competing test workers.
// Callback costs exclude layout/paint/GPU work and are not delivered FPS.
const name = process.argv[2] ?? "chromium";
const engine = { chromium, firefox, webkit }[name];
if (!engine) throw new Error("Use chromium, firefox or webkit");
const browser = await engine.launch(name === "chromium" ? { channel: "chromium" } : {});
const runs = [];
try {
  for (let repeat = 0; repeat < 3; repeat++) for (const diagnostics of [false, true]) {
    const page = await browser.newPage({ viewport: { width: 1200, height: 900 } });
    await page.addInitScript(() => {
      const durations = [], request = window.requestAnimationFrame.bind(window);
      Object.defineProperty(window, "identityProfile", { value: durations });
      window.requestAnimationFrame = callback => request(time => {
        const start = performance.now(); callback(time); durations.push(performance.now() - start);
      });
    });
    await page.goto(process.env.IDENTITY_PROFILE_URL ?? "http://127.0.0.1:4178/");
    await page.locator("#status").filter({ hasText: /^REST$/ }).waitFor();
    if (diagnostics) await page.locator("summary").click();
    const map = await page.locator(".stage").evaluate(svg => {
      const m = svg.getScreenCTM(); return { a: m.a, d: m.d, e: m.e, f: m.f };
    });
    await page.mouse.move(map.e + 280 * map.a, map.f + 310 * map.d);
    await page.waitForTimeout(50);
    for (let i = 0; i < 36; i++) {
      await page.mouse.move(map.e + (400 + 160 * Math.sin(i * .5)) * map.a,
        map.f + (310 + 100 * Math.cos(i * .5)) * map.d);
      await page.waitForTimeout(16);
    }
    await page.mouse.move(0, 0); await page.waitForTimeout(3000);
    const samples = await page.evaluate(() => window.identityProfile);
    if (!samples.length) throw new Error("No animation callbacks were exercised");
    const sorted = [...samples].sort((a, b) => a - b);
    const percentile = fraction => sorted[Math.ceil(sorted.length * fraction) - 1];
    runs.push({ repeat, diagnostics, callbacks: samples.length, p50: percentile(.5), p95: percentile(.95),
      max: sorted.at(-1), over16ms: samples.filter(t => t > 1000 / 60).length, samples });
    await page.close();
  }
  const directory = new URL("../../test-results/segmented-identity/profile/", import.meta.url);
  await mkdir(directory, { recursive: true });
  await writeFile(new URL(`${name}.json`, directory), JSON.stringify({ browser: name, version: browser.version(),
    viewport: { width: 1200, height: 900 }, runs }, null, 2));
  console.log(runs.map(({ samples, ...summary }) => summary));
} finally { await browser.close(); }
