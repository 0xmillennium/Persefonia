import { test, expect } from "@playwright/test";

const stagePoint = async (page: import("@playwright/test").Page, x: number, y: number) => {
  return page.locator(".stage").evaluate((svg, point) => {
    const mapped = new DOMPoint(point.x, point.y).matrixTransform((svg as SVGSVGElement).getScreenCTM()!);
    return { x: mapped.x, y: mapped.y };
  }, { x, y });
};
test("an out-and-back stroke delivered in one task is retained, and cancellation clears it", async ({ page }) => {
  await page.goto("/"); await expect(page.locator("#status")).toHaveText("REST");
  const start = await stagePoint(page, 280, 310), end = await stagePoint(page, 500, 310);
  await page.mouse.move(start.x, start.y); await page.waitForTimeout(30);
  await page.locator(".stage").evaluate((svg, points) => {
    for (const point of points) svg.dispatchEvent(new PointerEvent("pointermove", { clientX: point.x,
      clientY: point.y, pointerType: "mouse", isPrimary: true }));
  }, [end, start]);
  await expect(page.locator("#status")).toHaveText("In motion");
  await expect.poll(async () => Number(await page.locator(".navigator").getAttribute("cx"))).toBeCloseTo(280, 1);
  await page.locator(".stage").dispatchEvent("pointercancel");
  await expect(page.locator(".navigator")).toHaveAttribute("visibility", "hidden");
});
test("anchor diagnostics reuse nodes across frames and telemetry exposes bounded histories", async ({ page }) => {
  await page.goto("/"); await expect(page.locator("#status")).toHaveText("REST");
  await page.getByLabel("Show home anchors").check();
  const count = await page.locator(".anchors > *").count(); expect(count).toBeGreaterThan(0);
  await page.locator(".anchors > *").first().evaluate(node => node.setAttribute("data-reused", "true"));
  const start = await stagePoint(page, 280, 310), end = await stagePoint(page, 500, 310);
  await page.mouse.move(start.x, start.y); await page.mouse.move(end.x, end.y, { steps: 12 });
  await expect(page.locator("#status")).toHaveText("In motion");
  await expect(page.locator(".anchors > *").first()).toHaveAttribute("data-reused", "true");
  expect(await page.locator(".anchors > *").count()).toBe(count);
  await page.locator("summary").click();
  await expect.poll(async () => {
    const text = await page.locator("#telemetry").textContent();
    if (!text) return false;
    const snapshot = JSON.parse(text);
    return snapshot.events.length <= 512 && Number.isInteger(snapshot.eventCount) && Number.isInteger(snapshot.droppedPointerSamples);
  }).toBe(true);
});
test("a whole-stage stroke arrives promptly without a long animation callback", async ({ page, browserName }, info) => {
  await page.addInitScript(() => {
    const durations: number[] = [];
    const request = window.requestAnimationFrame.bind(window);
    window.requestAnimationFrame = callback => request(time => {
      const start = performance.now(); callback(time); durations.push(performance.now() - start);
    });
    Object.defineProperty(window, "identityFrameDurations", { value: durations });
  });
  await page.goto("/"); await expect(page.locator("#status")).toHaveText("REST");
  const start = await stagePoint(page, 50, 310), end = await stagePoint(page, 910, 310);
  await page.mouse.move(start.x, start.y); await page.waitForTimeout(40);
  await page.mouse.move(end.x, end.y);
  await expect.poll(async () => Number(await page.locator(".navigator").getAttribute("cx")), { timeout: 5000 }).toBeGreaterThan(880);
  const durations = await page.evaluate(() => (window as unknown as { identityFrameDurations: number[] }).identityFrameDurations);
  await info.attach(`frame-cost-${browserName}.json`, { body: JSON.stringify(durations), contentType: "application/json" });
  // Generous regression ceiling for CI hardware, not a claimed 60fps guarantee.
  expect(Math.max(...durations)).toBeLessThan(150);
});

test("native fast mouse movement reaches the rendered navigator without a replay tail", async ({ page, browserName }, info) => {
  const timings: { y: number; elapsed: number; updates: number }[] = [];
  for (const y of [100, 310]) for (let repeat = 0; repeat < 3; repeat++) {
    await page.goto("/"); await expect(page.locator("#status")).toHaveText("REST");
    const start = await stagePoint(page, 50, y), end = await stagePoint(page, 910, y);
    await page.mouse.move(0, 0);
    await page.mouse.move(start.x, start.y);
    await expect(page.locator(".navigator")).toHaveAttribute("visibility", "visible");
    await page.locator(".stage").evaluate(svg => {
      const nav = svg.querySelector(".navigator")!, state = { started: 0, elapsed: -1, updates: 0, x: NaN, y: NaN };
      Object.assign(window, { pointerArrival: state });
      svg.addEventListener("pointermove", e => {
        if (!state.started) state.started = performance.now();
        // Browser automation bridges may round native device coordinates.
        // Qualify tracking against the delivered event, not the requested point.
        const event = e as PointerEvent;
        const p = new DOMPoint(event.clientX, event.clientY).matrixTransform((svg as SVGSVGElement).getScreenCTM()!.inverse());
        state.x = p.x; state.y = p.y;
      }, { once: true });
      const observer = new MutationObserver(() => {
        if (!state.started) return;
        state.updates++;
        if (Math.abs(Number(nav.getAttribute("cx")) - state.x) < .02 && Math.abs(Number(nav.getAttribute("cy")) - state.y) < .02) {
          state.elapsed = performance.now() - state.started; observer.disconnect();
        }
      });
      observer.observe(nav, { attributes: true });
    });
    await page.mouse.move(end.x, end.y);
    await page.waitForFunction(() => (window as unknown as { pointerArrival: { elapsed: number } }).pointerArrival.elapsed >= 0);
    const result = await page.evaluate(() => (window as unknown as { pointerArrival: { elapsed: number; updates: number } }).pointerArrival);
    timings.push({ y, elapsed: result.elapsed, updates: result.updates });
    // CI allowance for input-to-DOM latency, separate from callback cost. The
    // previous 225+ ms replay policy fails this even in otherwise empty space.
    expect(result.elapsed).toBeLessThan(75); expect(result.updates).toBe(1);
  }
  await info.attach(`pointer-arrival-${browserName}.json`, { body: JSON.stringify(timings), contentType: "application/json" });
});

test("coalesced reversals are consumed on the first awake frame, and cancellation discards them", async ({ page }) => {
  await page.goto("/"); await expect(page.locator("#status")).toHaveText("REST");
  const start = await stagePoint(page, 280, 310), corner = await stagePoint(page, 680, 310), end = await stagePoint(page, 910, 100);
  await page.mouse.move(0, 0);
  await page.mouse.move(start.x, start.y);
  await expect(page.locator(".navigator")).toHaveAttribute("visibility", "visible");
  const result = await page.locator(".stage").evaluate((svg, points) => new Promise<{ x: number; y: number; expectedX: number; expectedY: number; moved: boolean }>(resolve => {
    const nodes = [...svg.querySelectorAll("[data-fragment]")], before = nodes.map(n => n.getAttribute("transform"));
    const observations = points.map(point => new PointerEvent("pointermove", { clientX: point.x, clientY: point.y,
      pointerType: "mouse", isPrimary: true }));
    const parent = observations.at(-1)!;
    const expected = new DOMPoint(parent.clientX, parent.clientY).matrixTransform((svg as SVGSVGElement).getScreenCTM()!.inverse());
    Object.defineProperty(parent, "getCoalescedEvents", { value: () => observations });
    svg.dispatchEvent(parent);
    requestAnimationFrame(() => {
      const nav = svg.querySelector(".navigator")!;
      resolve({ x: Number(nav.getAttribute("cx")), y: Number(nav.getAttribute("cy")), expectedX: expected.x, expectedY: expected.y,
        moved: nodes.some((n, i) => n.getAttribute("transform") !== before[i]) });
    });
  }), [corner, start, end]);
  expect(result.x).toBeCloseTo(result.expectedX, 4); expect(result.y).toBeCloseTo(result.expectedY, 4); expect(result.moved).toBe(true);
  await page.reload(); await expect(page.locator("#status")).toHaveText("REST");
  await page.mouse.move(0, 0);
  await page.mouse.move(start.x, start.y);
  await expect(page.locator(".navigator")).toHaveAttribute("visibility", "visible");
  const cancelled = await page.locator(".stage").evaluate((svg, point) => new Promise<{ hidden: boolean; moved: boolean }>(resolve => {
    const nodes = [...svg.querySelectorAll("[data-fragment]")], before = nodes.map(n => n.getAttribute("transform"));
    svg.dispatchEvent(new PointerEvent("pointermove", { clientX: point.x, clientY: point.y, pointerType: "mouse", isPrimary: true }));
    svg.dispatchEvent(new PointerEvent("pointercancel"));
    requestAnimationFrame(() => resolve({ hidden: svg.querySelector(".navigator")!.getAttribute("visibility") === "hidden",
      moved: nodes.some((n, i) => n.getAttribute("transform") !== before[i]) }));
  }), corner);
  expect(cancelled).toEqual({ hidden: true, moved: false });
  await page.reload(); await expect(page.locator("#status")).toHaveText("REST");
  await page.mouse.move(0, 0); await page.mouse.move(start.x, start.y);
  await expect(page.locator(".navigator")).toHaveAttribute("visibility", "visible");
  const reentered = await page.locator(".stage").evaluate((svg, points) => new Promise<{ x: number; y: number; expectedX: number; expectedY: number; moved: boolean }>(resolve => {
    const nodes = [...svg.querySelectorAll("[data-fragment]")], before = nodes.map(n => n.getAttribute("transform"));
    svg.dispatchEvent(new PointerEvent("pointermove", { clientX: points[0]!.x, clientY: points[0]!.y, pointerType: "mouse", isPrimary: true }));
    svg.dispatchEvent(new PointerEvent("pointercancel"));
    const fresh = new PointerEvent("pointermove", { clientX: points[1]!.x, clientY: points[1]!.y, pointerType: "mouse", isPrimary: true });
    const expected = new DOMPoint(fresh.clientX, fresh.clientY).matrixTransform((svg as SVGSVGElement).getScreenCTM()!.inverse());
    svg.dispatchEvent(fresh);
    requestAnimationFrame(() => {
      const nav = svg.querySelector(".navigator")!;
      resolve({ x: Number(nav.getAttribute("cx")), y: Number(nav.getAttribute("cy")), expectedX: expected.x, expectedY: expected.y,
        moved: nodes.some((n, i) => n.getAttribute("transform") !== before[i]) });
    });
  }), [corner, end]);
  expect(reentered.x).toBeCloseTo(reentered.expectedX, 4); expect(reentered.y).toBeCloseTo(reentered.expectedY, 4);
  expect(reentered.moved).toBe(false);
});

test("live batched strokes preserve strict physics and recover in the browser engine", async ({ page }) => {
  await page.goto("/?test=1"); await page.waitForFunction(() => !!window.__identityTest);
  const result = await page.evaluate(() => {
    const api = window.__identityTest, failures: string[] = [];
    api.stepStroke([{ x: 50, y: 100 }]);
    for (let i = 0; i < 16; i++) {
      api.stepStroke([{ x: 480 + 420 * Math.sin(i * 1.41), y: 310 + 220 * Math.cos(i * 1.17), elapsedSeconds: .008 },
        { x: 480 + 420 * Math.sin((i + 1) * 1.41), y: 310 + 220 * Math.cos((i + 1) * 1.17), elapsedSeconds: .008 }]);
      const s = api.getSnapshot();
      if (!s.finite || !s.contained || s.fragments.some(b => b.navigatorOverlap > .02)
        || s.pairs.some(p => p.state === "SOLID" && p.overlap > .3) || s.ledger.permissions !== 0) failures.push(`stroke ${i}`);
    }
    api.stepStroke([{ x: 0, y: 0, active: false }]);
    for (let i = 0; i < 5400 && !api.getSnapshot().rest; i++) api.stepFixed();
    const s = api.getSnapshot(); return { failures: [...failures, ...s.failures], rest: s.rest };
  });
  expect(result.failures).toEqual([]); expect(result.rest).toBe(true);
});

test("rendering an unchanged pose makes no SVG mutations, including visible anchors", async ({ page }) => {
  await page.goto("/?test=1");
  await page.waitForFunction(() => !!window.__identityTest);
  await page.getByLabel("Show home anchors").check();
  const mutations = await page.locator(".stage").evaluate(svg => {
    const observer = new MutationObserver(() => {});
    observer.observe(svg, { attributes: true, childList: true, subtree: true });
    window.__identityTest.stepFixed(0);
    const count = observer.takeRecords().length; observer.disconnect(); return count;
  });
  expect(mutations).toBe(0);
});

test("live telemetry refreshes at a bounded rate and stops updating while closed", async ({ page }) => {
  await page.goto("/"); await expect(page.locator("#status")).toHaveText("REST");
  await page.locator("summary").click();
  await expect(page.locator("#telemetry")).toContainText('"step": 0');
  const start = await stagePoint(page, 280, 310), end = await stagePoint(page, 600, 310);
  await page.mouse.move(start.x, start.y); await page.waitForTimeout(40);
  const observe = () => page.locator("#telemetry").evaluate(node => {
    const observer = new MutationObserver(records => { state.count += records.length; });
    const state = { count: 0, started: performance.now(), observer };
    observer.observe(node, { childList: true, characterData: true, subtree: true });
    Object.assign(window, { telemetryObservation: state });
  });
  const sample = () => page.evaluate(() => {
    const state = (window as unknown as { telemetryObservation: { count: number; started: number; observer: MutationObserver } }).telemetryObservation;
    state.observer.disconnect(); return { count: state.count, duration: performance.now() - state.started };
  });
  await observe(); await page.mouse.move(end.x, end.y); await page.waitForTimeout(1000);
  const open = await sample();
  expect(open.count).toBeGreaterThan(0);
  expect(open.count).toBeLessThanOrEqual(Math.ceil(open.duration / 200) + 2);
  await expect(page.locator("#telemetry")).not.toContainText('"step": 0');
  await page.locator("summary").click(); await observe(); await page.waitForTimeout(300);
  expect((await sample()).count).toBe(0);
});

for (const fault of ["exception", "empty exception", "return failure", "non-finite state"] as const) {
  test(`runtime ${fault} restores the canonical logo and waits for explicit Reset`, async ({ page }) => {
    await page.goto("/?test=1"); await page.waitForFunction(() => !!window.__identityTest);
    await page.evaluate(async fault => {
      const runtimePath = "/runtime.ts", corePath = "/core.ts", geometryPath = "/geometry.ts";
      const { mountIdentity } = await import(runtimePath) as typeof import("../runtime");
      const { PhysicsCore } = await import(corePath) as typeof import("../core");
      const { canonicalGeometry } = await import(geometryPath) as typeof import("../geometry");
      const original = document.querySelector("main")!, root = original.cloneNode(true) as HTMLElement;
      original.remove(); root.querySelectorAll(".anchors, .navigator").forEach(node => node.remove());
      document.body.append(root);
      const canonical = [...root.querySelectorAll("[data-fragment]")].map(node => node.getAttribute("transform"));
      const state = { factories: 0, ticks: 0, canonical, controller: null as ReturnType<typeof mountIdentity> | null };
      state.controller = mountIdentity(root, { createCore: () => {
        const core = new PhysicsCore(canonicalGeometry); state.factories++;
        if (state.factories === 1) core.stepStroke = () => {
          state.ticks++; core.bodies[0]!.position = { x: core.bodies[0]!.position.x + 10, y: core.bodies[0]!.position.y };
          if (fault === "exception") throw new Error("Injected tick failure");
          if (fault === "empty exception") throw new Error("");
          if (fault === "return failure") core.permissions.failures.push("Injected return deadline");
          else core.bodies[0]!.velocity = { x: NaN, y: 0 };
        };
        return core;
      } });
      Object.assign(window, { hardeningRuntime: state });
    }, fault);
    const start = await stagePoint(page, 280, 310);
    await page.mouse.move(start.x, start.y);
    await expect(page.locator("#status")).toHaveText("Static identity · motion unavailable");
    await expect(page.locator("#pause")).toBeDisabled(); await expect(page.locator("#anchors")).toBeDisabled();
    await expect(page.locator(".navigator")).toHaveAttribute("visibility", "hidden");
    await page.locator("summary").click(); await expect(page.locator("#telemetry")).toContainText('"runtimeFailure"');
    await page.mouse.move(start.x + 60, start.y); await page.waitForTimeout(100);
    const frozen = await page.evaluate(() => {
      const state = (window as unknown as { hardeningRuntime: { factories: number; ticks: number; canonical: (string | null)[] } }).hardeningRuntime;
      return { factories: state.factories, ticks: state.ticks, canonical: state.canonical,
        transforms: [...document.querySelectorAll("[data-fragment]")].map(node => node.getAttribute("transform")) };
    });
    expect(frozen.ticks).toBe(1); expect(frozen.factories).toBe(1); expect(frozen.transforms).toEqual(frozen.canonical);
    await page.getByRole("button", { name: "Reset" }).click(); await expect(page.locator("#status")).toHaveText("REST");
    await expect(page.locator("#pause")).toBeEnabled();
    await page.mouse.move(start.x, start.y); await expect(page.locator(".navigator")).toHaveAttribute("visibility", "visible");
    await page.evaluate(() => {
      const state = (window as unknown as { hardeningRuntime: { controller: { dispose(): void; reset(): void } } }).hardeningRuntime;
      state.controller.dispose(); state.controller.dispose(); state.controller.reset();
    });
    expect(await page.locator(".navigator, .anchors").count()).toBe(0);
    await page.getByRole("button", { name: "Reset" }).click();
    await page.mouse.move(start.x + 20, start.y); await page.waitForTimeout(50);
    await expect(page.locator("#status")).toHaveText("Static identity");
    await page.evaluate(async () => {
      const runtimePath = "/runtime.ts";
      const { mountIdentity } = await import(runtimePath) as typeof import("../runtime");
      mountIdentity(document.querySelector<HTMLElement>("main")!);
    });
    expect(await page.locator(".navigator").count()).toBe(1);
    await expect(page.locator("#status")).toHaveText("REST");
  });
}

test("scoped controls and disposal do not affect a sibling identity", async ({ page }) => {
  await page.goto("/?test=1"); await page.waitForFunction(() => !!window.__identityTest);
  await page.evaluate(async () => {
    const runtimePath = "/runtime.ts";
    const { mountIdentity } = await import(runtimePath) as typeof import("../runtime");
    const original = document.querySelector("main")!;
    const roots = ["first", "second"].map(id => {
      const root = original.cloneNode(true) as HTMLElement;
      root.dataset.instance = id;
      root.querySelectorAll(".anchors, .navigator").forEach(node => node.remove());
      document.body.append(root); return root;
    });
    original.remove();
    const controllers = roots.map(root => mountIdentity(root, { manual: true }));
    Object.assign(window, { scopedControllers: controllers });
  });
  const first = page.locator('[data-instance="first"]'), second = page.locator('[data-instance="second"]');
  await first.locator("#pause").click();
  await expect(first.locator("#status")).toHaveText("Paused"); await expect(second.locator("#status")).toHaveText("REST");
  await second.locator("#anchors").check();
  await expect(second.locator(".anchors")).toHaveAttribute("visibility", "visible");
  await expect(first.locator(".anchors")).toHaveAttribute("visibility", "hidden");
  await page.evaluate(() => {
    (window as unknown as { scopedControllers: { dispose(): void }[] }).scopedControllers[0]!.dispose();
  });
  expect(await first.locator(".navigator, .anchors").count()).toBe(0);
  expect(await second.locator(".navigator").count()).toBe(1);
  await second.locator("#pause").click(); await expect(second.locator("#status")).toHaveText("Paused");
});

test("native keyboard controls retain visible focus and operate without pointer input", async ({ page }) => {
  await page.goto("/"); await expect(page.locator("#status")).toHaveText("REST");
  await page.keyboard.press("Tab"); await expect(page.locator("#reset")).toBeFocused();
  expect(await page.locator("#reset").evaluate(node => getComputedStyle(node).outlineStyle)).not.toBe("none");
  await page.keyboard.press("Enter"); await expect(page.locator("#status")).toHaveText("REST");
  await page.keyboard.press("Tab"); await expect(page.locator("#pause")).toBeFocused();
  await page.keyboard.press("Space"); await expect(page.locator("#status")).toHaveText("Paused");
  await page.keyboard.press("Tab"); await expect(page.locator("#anchors")).toBeFocused();
  await page.keyboard.press("Space"); await expect(page.locator("#anchors")).toBeChecked();
  await page.keyboard.press("Tab"); await expect(page.locator("summary")).toBeFocused();
  await page.keyboard.press("Enter"); await expect(page.locator("details")).toHaveAttribute("open", "");
});

test("live resizing and orientation changes preserve pointer mapping and usable controls", async ({ page }) => {
  await page.goto("/"); await expect(page.locator("#status")).toHaveText("REST");
  for (const [width, height] of [[390, 844], [844, 390], [768, 1024], [1024, 768], [1200, 900], [1512, 982], [512, 740]]) {
    await page.setViewportSize({ width: width!, height: height! });
    const point = await stagePoint(page, 250, 100);
    await page.locator(".stage").evaluate(svg => {
      svg.addEventListener("pointermove", event => {
        const e = event as PointerEvent;
        const p = new DOMPoint(e.clientX, e.clientY).matrixTransform((svg as SVGSVGElement).getScreenCTM()!.inverse());
        Object.assign(window, { resizedPointer: { x: p.x, y: p.y } });
      }, { once: true });
    });
    await page.mouse.move(0, 0); await page.mouse.move(point.x, point.y);
    await expect(page.locator(".navigator")).toHaveAttribute("visibility", "visible");
    const delivered = await page.evaluate(() => (window as unknown as { resizedPointer: { x: number; y: number } }).resizedPointer);
    await expect.poll(async () => Number(await page.locator(".navigator").getAttribute("cx"))).toBeCloseTo(delivered.x, 2);
    await expect.poll(async () => Number(await page.locator(".navigator").getAttribute("cy"))).toBeCloseTo(delivered.y, 2);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.locator("#pause").click(); await expect(page.locator("#status")).toHaveText("Paused");
    await page.locator("#pause").click(); await page.locator("#reset").click();
    await expect(page.locator("#status")).toHaveText("REST");
  }
});

test("repeated mounting, input, reset and disposal release DOM nodes and listeners", async ({ page, browserName }, info) => {
  test.skip(browserName !== "chromium", "Chromium CDP exposes heap and listener counters");
  await page.goto("/?test=1"); await page.waitForFunction(() => !!window.__identityTest);
  const session = await page.context().newCDPSession(page);
  try {
    await session.send("Performance.enable");
    await page.evaluate(() => {
      const template = document.querySelector("main")!.cloneNode(true) as HTMLElement;
      template.querySelectorAll(".anchors, .navigator").forEach(node => node.remove());
      Object.assign(window, { lifetimeTemplate: template });
    });
    const measurements: Record<string, number>[] = [];
    for (let batch = 0; batch < 6; batch++) {
      await page.evaluate(async () => {
        const runtimePath = "/runtime.ts";
        const { mountIdentity } = await import(runtimePath) as typeof import("../runtime");
        const template = (window as unknown as { lifetimeTemplate: HTMLElement }).lifetimeTemplate;
        for (let cycle = 0; cycle < 20; cycle++) {
          const root = template.cloneNode(true) as HTMLElement; document.body.append(root);
          const controller = mountIdentity(root);
          const svg = root.querySelector<SVGSVGElement>(".stage")!, m = svg.getScreenCTM()!;
          const point = new DOMPoint(250, 100).matrixTransform(m);
          svg.dispatchEvent(new PointerEvent("pointermove", { clientX: point.x, clientY: point.y, pointerType: "mouse", isPrimary: true }));
          await new Promise<void>(resolve => requestAnimationFrame(() => resolve()));
          controller.reset(); controller.dispose(); controller.dispose(); root.remove();
        }
        await new Promise<void>(resolve => requestAnimationFrame(() => resolve()));
      });
      await session.send("HeapProfiler.collectGarbage");
      const { metrics } = await session.send("Performance.getMetrics");
      measurements.push(Object.fromEntries(metrics.map(m => [m.name, m.value])));
    }
    const settled = measurements.slice(1), baseline = settled[0]!;
    for (const sample of settled) {
      expect(sample.Nodes).toBe(baseline.Nodes);
      expect(sample.JSEventListeners).toBe(baseline.JSEventListeners);
      expect(sample.Documents).toBe(baseline.Documents);
      // Allow JIT/bookkeeping variation; reject sustained retained heap growth.
      expect(sample.JSHeapUsedSize! - baseline.JSHeapUsedSize!).toBeLessThan(1024 * 1024);
    }
    await info.attach("lifetime-counters.json", { body: JSON.stringify(measurements), contentType: "application/json" });
  } finally { await session.detach(); }
});
