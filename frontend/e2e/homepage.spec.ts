import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { expect, test, type Page } from "@playwright/test";

const databaseContainer = process.env.PERSEFONIA_BROWSER_DB_CONTAINER;
const chapters = ["identity", "work", "writing", "research", "architecture", "about", "contact"];

function setSettings(sql: string): void {
  if (!databaseContainer) throw new Error("browserCheck must provide an isolated fixture database");
  execFileSync("docker", ["exec", databaseContainer, "psql", "--set", "ON_ERROR_STOP=1",
    "-U", "persefonia_browser", "-d", "persefonia_browser", "-c", sql], { stdio: "pipe" });
}

async function visit(page: Page): Promise<void> {
  const response = await page.goto("/", { waitUntil: "networkidle" });
  expect(response?.status()).toBe(200);
  await page.evaluate(() => document.fonts.ready);
}

async function scrollToChapter(page: Page, chapter: string): Promise<void> {
  await page.locator(`#${chapter}`).evaluate(section => {
    window.scrollTo(0, section.getBoundingClientRect().top + window.scrollY +
      section.getBoundingClientRect().height / 2 - window.innerHeight / 2);
  });
  await page.waitForTimeout(120);
}

async function assertStructure(page: Page, language: string): Promise<void> {
  await expect(page.locator("html")).toHaveAttribute("lang", language.toLowerCase());
  await expect(page.locator("[data-archive-chapter]")).toHaveCount(7);
  await expect(page.getByRole("heading", { level: 1 })).toHaveCount(1);
  await expect(page.getByRole("heading", { level: 2 })).toHaveCount(6);
  for (const chapter of chapters) await expect(page.locator(`#${chapter}`)).toHaveCount(1);
  const overflow = await page.evaluate(() => ({
    width: document.documentElement.scrollWidth,
    viewport: window.innerWidth,
    elements: Array.from(document.querySelectorAll("body *"))
      .filter(node => node.getBoundingClientRect().right > window.innerWidth + 1)
      .slice(0, 8).map(node => `${node.tagName}.${node.className}`)
  }));
  expect(overflow.width, JSON.stringify(overflow)).toBeLessThanOrEqual(overflow.viewport + 1);
  expect(await page.locator("body").innerText()).not.toMatch(/\[Public project title\]|ref\. pending|\[excerpt incomplete\]/);
  await expect(page.locator("meta[name='color-scheme']")).toHaveAttribute("content", "dark");
  await expect(page.locator("link[href='/theme.css']")).toHaveCount(0);
}

test.describe.configure({ mode: "serial" });
test.beforeEach(() => {
  if (!databaseContainer) throw new Error("browserCheck must provide an isolated fixture database");
  execFileSync("docker", ["exec", "-i", databaseContainer, "psql", "--set", "ON_ERROR_STOP=1",
    "-U", "persefonia_browser", "-d", "persefonia_browser"],
    { input: readFileSync(new URL("./fixture.sql", import.meta.url)), stdio: ["pipe", "pipe", "pipe"] });
});

test("populated EN layout, routes, Archive transformation and screenshots", async ({ page }, testInfo) => {
  const errors: string[] = [];
  page.on("pageerror", error => errors.push(error.message));
  await page.addInitScript(() => {
    (window as Window & { browserLongTasks?: number[] }).browserLongTasks = [];
    try {
      new PerformanceObserver(list => {
        (window as Window & { browserLongTasks?: number[] }).browserLongTasks?.push(
          ...list.getEntries().map(entry => entry.duration));
      }).observe({ entryTypes: ["longtask"] });
    } catch { /* Unsupported observation is not a page failure. */ }
  });
  await visit(page);
  await assertStructure(page, "EN");
  await expect(page.locator(".home-projects__item")).toHaveCount(3);
  await expect(page.locator(".home-writing__list li")).toHaveCount(3);
  await expect(page.locator(".home-research__list li")).toHaveCount(3);
  await expect(page.locator(".archive-snapshot--identity")).toBeHidden();
  await expect(page.locator("html")).toHaveClass(/archive-enhanced/);
  await expect(page.locator("[data-archive-sheet]")).toHaveCount(4);
  await expect(page.locator("[data-archive-spine]")).toHaveCount(1);
  await expect(page.locator("[data-archive-branch]")).toHaveCount(1);

  const captures = testInfo.project.name === "desktop-1440"
    ? ["identity", "work", "research", "architecture"]
    : testInfo.project.name === "tablet-834" ? ["architecture"] : ["identity", "architecture"];
  for (const chapter of captures) {
    await scrollToChapter(page, chapter);
    await page.screenshot({ path: testInfo.outputPath(`${chapter}.png`) });
  }
  await scrollToChapter(page, "identity");
  const identitySpine = await page.locator("[data-archive-spine]").evaluate(node =>
    Number(node.getAttribute("y2")) - Number(node.getAttribute("y1")));
  await scrollToChapter(page, "writing");
  const writingSheetHeight = (await page.locator("[data-archive-sheet='0']").boundingBox())?.height ?? 0;
  await scrollToChapter(page, "research");
  const researchBranch = await page.locator("[data-archive-branch]").evaluate(node => Number((node as SVGElement).style.opacity));
  await scrollToChapter(page, "architecture");
  const architectureSpine = await page.locator("[data-archive-spine]").evaluate(node =>
    Number(node.getAttribute("y2")) - Number(node.getAttribute("y1")));
  const architectureSheetHeight = (await page.locator("[data-archive-sheet='0']").boundingBox())?.height ?? 0;
  const architectureBranch = await page.locator("[data-archive-branch]").evaluate(node => Number((node as SVGElement).style.opacity));
  expect(architectureSpine).toBeGreaterThan(identitySpine * 2);
  expect(architectureSheetHeight).toBeGreaterThan(writingSheetHeight * 2);
  expect(architectureBranch).toBeGreaterThan(researchBranch);
  await scrollToChapter(page, "contact");
  const closingSpine = await page.locator("[data-archive-spine]").evaluate(node =>
    Number(node.getAttribute("y2")) - Number(node.getAttribute("y1")));
  expect(closingSpine).toBeLessThan(architectureSpine / 2);
  await page.screenshot({ path: testInfo.outputPath("full-page.png"), fullPage: true });

  const projectHref = await page.locator(".home-project h3 a").first().getAttribute("href");
  const writingHref = await page.locator(".home-writing__list h3 a").first().getAttribute("href");
  const researchHref = await page.locator(".home-research__list h3 a").first().getAttribute("href");
  for (const href of [projectHref, writingHref, researchHref]) {
    expect(href).toMatch(/^\/en\/(projects|articles|notes|research)\/[a-z0-9-]+$/);
    expect((await page.request.get(href!)).status()).toBe(200);
  }
  const listingHref = await page.locator(".home-section__more").getAttribute("href");
  expect(listingHref).toMatch(/^\/en\/projects$/);
  expect((await page.request.get(listingHref!)).status()).toBe(200);
  expect(errors).toEqual([]);
  const longTasks = await page.evaluate(() => (window as Window & { browserLongTasks?: number[] }).browserLongTasks ?? []);
  const navigation = await page.evaluate(() => {
    const entry = performance.getEntriesByType("navigation")[0] as PerformanceNavigationTiming | undefined;
    return entry ? { domContentLoaded: entry.domContentLoadedEventEnd, load: entry.loadEventEnd } : null;
  });
  expect(navigation).not.toBeNull();
  console.log(`${testInfo.project.name} local navigation: DOM ready ${navigation!.domContentLoaded.toFixed(1)} ms, load ${navigation!.load.toFixed(1)} ms`);
  console.log(`${testInfo.project.name} local long tasks: ${longTasks.length}, max ${Math.max(0, ...longTasks).toFixed(1)} ms`);
});

test("Turkish copy and content use the same document language", async ({ page }) => {
  setSettings("UPDATE portfolio.site_presentation_settings SET default_language='TR'");
  await visit(page);
  await assertStructure(page, "TR");
  await expect(page.getByRole("heading", { level: 1 })).toContainText("Anlatmaya değer");
  await expect(page.locator(".home-projects__item")).toHaveCount(3);
  await expect(page.locator(".home-writing__list li")).toHaveCount(3);
  await expect(page.locator(".home-research__list li")).toHaveCount(3);
  await expect(page.getByRole("link", { name: /İletişime geç/ })).toHaveCount(1);
});

test("standard public theme responds to LIGHT, DARK and SYSTEM without changing homepage styling", async ({ page }) => {
  for (const [theme, expectedBackground] of [
    ["LIGHT", "rgb(247, 249, 248)"],
    ["DARK", "rgb(16, 22, 27)"],
    ["SYSTEM", "rgb(16, 22, 27)"]
  ] as const) {
    setSettings(`UPDATE portfolio.site_presentation_settings SET standard_public_theme='${theme}'`);
    await page.emulateMedia({ colorScheme: "dark" });
    await page.goto("/en/projects", { waitUntil: "networkidle" });
    await expect(page.locator("link[href='/theme.css']")).toHaveCount(1);
    expect(await page.locator("body").evaluate(node => getComputedStyle(node).backgroundColor)).toBe(expectedBackground);
    const stylesheet = await page.request.get("/theme.css");
    expect(stylesheet.status()).toBe(200);
    expect(stylesheet.headers()["cache-control"]).toBe("public, no-cache, must-revalidate");
  }
  await page.goto("/", { waitUntil: "networkidle" });
  await expect(page.locator("link[href='/theme.css']")).toHaveCount(0);
  expect(await page.locator("body").evaluate(node => getComputedStyle(node).backgroundColor)).toBe("rgb(11, 12, 15)");
});

test("enabled empty and disabled promotion keep chapters with distinct messages", async ({ page }) => {
  setSettings("DELETE FROM discovery.discoverable_resources WHERE source_context='CONTENT_PUBLISHING'; DELETE FROM portfolio.project_localizations; DELETE FROM portfolio.projects");
  await visit(page);
  await assertStructure(page, "EN");
  await expect(page.locator(".home-projects__item")).toHaveCount(0);
  await expect(page.locator(".home-empty")).toHaveCount(3);
  setSettings("UPDATE portfolio.site_presentation_settings SET promote_featured_projects=false, promote_latest_writing=false, promote_research_highlights=false");
  await page.reload({ waitUntil: "networkidle" });
  await expect(page.locator("[data-archive-chapter]")).toHaveCount(7);
  await expect(page.locator(".home-empty")).toHaveCount(0);
});

test("reduced motion uses complete static compositions", async ({ browser }, testInfo) => {
  test.skip(testInfo.project.name === "tablet-834");
  const context = await browser.newContext({ viewport: testInfo.project.use.viewport as { width: number; height: number }, reducedMotion: "reduce" });
  const page = await context.newPage();
  await visit(page);
  await assertStructure(page, "EN");
  await expect(page.locator("html")).not.toHaveClass(/archive-enhanced/);
  for (const chapter of ["identity", "work", "architecture"]) await expect(page.locator(`.archive-snapshot--${chapter}`)).toBeVisible();
  for (const chapter of ["identity", "architecture"]) {
    await scrollToChapter(page, chapter);
    await page.screenshot({ path: testInfo.outputPath(`reduced-${chapter}.png`) });
  }
  await context.close();
});

test("script absence and initialization failure preserve SSR and snapshots", async ({ browser }, testInfo) => {
  test.skip(testInfo.project.name !== "mobile-390");
  for (const failure of ["disabled", "load", "init"]) {
    const context = await browser.newContext({ viewport: { width: 390, height: 850 }, javaScriptEnabled: failure !== "disabled" });
    const page = await context.newPage();
    if (failure === "load") await page.route(/\/assets\/home-[^/]+\.js$/, route => route.abort());
    if (failure === "init") await page.addInitScript(() => {
      window.matchMedia = () => { throw new Error("simulated Archive initialization failure"); };
    });
    await visit(page);
    await assertStructure(page, "EN");
    await expect(page.locator("html")).not.toHaveClass(/archive-enhanced/);
    await expect(page.locator(".archive-snapshot--identity")).toBeVisible();
    await expect(page.locator(".archive-snapshot--architecture")).toBeVisible();
    await expect(page.locator(".home-project h3 a")).toHaveCount(3);
    await context.close();
  }
});

test("keyboard reaches skip link and mobile navigation", async ({ page }, testInfo) => {
  await visit(page);
  await page.keyboard.press("Tab");
  await expect(page.locator(":focus")).toHaveClass(/skip-link/);
  const outline = await page.locator(":focus").evaluate(node => getComputedStyle(node).outlineStyle);
  expect(outline).not.toBe("none");
  if (testInfo.project.name === "mobile-390") {
    await page.locator(".home-nav__mobile summary").focus();
    await page.keyboard.press("Enter");
    await expect(page.locator(".home-nav__mobile")).toHaveAttribute("open", "");
    await page.locator(".home-nav__mobile nav a[href='#work']").click();
    await expect(page.locator(".home-nav__mobile")).not.toHaveAttribute("open", "");
  }
});
