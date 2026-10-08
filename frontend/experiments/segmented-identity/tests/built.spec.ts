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
  expect(await page.locator("[data-fragment]").count()).toBe(9);
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
