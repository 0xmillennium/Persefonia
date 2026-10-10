import { test, expect } from "@playwright/test";
import { readdirSync, readFileSync } from "node:fs";

test("standalone release build renders under its CSP and excludes the test API", async ({ page }) => {
  const errors: string[] = [];
  page.on("pageerror", e => errors.push(e.message));
  page.on("console", e => { if (e.type() === "error") errors.push(`${e.text()} ${e.location().url}`); });
  page.on("response", response => {
    if (response.status() >= 400) errors.push(`${response.status()} ${response.url()}`);
  });
  await page.goto("/?test=1");
  await expect(page.locator("#status")).toHaveText("REST");
  expect(await page.evaluate(() => "__identityTest" in window)).toBe(false);
  expect(await page.locator("[data-fragment]").count()).toBe(8);
  const icon = await page.request.get((await page.locator('link[rel="icon"]').getAttribute("href"))!);
  expect(icon.status()).toBe(200);
  expect(icon.headers()["content-type"]).toContain("image/svg+xml");
  expect(errors).toEqual([]);
  const assets = readdirSync("dist/segmented-identity/assets").filter(p => p.endsWith(".js"))
    .map(p => readFileSync(`dist/segmented-identity/assets/${p}`, "utf8")).join("\n");
  expect(assets).not.toContain("__identityTest");
  expect(assets).not.toContain("failed to reach REST within");
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
    expect(errors).toEqual([]);
  } finally { await session.detach(); }
});

test("release navigator consumes a fast whole-stage stroke promptly", async ({ page }, info) => {
  const results: { elapsed: number; updates: number }[] = [];
  for (const y of [100, 310]) {
    await page.goto("/"); await expect(page.locator("#status")).toHaveText("REST");
    const points = await page.locator(".stage").evaluate((svg, y) => {
      const m = (svg as SVGSVGElement).getScreenCTM()!;
      return [50, 910].map(x => {
        const p = new DOMPoint(x, y).matrixTransform(m); return { x: p.x, y: p.y };
      });
    }, y);
    await page.mouse.move(0, 0); await page.mouse.move(points[0]!.x, points[0]!.y);
    await expect(page.locator(".navigator")).toHaveAttribute("visibility", "visible");
    await page.locator(".stage").evaluate(svg => {
      const nav = svg.querySelector(".navigator")!, state = { started: 0, elapsed: -1, updates: 0, x: NaN, y: NaN };
      Object.assign(window, { releaseArrival: state });
      svg.addEventListener("pointermove", e => {
        state.started = performance.now();
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
    await page.mouse.move(points[1]!.x, points[1]!.y);
    await page.waitForFunction(() => (window as unknown as { releaseArrival: { elapsed: number } }).releaseArrival.elapsed >= 0);
    const r = await page.evaluate(() => (window as unknown as { releaseArrival: { elapsed: number; updates: number } }).releaseArrival);
    results.push({ elapsed: r.elapsed, updates: r.updates });
    expect(r.elapsed).toBeLessThan(75); expect(r.updates).toBe(1);
  }
  await info.attach("release-pointer-arrival.json", { body: JSON.stringify(results), contentType: "application/json" });
});
