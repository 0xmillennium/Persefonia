import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { expect, test, type Page, type TestInfo } from "@playwright/test";
import { deriveNarrativeState } from "../src/homepage/narrative/derive-narrative-state";
import { deriveSpatialSceneModel } from "../src/homepage/spatial/scene-model";
import { deriveViewportProjection, projectSpatialPointToViewport } from "../src/homepage/spatial/projection";

const diagnosticKeys = ["ready", "state", "progress", "active-region", "navigator", "ariadne-points",
  "composition-profile", "motion-profile", "viewport-width", "viewport-height", "orientation",
  "hover-capability", "pointer-precision", "attention-active", "attention-modality", "attention-x", "attention-y"];
const truthKeys = ["progress", "active-region", "navigator", "ariadne-points"];
type Diagnostics = Record<string, string | null>;

test.beforeEach(({}, testInfo) => {
  const compact = testInfo.title.startsWith("J1-B2");
  test.skip(testInfo.project.name !== (compact ? "mobile-390" : "desktop-1440"));
  const container = process.env.PERSEFONIA_BROWSER_DB_CONTAINER;
  if (!container) throw new Error("browserCheck must provide an isolated fixture database");
  execFileSync("docker", ["exec", "-i", container, "psql", "--set", "ON_ERROR_STOP=1",
    "-U", "persefonia_browser", "-d", "persefonia_browser"],
    { input: readFileSync(new URL("./fixture.sql", import.meta.url)), stdio: ["pipe", "pipe", "pipe"] });
});

function collectErrors(page: Page) {
  const errors = { pageErrors: [] as string[], consoleErrors: [] as string[] };
  page.on("pageerror", error => errors.pageErrors.push(error.message));
  page.on("console", message => { if (message.type() === "error") errors.consoleErrors.push(message.text()); });
  return errors;
}

const mount = (page: Page) => page.locator("[data-homepage-runtime]");
const canvas = (page: Page) => mount(page).locator("canvas");

async function injectPrototype(page: Page, progress?: string): Promise<void> {
  await page.addInitScript(() => {
    const observer = new MutationObserver(() => {
      const node = document.querySelector<HTMLElement>("[data-homepage-runtime]");
      if (!node) return;
      Object.assign(node.style, { position: "fixed", right: "0", bottom: "0", zIndex: "10", pointerEvents: "none" });
      observer.disconnect();
    });
    observer.observe(document, { childList: true, subtree: true });
  });
  await page.route("**/", async route => {
    const response = await route.fetch();
    const html = await response.text();
    const script = '<script type="module"';
    expect(html).toContain(script);
    const mode = progress === undefined ? 'data-homepage-prototype-mode="native"'
      : `data-homepage-prototype-progress="${progress}"`;
    await route.fulfill({ response, body: html.replace(script,
      `<div data-homepage-runtime ${mode} aria-hidden="true"></div>` + script) });
  });
}

async function visit(page: Page): Promise<void> {
  const response = await page.goto("/", { waitUntil: "networkidle" });
  expect(response?.status()).toBe(200);
  await page.evaluate(() => document.fonts.ready);
  await assertAttributes(page, { ready: "true", state: "running" });
}

async function diagnostics(page: Page): Promise<Diagnostics> {
  return mount(page).evaluate((node, keys) => Object.fromEntries(
    keys.map(key => [key, node.getAttribute(`data-homepage-runtime-${key}`)])), diagnosticKeys);
}

function truth(state: Diagnostics): Diagnostics {
  return Object.fromEntries(truthKeys.map(key => [key, state[key]]));
}

async function assertAttributes(page: Page, expected: Diagnostics): Promise<void> {
  for (const [key, value] of Object.entries(expected)) {
    if (value === null) await expect.poll(() => mount(page).getAttribute(`data-homepage-runtime-${key}`)).toBeNull();
    else await expect(mount(page)).toHaveAttribute(`data-homepage-runtime-${key}`, value);
  }
}

async function assertInactive(page: Page): Promise<void> {
  await assertAttributes(page, { "attention-active": "false", "attention-modality": "none",
    "attention-x": null, "attention-y": null });
}

async function assertTruth(page: Page, expected: Diagnostics, composition: string): Promise<void> {
  expect(truth(await diagnostics(page))).toEqual(expected);
  await assertAttributes(page, { "composition-profile": composition });
}

async function assertCanvas(page: Page, width: number, height: number) {
  await expect(mount(page)).toHaveCount(1);
  await expect(canvas(page)).toHaveCount(1);
  const surface = await canvas(page).evaluate(node => {
    const element = node as HTMLCanvasElement;
    return { width: element.width, height: element.height, webgl2: element.getContext("webgl2") !== null };
  });
  expect(surface).toEqual({ width, height, webgl2: true });
  return surface;
}

async function capture(page: Page, testInfo: TestInfo, filename: string): Promise<Buffer> {
  return canvas(page).screenshot({ path: testInfo.outputPath(filename) });
}

async function assertAttention(page: Page, modality: string, x: number, y: number): Promise<void> {
  await assertAttributes(page, { "attention-active": "true", "attention-modality": modality });
  for (const [key, value] of [["attention-x", x], ["attention-y", y]] as const) {
    await expect.poll(async () => {
      const observed = await mount(page).getAttribute(`data-homepage-runtime-${key}`);
      return observed === null ? NaN : Number(observed);
    }).toBeCloseTo(value, 4);
  }
}

async function reducedEquivalence(page: Page, base: Buffer, expected: Diagnostics, composition: string): Promise<void> {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await assertAttributes(page, { "motion-profile": "reduced" });
  await assertTruth(page, expected, composition);
  expect((await canvas(page).screenshot()).equals(base), "reduced framebuffer equals full base").toBe(true);
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await assertAttributes(page, { "motion-profile": "full" });
  await assertTruth(page, expected, composition);
}

async function navigatorCrop(page: Page, testInfo: TestInfo, surface: { width: number; height: number }) {
  const box = await canvas(page).boundingBox();
  expect(box).not.toBeNull();
  const bounds = box!;
  const scene = deriveSpatialSceneModel(deriveNarrativeState(.58));
  const projection = deriveViewportProjection(scene.macroMap.route, surface.width, surface.height);
  const projected = projectSpatialPointToViewport(scene.navigator.position, projection);
  expect(projected.x).toBeGreaterThanOrEqual(0);
  expect(projected.x).toBeLessThanOrEqual(surface.width);
  expect(projected.y).toBeGreaterThanOrEqual(0);
  expect(projected.y).toBeLessThanOrEqual(surface.height);
  const center = { x: bounds.x + projected.x * bounds.width / surface.width,
    y: bounds.y + projected.y * bounds.height / surface.height };
  const left = Math.max(bounds.x, center.x - 40), top = Math.max(bounds.y, center.y - 40);
  const right = Math.min(bounds.x + bounds.width, center.x + 40);
  const bottom = Math.min(bounds.y + bounds.height, center.y + 40);
  const clip = { x: left, y: top, width: right - left, height: bottom - top };
  expect(center.x).toBeGreaterThanOrEqual(bounds.x);
  expect(center.x).toBeLessThanOrEqual(bounds.x + bounds.width);
  expect(center.y).toBeGreaterThanOrEqual(bounds.y);
  expect(center.y).toBeLessThanOrEqual(bounds.y + bounds.height);
  await assertAttributes(page, { progress: "0.58", "composition-profile": "wide", "motion-profile": "full" });
  await assertInactive(page);
  await page.screenshot({ path: testInfo.outputPath("j1-wide-mid-navigator.png"), clip });
  return { bounds, surface, projected, center, clip };
}

async function currentNativeProgress(page: Page): Promise<number> {
  return page.evaluate(() => {
    const range = Math.max(document.documentElement.scrollHeight - window.innerHeight, 0);
    return range > 0 ? Math.min(1, Math.max(0, window.scrollY / range)) : 0;
  });
}

async function scrollNative(page: Page, progress: number): Promise<Diagnostics> {
  await page.evaluate(value => window.scrollTo(0,
    Math.max(document.documentElement.scrollHeight - window.innerHeight, 0) * value), progress);
  await expect.poll(() => currentNativeProgress(page)).toBeCloseTo(progress, 2);
  let state: Diagnostics | undefined;
  await expect.poll(async () => {
    const snapshot = await mount(page).evaluate((node, keys) => {
      const range = Math.max(document.documentElement.scrollHeight - window.innerHeight, 0);
      return { progress: range > 0 ? Math.min(1, Math.max(0, window.scrollY / range)) : 0,
        diagnostics: Object.fromEntries(keys.map(key => [key, node.getAttribute(`data-homepage-runtime-${key}`)])) };
    }, diagnosticKeys);
    state = snapshot.diagnostics;
    return Math.abs(Number(state.progress) - snapshot.progress);
  }).toBeLessThan(.001);
  const narrative = deriveNarrativeState(Number(state!.progress));
  const scene = deriveSpatialSceneModel(narrative);
  expect(state!["active-region"]).toBe(narrative.activeRegion);
  expect(state!.navigator).toBe(JSON.stringify(scene.navigator.position));
  expect(state!["ariadne-points"]).toBe(String(scene.ariadne.points.length));
  await assertAttributes(page, { state: "running", "composition-profile": "wide", "motion-profile": "full" });
  await assertInactive(page);
  return state!;
}

test("J1-B1 — wide fixed-state WebGL qualification evidence", async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.emulateMedia({ reducedMotion: "no-preference" });
  const errors = collectErrors(page);
  await injectPrototype(page, "0.000");
  await visit(page);
  await assertAttributes(page, { progress: "0", "active-region": "threshold", "ariadne-points": "1",
    "composition-profile": "wide", "motion-profile": "full", "viewport-width": "1280", "viewport-height": "900" });
  await assertInactive(page);
  const surface = await assertCanvas(page, 640, 358);
  const origin = await diagnostics(page);
  await capture(page, testInfo, "j1-wide-origin.png");

  await page.unroute("**/");
  await injectPrototype(page, "0.580");
  await visit(page);
  await assertAttributes(page, { progress: "0.58", "active-region": "architecture", "ariadne-points": "10",
    "composition-profile": "wide", "motion-profile": "full", "pointer-precision": "fine" });
  await assertInactive(page);
  await assertCanvas(page, 640, 358);
  const mid = await diagnostics(page), wideTruth = truth(mid);
  const wideBase = await capture(page, testInfo, "j1-wide-mid-base.png");
  const crop = await navigatorCrop(page, testInfo, surface);
  await reducedEquivalence(page, wideBase, wideTruth, "wide");

  await page.mouse.move(704, 432);
  await assertAttention(page, "pointer", .55, .48);
  await assertTruth(page, wideTruth, "wide");
  const clarified = await diagnostics(page);
  const wideClarified = await capture(page, testInfo, "j1-wide-mid-clarified.png");
  expect(wideClarified.equals(wideBase), "pointer clarification changes framebuffer").toBe(false);
  await page.evaluate(() => window.dispatchEvent(new Event("blur")));
  await assertInactive(page);
  await assertTruth(page, wideTruth, "wide");
  expect((await canvas(page).screenshot()).equals(wideBase), "blur restores base framebuffer").toBe(true);

  await page.unroute("**/");
  await injectPrototype(page, "0.950");
  await visit(page);
  await assertAttributes(page, { progress: "0.95", "active-region": "final", "composition-profile": "wide", "motion-profile": "full" });
  await assertInactive(page);
  const late = await diagnostics(page);
  expect(Number(late["ariadne-points"])).toBeGreaterThan(Number(mid["ariadne-points"]));
  await capture(page, testInfo, "j1-wide-late.png");
  expect(errors.pageErrors).toEqual([]);
  expect(errors.consoleErrors).toEqual([]);
  console.log(`J1-B1 evidence: ${JSON.stringify({ origin, surface, mid, crop, clarified, late,
    reducedEqualsBase: true, clarifiedDiffers: true, restoredEqualsBase: true, errors })}`);
});

test("J1-B2 — compact real-touch qualification evidence", async ({ browser }, testInfo) => {
  const settings = { baseURL: testInfo.project.use.baseURL,
    viewport: { width: 390, height: 844 }, hasTouch: true, reducedMotion: "no-preference" as const };
  const context = await browser.newContext(settings);
  try {
    const page = await context.newPage();
    const errors = collectErrors(page);
    await injectPrototype(page, "0.580");
    await visit(page);
    await assertAttributes(page, { progress: "0.58", "active-region": "architecture", "ariadne-points": "10",
      "composition-profile": "compact", "motion-profile": "full", "pointer-precision": "coarse",
      "viewport-width": "390", "viewport-height": "844" });
    await assertInactive(page);
    const surface = await assertCanvas(page, 374, 209);
    const base = await diagnostics(page), compactTruth = truth(base);
    const compactBase = await capture(page, testInfo, "j1-compact-mid-base.png");
    await reducedEquivalence(page, compactBase, compactTruth, "compact");

    const cdp = await context.newCDPSession(page);
    try {
      const touchPoints = [{ x: 195, y: 422, id: 1, radiusX: 1, radiusY: 1, force: 1 }];
      await cdp.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints });
      await assertAttention(page, "touch", .5, .5);
      await assertTruth(page, compactTruth, "compact");
      const clarified = await diagnostics(page);
      const compactClarified = await capture(page, testInfo, "j1-compact-mid-touch-clarified.png");
      expect(compactClarified.equals(compactBase), "touch clarification changes framebuffer").toBe(false);
      await cdp.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
      await assertInactive(page);
      await assertTruth(page, compactTruth, "compact");
      expect((await canvas(page).screenshot()).equals(compactBase), "touchEnd restores base framebuffer").toBe(true);
      expect(errors.pageErrors).toEqual([]);
      expect(errors.consoleErrors).toEqual([]);
      console.log(`J1-B2 evidence: ${JSON.stringify({ settings, surface, base, touchPoints, clarified,
        reducedEqualsBase: true, clarifiedDiffers: true, restoredEqualsBase: true, errors })}`);
    } finally { await cdp.detach(); }
  } finally { await context.close(); }
});

test("J1-B3 — native backscroll framebuffer reversibility", async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.emulateMedia({ reducedMotion: "no-preference" });
  const errors = collectErrors(page);
  await injectPrototype(page);
  await visit(page);
  await assertAttributes(page, { "composition-profile": "wide", "motion-profile": "full" });
  await assertInactive(page);
  const surface = await assertCanvas(page, 640, 358);

  const before = await scrollNative(page, .3);
  expect(before["active-region"]).toBe("writing");
  const nativeBefore = await capture(page, testInfo, "j1-native-writing-before.png");
  const later = await scrollNative(page, .8);
  expect(later["active-region"]).toBe("person");
  expect(Number(later["ariadne-points"])).toBeGreaterThan(Number(before["ariadne-points"]));
  expect(later.navigator).not.toBe(before.navigator);
  const nativeLater = await capture(page, testInfo, "j1-native-person-later.png");
  expect(nativeLater.equals(nativeBefore), "forward traversal changes framebuffer").toBe(false);

  const restored = await scrollNative(page, .3);
  expect(Number(restored.progress)).toBeCloseTo(Number(before.progress), 3);
  expect(restored["active-region"]).toBe(before["active-region"]);
  expect(restored["ariadne-points"]).toBe(before["ariadne-points"]);
  const beforePosition = JSON.parse(before.navigator!) as Record<string, number>;
  const restoredPosition = JSON.parse(restored.navigator!) as Record<string, number>;
  for (const key of ["x", "y", "depth"]) expect(restoredPosition[key]).toBeCloseTo(beforePosition[key]!, 2);
  const nativeRestored = await capture(page, testInfo, "j1-native-writing-restored.png");
  expect(nativeRestored.equals(nativeBefore), "backscroll restores identical framebuffer").toBe(true);
  expect(errors.pageErrors).toEqual([]);
  expect(errors.consoleErrors).toEqual([]);
  console.log(`J1-B3 evidence: ${JSON.stringify({ surface, before, later, restored,
    laterDiffers: true, restoredEqualsBefore: true, errors })}`);
});
