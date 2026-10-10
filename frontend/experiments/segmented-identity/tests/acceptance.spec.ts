import { test, expect } from "@playwright/test";
import type { Page } from "@playwright/test";
import type {} from "../test-support";

const open = async (page: Page) => {
  await page.goto("/?test=1");
  await page.waitForFunction(() => !!window.__identityTest);
};

test("renewed navigator strikes restore separated collisions before the group settles home", async ({ page }, info) => {
  await open(page);
  const outcome = await page.evaluate(() => {
    const api = window.__identityTest, fixture = api.seedFixture(83);
    api.loadFixture(fixture.fragments); api.applyDeterministicPointerPath(fixture.path);
    for (let tick = 0; tick < 1200 && !api.getSnapshot().events.length; tick++) api.stepFixed();
    const initialGhosts = api.getSnapshot().pairs.filter(p => p.state === "GHOSTED_LATCHED").length;
    let unsafeRearm = false, penetration = false, energyGain = false, restoredWhileMoving = false;
    const counts: number[] = [], violations: { tick: number; navigator: { id: string; depth: number }[]; solids: { key: string; depth: number }[] }[] = [];
    for (let tick = 0; tick < 720; tick++) {
      api.stepStroke(Array.from({ length: 3 }, (_, i) => {
        const angle = (tick + (i + 1) / 3) * 12 / 120;
        return { x: 480 + 100 * Math.cos(angle), y: 310 + 140 * Math.sin(angle), elapsedSeconds: 1 / 360 };
      }));
      const s = api.getSnapshot();
      counts.push(s.pairs.filter(p => p.state === "GHOSTED_LATCHED").length);
      const navigator = s.fragments.filter(b => b.navigatorOverlap > .02).map(b => ({ id: b.id, depth: b.navigatorOverlap }));
      const solids = s.pairs.filter(p => p.state === "SOLID" && p.overlap > .3).map(p => ({ key: p.key, depth: p.overlap }));
      if ((navigator.length || solids.length) && violations.length < 8) violations.push({ tick, navigator, solids });
      penetration ||= !s.finite || !s.contained || s.fragments.some(b => b.navigatorOverlap > .02)
        || s.pairs.some(p => p.state === "SOLID" && p.overlap > .3);
      energyGain ||= s.ledger.permissions !== 0 || Math.max(s.ledger.fragments, s.ledger.boundaries, s.ledger.damping) > 1e-6;
      for (const e of s.events.filter(e => e.type === "rearm" && e.step === s.step)) {
        unsafeRearm ||= e.overlap > 0 || e.gap < .65 || e.separatedSteps < 8;
        restoredWhileMoving ||= s.fragments.some(b => e.key.split("/").includes(b.id) && b.homeAnchorError > 1);
      }
    }
    api.stepStroke([{ x: 0, y: 0, active: false }]);
    for (let tick = 0; tick < 5400 && !api.getSnapshot().rest; tick++) api.stepFixed();
    const final = api.getSnapshot();
    return { initialGhosts, counts, violations, unsafeRearm, penetration, energyGain, restoredWhileMoving,
      rest: final.rest, failures: final.failures };
  });
  await info.attach("renewed-interaction.json", { body: JSON.stringify(outcome, null, 2), contentType: "application/json" });
  expect(outcome.initialGhosts).toBeGreaterThan(0); expect(outcome.counts[120]).toBeLessThanOrEqual(1);
  expect(outcome.counts.at(-1)).toBe(0); expect(outcome.restoredWhileMoving).toBe(true);
  expect(outcome.unsafeRearm).toBe(false); expect(outcome.penetration).toBe(false); expect(outcome.energyGain).toBe(false);
  expect(outcome.rest).toBe(true); expect(outcome.failures).toEqual([]);
});

test("the real forecast solver releases a three-piece group together in Chromium", async ({ page }, info) => {
  await open(page);
  const outcome = await page.evaluate(async () => {
    const modulePath = "/core.ts", mathPath = "/math.ts";
    const { PhysicsCore } = await import(modulePath) as typeof import("../core");
    const { polygonProperties } = await import(mathPath) as typeof import("../math");
    const square = [{ x: -20, y: -20 }, { x: 20, y: -20 }, { x: 20, y: 20 }, { x: -20, y: 20 }];
    const geometry = ["a", "b", "c"].map((id, i) => ({ id, vertices: square, parts: [square], anchors: square,
      home: { x: (i - 1) * 90, y: 0 }, mass: 1, inertia: polygonProperties(square).inertiaPerMass }));
    return [0, 100].map(speed => {
      const core = new PhysicsCore(geometry);
      core.loadFixture([{ id: "a", position: { x: 520, y: 310 }, velocity: { x: speed, y: 0 } },
        { id: "b", position: { x: 480, y: 310 }, velocity: { x: speed, y: 0 } },
        { id: "c", position: { x: 440, y: 310 }, velocity: { x: speed, y: 0 } }]);
      let permissionEnergy = 0;
      for (let step = 0; step < 2000 && !core.getSnapshot().rest; step++) {
        core.stepFixed(); permissionEnergy = Math.max(permissionEnergy, Math.abs(core.ledger.permissions));
      }
      const s = core.getSnapshot();
      return { speed, events: s.events, rest: s.rest, permissionEnergy, work: s.forecastWork };
    });
  });
  await info.attach("predictive-group.json", { body: JSON.stringify(outcome, null, 2), contentType: "application/json" });
  for (const result of outcome) {
    const events = result.events.filter((e: { type: string }) => e.type === "ghost");
    expect(events.slice(0, 3).map((e: { key: string }) => e.key).sort()).toEqual(["a/b", "a/c", "b/c"]);
    expect(events[0]!.step).toBe(events[1]!.step);
    expect(events[0]!.step / 120).toBeLessThan(.25);
    expect(result.rest).toBe(true); expect(result.permissionEnergy).toBe(0);
  }
});

test("exact contour-vertex entry keeps the real navigator collider strict", async ({ page }) => {
  await open(page);
  const result = await page.evaluate(() => {
    const api = window.__identityTest;
    const outcomes = api.getSnapshot().fragments.map(fragment => {
      api.reset();
      const point = api.getFragmentState(fragment.id)!.vertices[0]!;
      api.stepFixed(1, { x: point.x, y: point.y });
      return api.getSnapshot().fragments.every(b => b.navigatorOverlap < .02);
    });
    return outcomes.every(Boolean);
  });
  expect(result).toBe(true);
});

test("swept navigator cannot tunnel and cannot force fragments through stage walls", async ({ page }) => {
  await open(page);
  const result = await page.evaluate(() => {
    const api = window.__identityTest;
    api.loadFixture([{ id: "inner-right", position: { x: 910, y: 310 } }]);
    api.stepFixed(1, { x: 840, y: 310 });
    let contained = true, strict = true;
    for (const x of [880, 940, 960, 960, 700, 100, 950]) {
      api.stepFixed(1, { x, y: 310 }); const s = api.getSnapshot();
      contained &&= s.contained; strict &&= s.fragments.every(b => b.navigatorOverlap < .02);
    }
    return { contained, strict };
  });
  expect(result).toEqual({ contained: true, strict: true });
});

test("navigator contact during an existing ghost overlap preserves its latch", async ({ page }) => {
  await open(page);
  const result = await page.evaluate(() => {
    const api = window.__identityTest;
    const upper = api.getFragmentState("inner-top")!, lower = api.getFragmentState("inner-bottom")!;
    api.loadFixture([{ id: upper.id, position: lower.position }, { id: lower.id, position: upper.position }]);
    let pair: ReturnType<typeof api.getSnapshot>["pairs"][number] | undefined;
    for (let i = 0; i < 2500; i++) {
      api.stepFixed(); pair = api.getSnapshot().pairs.find(p => p.state === "GHOSTED_LATCHED" && p.overlap > .2);
      if (pair) break;
    }
    if (!pair) return { exercised: false, failures: ["fixture did not produce overlap"], rest: false };
    const contacted = api.getFragmentState(pair.a)!;
    const point = contacted.vertices[0]!;
    api.stepFixed(1, { x: point.x, y: point.y });
    const s = api.getSnapshot(), current = s.pairs.find(p => p.key === pair!.key)!;
    const failures: string[] = [];
    if (!api.getFragmentState(pair.a)!.navigatorContact) failures.push("no navigator contact");
    if (current.overlap > 0 && current.state !== "GHOSTED_LATCHED") failures.push("overlap rearmed");
    if (s.fragments.some(b => b.navigatorOverlap > .02)) failures.push("penetration");
    for (const e of s.events.filter(e => e.step === s.step && e.type === "ghost"))
      if (e.key.split("/").includes(pair.a)) failures.push("additional ghost relationship");
    api.stepFixed(1, { x: 0, y: 0, active: false }); api.stepFixed(5000);
    return { exercised: true, failures, rest: api.getSnapshot().rest };
  });
  expect(result).toEqual({ exercised: true, failures: [], rest: true });
});

test("A: canonical rest is stable, rendered from exact collider vertices", async ({ page }) => {
  await open(page);
  const result = await page.evaluate(() => {
    const api = window.__identityTest, before = api.getSnapshot(); api.stepFixed(1200);
    return { before, after: api.getSnapshot() };
  });
  expect(result.after.rest).toBe(true);
  expect(result.after.energy).toBe(0);
  expect(result.after.events).toEqual([]);
  expect(result.after.fragments.map(b => b.position)).toEqual(result.before.fragments.map(b => b.position));
  expect(result.after.pairs.every(p => p.overlap === 0)).toBe(true);
  expect(await page.locator("[data-fragment]").count()).toBe(8);
  await page.screenshot({ path: "test-results/segmented-identity/rest-desktop.png" });
});

test("B/C: slow, medium, fast strikes are bounded and off-center strikes rotate", async ({ page }) => {
  await open(page);
  const results = await page.evaluate(() => {
    const api = window.__identityTest;
    return [60, 240, 960, 9600, 96000].map(speed => {
      api.reset();
      const f = api.getFragmentState("inner-right")!;
      const edge = Math.min(...f.vertices.map(p => p.x));
      let x = edge - 11, y = f.position.y + 6;
      api.stepFixed(1, { x, y });
      // Identical swept contact geometry; only observed pointer speed changes.
      api.stepFixed(1, { x: x + 4, y, elapsedSeconds: 4 / speed });
      const b = api.getFragmentState("inner-right")!;
      return { speed, energy: b.kineticEnergy, angular: b.angularVelocity,
        penetration: api.getSnapshot().fragments.reduce((m, b) => Math.max(m, b.navigatorOverlap), 0) };
    });
  });
  expect(results[0]!.energy).toBeGreaterThan(0);
  expect(results[0]!.energy).toBeLessThan(results[1]!.energy);
  expect(results[1]!.energy).toBeLessThan(results[2]!.energy);
  expect(Math.abs(results[1]!.angular)).toBeGreaterThan(.001);
  expect(results.every(r => r.energy < 1e6 && r.penetration < .02)).toBe(true);
  expect(results[4]!.energy - results[3]!.energy).toBeLessThan(results[2]!.energy - results[1]!.energy);
});

test("D: fragment collisions transfer linear and angular momentum without energy gain", async ({ page }) => {
  await open(page);
  const result = await page.evaluate(() => {
    const api = window.__identityTest;
    // The receiving fragment starts at canonical REST. It has no home force
    // or momentum until this strike, so a large first-contact response proves
    // momentum transfer rather than measuring the target's own home gravity.
    const target = api.getFragmentState("corner-top-right")!;
    api.loadFixture([{ id: "inner-right", position: { x: target.position.x + 18, y: 140 },
      velocity: { x: 0, y: 600 } }]);
    let transfer = false, rotation = false, maxCollisionGain = 0;
    for (let i = 0; i < 90; i++) {
      const before = api.getFragmentState(target.id)!;
      api.stepFixed(); const s = api.getSnapshot(), after = api.getFragmentState(target.id)!;
      maxCollisionGain = Math.max(maxCollisionGain, s.ledger.fragments);
      if (after.contacts.includes("inner-right")) {
        transfer = before.kineticEnergy === 0 && after.kineticEnergy > 100
          && Math.hypot(after.velocity.x, after.velocity.y) > 10;
        rotation = Math.abs(after.angularVelocity) > .05;
        break;
      }
    }
    return { transfer, rotation, maxCollisionGain };
  });
  expect(result.transfer).toBe(true); expect(result.rotation).toBe(true);
  expect(result.maxCollisionGain).toBeLessThan(1e-6);
});

test("E: high-energy fragments stay contained and walls dissipate energy", async ({ page }) => {
  await open(page);
  const result = await page.evaluate(() => {
    const api = window.__identityTest;
    api.loadFixture([{ id: "inner-right", position: { x: 30, y: 30 }, velocity: { x: -1500, y: -1200 }, angularVelocity: 8 }]);
    let contained = true, finite = true, dissipated = false, maximumGain = 0;
    for (let i = 0; i < 800; i++) {
      api.stepFixed(); const s = api.getSnapshot(); contained &&= s.contained; finite &&= s.finite;
      dissipated ||= s.ledger.boundaries < -1;
      maximumGain = Math.max(maximumGain, s.ledger.boundaries);
    }
    return { contained, finite, dissipated, maximumGain };
  });
  expect(result).toEqual({ contained: true, finite: true, dissipated: true, maximumGain: 0 });
});

test("F/I: ordinary disturbances rotate while travelling and recover repeatedly without ghosting", async ({ page }) => {
  await open(page);
  const result = await page.evaluate(() => {
    const api = window.__identityTest;
    const cycles: { rest: boolean; ghosts: number; rotationDuringTravel: boolean }[] = [];
    for (let cycle = 0; cycle < 5; cycle++) {
      // An outward-displaced corner has a clear return path. Moving a core
      // flank outside its surrounding corners necessarily changes that setup.
      api.reset(); const b = api.getFragmentState("corner-top-right")!;
      api.loadFixture([{ id: b.id, position: { x: b.position.x + 80, y: b.position.y }, angle: .45,
        velocity: { x: 180 + cycle * 20, y: 0 }, angularVelocity: .5 }]);
      api.stepFixed(60); const travelling = api.getFragmentState(b.id)!;
      const rotationDuringTravel = Math.abs(travelling.rotation) < .45 && travelling.homeAnchorError > 1;
      api.stepFixed(3000); const s = api.getSnapshot();
      cycles.push({ rest: s.rest, ghosts: s.events.filter(e => e.type === "ghost").length, rotationDuringTravel });
    }
    return cycles;
  });
  expect(result.every(c => c.rest && c.ghosts === 0 && c.rotationDuringTravel)).toBe(true);
});

test("G: swapped canonical caps reproduce a deadlock and recover with latched local permissions", async ({ page }) => {
  await open(page);
  const result = await page.evaluate(() => {
    const api = window.__identityTest;
    function fixture() {
      const a = api.getFragmentState("inner-top")!, b = api.getFragmentState("inner-bottom")!;
      api.loadFixture([{ id: a.id, position: b.position }, { id: b.id, position: a.position }]);
    }
    api.reset({ deadlock: false }); fixture(); api.stepFixed(3000); const blocked = api.getSnapshot();
    api.reset(); fixture();
    let overlapWhileGhosted = false, unsafeRearm = false, permissionEnergy = 0, passiveGain = 0;
    const transitionEnergy: { step: number; type: string; before: number; after: number; permissions: number }[] = [];
    for (let i = 0; i < 5400; i++) {
      api.stepFixed(); const s = api.getSnapshot();
      overlapWhileGhosted ||= s.pairs.some(p => p.state === "GHOSTED_LATCHED" && p.overlap > .1);
      permissionEnergy = Math.max(permissionEnergy, Math.abs(s.ledger.permissions));
      passiveGain = Math.max(passiveGain, s.ledger.fragments, s.ledger.boundaries);
      for (const event of s.events.filter(e => e.step === s.step)) {
        transitionEnergy.push({ step: s.step, type: event.type, before: s.ledger.before, after: s.energy, permissions: s.ledger.permissions });
        if (event.type === "rearm") unsafeRearm ||= event.overlap > 0 || event.gap < .65 || event.separatedSteps < 8;
      }
      if (s.rest) break;
    }
    return { blocked, recovered: api.getSnapshot(), overlapWhileGhosted, unsafeRearm,
      permissionEnergy, passiveGain, transitionEnergy };
  });
  expect(result.blocked.rest).toBe(false);
  expect(result.blocked.clusters.some(c => c.deadlocked)).toBe(true);
  expect(result.recovered.rest).toBe(true);
  expect(result.overlapWhileGhosted).toBe(true);
  expect(result.unsafeRearm).toBe(false);
  expect(result.permissionEnergy).toBe(0);
  expect(result.passiveGain).toBeLessThan(1e-6);
  expect(result.recovered.failures).toEqual([]);
  expect(result.recovered.events.filter(e => e.type === "ghost").length).toBeGreaterThan(0);
});

test("H: navigator contact is strict and prevents new ghost permissions", async ({ page }) => {
  await open(page);
  const result = await page.evaluate(() => {
    const api = window.__identityTest;
    const a = api.getFragmentState("inner-top")!, b = api.getFragmentState("inner-bottom")!;
    api.loadFixture([{ id: a.id, position: b.position }, { id: b.id, position: a.position }]);
    const failures: string[] = [];
    let activeContact = false;
    for (let i = 0; i < 1200; i++) {
      const current = api.getFragmentState(a.id)!;
      const top = current.vertices.reduce((best, p) => p.y < best.y ? p : best, current.vertices[0]!);
      api.stepFixed(1, { x: top.x, y: top.y + 1 });
      const s = api.getSnapshot(); activeContact ||= s.fragments.some(b => b.navigatorContact);
      if (s.fragments.some(b => b.navigatorOverlap > .02)) failures.push("penetration");
      for (const e of s.events.filter(e => e.step === s.step && e.type === "ghost")) {
        const ids = e.key.split("/");
        if (s.fragments.some(b => ids.includes(b.id) && b.navigatorContact)) failures.push("unsafe permission");
      }
    }
    api.stepFixed(1, { x: 0, y: 0, active: false }); api.stepFixed(5000);
    return { activeContact, failures, recovered: api.getSnapshot() };
  });
  expect(result.activeContact).toBe(true);
  expect(result.failures).toEqual([]);
  expect(result.recovered.rest).toBe(true);
});

test("real mouse input, keyboard controls, resizing, and reduced-motion behavior", async ({ page }) => {
  await page.goto("/");
  await expect(page.locator("#status")).toHaveText("REST");
  const box = (await page.locator(".stage").boundingBox())!;
  await page.mouse.move(box.x + box.width * .3, box.y + box.height * .5);
  await page.mouse.move(box.x + box.width * .52, box.y + box.height * .5, { steps: 12 });
  await expect(page.locator("#status")).toHaveText("In motion");
  expect(await page.evaluate(() => "__identityTest" in window)).toBe(false);
  await page.getByRole("button", { name: "Pause", exact: true }).focus();
  await page.keyboard.press("Enter");
  await expect(page.locator("#status")).toHaveText("Paused");
  await page.waitForTimeout(100);
  const frozen = await page.locator("[data-fragment]").evaluateAll(nodes => nodes.map(n => n.getAttribute("transform")));
  await page.waitForTimeout(100);
  expect(await page.locator("[data-fragment]").evaluateAll(nodes => nodes.map(n => n.getAttribute("transform")))).toEqual(frozen);
  await page.keyboard.press("Enter");
  await expect(page.locator("#status")).toHaveText("In motion");
  await expect.poll(() => page.locator("[data-fragment]").evaluateAll(nodes => nodes.map(n => n.getAttribute("transform"))))
    .not.toEqual(frozen);
  await page.getByRole("button", { name: "Reset", exact: true }).focus();
  await page.keyboard.press("Enter");
  await expect(page.locator("#status")).toHaveText("REST");
  for (const width of [834, 601, 390, 780, 1200]) {
    await page.setViewportSize({ width, height: 850 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  }
  await page.emulateMedia({ reducedMotion: "reduce" });
  await expect(page.locator("#status")).toHaveText("Reduced motion · static identity");
  const before = await page.locator("[data-fragment]").first().getAttribute("transform");
  await page.mouse.move(400, 400); await page.mouse.move(700, 400, { steps: 8 });
  expect(await page.locator("[data-fragment]").first().getAttribute("transform")).toBe(before);
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await expect(page.locator("#status")).toHaveText("REST");
});

test("actual mouse input works when the primary device reports touch capabilities", async ({ browser }) => {
  const context = await browser.newContext({ hasTouch: true, isMobile: false, viewport: { width: 1200, height: 900 } });
  try {
    const page = await context.newPage(); await page.goto("http://127.0.0.1:4178/");
    await expect(page.locator("#status")).toHaveText("REST");
    expect(await page.evaluate(() => matchMedia("(hover: hover) and (pointer: fine)").matches)).toBe(false);
    await page.locator(".stage").tap();
    await expect(page.locator("#status")).toHaveText("REST");
    await expect(page.locator(".navigator")).toHaveAttribute("visibility", "hidden");
    const box = (await page.locator(".stage").boundingBox())!;
    await page.mouse.move(box.x + box.width * .3, box.y + box.height * .5);
    await page.waitForTimeout(50);
    await page.mouse.move(box.x + box.width * .52, box.y + box.height * .5, { steps: 12 });
    await expect(page.locator("#status")).toHaveText("In motion");
    await expect(page.locator(".navigator")).toHaveAttribute("visibility", "visible");
    // A handoff must not leave the old touch position as a permanent input hole.
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
    await expect.poll(async () => Number(await page.locator(".navigator").getAttribute("cx"))).toBeCloseTo(480, 0);
    await page.mouse.move(10, 10);
    await expect(page.locator(".navigator")).toHaveAttribute("visibility", "hidden");
  } finally { await context.close(); }
});

test("pen-style desktop pointer movements drive the navigator without requiring a click", async ({ page, browserName }) => {
  test.skip(browserName !== "chromium", "CDP desktop pen bridge is Chromium-specific");
  await page.goto("/"); await expect(page.locator("#status")).toHaveText("REST");
  const session = await page.context().newCDPSession(page);
  try {
    const box = (await page.locator(".stage").boundingBox())!;
    for (let i = 0; i <= 12; i++) {
      await session.send("Input.dispatchMouseEvent", { type: "mouseMoved", pointerType: "pen",
        x: box.x + box.width * (.3 + .22 * i / 12), y: box.y + box.height * .5 });
      await page.waitForTimeout(20);
    }
    await expect(page.locator("#status")).toHaveText("In motion");
    await expect(page.locator(".navigator")).toHaveAttribute("visibility", "visible");
    await page.emulateMedia({ reducedMotion: "reduce" });
    await expect(page.locator("#status")).toHaveText("Reduced motion · static identity");
    await session.send("Input.dispatchMouseEvent", { type: "mouseMoved", pointerType: "pen",
      x: box.x + box.width * .4, y: box.y + box.height * .5 });
    await expect(page.locator(".navigator")).toHaveAttribute("visibility", "hidden");
  } finally { await session.detach(); }
});

test("idle and reduced-motion presentations stop scheduling animation frames", async ({ page }) => {
  await page.addInitScript(() => {
    const request = window.requestAnimationFrame.bind(window);
    (window as unknown as { testFrames: number }).testFrames = 0;
    window.requestAnimationFrame = callback => request(time => {
      (window as unknown as { testFrames: number }).testFrames++;
      callback(time);
    });
  });
  await page.goto("/"); await expect(page.locator("#status")).toHaveText("REST");
  const frames = await page.evaluate(() => (window as unknown as { testFrames: number }).testFrames);
  await page.waitForTimeout(150);
  expect(await page.evaluate(() => (window as unknown as { testFrames: number }).testFrames)).toBe(frames);
  await page.emulateMedia({ reducedMotion: "reduce" });
  await expect(page.locator("#status")).toHaveText("Reduced motion · static identity");
  const reduced = await page.evaluate(() => (window as unknown as { testFrames: number }).testFrames);
  await page.waitForTimeout(150);
  expect(await page.evaluate(() => (window as unknown as { testFrames: number }).testFrames)).toBe(reduced);
});

test("semantic static identity remains without JavaScript and on touch", async ({ browser, browserName }) => {
  const context = await browser.newContext({ javaScriptEnabled: false, viewport: { width: 390, height: 850 } });
  const page = await context.newPage(); await page.goto("http://127.0.0.1:4178/");
  await expect(page.getByRole("img", { name: "Segmented 0x identity" })).toBeVisible();
  expect(await page.locator("[data-fragment]").count()).toBe(8);
  await page.screenshot({ path: "test-results/segmented-identity/rest-mobile-no-js.png" });
  await context.close();
  const touch = await browser.newContext({ hasTouch: true, isMobile: browserName !== "firefox", viewport: { width: 390, height: 850 } });
  const touchPage = await touch.newPage(); await touchPage.goto("http://127.0.0.1:4178/");
  await touchPage.locator(".stage").tap();
  await expect(touchPage.locator("#status")).toHaveText("REST");
  await touch.close();
});
