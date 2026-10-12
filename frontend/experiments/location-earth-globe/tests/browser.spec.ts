import { test, expect, type Page } from "@playwright/test";
import type { GlobeExperiment } from "../src/experiment";
type State = ReturnType<GlobeExperiment["state"]>;
const state = (page: Page): Promise<State> => page.evaluate(() => window.__globeExperiment!.state());
async function ready(page: Page): Promise<void> {
  await page.goto("/?inspect=1");
  await expect(page.locator("#stage")).toHaveAttribute("data-ready", "true");
}
async function center(page: Page) {
  const rect = (await page.locator("#stage").boundingBox())!;
  return { x: rect.x + rect.width / 2, y: rect.y + rect.height / 2, rect };
}

test("shared projection, sphere hover, press suppression and retained drag", async ({ page }, info) => {
  test.skip(info.project.name.includes("touch"), "Mouse drag is covered by desktop; touch has its own gesture test.");
  const errors: string[] = []; page.on("pageerror", e => errors.push(e.message));
  await ready(page);
  await page.locator("#pause").click();
  const c = await center(page), initial = await state(page);
  await page.mouse.move(c.x, c.y);
  await expect.poll(async () => (await state(page)).hover?.[2]).toBeCloseTo(1, 4);
  const hoverSpin = (await state(page)).orientation.spin;
  await page.mouse.move(c.x + 80, c.y - 70);
  await expect.poll(async () => (await state(page)).hover?.[0] ?? 0).toBeGreaterThan(0);
  expect((await state(page)).orientation.spin).toBe(hoverSpin);
  await page.locator("#pause").click();
  await page.mouse.move(c.x, c.y); await page.mouse.down();
  await expect.poll(async () => (await state(page)).phase).toBe("pressed");
  await expect.poll(async () => (await state(page)).hover).toBeNull();
  const held = await state(page);
  await page.waitForTimeout(80);
  expect((await state(page)).orientation.spin).toBe(held.orientation.spin);
  await page.mouse.move(c.x + 100, c.y + 90, { steps: 8 });
  await expect.poll(async () => (await state(page)).phase).toBe("dragging");
  const dragged = await state(page); expect(dragged.orientation.pitch).toBeGreaterThan(initial.orientation.pitch);
  // A final recent stroke precedes release; polling the dragging state can
  // otherwise intentionally turn the gesture into the fast-then-held case.
  await page.mouse.move(c.x + 125, c.y + 90);
  await page.mouse.up();
  await expect.poll(async () => (await state(page)).orientation.speed).toBeGreaterThan(0.2);
  expect((await state(page)).orientation.pitch).toBe(dragged.orientation.pitch);
  await page.waitForTimeout(200);
  expect((await state(page)).orientation.pitch).toBe(dragged.orientation.pitch);
  await page.mouse.move(c.rect.x + 2, c.rect.y + 2);
  await expect.poll(async () => (await state(page)).hover).toBeNull();
  expect(errors).toEqual([]);
});

test("geographic proximity reveals, withdraws, and occludes the panel", async ({ page }) => {
  await ready(page); await page.locator("#pause").click();
  const c = await center(page), location = (await state(page)).location;
  expect(location.visible).toBe(true);
  await page.mouse.move(c.rect.x + location.x, c.rect.y + location.y);
  await expect(page.locator("#location-panel")).toHaveAttribute("data-open", "true");
  await expect(page.locator("#location-panel")).toHaveAttribute("aria-hidden", "false");
  await page.mouse.move(c.x, c.y + 100);
  await expect(page.locator("#location-panel")).toHaveAttribute("data-open", "false");
  // Keyboard rotates the actual shared orientation; marker follows and hides.
  await page.locator("canvas").focus();
  for (let i = 0; i < 22; i++) await page.keyboard.press("ArrowRight");
  await expect.poll(async () => (await state(page)).location.visible).toBe(false);
  await expect(page.locator("#marker")).toBeHidden();
  await expect(page.locator("#location-panel")).toHaveAttribute("data-open", "false");
});

test("stationary proximity tracks a turning marker; captured drag and cancellation stay owned", async ({ page }, info) => {
  test.skip(info.project.name.includes("touch"), "Mouse capture coverage; touch intent is tested separately.");
  await ready(page); await page.locator("#pause").click();
  const c = await center(page), marker = (await state(page)).location;
  await page.mouse.move(c.rect.x + marker.x, c.rect.y + marker.y);
  await expect(page.locator("#location-panel")).toHaveAttribute("data-open", "true");
  // DOM activation leaves the pointer stationary over the marker's old position.
  await page.locator("#pause").evaluate(el => (el as HTMLButtonElement).click());
  await page.mouse.move(c.rect.x + marker.x, c.rect.y + marker.y);
  await expect(page.locator("#location-panel")).toHaveAttribute("data-open", "true");
  await expect(page.locator("#location-panel")).toHaveAttribute("data-open", "false", { timeout: 7000 });
  expect((await state(page)).location.x).toBeGreaterThan(marker.x + 40);
  await page.mouse.move(c.rect.x + 2, c.rect.y + 2); await page.mouse.down();
  expect((await state(page)).orientation.pressed).toBe(false); await page.mouse.up();
  await page.mouse.move(c.x, c.y); await page.mouse.down();
  await page.mouse.move(c.rect.x + c.rect.width + 30, c.y + 110, { steps: 5 });
  await expect.poll(async () => (await state(page)).phase).toBe("dragging");
  await page.evaluate(() => {
    const pointerId = window.__globeExperiment!.state().pointer!;
    document.querySelector("canvas")!.dispatchEvent(new PointerEvent("pointercancel", { pointerId }));
  });
  expect((await state(page)).pointer).toBeNull(); expect((await state(page)).orientation.excess).toBe(0);
  await page.mouse.up();
});

test("keyboard, pause, live reduced motion and responsive marker alignment", async ({ page }) => {
  await ready(page);
  await page.emulateMedia({ reducedMotion: "reduce" });
  await expect.poll(async () => (await state(page)).reduced).toBe(true);
  await expect.poll(async () => (await state(page)).scheduled).toBe(false);
  const start = await state(page);
  await page.locator("canvas").focus(); await page.keyboard.press("ArrowDown");
  await expect.poll(async () => (await state(page)).orientation.pitch).toBeGreaterThan(start.orientation.pitch);
  await page.locator("#find-location").click();
  await expect(page.locator("#location-panel")).toHaveAttribute("data-open", "true");
  for (const size of [{ width: 390, height: 844 }, { width: 768, height: 1024 }, { width: 1137, height: 670 }]) {
    await page.setViewportSize(size);
    await expect.poll(async () => (await state(page)).camera.width).toBe(await page.locator("#stage").evaluate(el => el.clientWidth));
    await expect.poll(async () => (await state(page)).location.x).toBeCloseTo((await page.locator("#stage").evaluate(el => el.clientWidth)) / 2, 0);
    const s = await state(page), marker = await page.locator("#marker").boundingBox(), stage = (await page.locator("#stage").boundingBox())!;
    expect(marker).not.toBeNull();
    expect(marker!.x + marker!.width / 2 - stage.x).toBeCloseTo(s.location.x, 0);
    expect(marker!.y + marker!.height / 2 - stage.y).toBeCloseTo(s.location.y, 0);
    const panel = (await page.locator("#location-panel").boundingBox())!;
    expect(panel.x).toBeGreaterThanOrEqual(stage.x);
    expect(panel.x + panel.width).toBeLessThanOrEqual(stage.x + stage.width + 1);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  }
  await page.keyboard.press("Escape");
  await expect(page.locator("#location-panel")).toHaveAttribute("data-open", "false");
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await expect.poll(async () => (await state(page)).reduced).toBe(false);
  expect((await state(page)).paused).toBe(true); // Explicit pause survives preference change.
});

test("cancel, context restoration, offscreen suspension and idempotent disposal", async ({ page }) => {
  await ready(page); const c = await center(page);
  await page.mouse.move(c.x, c.y); await page.mouse.down();
  await page.evaluate(() => window.dispatchEvent(new Event("blur")));
  await expect.poll(async () => (await state(page)).pointer).toBeNull();
  expect((await state(page)).orientation.excess).toBe(0); await page.mouse.up();
  await page.evaluate(() => { const tail = document.createElement("div"); tail.style.height = "1800px"; document.body.append(tail); window.scrollTo(0, 1500); });
  await expect.poll(async () => (await state(page)).active).toBe(false);
  const offscreen = await state(page); await page.waitForTimeout(80);
  expect((await state(page)).submitted).toBe(offscreen.submitted);
  await page.evaluate(() => window.scrollTo(0, 0));
  await expect.poll(async () => (await state(page)).active).toBe(true);
  const beforeLoss = await state(page);
  await page.evaluate(() => window.__globeExperiment!.contextLoss());
  await expect(page.locator("#stage")).toHaveAttribute("data-ready", "false");
  expect((await state(page)).resources.bytes).toBe(0);
  await page.evaluate(() => { window.dispatchEvent(new Event("pagehide")); window.dispatchEvent(new Event("pageshow")); });
  expect((await state(page)).active).toBe(false);
  expect((await state(page)).scheduled).toBe(false);
  await page.evaluate(() => window.__globeExperiment!.contextRestore());
  await expect(page.locator("#stage")).toHaveAttribute("data-ready", "true");
  expect((await state(page)).resources).toEqual(beforeLoss.resources);
  expect((await state(page)).orientation.pitch).toBe(beforeLoss.orientation.pitch);
  await page.evaluate(() => { window.__globeExperiment!.dispose(); window.__globeExperiment!.dispose(); });
  const disposed = await state(page);
  expect(disposed.disposed).toBe(true); expect(disposed.scheduled).toBe(false);
  expect(disposed.resources).toEqual({ buffers: 0, programs: 0, vaos: 0, bytes: 0 });
  await page.mouse.move(c.x, c.y); await page.setViewportSize({ width: 700, height: 600 });
  await page.waitForTimeout(80); expect((await state(page)).submitted).toBe(disposed.submitted);
});

test("no JavaScript, no GPU, and missing geography retain semantic Istanbul content", async ({ browser }) => {
  for (const kind of ["no-js", "no-gpu", "missing-mask"] as const) {
    const context = await browser.newContext({ javaScriptEnabled: kind !== "no-js" });
    const page = await context.newPage();
    if (kind === "no-gpu") await page.addInitScript(() => {
      const original = HTMLCanvasElement.prototype.getContext;
      HTMLCanvasElement.prototype.getContext = function (this: HTMLCanvasElement, ...args: Parameters<typeof original>) {
        if (args[0] === "webgl2") return null;
        return original.apply(this, args);
      } as typeof original;
    });
    if (kind === "missing-mask") await page.route("**/*land-mask*", route => route.abort());
    await page.goto("/?inspect=1");
    await expect(page.locator("#fallback")).toBeVisible();
    await expect(page.locator("#fallback")).toContainText("Istanbul, Türkiye");
    await expect(page.locator("#find-location")).toBeDisabled();
    await context.close();
  }
});

test("touch horizontal exploration and native vertical scrolling", async ({ page }, info) => {
  test.skip(info.project.name !== "chromium-touch", "Requires Chromium touch emulation and CDP touch input.");
  await ready(page);
  const session = await page.context().newCDPSession(page), c = await center(page);
  const before = await state(page);
  const markerX = c.rect.x + before.location.x, markerY = c.rect.y + before.location.y;
  for (const expected of ["true", "false"] as const) {
    await session.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: [{ x: markerX, y: markerY }] });
    await page.waitForFunction(() => window.__globeExperiment!.state().orientation.pressed);
    await session.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
    await expect(page.locator("#location-panel")).toHaveAttribute("data-open", expected);
  }
  await session.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: [{ x: c.x, y: c.y }] });
  for (let i = 1; i <= 8; i++) await session.send("Input.dispatchTouchEvent", { type: "touchMove", touchPoints: [{ x: c.x + i * 9, y: c.y + 2 }] });
  await session.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
  expect((await state(page)).orientation.spin).not.toBe(before.orientation.spin);
  expect((await state(page)).orientation.pitch).toBe(before.orientation.pitch);
  expect((await state(page)).hover).toBeNull();
  await page.locator("#tilt-down").click(); expect((await state(page)).orientation.pitch).toBeGreaterThan(before.orientation.pitch);
  await page.evaluate(() => { const tail = document.createElement("div"); tail.style.height = "1000px"; document.body.append(tail); });
  const current = await center(page);
  await session.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: [{ x: current.x, y: current.y }] });
  for (let i = 1; i <= 6; i++) await session.send("Input.dispatchTouchEvent", { type: "touchMove", touchPoints: [{ x: current.x, y: current.y - i * 22 }] });
  await session.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
  await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(0);
  expect((await state(page)).pointer).toBeNull(); expect((await state(page)).orientation.excess).toBe(0);
});

test("GPU allocations stay bounded and state inspection makes no hot metadata queries", async ({ page }) => {
  await page.addInitScript(() => {
    const live = { buffer: new Set(), program: new Set(), vao: new Set(), shader: new Set() };
    const p = WebGL2RenderingContext.prototype as unknown as Record<string, (...args: unknown[]) => unknown>;
    let metadata = 0, allocations = 0, uploads = 0;
    for (const [create, remove, kind] of [["createBuffer", "deleteBuffer", "buffer"], ["createProgram", "deleteProgram", "program"],
      ["createVertexArray", "deleteVertexArray", "vao"], ["createShader", "deleteShader", "shader"]] as const) {
      const allocate = p[create]!, dispose = p[remove]!;
      p[create] = function (this: WebGL2RenderingContext, ...args) {
        const object = allocate.apply(this, args); if (object) { live[kind].add(object); allocations++; } return object;
      };
      p[remove] = function (this: WebGL2RenderingContext, ...args) { live[kind].delete(args[0]); return dispose.apply(this, args); };
    }
    for (const name of ["getParameter", "getProgramParameter", "getUniformLocation", "getExtension", "getError", "readPixels"]) {
      const original = p[name]!; p[name] = function (this: WebGL2RenderingContext, ...args) { metadata++; return original.apply(this, args); };
    }
    const upload = p.bufferData!;
    p.bufferData = function (this: WebGL2RenderingContext, ...args) { uploads++; return upload.apply(this, args); };
    Object.assign(window, { gpuAudit: () => ({ live: Object.fromEntries(Object.entries(live).map(([k, set]) => [k, set.size])), metadata, allocations, uploads }) });
  });
  const audit = () => page.evaluate(() => (window as unknown as { gpuAudit(): { live: Record<string, number>; metadata: number; allocations: number; uploads: number } }).gpuAudit());
  await ready(page);
  const first = await audit(), submitted = (await state(page)).submitted;
  expect(first.live).toEqual({ buffer: 1, program: 1, vao: 1, shader: 0 });
  await expect.poll(async () => (await state(page)).submitted).toBeGreaterThan(submitted + 12);
  expect(await audit()).toEqual(first);
  await page.evaluate(() => window.__globeExperiment!.dispose());
  expect((await audit()).live).toEqual({ buffer: 0, program: 0, vao: 0, shader: 0 });
});

test("document visibility and navigation lifecycle clear gestures without jumps", async ({ page }) => {
  await ready(page);
  await page.locator("#pause").click();
  const c = await center(page); await page.mouse.move(c.x, c.y); await page.mouse.down();
  const initial = await state(page);
  await page.evaluate(() => {
    Object.defineProperty(document, "hidden", { configurable: true, value: true });
    document.dispatchEvent(new Event("visibilitychange"));
  });
  expect((await state(page)).active).toBe(false);
  expect((await state(page)).pointer).toBeNull(); expect((await state(page)).scheduled).toBe(false);
  await page.evaluate(() => {
    Object.defineProperty(document, "hidden", { configurable: true, value: false });
    document.dispatchEvent(new Event("visibilitychange"));
  });
  await expect.poll(async () => (await state(page)).active).toBe(true);
  expect((await state(page)).orientation.spin).toBe(initial.orientation.spin);
  await page.mouse.up();
  await page.evaluate(() => window.dispatchEvent(new Event("pagehide")));
  expect((await state(page)).scheduled).toBe(false);
  await page.evaluate(() => window.dispatchEvent(new Event("pageshow")));
  await expect.poll(async () => (await state(page)).active).toBe(true);
  expect((await state(page)).paused).toBe(true);
});

test("built resources run under strict self-hosted CSP", async ({ page }) => {
  test.skip(process.env.GLOBE_BUILT !== "1", "Vite's development transport is outside the production CSP check.");
  const errors: string[] = []; page.on("pageerror", e => errors.push(e.message));
  page.on("console", m => { if (m.type() === "error") errors.push(m.text()); });
  const origins = new Set<string>(); page.on("request", r => { if (r.url().startsWith("http")) origins.add(new URL(r.url()).origin); });
  await page.route("**/*", async route => {
    const response = await route.fetch();
    const headers = response.headers();
    if (headers["content-type"]?.includes("text/html")) headers["content-security-policy"] = "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self'; base-uri 'none'; object-src 'none'";
    await route.fulfill({ response, headers });
  });
  await ready(page); await page.locator("#find-location").click();
  await expect(page.locator("#location-panel")).toHaveAttribute("data-open", "true");
  expect(origins).toEqual(new Set(["http://127.0.0.1:4281"])); expect(errors).toEqual([]);
});
