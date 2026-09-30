import assert from "node:assert/strict";
import { chromium } from "playwright-core";

const baseUrl = process.env.PERSEFONIA_BROWSER_URL ?? "http://127.0.0.1:8080";
const executablePath = process.env.PERSEFONIA_BROWSER_EXECUTABLE;
const chapters = ["identity", "work", "writing", "research", "architecture", "about", "contact"];

const browser = await chromium.launch({
  ...(executablePath ? { executablePath } : {}),
  headless: true,
  args: ["--no-sandbox"]
});

try {
  for (const width of [1440, 834, 390]) {
    const page = await browser.newPage({
      viewport: { width, height: width === 390 ? 850 : 900 },
      reducedMotion: "no-preference"
    });
    const errors = [];
    page.on("pageerror", (error) => errors.push(error.message));
    const response = await page.goto(baseUrl, { waitUntil: "networkidle" });
    assert.equal(response?.status(), 200);
    await page.evaluate(() => document.fonts.ready);
    assert.equal(await page.locator("main [data-archive-chapter]").count(), 7);
    assert.equal(await page.getByRole("heading", { level: 1 }).count(), 1);
    assert.equal(await page.getByRole("heading", { level: 2 }).count(), 6);
    assert.equal(await page.evaluate(() => document.body.scrollWidth), width);
    assert.deepEqual(errors, []);

    const html = await page.content();
    assert.doesNotMatch(html, /\[Public project title\]|\[Published article title\]|\[Published research title\]|ref\. pending/);
    for (const chapter of chapters) {
      assert.equal(await page.locator("#" + chapter).count(), 1);
    }

    const sheet = page.locator("[data-archive-sheet='0']");
    const heights = {};
    for (const chapter of ["identity", "work", "writing", "research", "architecture", "about", "contact"]) {
      await page.evaluate((id) => {
        const section = document.getElementById(id);
        scrollTo(0, section.offsetTop + section.offsetHeight / 2 - innerHeight / 2);
      }, chapter);
      await page.waitForTimeout(60);
      heights[chapter] = (await sheet.boundingBox())?.height ?? 0;
    }
    console.log("Archive sheet heights " + width + "px:", heights);
    assert.ok(heights.writing < heights.work * 0.25, "Writing should flatten the Archive");
    assert.ok(heights.architecture > heights.identity, "Architecture should fully reveal the Archive");
    assert.ok(heights.contact < heights.architecture, "Closing should resolve to a remnant");

    await page.evaluate(() => scrollTo(0, 0));
    await page.keyboard.press("Tab");
    assert.equal(await page.evaluate(() => document.activeElement?.textContent?.trim()), "Skip to main content");
    await page.getByRole("link", { name: "Explore work" }).click();
    assert.equal(new URL(page.url()).hash, "#work");
    assert.equal((await page.request.get(baseUrl + "/contact")).status(), 200);
    assert.equal((await page.request.get(baseUrl + "/tr/projects")).status(), 200);
    console.log("PASS browser " + width + "px: seven chapters, scroll states, keyboard, actions, routes");
    await page.close();
  }

  for (const width of [1440, 390]) {
    const page = await browser.newPage({
      viewport: { width, height: width === 390 ? 850 : 900 },
      reducedMotion: "reduce"
    });
    await page.goto(baseUrl, { waitUntil: "networkidle" });
    assert.equal(await page.evaluate(() => document.documentElement.classList.contains("archive-enhanced")), false);
    assert.equal(await page.locator("[data-archive-chapter]").count(), 7);
    assert.equal(await page.locator(".archive-stage").evaluate((node) => getComputedStyle(node).position), "absolute");
    console.log("PASS reduced motion " + width + "px: static composition and semantic content");
    await page.close();
  }

  for (const failure of ["disabled", "load-failure", "init-error"]) {
    const page = await browser.newPage({
      viewport: { width: 390, height: 850 },
      ...(failure === "disabled" ? { javaScriptEnabled: false } : {})
    });
    if (failure === "load-failure") {
      await page.route(/\/assets\/main-.*\.js/, (route) => route.abort());
    }
    if (failure === "init-error") {
      await page.addInitScript(() => {
        window.matchMedia = () => { throw new Error("simulated initialization failure"); };
      });
    }
    await page.goto(baseUrl, { waitUntil: "networkidle" });
    assert.equal(await page.locator("[data-archive-chapter]").count(), 7);
    assert.equal(await page.getByRole("link", { name: "Get in touch" }).count(), 1);
    assert.equal(await page.getByRole("link", { name: "View all projects" }).count(), 1);
    console.log("PASS enhancement " + failure + ": SSR content and navigation remain available");
    await page.close();
  }
} finally {
  await browser.close();
}
