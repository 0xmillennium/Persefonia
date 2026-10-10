import { chromium, firefox, webkit } from "@playwright/test";
import { mkdir, writeFile } from "node:fs/promises";
import { createHash } from "node:crypto";

// Run against an already running experiment without competing test workers.
// Callback costs exclude layout/paint/GPU work and are not delivered FPS.
const name = process.argv[2] ?? "chromium";
const engine = { chromium, firefox, webkit }[name];
if (!engine) throw new Error("Use chromium, firefox or webkit");
const coreOnly = process.argv.includes("--core");
if (coreOnly && name !== "chromium") throw new Error("Renderer-process CPU measurement requires Chromium");
const browser = await engine.launch(name === "chromium" ? { channel: "chromium" } : {});
const runs = [];
const targets = [process.env.IDENTITY_PROFILE_URL ?? "http://127.0.0.1:4178/"];
if (process.env.IDENTITY_PROFILE_COMPARE_URL) targets.push(process.env.IDENTITY_PROFILE_COMPARE_URL);
const cdp = name === "chromium" ? await browser.newBrowserCDPSession() : null;
const rendererCpu = async () => cdp ? (await cdp.send("SystemInfo.getProcessInfo")).processInfo.filter(p => p.type === "renderer") : [];
const cpuDelta = (before, after) => {
  if (!cdp) return { stableRendererProcesses: null, rendererCpuMs: null };
  const sameProcesses = before.length === after.length && before.every(p => after.some(q => q.id === p.id));
  return { stableRendererProcesses: sameProcesses,
    rendererCpuMs: sameProcesses ? 1000 * after.reduce((sum, p) => sum + p.cpuTime - before.find(q => q.id === p.id).cpuTime, 0) : null };
};
const gpu = cdp ? (await cdp.send("SystemInfo.getInfo")).gpu : null;
const environment = gpu ? { devices: gpu.devices, renderer: gpu.auxAttributes?.glRenderer, vendor: gpu.auxAttributes?.glVendor } : null;
const percentile = (samples, fraction) => [...samples].sort((a, b) => a - b)[Math.ceil(samples.length * fraction) - 1] ?? null;
try {
  if (coreOnly) {
    // Fixed work isolates CPU savings from scheduling, dropped time and different
    // callback counts. Alternate target order and warm each page before sampling.
    for (let repeat = 0; repeat < 8; repeat++) for (const target of repeat % 2 ? [...targets].reverse() : targets) {
      const page = await browser.newPage();
      const url = new URL(target); url.searchParams.set("test", "1");
      await page.goto(url.href); await page.waitForFunction(() => !!window.__identityTest);
      await page.evaluate(async () => {
        const { PhysicsCore } = await import("/core.ts"), { canonicalGeometry } = await import("/geometry.ts");
        const { seedFixture } = await import("/scenarios.ts");
        window.identityCpuRun = scenario => {
          const core = new PhysicsCore(canonicalGeometry), failures = [];
          const check = () => { const failure = core.runtimeFailure?.(); if (failure && !failures.includes(failure)) failures.push(failure); };
          const start = performance.now();
          if (scenario === "jam") {
            const fixture = seedFixture(1); core.loadFixture(fixture.fragments);
            for (const p of fixture.path) { core.stepFixed(1, p, 2); check(); }
          } else {
            core.stepStroke([{ x: 280, y: 310 }], 2); check();
            for (let i = 0; i < 16; i++) {
              core.stepStroke([{ x: 480 + 200 * Math.sin(i * 1.41), y: 310 + 100 * Math.cos(i * 1.17), elapsedSeconds: .008 }], 2); check();
            }
            core.stepStroke([{ x: 0, y: 0, active: false }], 2); check();
          }
          for (let i = 0; i < 600; i++) { core.stepFixed(1, undefined, 2); check(); }
          const elapsedMs = performance.now() - start, s = core.getSnapshot();
          return { elapsedMs, failures: [...failures, ...s.failures], forecastWork: s.forecastWork,
            state: { fragments: s.fragments, permissions: core.permissions.snapshot(), events: s.events, ledger: s.ledger, rest: s.rest } };
        };
      });
      for (const scenario of ["jam", "stroke"]) {
        await page.evaluate(scenario => { window.identityCpuRun(scenario); window.identityCpuRun(scenario); }, scenario);
        const before = await rendererCpu();
        const result = await page.evaluate(scenario => window.identityCpuRun(scenario), scenario);
        const after = await rendererCpu();
        const { state, ...metrics } = result;
        runs.push({ repeat, target, scenario, ...cpuDelta(before, after), ...metrics,
          stateHash: createHash("sha256").update(JSON.stringify(state)).digest("hex") });
      }
      await page.close();
    }
  } else for (let repeat = 0; repeat < 3; repeat++) for (const target of repeat % 2 ? [...targets].reverse() : targets) for (const diagnostics of [false, true]) {
    const page = await browser.newPage({ viewport: { width: 1200, height: 900 } });
    await page.addInitScript(() => {
      const durations = [], intervals = [], longTasks = [], request = window.requestAnimationFrame.bind(window);
      Object.defineProperty(window, "identityProfile", { value: durations });
      Object.assign(window, { identityIntervals: intervals, identityLongTasks: longTasks });
      let inside = false, lastTime = null;
      window.requestAnimationFrame = callback => {
        const continuous = inside;
        return request(time => {
          if (continuous && lastTime !== null) intervals.push(time - lastTime);
          lastTime = time; inside = true;
          const start = performance.now();
          try { callback(time); } finally { inside = false; durations.push(performance.now() - start); }
        });
      };
      if (PerformanceObserver.supportedEntryTypes.includes("longtask"))
        new PerformanceObserver(list => longTasks.push(...list.getEntries().map(e => e.duration))).observe({ type: "longtask" });
    });
    await page.goto(target);
    await page.locator("#status").filter({ hasText: /^REST$/ }).waitFor();
    if (diagnostics) await page.locator("summary").click();
    const map = await page.locator(".stage").evaluate(svg => {
      window.identityLongTasks.length = 0;
      const arrival = [], nav = svg.querySelector(".navigator");
      let pending;
      svg.addEventListener("pointermove", e => {
        const p = new DOMPoint(e.clientX, e.clientY).matrixTransform(svg.getScreenCTM().inverse());
        pending = { x: p.x, y: p.y, time: performance.now() };
      });
      new MutationObserver(() => {
        if (pending && Math.abs(Number(nav.getAttribute("cx")) - pending.x) < .02
          && Math.abs(Number(nav.getAttribute("cy")) - pending.y) < .02) {
          arrival.push(performance.now() - pending.time); pending = undefined;
        }
      }).observe(nav, { attributes: true });
      Object.assign(window, { identityArrival: arrival });
      const m = svg.getScreenCTM(); return { a: m.a, d: m.d, e: m.e, f: m.f };
    });
    const before = await rendererCpu();
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
    const after = await rendererCpu();
    const observations = await page.evaluate(() => ({ intervals: window.identityIntervals,
      longTasks: window.identityLongTasks, arrival: window.identityArrival, status: document.querySelector("#status").textContent }));
    if (observations.status.startsWith("Static identity")) throw new Error("Runtime entered its failure fallback");
    runs.push({ repeat, target, diagnostics, ...cpuDelta(before, after), callbacks: samples.length, p50: percentile(samples, .5), p95: percentile(samples, .95),
      max: Math.max(...samples), over16ms: samples.filter(t => t > 1000 / 60).length,
      intervalP95: percentile(observations.intervals, .95), arrivalP95: percentile(observations.arrival, .95), ...observations, samples });
    await page.close();
  }
  const directory = new URL("../../test-results/segmented-identity/profile/", import.meta.url);
  await mkdir(directory, { recursive: true });
  await writeFile(new URL(`${name}${coreOnly ? "-core" : ""}.json`, directory), JSON.stringify({ browser: name, version: browser.version(), mode: coreOnly ? "fixed-work-core" : "native-runtime", environment,
    viewport: { width: 1200, height: 900 }, runs }, null, 2));
  console.log(runs.map(({ samples, intervals, arrival, longTasks, ...summary }) => ({ ...summary,
    ...(samples ? { arrivals: arrival.length, longTasks: longTasks.length } : {}) })));
} finally { await browser.close(); }
