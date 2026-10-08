import { test, expect } from "@playwright/test";

test("a persisted history restoration resumes the same document and real mouse input", async ({ page }) => {
  await page.goto("/"); await expect(page.locator("#status")).toHaveText("REST");
  const marker = await page.evaluate(() => {
    const marker = crypto.randomUUID();
    document.documentElement.dataset.historyMarker = marker;
    window.addEventListener("pageshow", event => { document.documentElement.dataset.persisted = String(event.persisted); });
    return marker;
  });
  await page.goto("/identity.svg"); await page.goBack({ waitUntil: "commit" });
  await expect(page.locator("html")).toHaveAttribute("data-history-marker", marker);
  await expect(page.locator("html")).toHaveAttribute("data-persisted", "true");
  expect(await page.locator(".navigator").count()).toBe(1);
  const box = (await page.locator(".stage").boundingBox())!;
  await page.mouse.move(box.x + box.width * .3, box.y + box.height * .5);
  await page.waitForTimeout(30);
  await page.mouse.move(box.x + box.width * .52, box.y + box.height * .5, { steps: 12 });
  await expect(page.locator("#status")).toHaveText("In motion");
  await expect(page.locator(".navigator")).toHaveAttribute("visibility", "visible");
});
