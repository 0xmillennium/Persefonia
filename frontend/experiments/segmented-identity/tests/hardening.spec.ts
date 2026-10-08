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
test("a whole-stage stroke is replayed without a single half-second animation callback", async ({ page, browserName }, info) => {
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
