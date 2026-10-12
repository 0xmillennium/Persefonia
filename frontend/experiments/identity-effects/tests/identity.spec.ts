import { test, expect, type Page } from "@playwright/test";
import { variants } from "../src/choreography";

const stage = (page: Page) => page.locator("#stage");
async function phase(page: Page, state: string): Promise<void> {
  // Brief phases can fall between rendered frames under headless contention.
  // Observe the controller's actual transition before interrupting it.
  await page.evaluate(value => new Promise<void>((resolve, reject) => {
    const element = document.querySelector<HTMLElement>("#stage")!;
    if (element.dataset.state === value) { resolve(); return; }
    const observer = new MutationObserver(() => {
      if (element.dataset.state !== value) return;
      clearTimeout(timer); observer.disconnect(); resolve();
    });
    const timer = setTimeout(() => {
      observer.disconnect(); reject(new Error(`Expected ${value}; observed ${element.dataset.state}, ${element.dataset.variant}`));
    }, 5000);
    observer.observe(element, { attributes: true, attributeFilter: ["data-state"] });
  }), state);
}
async function hoverMode(page: Page): Promise<void> {
  await page.getByRole("button", { name: "Hover Mode", exact: true }).click();
  await expect(stage(page)).toHaveAttribute("data-state", "baseline");
}
async function baseline(page: Page): Promise<void> {
  await expect(stage(page)).toHaveAttribute("data-state", "baseline");
  await expect(page.locator(".m-mark")).toHaveCSS("opacity", "0");
  await expect(page.locator(".reflection")).toHaveCSS("opacity", "0");
  expect(await page.locator(".writing-point").evaluateAll(elements => elements.every(el => getComputedStyle(el).opacity === "0"))).toBe(true);
  expect(await page.locator(".writing-trace, .period-trace").evaluateAll(elements => elements.every(el => parseFloat(getComputedStyle(el).strokeDashoffset) === 1))).toBe(true);
  expect(await page.locator(".writing-trace, .period-trace").evaluateAll(elements => elements.every(el => getComputedStyle(el).opacity === "0"))).toBe(true);
  expect(await page.locator(".written-ink").evaluate(el => getComputedStyle(el).maskImage.includes("identity-writing-mask"))).toBe(true);
  await expect(page.locator(".period-point")).toHaveCSS("opacity", "0");
  await expect(page.locator(".floor-shadow")).toHaveCSS("opacity", "0");
  expect(await page.locator(".identity-plane").evaluate(el => new DOMMatrix(getComputedStyle(el).transform).isIdentity)).toBe(true);
  expect(await page.locator(".registration").evaluate(element => {
    const m = new DOMMatrix(getComputedStyle(element).transform);
    return Math.abs(m.b) < .001 && Math.abs(m.c) < .001 && Math.abs(m.a - 1) < .001
      && Math.abs(m.d - 1) < .001 && Math.abs(m.e) < .001 && Math.abs(m.f) < .001;
  })).toBe(true);
  await expect.poll(() => page.locator("#identity").evaluate(svg => svg.getAnimations({ subtree: true }).length)).toBe(0);
  expect(await page.locator(".segment").evaluateAll(elements => elements.every(element => {
    const g = element as SVGGElement;
    const matrix = g.transform.baseVal.consolidate()?.matrix;
    return matrix && Math.abs(matrix.e - Number(g.dataset.homeX)) < 1e-6
      && Math.abs(matrix.f - Number(g.dataset.homeY)) < 1e-6;
  }))).toBe(true);
}

test("loading repeats the shared sequence; pause recovers the original geometry", async ({ page }) => {
  const errors: string[] = [];
  page.on("pageerror", error => errors.push(error.message));
  page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
  await page.goto("/");
  await expect(page.locator(".segment")).toHaveCount(8);
  await phase(page, "resolved");
  await expect(page.locator(".m-mark")).toHaveCSS("opacity", "1");
  await expect.poll(() => stage(page).getAttribute("data-cycle")).toBe("2");
  await phase(page, "resolved");
  await page.getByRole("button", { name: "Pause loop", exact: true }).click();
  await baseline(page);
  const cycle = await stage(page).getAttribute("data-cycle");
  await page.waitForTimeout(900);
  await expect(stage(page)).toHaveAttribute("data-cycle", cycle!);
  await page.getByRole("button", { name: "Resume loop", exact: true }).click();
  await expect.poll(() => stage(page).getAttribute("data-cycle")).not.toBe(cycle);
  expect(errors).toEqual([]);
});

test("each hover entry plays once, all variants close exactly, hold and leave are stable", async ({ page }, testInfo) => {
  test.setTimeout(90_000);
  await page.goto("/");
  await hoverMode(page);
  const seen = new Set<string>();
  for (let i = 0; i < variants.length; i++) {
    await page.locator("#identity").hover();
    await phase(page, "active");
    const variant = await stage(page).getAttribute("data-variant");
    if (variant === "counterturn") {
      for (const state of ["active", "closing"]) {
        await phase(page, state);
        await page.waitForFunction(() => [...document.querySelectorAll<SVGGElement>('[data-fragment^="inner-"]')]
          .every(element => {
            const matrix = new DOMMatrix(getComputedStyle(element).transform);
            const angle = Math.abs(Math.atan2(matrix.b, matrix.a) * 180 / Math.PI);
            return angle > 20 && angle < 160;
          }));
        const angles = await page.locator('[data-fragment^="inner-"]').evaluateAll(elements =>
          Object.fromEntries(elements.map(element => {
            const matrix = new DOMMatrix(getComputedStyle(element).transform);
            return [(element as SVGGElement).dataset.fragment!, Math.atan2(matrix.b, matrix.a) * 180 / Math.PI];
          })));
        expect(angles["inner-top"]).toBeGreaterThan(0);
        expect(angles["inner-left"]).toBeGreaterThan(0);
        expect(angles["inner-bottom"]).toBeLessThan(0);
        expect(angles["inner-right"]).toBeLessThan(0);
      }
    }
    if (variant === "rail-lock") {
      await phase(page, "closing");
      await page.waitForFunction(() => [...document.querySelectorAll<SVGGElement>('[data-fragment^="corner-"]')]
        .every(element => {
          const m = new DOMMatrix(getComputedStyle(element).transform);
          return Math.abs(m.e) < .1 && Math.abs(m.f) > 3;
        }));
    }
    if (variant === "quarter-index") {
      await phase(page, "active");
      await page.waitForFunction(() => {
        const m = new DOMMatrix(getComputedStyle(document.querySelector(".registration")!).transform);
        return Math.atan2(m.b, m.a) * 180 / Math.PI > 75;
      });
    }
    if (variant === "front-flip" || variant === "back-flip") {
      await page.waitForFunction(() => {
        const m = new DOMMatrix(getComputedStyle(document.querySelector(".identity-plane")!).transform);
        return Math.abs(m.m23) > .3 && m.m22 > 0;
      });
      const pitch = await page.locator(".identity-plane").evaluate(el => new DOMMatrix(getComputedStyle(el).transform).m23);
      expect(pitch * (variant === "front-flip" ? -1 : 1)).toBeGreaterThan(0);
      // Actual inverted frame, rather than an in-plane tilt or a partial rock.
      await page.waitForFunction(() => new DOMMatrix(getComputedStyle(document.querySelector(".identity-plane")!).transform).m22 < -.6);
    }
    if (variant === "floor-bounce") {
      await page.waitForFunction(() => new DOMMatrix(getComputedStyle(document.querySelector(".identity-plane")!).transform).m42 > 15);
      await page.waitForFunction(() => {
        const m = new DOMMatrix(getComputedStyle(document.querySelector(".identity-plane")!).transform);
        return m.m11 > 1.12 && m.m22 < .85 && m.m42 > 0;
      });
      await page.waitForFunction(() => new DOMMatrix(getComputedStyle(document.querySelector(".identity-plane")!).transform).m42 < -8);
    }
    await expect(stage(page)).toHaveAttribute("data-state", "resolved");
    seen.add((await stage(page).getAttribute("data-variant"))!);
    await expect(page.locator(".m-mark")).toHaveCSS("opacity", "1");
    expect(await page.locator(".segment, .registration").evaluateAll(elements => elements.every(element => {
      const matrix = new DOMMatrix(getComputedStyle(element).transform);
      return Math.abs(matrix.a - 1) < .001 && Math.abs(matrix.b) < .001
        && Math.abs(matrix.c) < .001 && Math.abs(matrix.d - 1) < .001
        && Math.abs(matrix.e) < .001 && Math.abs(matrix.f) < .001;
    }))).toBe(true);
    expect(await page.locator(".identity-plane").evaluate(el => new DOMMatrix(getComputedStyle(el).transform).isIdentity)).toBe(true);
    const cycle = await stage(page).getAttribute("data-cycle");
    await page.waitForTimeout(i === 0 ? 3400 : 700);
    await expect(stage(page)).toHaveAttribute("data-cycle", cycle!);
    expect(await page.locator("#identity svg").evaluate(svg =>
      svg.getAnimations({ subtree: true }).some(effect => effect.playState === "running"))).toBe(false);
    if (i === 0 && testInfo.project.name === "chromium") {
      await page.screenshot({ path: "test-results/identity-effects/resolved-desktop.png" });
    }
    await page.mouse.move(1, 1);
    await baseline(page);
  }
  expect([...seen].sort()).toEqual(variants.map(variant => variant.id).sort());
});

test("turquoise reflection travels once, interruption clears it, reduced finish stays static", async ({ page }) => {
  await page.goto("/");
  await hoverMode(page);
  await page.locator("#identity").hover();
  await phase(page, "reveal");
  await page.waitForFunction(() => Number(getComputedStyle(document.querySelector(".reflection")!).opacity) > .7);
  const firstX = await page.locator(".reflection").evaluate(el => new DOMMatrix(getComputedStyle(el).transform).e);
  await page.waitForTimeout(90);
  const nextX = await page.locator(".reflection").evaluate(el => new DOMMatrix(getComputedStyle(el).transform).e);
  expect(nextX).toBeGreaterThan(firstX);
  await page.mouse.move(1, 1);
  await baseline(page);
  await page.locator("#identity").hover();
  await phase(page, "resolved");
  await expect(page.locator(".reflection")).toHaveCSS("opacity", "0");
  await expect(page.locator(".settled-finish")).toBeVisible();
  await page.emulateMedia({ reducedMotion: "reduce" });
  await phase(page, "resolved");
  expect(await page.locator(".reflection").evaluate(el => el.getAnimations().length)).toBe(0);
  await expect(page.locator(".reflection")).toHaveCSS("opacity", "0");
  await expect(page.locator(".settled-finish")).toBeVisible();
});

test("capital signature follows the exact stroke order, then lifts to write its period", async ({ page }, testInfo) => {
  await page.goto("/");
  await hoverMode(page);
  await baseline(page);
  await page.locator("#identity").hover();
  await page.waitForFunction(() => {
    const traces = [...document.querySelectorAll(".writing-trace")];
    const progress = 1 - parseFloat(getComputedStyle(traces[1]!).strokeDashoffset);
    return progress > .2 && progress < .7;
  });
  const sample = await page.evaluate(() => {
    const traces = [...document.querySelectorAll<SVGPathElement>(".writing-trace")];
    const path = document.querySelector<SVGPathElement>("#identity-writing-path-1")!;
    const pen = document.querySelector<SVGGElement>('.writing-point[data-stroke="1"]')!;
    const distance = parseFloat(getComputedStyle(pen).offsetDistance) / 100;
    const point = path.getPointAtLength(path.getTotalLength() * distance);
    const expected = new DOMPoint(point.x, point.y).matrixTransform(document.querySelector("svg")!.getScreenCTM()!);
    const box = pen.getBoundingClientRect();
    const progress = traces.map(trace => 1 - parseFloat(getComputedStyle(trace).strokeDashoffset));
    return { progress, distance,
      aggregate: progress[1]!,
      inactiveHidden: traces.slice(2).every(trace => getComputedStyle(trace).opacity === "0")
        && getComputedStyle(document.querySelector(".period-trace")!).opacity === "0",
      dot: parseFloat(getComputedStyle(document.querySelector(".period-trace")!).strokeDashoffset),
      error: Math.hypot(expected.x - box.x - box.width / 2, expected.y - box.y - box.height / 2) };
  });
  expect(sample.progress[0]).toBe(1);
  expect(sample.progress[1]).toBeGreaterThan(0);
  expect(sample.progress.slice(2).every(progress => progress === 0)).toBe(true);
  expect(sample.dot).toBe(1);
  expect(sample.inactiveHidden).toBe(true);
  expect(Math.abs(sample.aggregate - sample.distance)).toBeLessThan(.02);
  expect(sample.error).toBeLessThan(3);
  await expect(page.locator('.writing-point[data-stroke="1"]')).toHaveCSS("opacity", "1");
  if (testInfo.project.name === "chromium") await page.screenshot({ path: "test-results/identity-effects/review/writing-midpoint.png" });
  await page.waitForFunction(() => Number(getComputedStyle(document.querySelector(".period-point")!).opacity) > .5);
  const period = await page.evaluate(() => {
    const traces = [...document.querySelectorAll<SVGPathElement>(".writing-trace")];
    const tail = traces.at(-1)!;
    const end = tail.getPointAtLength(tail.getTotalLength());
    const dot = document.querySelector<SVGPathElement>(".period-trace")!;
    const start = dot.getPointAtLength(0);
    const progress = 1 - parseFloat(getComputedStyle(dot).strokeDashoffset);
    const pen = document.querySelector<SVGGElement>(".period-point")!;
    const point = dot.getPointAtLength(dot.getTotalLength() * progress);
    const expected = new DOMPoint(point.x, point.y).matrixTransform(document.querySelector("svg")!.getScreenCTM()!);
    const box = pen.getBoundingClientRect();
    return { gap: Math.hypot(start.x - end.x, start.y - end.y),
      nibError: Math.hypot(expected.x - box.x - box.width / 2, expected.y - box.y - box.height / 2) };
  });
  expect(period.gap).toBeGreaterThan(3);
  // The requested centimetre adjustment increases the approved tail-to-period gap.
  expect(period.gap).toBeLessThan(14);
  expect(period.nibError).toBeLessThan(3);
  // Fractional delays can serialize a finished Chromium effect as ~1e-15px.
  // Require finished effects as well as negligible numerical residue.
  expect(await page.locator(".writing-trace").evaluateAll(elements => elements.every(el =>
    Math.abs(parseFloat(getComputedStyle(el).strokeDashoffset)) < 1e-9
      && el.getAnimations().every(animation => animation.playState === "finished")))).toBe(true);
  expect(await page.locator(".writing-point").evaluateAll(elements => elements.every(el =>
    Number(getComputedStyle(el).opacity) < 1e-9
      && el.getAnimations().every(animation => animation.playState === "finished")))).toBe(true);
  await page.mouse.move(1, 1);
  await baseline(page);
  await page.locator("#identity").hover();
  await phase(page, "resolved");
  expect(await page.locator(".writing-trace, .period-trace").evaluateAll(elements => elements.every(el =>
    Math.abs(parseFloat(getComputedStyle(el).strokeDashoffset)) < 1e-9
      && el.getAnimations().every(animation => animation.playState === "finished")))).toBe(true);
  await expect(page.locator(".written-ink")).toHaveCSS("mask-image", "none");
  await expect(page.locator(".period-point")).toHaveCSS("opacity", "0");
  await page.emulateMedia({ reducedMotion: "reduce" });
  // Wait for the new quiet reveal, rather than the previous resolved frame.
  await page.waitForFunction(() => {
    const stage = document.querySelector<HTMLElement>("#stage")!;
    return stage.dataset.reduced === "true" && stage.dataset.state === "resolved"
      && [...document.querySelectorAll(".writing-point, .period-point")].every(el => el.getAnimations().length === 0)
      && [...document.querySelectorAll(".writing-trace, .period-trace")].every(el => parseFloat(getComputedStyle(el).strokeDashoffset) === 0);
  });
  await phase(page, "resolved");
  expect(await page.locator(".writing-point, .period-point").evaluateAll(elements => elements.every(el => el.getAnimations().length === 0))).toBe(true);
});

test("rapid leave, reentry and mode changes recover during every phase", async ({ page }) => {
  test.setTimeout(90_000);
  await page.goto("/");
  await hoverMode(page);
  for (const state of ["active", "closing", "closed", "reveal", "resolved"]) {
    await page.locator("#identity").hover();
    await phase(page, state);
    await page.getByRole("button", { name: "Loading Mode", exact: true }).focus();
    await page.keyboard.press("Enter");
    await expect(stage(page)).toHaveAttribute("data-mode", "loading");
    await phase(page, "active");
    await page.getByRole("button", { name: "Hover Mode", exact: true }).click();
    await baseline(page);
  }
  // Loading may have consumed variants; find every wrapper movement by ID.
  for (const target of ["quarter-index", "front-flip", "back-flip", "floor-bounce"]) {
    for (let i = 0; i < variants.length; i++) {
      await page.locator("#identity").hover();
      await phase(page, "active");
      const found = await stage(page).getAttribute("data-variant") === target;
      if (found) await page.waitForFunction(() => {
        const m = new DOMMatrix(getComputedStyle(document.querySelector(".registration")!).transform);
        const plane = new DOMMatrix(getComputedStyle(document.querySelector(".identity-plane")!).transform);
        return Math.abs(m.b) > .2 || Math.abs(plane.m23) > .2 || Math.abs(plane.m42) > 10;
      });
      else await phase(page, "resolved");
      await page.mouse.move(1, 1);
      await baseline(page);
      if (found) break;
      expect(i).toBeLessThan(variants.length - 1);
    }
  }
  await page.locator("#identity").hover();
  await expect(stage(page)).toHaveAttribute("data-state", "active");
  await page.mouse.move(1, 1);
  await expect(stage(page)).toHaveAttribute("data-state", "reset");
  await page.locator("#identity").hover();
  await expect(stage(page)).toHaveAttribute("data-state", "resolved");
  await page.mouse.move(1, 1);
  await baseline(page);
});

test("keyboard inspection, visible focus, held state, escape and blur", async ({ page }) => {
  await page.goto("/");
  await hoverMode(page);
  await page.mouse.move(1, 1);
  await page.locator(".wordmark").focus();
  await page.keyboard.press("Tab");
  await expect(page.locator("#identity")).toBeFocused();
  await expect(page.locator("#identity")).toHaveCSS("outline-style", "solid");
  await page.keyboard.press("Enter");
  await expect(stage(page)).toHaveAttribute("data-state", "resolved");
  await expect(page.locator("#identity")).toHaveAttribute("aria-pressed", "true");
  await page.keyboard.press("Escape");
  await baseline(page);
  await page.keyboard.press("Space");
  await expect(stage(page)).toHaveAttribute("data-state", "resolved");
  await page.keyboard.press("Tab");
  await baseline(page);
});

test("reduced motion skips rotations and handles a live preference change", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  await expect(page.locator("#motion-note")).toBeVisible();
  await hoverMode(page);
  await page.locator("#identity").hover();
  await expect(stage(page)).toHaveAttribute("data-state", "resolved");
  await expect(page.locator(".m-mark")).toHaveCSS("opacity", "1");
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await phase(page, "active");
  await page.waitForFunction(() => [...document.querySelectorAll('.segment')].some(element => {
    const matrix = new DOMMatrix(getComputedStyle(element).transform);
    return Math.abs(matrix.b) > .05;
  }));
  await page.emulateMedia({ reducedMotion: "reduce" });
  await expect(stage(page)).toHaveAttribute("data-reduced", "true");
  for (let i = 0; i < 4; i++) {
    expect(await page.locator(".segment").evaluateAll(elements => elements.every(element => {
      const matrix = new DOMMatrix(getComputedStyle(element).transform);
      return Math.abs(matrix.b) < .001 && Math.abs(matrix.c) < .001 && Math.abs(matrix.a - 1) < .001;
    }))).toBe(true);
    await page.waitForTimeout(80);
  }
  await expect(stage(page)).toHaveAttribute("data-state", "resolved");
  await page.mouse.move(1, 1);
  await baseline(page);
  await page.getByRole("button", { name: "Loading Mode", exact: true }).click();
  await expect(stage(page)).toHaveAttribute("data-state", "resolved");
  const cycle = await stage(page).getAttribute("data-cycle");
  await page.waitForTimeout(1000);
  await expect(stage(page)).toHaveAttribute("data-cycle", cycle!);
  await expect.poll(() => stage(page).getAttribute("data-cycle"), { timeout: 7000 }).not.toBe(cycle);
  await phase(page, "resolved");
});

test("responsive resizing preserves the held mark and usable controls", async ({ page }, testInfo) => {
  await page.goto("/");
  await hoverMode(page);
  await page.locator("#identity").focus();
  await page.keyboard.press("Enter");
  await expect(stage(page)).toHaveAttribute("data-state", "resolved");
  for (const [width, height] of [[320, 568], [390, 844], [600, 800], [768, 1024], [844, 390], [1440, 900]]) {
    await page.setViewportSize({ width: width!, height: height! });
    await expect(stage(page)).toHaveAttribute("data-state", "resolved");
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    const controls = await page.locator(".modes").boundingBox();
    expect(controls!.x).toBeGreaterThanOrEqual(0);
    expect(controls!.x + controls!.width).toBeLessThanOrEqual(width!);
    const surface = await stage(page).boundingBox();
    const trigger = await page.locator("#identity").boundingBox();
    // Include the 2px focus outline and its 6px offset in the clearance.
    expect(trigger!.y - 8).toBeGreaterThanOrEqual(surface!.y);
    expect(trigger!.y + trigger!.height + 8).toBeLessThanOrEqual(surface!.y + surface!.height);
    const mark = await page.locator(".m-mark").boundingBox();
    expect(mark!.width).toBeGreaterThan(40);
    expect(mark!.x).toBeGreaterThan(0);
    expect(mark!.x + mark!.width).toBeLessThan(width!);
    if (width === 390 && testInfo.project.name === "chromium") {
      await page.screenshot({ path: "test-results/identity-effects/resolved-phone.png", fullPage: true });
    }
  }
});

test("touch activation holds once and a second tap resets", async ({ browser, baseURL }) => {
  const context = await browser.newContext({ baseURL, hasTouch: true, viewport: { width: 390, height: 844 } });
  const page = await context.newPage();
  await page.goto("/");
  await hoverMode(page);
  await page.locator("#identity").tap();
  await expect(stage(page)).toHaveAttribute("data-state", "resolved");
  const cycle = await stage(page).getAttribute("data-cycle");
  await page.waitForTimeout(700);
  await expect(stage(page)).toHaveAttribute("data-cycle", cycle!);
  await page.locator("#identity").tap();
  await baseline(page);
  await context.close();
});

test("no JavaScript and unavailable WAAPI preserve semantic content and approved geometry", async ({ browser, baseURL }) => {
  for (const javaScriptEnabled of [false, true]) {
    const context = await browser.newContext({ baseURL, javaScriptEnabled });
    if (javaScriptEnabled) await context.addInitScript(() => {
      Object.defineProperty(Element.prototype, "animate", { value: undefined });
    });
    const page = await context.newPage();
    await page.goto("/");
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    await expect(page.locator(".segment")).toHaveCount(8);
    await expect(page.locator("#identity")).toBeDisabled();
    await expect(page.locator(".m-mark")).toHaveCSS("opacity", "0");
    if (!javaScriptEnabled) await expect(page.locator("noscript")).toBeVisible();
    await context.close();
  }
});
