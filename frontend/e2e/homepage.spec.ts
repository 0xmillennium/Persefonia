import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { chromium, expect, test, type Page } from "@playwright/test";
import { chapterPosition } from "../src/archive-model";
import { renderedArchivePositionMatches } from "./support/archive-position";
import { deriveNarrativeState } from "../src/homepage/narrative/derive-narrative-state";
import { deriveSpatialSceneModel } from "../src/homepage/spatial/scene-model";

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
  expect(await page.locator("html").evaluate(node => getComputedStyle(node).colorScheme)).toBe("dark");
  await expect(page.locator("link[href='/theme.css']")).toHaveCount(0);
}

function contrast(foreground: string, background: string): number {
  const luminance = (color: string): number => {
    const channels = color.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+)/);
    if (!channels) throw new Error(`Expected an opaque computed RGB color: ${color}`);
    const linear = channels.slice(1, 4).map(value => {
      const channel = Number(value) / 255;
      return channel <= .04045 ? channel / 12.92 : ((channel + .055) / 1.055) ** 2.4;
    });
    return linear[0]! * .2126 + linear[1]! * .7152 + linear[2]! * .0722;
  };
  const values = [luminance(foreground), luminance(background)].sort((a, b) => b - a);
  return (values[0]! + .05) / (values[1]! + .05);
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
  await visit(page);
  await assertStructure(page, "EN");
  await expect(page.locator("[data-homepage-runtime]")).toHaveCount(0);
  expect(await page.evaluate(() => ["Instrument Serif", "Inter", "IBM Plex Mono"].every(font =>
    document.fonts.check(`16px "${font}"`)))).toBe(true);
  await expect(page.locator(".home-projects__item")).toHaveCount(3);
  await expect(page.locator(".home-writing__list li")).toHaveCount(3);
  await expect(page.locator(".home-research__list li")).toHaveCount(3);
  await expect(page.locator(".archive-snapshot--identity")).toBeHidden();
  await expect(page.locator("html")).toHaveClass(/archive-enhanced/);
  await expect(page.locator("[data-archive-sheet]")).toHaveCount(4);
  await expect(page.locator("[data-archive-spine]")).toHaveCount(1);
  await expect(page.locator("[data-archive-tick]")).toHaveCount(12);
  await expect(page.locator("[data-archive-seam]")).toHaveCount(4);
  await expect(page.locator("[data-archive-joint]")).toHaveCount(4);
  await expect(page.locator("[data-archive-trace]")).toHaveCount(3);
  await expect(page.locator("[data-archive-tab]")).toHaveCount(4);
  await expect(page.locator("[data-archive-vellum]")).toHaveCount(2);
  await expect(page.locator("[data-archive-branch]")).toHaveCount(1);

  const captures = testInfo.project.name === "desktop-1440"
    ? ["identity", "work", "research", "architecture"]
    : testInfo.project.name === "tablet-834" ? ["architecture"] : ["identity", "research", "architecture"];
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
  const navigation = await page.evaluate(() => {
    const entry = performance.getEntriesByType("navigation")[0] as PerformanceNavigationTiming | undefined;
    return entry ? { domContentLoaded: entry.domContentLoadedEventEnd, load: entry.loadEventEnd } : null;
  });
  expect(navigation).not.toBeNull();
  console.log(`${testInfo.project.name} local navigation: DOM ready ${navigation!.domContentLoaded.toFixed(1)} ms, load ${navigation!.load.toFixed(1)} ms`);
});

test("isolated Archive scroll records runtime long tasks after load settles", async ({ page }, testInfo) => {
  await visit(page);
  await expect(page.locator("html")).toHaveClass(/archive-enhanced/);
  await page.waitForTimeout(250);
  const timing = await page.evaluate(() => {
    const entry = performance.getEntriesByType("navigation")[0] as PerformanceNavigationTiming;
    return { domContentLoaded: entry.domContentLoadedEventEnd, load: entry.loadEventEnd };
  });
  const supported = await page.evaluate(() => {
    const state = window as Window & { archiveLongTasks?: number[]; archiveObserver?: PerformanceObserver };
    state.archiveLongTasks = [];
    if (!PerformanceObserver.supportedEntryTypes.includes("longtask")) return false;
    state.archiveObserver = new PerformanceObserver(list => {
      state.archiveLongTasks!.push(...list.getEntries().map(entry => entry.duration));
    });
    state.archiveObserver.observe({ entryTypes: ["longtask"] });
    return true;
  });
  expect(supported, "Long Tasks API support is required for scroll performance evidence").toBe(true);
  const steps = 14;
  for (let index = 0; index < steps; index++) {
    await page.evaluate(fraction => window.scrollTo(0, (document.documentElement.scrollHeight - window.innerHeight) * fraction), index / (steps - 1));
    await page.waitForTimeout(80);
  }
  const durations = await page.evaluate(() => {
    const state = window as Window & { archiveLongTasks?: number[]; archiveObserver?: PerformanceObserver };
    const recorded = state.archiveLongTasks ?? [];
    state.archiveObserver?.disconnect();
    return recorded;
  });
  console.log(`${testInfo.project.name} local Chromium loading: DOMContentLoaded ${timing.domContentLoaded.toFixed(1)} ms, load ${timing.load.toFixed(1)} ms`);
  console.log(`${testInfo.project.name} isolated scroll: API supported ${supported}, ${steps} steps, long tasks ${durations.length}, max ${Math.max(0, ...durations).toFixed(1)} ms; settled first, observer started with no buffered records, no screenshots during interval`);
});

test("Archive sheet materials follow scroll position across key transitions", async ({ page }, testInfo) => {
  await visit(page);
  await expect(page.locator("html")).toHaveClass(/archive-enhanced/);
  const anchors = await page.evaluate(() => Array.from(document.querySelectorAll<HTMLElement>("[data-archive-chapter]"))
    .map(chapter => chapter.getBoundingClientRect().top + window.scrollY + chapter.offsetHeight / 2 - window.innerHeight / 2));
  for (const [start, end] of [[0, 1], [3, 4], [4, 5]]) {
    const progressValues = start === 3 ? [0, .25, .49, .5, .51, .75, 1] : [0, .49, .5, .51, 1];
    const samples: { prominence: number[]; surface: string; border: string }[] = [];
    for (const progress of progressValues) {
      const scrollY = await page.evaluate(y => {
        window.scrollTo(0, y);
        return window.scrollY;
      }, anchors[start]! + (anchors[end]! - anchors[start]!) * progress);
      const target = chapterPosition(scrollY, anchors);
      // The controller publishes this position after writing every rendered Archive style.
      await expect.poll(async () => {
        const observed = await page.locator(".archive-stage").getAttribute("data-archive-position");
        return observed !== null && renderedArchivePositionMatches(Number(observed), target);
      }).toBe(true);
      samples.push(await page.locator("[data-archive-sheet]").evaluateAll(sheets => ({
        prominence: sheets.map(sheet => Number(getComputedStyle(sheet).getPropertyValue("--archive-sheet-prominence"))),
        surface: getComputedStyle(sheets[0]!).backgroundColor,
        border: getComputedStyle(sheets[0]!).borderTopColor
      })));
      if (testInfo.project.name === "desktop-1440" && progress > 0 && progress < 1)
        await page.screenshot({ path: testInfo.outputPath(`material-${start}-${end}-${Math.round(progress * 100)}.png`) });
    }
    for (let sheet = 0; sheet < 4; sheet++) {
      const first = samples[0]!.prominence[sheet]!;
      const last = samples.at(-1)!.prominence[sheet]!;
      for (let index = 0; index < progressValues.length; index++) {
        expect(samples[index]!.prominence[sheet]!, `${testInfo.project.name} transition ${start}→${end} sheet ${sheet}`)
          .toBeCloseTo(first + (last - first) * progressValues[index]!, 2);
      }
    }
    expect(samples[0]!.surface).not.toBe(samples.at(-1)!.surface);
    expect(samples[0]!.border).not.toBe(samples.at(-1)!.border);
  }
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

test("standard theme covers projects, content TOC, and CV in both system preferences", async ({ page }, testInfo) => {
  test.setTimeout(120_000);
  for (const [theme, preference, expectedBackground, expectedSurface] of [
    ["LIGHT", "dark", "rgb(247, 249, 248)", "rgb(255, 255, 255)"],
    ["DARK", "light", "rgb(16, 22, 27)", "rgb(27, 37, 43)"],
    ["SYSTEM", "light", "rgb(247, 249, 248)", "rgb(255, 255, 255)"],
    ["SYSTEM", "dark", "rgb(16, 22, 27)", "rgb(27, 37, 43)"]
  ] as const) {
    setSettings(`UPDATE portfolio.site_presentation_settings SET standard_public_theme='${theme}'`);
    await page.emulateMedia({ colorScheme: preference });
    for (const route of ["/en/projects", "/en/articles/fixture-writing-1", "/cv", "/cv/en", "/cv/tr"]) {
      const response = await page.goto(route, { waitUntil: "networkidle" });
      expect(response?.status(), route).toBe(200);
      const links = await page.locator("head link[rel='stylesheet']").evaluateAll(nodes => nodes.map(node => node.getAttribute("href")));
      expect(links.at(-1), route).toBe("/theme.css");
      expect(links.some(href => href?.startsWith("/assets/main-")), route).toBe(true);
      expect(await page.locator("body").evaluate(node => getComputedStyle(node).backgroundColor), route).toBe(expectedBackground);
      expect(await page.locator("body").evaluate(node => getComputedStyle(node).color), route).not.toBe(expectedBackground);
      if (route === "/cv/tr") await expect(page.locator("html")).toHaveAttribute("lang", "tr");
      if (route.includes("articles")) {
        await expect(page.locator(".public-toc")).toBeVisible();
        const colors = await page.locator(".public-toc").evaluate(node => ({
          background: getComputedStyle(node).backgroundColor,
          text: getComputedStyle(node).color,
          link: getComputedStyle(node.querySelector("a")!).color
        }));
        expect(colors.background).toBe(expectedSurface);
        const textContrast = contrast(colors.text, colors.background);
        expect(textContrast).toBeGreaterThanOrEqual(4.5);
        expect(contrast(colors.link, colors.background)).toBeGreaterThanOrEqual(4.5);
        console.log(`${theme}/${preference} TOC text contrast ${textContrast.toFixed(2)}:1`);
        await page.locator(".public-toc a").focus();
        expect(await page.locator(".public-toc a").evaluate(node => getComputedStyle(node).outlineStyle)).not.toBe("none");
        if (testInfo.project.name === "desktop-1440" && theme !== "SYSTEM")
          await page.screenshot({ path: testInfo.outputPath(`toc-${theme.toLowerCase()}.png`) });
      }
      if (route === "/cv/en" && testInfo.project.name === "desktop-1440" && theme !== "SYSTEM")
        await page.screenshot({ path: testInfo.outputPath(`cv-${theme.toLowerCase()}.png`) });
    }
    const stylesheet = await page.request.get("/theme.css");
    expect(stylesheet.status()).toBe(200);
    expect(stylesheet.headers()["content-type"]).toContain("text/css");
    expect(stylesheet.headers()["cache-control"]).toBe("public, no-cache, must-revalidate");
    await page.goto("/", { waitUntil: "networkidle" });
    await expect(page.locator("link[href='/theme.css']")).toHaveCount(0);
    expect(await page.locator("body").evaluate(node => getComputedStyle(node).backgroundColor)).toBe("rgb(11, 12, 15)");
  }
});

test("standard public CSS remains coherently light when the theme resource fails", async ({ browser }, testInfo) => {
  const context = await browser.newContext({
    viewport: testInfo.project.use.viewport as { width: number; height: number },
    colorScheme: "dark",
    javaScriptEnabled: false
  });
  const page = await context.newPage();
  const errors: string[] = [];
  page.on("pageerror", error => errors.push(error.message));
  await page.route("**/theme.css", route => route.abort());
  const response = await page.goto("/contact", { waitUntil: "networkidle" });
  expect(response?.status()).toBe(200);
  await expect(page.getByRole("heading", { name: "Contact" })).toBeVisible();
  expect(await page.locator("html").evaluate(node => getComputedStyle(node).colorScheme)).toBe("light");
  expect(await page.locator("body").evaluate(node => getComputedStyle(node).backgroundColor)).toBe("rgb(247, 249, 248)");
  expect(await page.locator("body").evaluate(node => getComputedStyle(node).color)).toBe("rgb(31, 41, 51)");
  await expect(page.getByLabel("Name")).toBeVisible();
  await expect(page.getByRole("button", { name: "Send message" })).toBeVisible();
  await page.keyboard.press("Tab");
  await expect(page.getByRole("link", { name: "Skip to main content" })).toBeFocused();
  await page.getByLabel("Name").focus();
  await page.keyboard.type("Ada");
  await expect(page.getByLabel("Name")).toHaveValue("Ada");
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(testInfo.project.use.viewport!.width);
  expect(errors).toEqual([]);
  await context.close();
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
  for (const chapter of ["identity", "work", "architecture"]) {
    if (chapter === "work") {
      await page.locator("#work").evaluate(section => window.scrollTo(0, section.getBoundingClientRect().top + window.scrollY + 16));
    } else {
      await scrollToChapter(page, chapter);
    }
    await page.screenshot({ path: testInfo.outputPath(`reduced-${chapter}.png`) });
  }
  await context.close();
});

test("script absence and initialization failure preserve SSR and snapshots", async ({ browser }, testInfo) => {
  test.skip(testInfo.project.name !== "mobile-390");
  for (const failure of ["disabled", "load", "init", "first-render", "after-render-init"]) {
    const context = await browser.newContext({ viewport: { width: 390, height: 850 }, javaScriptEnabled: failure !== "disabled" });
    const page = await context.newPage();
    if (failure === "load") await page.route(/\/assets\/home-[^/]+\.js$/, route => route.abort());
    if (failure === "init") await page.addInitScript(() => {
      window.matchMedia = () => { throw new Error("simulated Archive initialization failure"); };
    });
    if (failure === "first-render") await page.addInitScript(() => {
      const original = SVGElement.prototype.setAttribute;
      SVGElement.prototype.setAttribute = function(name, value) {
        if (this.matches("[data-archive-spine]") && name === "x1") throw new Error("simulated first render failure");
        original.call(this, name, value);
      };
    });
    if (failure === "after-render-init") await page.addInitScript(() => {
      window.ResizeObserver = class { constructor() { throw new Error("simulated observer initialization failure"); } } as typeof ResizeObserver;
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

test("the opt-in Pixi candidate initializes without replacing semantic content", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop-1440");
  const errors: string[] = [];
  page.on("pageerror", error => errors.push(error.message));
  page.on("console", message => {
    if (message.type() === "error") errors.push(message.text());
  });
  await page.route("**/", async route => {
    const response = await route.fetch();
    const html = await response.text();
    const script = '<script type="module"';
    expect(html).toContain(script);
    await route.fulfill({ response, body: html.replace(script, '<div data-homepage-runtime aria-hidden="true"></div>' + script) });
  });

  await visit(page);
  await expect(page.locator("[data-homepage-runtime] canvas")).toHaveCount(1);
  await expect(page.getByRole("heading", { level: 1 })).toHaveCount(1);
  await expect(page.locator("[data-archive-chapter]")).toHaveCount(7);
  expect(await page.locator("[data-homepage-runtime] canvas").evaluate(canvas =>
    (canvas as HTMLCanvasElement).getContext("webgl2") !== null)).toBe(true);
  expect(errors).toEqual([]);
});

test("the opt-in spatial topology renders from canonical progress under CSP", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop-1440");
  const errors: string[] = [];
  page.on("pageerror", error => errors.push(error.message));
  page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
  let progress = "0.000";
  await page.route("**/", async route => {
    const response = await route.fetch();
    const html = await response.text();
    const script = '<script type="module"';
    expect(html).toContain(script);
    await route.fulfill({ response, body: html.replace(script,
      `<div data-homepage-runtime data-homepage-prototype-progress="${progress}" aria-hidden="true"></div>${script}`) });
  });
  const captures: Buffer[] = [];
  for (const value of ["0.000", "0.180", "0.437", "0.580", "0.810", "1.000"]) {
    progress = value;
    await visit(page);
    const mount = page.locator("[data-homepage-runtime]");
    await expect(mount).toHaveAttribute("data-homepage-runtime-ready", "true");
    await expect(mount).toHaveAttribute("data-homepage-runtime-progress", String(Number(value)));
    const canvas = mount.locator("canvas");
    await expect(canvas).toHaveCount(1);
    expect(await canvas.evaluate(node => (node as HTMLCanvasElement).getContext("webgl2") !== null)).toBe(true);
    await expect(page.getByRole("heading", { level: 1 })).toHaveCount(1);
    await expect(page.locator("[data-archive-chapter]")).toHaveCount(7);
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
      await page.evaluate(() => window.innerWidth + 1));
    captures.push(await canvas.screenshot());
    if (value === "0.580") {
      await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
      await expect(mount).toHaveAttribute("data-homepage-runtime-progress", "0.58");
    }
  }
  expect(captures[0]!.equals(captures[2]!)).toBe(false);
  expect(captures[2]!.equals(captures[5]!)).toBe(false);
  expect(errors).toEqual([]);
});

test("runtime render failure restores snapshots and stops the enhancement", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "mobile-390");
  await visit(page);
  await expect(page.locator("html")).toHaveClass(/archive-enhanced/);
  await page.evaluate(() => {
    const original = SVGElement.prototype.setAttribute;
    const state = window as Window & { failedArchiveWrites?: number };
    state.failedArchiveWrites = 0;
    SVGElement.prototype.setAttribute = function(name, value) {
      if (this.matches("[data-archive-spine]") && name === "x1") {
        state.failedArchiveWrites!++;
        throw new Error("simulated runtime render failure");
      }
      original.call(this, name, value);
    };
  });
  await scrollToChapter(page, "research");
  await expect(page.locator("html")).not.toHaveClass(/archive-enhanced/);
  await expect(page.locator(".archive-snapshot--architecture")).toBeVisible();
  await scrollToChapter(page, "architecture");
  expect(await page.evaluate(() => (window as Window & { failedArchiveWrites?: number }).failedArchiveWrites)).toBe(1);
  await expect(page.locator("#architecture h2")).toBeVisible();
});

test("changing reduced-motion preference restores and reactivates the Archive safely", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "mobile-390");
  await visit(page);
  await expect(page.locator("html")).toHaveClass(/archive-enhanced/);
  await page.emulateMedia({ reducedMotion: "reduce" });
  await expect(page.locator("html")).not.toHaveClass(/archive-enhanced/);
  await expect(page.locator(".archive-snapshot--identity")).toBeVisible();
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await expect(page.locator("html")).toHaveClass(/archive-enhanced/);
  await expect(page.locator(".archive-snapshot--identity")).toBeHidden();
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

async function injectPrototype(page: Page, fixedProgress?: string): Promise<void> {
  await page.addInitScript(() => {
    const observer = new MutationObserver(() => {
      const mount = document.querySelector<HTMLElement>("[data-homepage-runtime]");
      if (!mount) return;
      Object.assign(mount.style, { position: "fixed", right: "0", bottom: "0", zIndex: "10", pointerEvents: "none" });
      observer.disconnect();
    });
    observer.observe(document, { childList: true, subtree: true });
  });
  await page.route("**/", async route => {
    const response = await route.fetch();
    const html = await response.text();
    const script = '<script type="module"';
    expect(html).toContain(script);
    await route.fulfill({ response, body: html.replace(script,
      `<div data-homepage-runtime ${fixedProgress === undefined ? 'data-homepage-prototype-mode="native"' : ""} data-homepage-prototype-progress="${fixedProgress ?? "0.10"}" aria-hidden="true"></div>` + script) });
  });
}

async function currentNativeProgress(page: Page): Promise<number> {
  return page.evaluate(() => {
    const range = Math.max(document.documentElement.scrollHeight - window.innerHeight, 0);
    return range > 0 ? Math.min(1, Math.max(0, window.scrollY / range)) : 0;
  });
}

async function assertNativeProgress(page: Page): Promise<Awaited<ReturnType<typeof nativeBrowserSnapshot>>> {
  let snapshot: Awaited<ReturnType<typeof nativeBrowserSnapshot>> | undefined;
  await expect.poll(async () => {
    snapshot = await nativeBrowserSnapshot(page);
    return Math.abs(Number(snapshot.diagnostics.progress) - snapshot.nativeProgress);
  }).toBeLessThan(.001);
  const diagnostics = ["progress", "active-region", "navigator", "ariadne-points"]
    .map(key => snapshot!.diagnostics[key]);
  const narrative = deriveNarrativeState(Number(diagnostics[0]));
  const scene = deriveSpatialSceneModel(narrative);
  expect(diagnostics[1]).toBe(narrative.activeRegion);
  expect(diagnostics[2]).toBe(JSON.stringify(scene.navigator.position));
  expect(diagnostics[3]).toBe(String(scene.ariadne.points.length));
  return snapshot!;
}

async function scrollNative(page: Page, progress: number): Promise<void> {
  await page.evaluate(value => window.scrollTo(0,
    Math.max(document.documentElement.scrollHeight - window.innerHeight, 0) * value), progress);
  await expect.poll(() => currentNativeProgress(page)).toBeCloseTo(progress, 2);
  await assertNativeProgress(page);
}

test("prototype current attention and independent presentation profiles preserve fixed narrative truth", async ({ page }) => {
  const errors: string[] = [];
  page.on("pageerror", error => errors.push(error.message));
  page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await injectPrototype(page, "0.580");
  await visit(page);
  const mount = page.locator("[data-homepage-runtime]");
  await expect(mount).toHaveAttribute("data-homepage-runtime-ready", "true");
  await expect(mount).toHaveAttribute("data-homepage-runtime-composition-profile", "wide");
  await expect(mount).toHaveAttribute("data-homepage-runtime-motion-profile", "full");
  const truth = await mount.evaluate(node => ["progress", "active-region", "navigator", "ariadne-points"]
    .map(key => node.getAttribute(`data-homepage-runtime-${key}`)));
  expect(truth[0]).toBe("0.58");
  await page.mouse.move(320, 225);
  await expect(mount).toHaveAttribute("data-homepage-runtime-attention-active", "true");
  await expect(mount).toHaveAttribute("data-homepage-runtime-attention-modality", "pointer");
  await expect.poll(async () => Number(await mount.getAttribute("data-homepage-runtime-attention-x"))).toBeCloseTo(.25, 4);
  await expect.poll(async () => Number(await mount.getAttribute("data-homepage-runtime-attention-y"))).toBeCloseTo(.25, 4);
  await expect(mount).toHaveAttribute("data-homepage-runtime-hover-capability", "hover");
  await expect(mount).toHaveAttribute("data-homepage-runtime-pointer-precision", "fine");
  for (const [width, height, reducedMotion, composition, motion] of [
    [1280, 900, "no-preference", "wide", "full"], [1280, 900, "reduce", "wide", "reduced"],
    [1280, 900, "no-preference", "wide", "full"], [390, 844, "no-preference", "compact", "full"],
    [390, 844, "reduce", "compact", "reduced"], [390, 844, "no-preference", "compact", "full"]
  ] as const) {
    await page.setViewportSize({ width, height });
    await page.emulateMedia({ reducedMotion });
    expect(await page.evaluate(() => [window.innerWidth, window.innerHeight])).toEqual([width, height]);
    await expect(mount).toHaveAttribute("data-homepage-runtime-composition-profile", composition);
    await expect(mount).toHaveAttribute("data-homepage-runtime-motion-profile", motion);
    await expect(mount).toHaveAttribute("data-homepage-runtime-active-region", "architecture");
    await expect(mount).toHaveAttribute("data-homepage-runtime-ariadne-points", "10");
    await expect(mount).toHaveAttribute("data-homepage-runtime-hover-capability", "hover");
    await expect(mount).toHaveAttribute("data-homepage-runtime-pointer-precision", "fine");
    expect(await narrativeDiagnostics(page)).toEqual(truth);
    console.log(`Fixed profile ${width}x${height}/${motion}: ${JSON.stringify(await runtimeDiagnostics(page))}`);
  }
  for (const [width, reducedMotion, composition, motion] of [
    [672, "no-preference", "compact", "full"], [672, "reduce", "compact", "reduced"],
    [673, "reduce", "wide", "reduced"], [673, "no-preference", "wide", "full"]
  ] as const) {
    await page.setViewportSize({ width, height: 800 });
    await page.emulateMedia({ reducedMotion });
    expect(await page.evaluate(() => window.innerWidth)).toBe(width);
    await expect(mount).toHaveAttribute("data-homepage-runtime-viewport-width", String(width));
    await expect(mount).toHaveAttribute("data-homepage-runtime-viewport-height", "800");
    await expect(mount).toHaveAttribute("data-homepage-runtime-orientation", "portrait");
    await expect(mount).toHaveAttribute("data-homepage-runtime-composition-profile", composition);
    await expect(mount).toHaveAttribute("data-homepage-runtime-motion-profile", motion);
    expect(await mount.evaluate(node => ["progress", "active-region", "navigator", "ariadne-points"]
      .map(key => node.getAttribute(`data-homepage-runtime-${key}`)))).toEqual(truth);
    await expect(mount.locator("canvas")).toHaveCount(1);
  }
  await page.mouse.move(200, 200);
  await expect.poll(async () => Number(await mount.getAttribute("data-homepage-runtime-attention-x"))).toBeCloseTo(200 / 673, 4);
  await page.evaluate(() => window.dispatchEvent(new Event("blur")));
  await expect(mount).toHaveAttribute("data-homepage-runtime-attention-active", "false");
  await expect(mount).toHaveAttribute("data-homepage-runtime-attention-modality", "none");
  expect(await mount.getAttribute("data-homepage-runtime-attention-x")).toBeNull();
  expect(await mount.getAttribute("data-homepage-runtime-attention-y")).toBeNull();
  await expect(page.locator("[data-archive-chapter]")).toHaveCount(7);
  expect(errors).toEqual([]);
});

test("prototype native scroll stays authoritative through attention and live motion changes", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await injectPrototype(page);
  await visit(page);
  const mount = page.locator("[data-homepage-runtime]");
  await expect(mount).toHaveAttribute("data-homepage-runtime-ready", "true");
  await scrollNative(page, .58);
  const before = await mount.evaluate(node => ["progress", "active-region", "navigator", "ariadne-points"]
    .map(key => node.getAttribute(`data-homepage-runtime-${key}`)));
  await page.mouse.move(100, 100);
  await expect(mount).toHaveAttribute("data-homepage-runtime-attention-active", "true");
  expect(await mount.evaluate(node => ["progress", "active-region", "navigator", "ariadne-points"]
    .map(key => node.getAttribute(`data-homepage-runtime-${key}`)))).toEqual(before);
  for (const [reducedMotion, motion] of [["reduce", "reduced"], ["no-preference", "full"]] as const) {
    await page.emulateMedia({ reducedMotion });
    await expect(mount).toHaveAttribute("data-homepage-runtime-motion-profile", motion);
    await assertNativeProgress(page);
    expect(Number(await mount.getAttribute("data-homepage-runtime-progress"))).toBeGreaterThan(0);
    // Archive fallback changes document geometry; native progress must follow that current geometry.
    console.log(`Native live ${motion}: ${JSON.stringify(await runtimeDiagnostics(page))}`);
  }
  await expect(mount.locator("canvas")).toHaveCount(1);
});

test("native prototype follows forward scroll, reverses Ariadne, and refreshes viewport geometry", async ({ page }, testInfo) => {
  const errors: string[] = [];
  page.on("pageerror", error => errors.push(error.message));
  page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
  await injectPrototype(page);
  await visit(page);
  const mount = page.locator("[data-homepage-runtime]");
  await expect(mount).toHaveAttribute("data-homepage-runtime-ready", "true");
  const originalCanvas = await mount.locator("canvas").elementHandle();
  await scrollNative(page, 0);
  await expect(mount).toHaveAttribute("data-homepage-runtime-active-region", "threshold");
  let prefix = Number(await mount.getAttribute("data-homepage-runtime-ariadne-points"));
  let navigator = await mount.getAttribute("data-homepage-runtime-navigator");
  for (const [progress, region] of [[.18, "work"], [.58, "architecture"], [.95, "final"]] as const) {
    await scrollNative(page, progress);
    await expect(mount).toHaveAttribute("data-homepage-runtime-active-region", region);
    const currentPrefix = Number(await mount.getAttribute("data-homepage-runtime-ariadne-points"));
    const currentNavigator = await mount.getAttribute("data-homepage-runtime-navigator");
    expect(currentPrefix).toBeGreaterThan(prefix);
    expect(currentNavigator).not.toBe(navigator);
    prefix = currentPrefix;
    navigator = currentNavigator;
    console.log(`Native journey ${progress}: ${JSON.stringify(await runtimeDiagnostics(page))}`);
  }
  await scrollNative(page, .3);
  await expect(mount).toHaveAttribute("data-homepage-runtime-active-region", "writing");
  expect(Number(await mount.getAttribute("data-homepage-runtime-ariadne-points"))).toBeLessThan(prefix);
  const earlierPosition = await mount.getAttribute("data-homepage-runtime-navigator");
  await scrollNative(page, .8);
  await expect(mount).toHaveAttribute("data-homepage-runtime-active-region", "person");
  const laterPrefix = Number(await mount.getAttribute("data-homepage-runtime-ariadne-points"));
  const laterPosition = await mount.getAttribute("data-homepage-runtime-navigator");
  expect(laterPosition).not.toBe(earlierPosition);
  await page.screenshot({ path: testInfo.outputPath("native-later.png") });
  await scrollNative(page, .3);
  await expect(mount).toHaveAttribute("data-homepage-runtime-active-region", "writing");
  expect(Number(await mount.getAttribute("data-homepage-runtime-ariadne-points"))).toBeLessThan(laterPrefix);
  const restoredPosition = JSON.parse((await mount.getAttribute("data-homepage-runtime-navigator"))!);
  const initialPosition = JSON.parse(earlierPosition!);
  expect(restoredPosition.x).toBeCloseTo(initialPosition.x, 2);
  expect(restoredPosition.y).toBeCloseTo(initialPosition.y, 2);
  await page.screenshot({ path: testInfo.outputPath("native-backscroll.png") });
  await page.setViewportSize({ width: 1280, height: 900 });
  await expect(mount).toHaveAttribute("data-homepage-runtime-composition-profile", "wide");
  await assertNativeProgress(page);
  await page.setViewportSize({ width: 500, height: 600 });
  await assertNativeProgress(page);
  await expect(mount.locator("canvas")).toHaveAttribute("width", "484");
  await expect(mount.locator("canvas")).toHaveAttribute("height", "271");
  await expect(mount).toHaveAttribute("data-homepage-runtime-composition-profile", "compact");
  expect(await page.evaluate(canvas => canvas === document.querySelector("[data-homepage-runtime] canvas"), originalCanvas)).toBe(true);
  await page.setViewportSize({ width: 1280, height: 900 });
  await expect(mount).toHaveAttribute("data-homepage-runtime-composition-profile", "wide");
  await assertNativeProgress(page);
  await expect(mount.locator("canvas")).toHaveAttribute("width", "640");
  expect(await page.evaluate(canvas => canvas === document.querySelector("[data-homepage-runtime] canvas"), originalCanvas)).toBe(true);
  expect(Number(await mount.getAttribute("data-homepage-runtime-progress"))).toBeGreaterThan(0);
  await expect(mount.locator("canvas")).toHaveCount(1);
  await expect(page.locator("[data-archive-chapter]")).toHaveCount(7);
  expect(errors).toEqual([]);
});

test("native prototype first renders the current mid-document state", async ({ page }) => {
  await injectPrototype(page);
  let release: (() => void) | undefined;
  const gate = new Promise<void>(resolve => { release = resolve; });
  await page.route(/\/assets\/home-[^/]+\.js$/, async route => {
    await gate;
    await route.continue();
  });
  try {
    await page.goto("/", { waitUntil: "commit" });
    const mount = page.locator("[data-homepage-runtime]");
    await expect(mount).toHaveCount(1);
    await page.evaluate(() => document.fonts.ready);
    await page.evaluate(() => {
      const state = window as Window & { initialPrototypeProgress?: { rendered: number; current: number } };
      const mount = document.querySelector("[data-homepage-runtime]")!;
      const observer = new MutationObserver(() => {
        const progress = mount.getAttribute("data-homepage-runtime-progress");
        if (progress !== null) {
          const range = Math.max(document.documentElement.scrollHeight - window.innerHeight, 0);
          state.initialPrototypeProgress = {
            rendered: Number(progress),
            current: range > 0 ? Math.min(1, Math.max(0, window.scrollY / range)) : 0
          };
          observer.disconnect();
        }
      });
      observer.observe(mount, { attributes: true, attributeFilter: ["data-homepage-runtime-progress"] });
      window.scrollTo(0, (document.documentElement.scrollHeight - window.innerHeight) * .6);
    });
    await expect.poll(() => currentNativeProgress(page)).toBeCloseTo(.6, 2);
    release!();
    await expect(mount).toHaveAttribute("data-homepage-runtime-ready", "true");
    const initial = await page.evaluate(() => (window as Window & {
      initialPrototypeProgress?: { rendered: number; current: number }
    }).initialPrototypeProgress);
    expect(initial).toBeDefined();
    expect(initial!.rendered).toBeGreaterThan(.2);
    expect(initial!.rendered).toBeCloseTo(initial!.current, 3);
    await assertNativeProgress(page);
    await expect(mount.locator("canvas")).toHaveCount(1);
  } finally { release?.(); }
});

test("native prototype retains its canvas and current state through real BFCache navigation", async ({}, testInfo) => {
  test.skip(testInfo.project.name !== "desktop-1440");
  // Playwright normally disables BFCache. Enable it only for this lifecycle qualification.
  const browser = await chromium.launch({ channel: "chromium", ignoreDefaultArgs: ["--disable-back-forward-cache"] });
  const context = await browser.newContext({ baseURL: testInfo.project.use.baseURL,
    viewport: { width: 1280, height: 900 }, reducedMotion: "no-preference" });
  const page = await context.newPage();
  const errors: string[] = [];
  page.on("pageerror", error => errors.push(error.message));
  try {
    page.on("console", message => {
      if (message.text().startsWith("Prototype lifecycle:")) console.log(message.text());
    });
    await page.addInitScript(() => {
      const state = window as Window & { persistedPrototypeShows?: number; prototypeCanvas?: HTMLCanvasElement };
      state.persistedPrototypeShows = 0;
      window.addEventListener("pagehide", event => console.log(`Prototype lifecycle: pagehide persisted=${event.persisted}`));
      window.addEventListener("pageshow", event => {
        if (event.persisted) state.persistedPrototypeShows!++;
        console.log(`Prototype lifecycle: pageshow persisted=${event.persisted}`);
      });
    });
    await injectPrototype(page);
    await visit(page);
    await expect(page.locator("[data-homepage-runtime]")).toHaveAttribute("data-homepage-runtime-ready", "true");
    await scrollNative(page, .6);
    const mount = page.locator("[data-homepage-runtime]");
    await expect(mount).toHaveAttribute("data-homepage-runtime-composition-profile", "wide");
    await expect(mount).toHaveAttribute("data-homepage-runtime-motion-profile", "full");
    await page.mouse.move(100, 100);
    await expect(mount).toHaveAttribute("data-homepage-runtime-attention-active", "true");
    await page.evaluate(() => {
      (window as Window & { prototypeCanvas?: HTMLCanvasElement }).prototypeCanvas =
        document.querySelector<HTMLCanvasElement>("[data-homepage-runtime] canvas")!;
    });
    await page.goto("/contact", { waitUntil: "networkidle" });
    await page.emulateMedia({ reducedMotion: "reduce" });
    console.log("Prototype lifecycle: returning from /contact");
    await page.goBack({ waitUntil: "commit" });
    console.log("Prototype lifecycle: history return committed");
    const persisted = await page.evaluate(() => (window as Window & { persistedPrototypeShows?: number }).persistedPrototypeShows);
    if (persisted !== 1) {
      const limitation = await page.evaluate(() => {
        const navigation = performance.getEntriesByType("navigation")[0] as PerformanceNavigationTiming & { notRestoredReasons?: unknown };
        return JSON.stringify(navigation?.notRestoredReasons ?? "Chromium did not expose notRestoredReasons");
      });
      console.log(`BFCache GAP: persisted pageshow was ${persisted}; notRestoredReasons: ${limitation}`);
      testInfo.annotations.push({ type: "GAP", description: `Real BFCache unavailable: ${limitation}` });
      expect(persisted, `Real BFCache unavailable: ${limitation}`).toBe(1);
    }
    expect(await page.evaluate(() => (window as Window & { prototypeCanvas?: HTMLCanvasElement }).prototypeCanvas ===
      document.querySelector("[data-homepage-runtime] canvas"))).toBe(true);
    await expect(page.locator("[data-homepage-runtime] canvas")).toHaveCount(1);
    await expect(page.locator("[data-homepage-runtime]")).toHaveCount(1);
    await assertNativeProgress(page);
    await expect(mount).toHaveAttribute("data-homepage-runtime-state", "running");
    await expect(mount).toHaveAttribute("data-homepage-runtime-composition-profile", "wide");
    await expect(mount).toHaveAttribute("data-homepage-runtime-motion-profile", "reduced");
    await expect(mount).toHaveAttribute("data-homepage-runtime-viewport-width", "1280");
    await expect(mount).toHaveAttribute("data-homepage-runtime-viewport-height", "900");
    await expect(mount).toHaveAttribute("data-homepage-runtime-hover-capability", "hover");
    await expect(mount).toHaveAttribute("data-homepage-runtime-pointer-precision", "fine");
    await expect(mount).toHaveAttribute("data-homepage-runtime-attention-active", "false");
    await expect(mount).toHaveAttribute("data-homepage-runtime-attention-modality", "none");
    expect(await mount.getAttribute("data-homepage-runtime-attention-x")).toBeNull();
    expect(await mount.getAttribute("data-homepage-runtime-attention-y")).toBeNull();
    await expect(mount.locator("canvas")).toHaveAttribute("aria-hidden", "true");
    expect(await mount.locator("canvas").evaluate(node => getComputedStyle(node).pointerEvents)).toBe("none");
    expect(await page.evaluate(() => window.matchMedia("(prefers-reduced-motion: reduce)").matches)).toBe(true);
    console.log(`BFCache restored: ${JSON.stringify(await runtimeDiagnostics(page))}`);
    await scrollNative(page, .3);
    await expect(page.locator("[data-homepage-runtime]")).toHaveAttribute("data-homepage-runtime-active-region", "writing");
    expect(errors).toEqual([]);
  } finally { await context.close(); await browser.close(); }
});

async function narrativeDiagnostics(page: Page): Promise<(string | null)[]> {
  return page.locator("[data-homepage-runtime]").evaluate(node =>
    ["progress", "active-region", "navigator", "ariadne-points"].map(key => node.getAttribute(`data-homepage-runtime-${key}`)));
}

const runtimeDiagnosticKeys = ["progress", "active-region", "navigator", "ariadne-points", "state",
  "composition-profile", "motion-profile", "viewport-width", "viewport-height", "hover-capability", "pointer-precision",
  "attention-active", "attention-modality", "attention-x", "attention-y"];

async function runtimeDiagnostics(page: Page): Promise<Record<string, string | null>> {
  return page.locator("[data-homepage-runtime]").evaluate((node, keys) => Object.fromEntries(
    keys.map(key => [key, node.getAttribute(`data-homepage-runtime-${key}`)])), runtimeDiagnosticKeys);
}

async function nativeBrowserSnapshot(page: Page) {
  return page.locator("[data-homepage-runtime]").evaluate((node, keys) => {
    const range = Math.max(document.documentElement.scrollHeight - window.innerHeight, 0);
    return { scrollY: window.scrollY, nativeProgress: range > 0 ? Math.min(1, Math.max(0, window.scrollY / range)) : 0,
      diagnostics: Object.fromEntries(keys.map(key => [key, node.getAttribute(`data-homepage-runtime-${key}`)])) };
  }, runtimeDiagnosticKeys);
}

async function assertSemanticHomepage(page: Page): Promise<void> {
  await assertStructure(page, "EN");
  await expect(page.locator("h1")).toHaveCount(1);
  await expect(page.locator("h1")).toHaveText("Building systems worth explaining.");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Building systems worth explaining.");
  for (const selector of [".home-project h3 a", ".home-writing__list h3 a", ".home-research__list h3 a"]) {
    await expect(page.locator(selector)).toHaveCount(3);
    const link = page.locator(selector).first();
    expect(await link.innerText()).not.toBe("");
    expect(await link.getAttribute("href")).toMatch(/^\/en\/(projects|articles|notes|research)\/[a-z0-9-]+$/);
  }
  for (const target of ["#work", "#writing", "#research", "#about"]) {
    await expect(page.locator(`.home-nav a[href='${target}']`).first()).toHaveAttribute("href", target);
  }
  await expect(page.locator("#about")).toContainText("Fixture Engineer");
  await expect(page.locator(".home-contact__link")).toHaveAttribute("href", "/contact");
  // The current homepage has no CV anchor; the server owns a separate real CV route.
  expect((await page.request.get("/cv")).status()).toBe(200);
}

async function auditHomepageAccessibility(page: Page): Promise<void> {
  await assertSemanticHomepage(page);
  await expect(page.getByRole("main")).toHaveCount(1);
  await expect(page.getByRole("link", { name: "Skip to main content" })).toHaveAttribute("href", "#main-content");
  const audit = await page.evaluate(() => ({
    unnamedLinks: Array.from(document.querySelectorAll("a")).filter(link =>
      !link.textContent?.trim() && !link.getAttribute("aria-label") && !link.querySelector("img[alt]")).length,
    missingAlt: document.querySelectorAll("img:not([alt])").length,
    positiveTabindex: Array.from(document.querySelectorAll("[tabindex]"))
      .filter(node => Number(node.getAttribute("tabindex")) > 0).length,
    invalidChapterLabels: Array.from(document.querySelectorAll("[data-archive-chapter]")).filter(section =>
      !document.getElementById(section.getAttribute("aria-labelledby") ?? "")?.textContent?.trim()).length,
    background: getComputedStyle(document.body).backgroundColor,
    text: getComputedStyle(document.querySelector("h1")!).color,
    intro: getComputedStyle(document.querySelector(".home-identity__intro")!).color
  }));
  expect(audit.unnamedLinks).toBe(0);
  expect(audit.missingAlt).toBe(0);
  expect(audit.positiveTabindex).toBe(0);
  expect(audit.invalidChapterLabels).toBe(0);
  expect(contrast(audit.text, audit.background)).toBeGreaterThanOrEqual(4.5);
  expect(contrast(audit.intro, audit.background)).toBeGreaterThanOrEqual(4.5);
  console.log(`Homepage accessibility ${await page.evaluate(() => window.innerWidth)}px: ${JSON.stringify(audit)}`);
}

test("decorative prototype preserves semantic accessibility, visible keyboard focus and DOM link activation", async ({ page }, testInfo) => {
  if (testInfo.project.name === "mobile-390") await page.setViewportSize({ width: 390, height: 844 });
  await visit(page);
  await expect(page.locator("[data-homepage-runtime]")).toHaveCount(0);
  await expect(page.locator("html")).toHaveClass(/archive-enhanced/);
  await auditHomepageAccessibility(page);
  await injectPrototype(page, "0.580");
  await visit(page);
  const mount = page.locator("[data-homepage-runtime]");
  const canvas = mount.locator("canvas");
  await expect(mount).toHaveAttribute("data-homepage-runtime-ready", "true");
  await expect(canvas).toHaveCount(1);
  await expect(canvas).toHaveAttribute("aria-hidden", "true");
  expect(await canvas.getAttribute("tabindex")).toBeNull();
  expect(await canvas.evaluate(node => (node as HTMLCanvasElement).tabIndex)).toBeLessThan(0);
  for (const attribute of ["role", "aria-label", "aria-labelledby", "title"]) expect(await canvas.getAttribute(attribute)).toBeNull();
  expect(await canvas.evaluate(node => getComputedStyle(node).pointerEvents)).toBe("none");
  await expect(mount.locator("a, button, h1, h2, nav, main, [role]")).toHaveCount(0);
  await auditHomepageAccessibility(page);
  // Visit the complete source-order tab sequence; the renderer must never receive focus.
  const seen = new Set<string>();
  let visibleFocus: unknown;
  let completedTabSequence = false;
  for (let index = 0; index < 50; index++) {
    await page.keyboard.press("Tab");
    const focused = await page.evaluate(() => {
      const node = document.activeElement as HTMLElement;
      const style = getComputedStyle(node);
      return { tag: node.tagName, className: node.className, href: node.getAttribute("href"),
        focusVisible: node.matches(":focus-visible"), outlineStyle: style.outlineStyle,
        outlineWidth: style.outlineWidth, outlineColor: style.outlineColor, outlineOffset: style.outlineOffset };
    });
    expect(focused.tag).not.toBe("CANVAS");
    if (focused.className === "skip-link" && seen.size > 0) { completedTabSequence = true; break; }
    if (focused.tag === "A") seen.add(focused.href!);
    if (focused.className === "home-button") {
      await expect(page.locator(".home-actions .home-button")).toBeFocused();
      expect(focused.focusVisible).toBe(true);
      expect(focused.outlineStyle).toBe("solid");
      expect(parseFloat(focused.outlineWidth)).toBeGreaterThanOrEqual(2);
      expect(focused.outlineColor).not.toBe("rgba(0, 0, 0, 0)");
      visibleFocus = focused;
      await page.screenshot({ path: testInfo.outputPath("prototype-visible-focus.png") });
    }
  }
  expect(completedTabSequence).toBe(true);
  expect(visibleFocus).toBeDefined();
  expect(seen.has("/contact")).toBe(true);
  console.log(`Keyboard DOM focus: ${JSON.stringify(visibleFocus)}`);
  await page.screenshot({ path: testInfo.outputPath("prototype-semantic-page.png"), fullPage: true });
  await page.emulateMedia({ reducedMotion: "reduce" });
  await expect(mount).toHaveAttribute("data-homepage-runtime-motion-profile", "reduced");
  await auditHomepageAccessibility(page);
  // Put the decorative canvas over a real target and prove browser hit-testing reaches the anchor.
  const link = page.locator(".home-contact__link");
  await link.scrollIntoViewIfNeeded();
  // Move only the renderer surface: the test-owned mount must not become a covering hit target.
  await canvas.evaluate((node, box) => {
    Object.assign((node as HTMLCanvasElement).style,
      { position: "fixed", left: `${box!.x}px`, top: `${box!.y}px` });
  }, await link.boundingBox());
  const box = (await link.boundingBox())!;
  expect(await canvas.evaluate((node, target) => {
    const rect = node.getBoundingClientRect();
    return target.x + target.width / 2 >= rect.left && target.x + target.width / 2 <= rect.right &&
      target.y + target.height / 2 >= rect.top && target.y + target.height / 2 <= rect.bottom;
  }, box), "renderer canvas covers the DOM activation point").toBe(true);
  expect(await link.evaluate(node => {
    const rect = node.getBoundingClientRect();
    return node.contains(document.elementFromPoint(rect.x + rect.width / 2, rect.y + rect.height / 2));
  }), "native hit-testing reaches the DOM anchor through the renderer").toBe(true);
  await link.click();
  await expect(page).toHaveURL(/\/contact$/);
  await expect(page.getByRole("heading", { name: "Contact", exact: true })).toBeVisible();
});

test("native Chromium touch coexists with attention and browser-owned scrolling", async ({ browser }, testInfo) => {
  test.skip(testInfo.project.name !== "mobile-390");
  const context = await browser.newContext({ baseURL: testInfo.project.use.baseURL,
    viewport: { width: 390, height: 844 }, hasTouch: true, reducedMotion: "no-preference" });
  const page = await context.newPage();
  try {
    await injectPrototype(page);
    await visit(page);
    const mount = page.locator("[data-homepage-runtime]");
    await expect(mount).toHaveAttribute("data-homepage-runtime-ready", "true");
    await expect(mount).toHaveAttribute("data-homepage-runtime-pointer-precision", "coarse");
    await scrollNative(page, .12);
    const initial = await assertNativeProgress(page);
    await page.evaluate(() => {
      const state = window as Window & { nativeTouchEvents?: { type: string; trusted: boolean; x: number; y: number }[] };
      state.nativeTouchEvents = [];
      for (const type of ["pointerdown", "pointermove", "pointercancel", "pointerup"]) {
        document.addEventListener(type, event => {
          const pointer = event as PointerEvent;
          state.nativeTouchEvents!.push({ type, trusted: event.isTrusted, x: pointer.clientX, y: pointer.clientY });
        }, { passive: true });
      }
    });
    const cdp = await context.newCDPSession(page);
    const touch = (y: number) => [{ x: 195, y, id: 1, radiusX: 1, radiusY: 1, force: 1 }];
    await cdp.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: touch(680) });
    await expect(mount).toHaveAttribute("data-homepage-runtime-attention-active", "true");
    await expect(mount).toHaveAttribute("data-homepage-runtime-attention-modality", "touch");
    await expect.poll(async () => Number(await mount.getAttribute("data-homepage-runtime-attention-x"))).toBeCloseTo(.5, 4);
    await expect.poll(async () => Number(await mount.getAttribute("data-homepage-runtime-attention-y"))).toBeCloseTo(680 / 844, 4);
    expect(await narrativeDiagnostics(page)).toEqual([initial.diagnostics.progress, initial.diagnostics["active-region"],
      initial.diagnostics.navigator, initial.diagnostics["ariadne-points"]]);
    await cdp.send("Input.dispatchTouchEvent", { type: "touchMove", touchPoints: touch(675) });
    await expect.poll(async () => Number(await mount.getAttribute("data-homepage-runtime-attention-y"))).toBeCloseTo(675 / 844, 4);
    const contact = await runtimeDiagnostics(page);
    for (const y of [650, 600, 520, 420, 320, 220]) {
      await cdp.send("Input.dispatchTouchEvent", { type: "touchMove", touchPoints: touch(y) });
    }
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(initial.scrollY + 100);
    await assertNativeProgress(page);
    await cdp.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
    await expect(mount).toHaveAttribute("data-homepage-runtime-attention-active", "false");
    await expect(mount).toHaveAttribute("data-homepage-runtime-attention-modality", "none");
    const result = await assertNativeProgress(page);
    expect(Number(result.diagnostics.progress)).toBeGreaterThan(Number(initial.diagnostics.progress));
    expect(result.diagnostics["active-region"]).not.toBe(initial.diagnostics["active-region"]);
    expect(result.diagnostics.navigator).not.toBe(initial.diagnostics.navigator);
    expect(Number(result.diagnostics["ariadne-points"])).toBeGreaterThan(Number(initial.diagnostics["ariadne-points"]));
    const events = await page.evaluate(() => (window as Window & { nativeTouchEvents?: unknown[] }).nativeTouchEvents);
    expect(events).toEqual(expect.arrayContaining([expect.objectContaining({ type: "pointerdown", trusted: true })]));
    await expect(mount.locator("canvas")).toHaveCount(1);
    await expect(page.locator("[data-homepage-runtime]")).toHaveCount(1);
    console.log(`Native CDP touch: ${JSON.stringify({ initial, contact, result, events })}`);
    await cdp.detach();
  } finally { await context.close(); }
});

test("unavailable WebGL removes the candidate safely and preserves real DOM navigation", async ({ browser }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop-1440");
  const context = await browser.newContext({ baseURL: testInfo.project.use.baseURL, viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  const errors: string[] = [];
  const consoleErrors: string[] = [];
  page.on("pageerror", error => errors.push(error.message));
  page.on("console", message => { if (message.type() === "error") consoleErrors.push(message.text()); });
  try {
    await page.addInitScript(() => {
      const getContext = HTMLCanvasElement.prototype.getContext;
      HTMLCanvasElement.prototype.getContext = function(type: string, options?: unknown) {
        if (["webgl", "webgl2", "experimental-webgl"].includes(type)) return null;
        return getContext.call(this, type, options);
      } as typeof getContext;
    });
    await injectPrototype(page);
    await visit(page);
    await expect.poll(() => consoleErrors.filter(message => message.startsWith("Homepage candidate runtime could not initialize")).length).toBe(1);
    await expect(page.locator("[data-homepage-runtime] canvas")).toHaveCount(0);
    expect(await page.locator("[data-homepage-runtime]").evaluate(node => node.getAttributeNames()
      .filter(name => name.startsWith("data-homepage-runtime-")))).toEqual([]);
    await expect(page.locator("html")).toHaveClass(/archive-enhanced/);
    await assertSemanticHomepage(page);
    expect(errors).toEqual([]);
    expect(consoleErrors.filter(message => !message.startsWith("Homepage candidate runtime could not initialize"))).toEqual([]);
    console.log(`Handled unavailable WebGL: ${JSON.stringify(consoleErrors)}`);
    await page.locator(".home-contact__link").click();
    await expect(page).toHaveURL(/\/contact$/);
    await expect(page.getByRole("heading", { name: "Contact", exact: true })).toBeVisible();
  } finally { await context.close(); }
});

test("compact JavaScript-disabled homepage retains initial SSR content and real navigation", async ({ browser }, testInfo) => {
  test.skip(testInfo.project.name !== "mobile-390");
  const context = await browser.newContext({ baseURL: testInfo.project.use.baseURL,
    viewport: { width: 390, height: 844 }, javaScriptEnabled: false });
  const page = await context.newPage();
  try {
    const response = await page.goto("/", { waitUntil: "networkidle" });
    expect(response?.status()).toBe(200);
    const html = await response!.text();
    expect(html).toContain('<h1 id="identity-title">Building systems');
    expect(html).toContain('href="/contact"');
    expect(html).toContain('href="/en/projects/fixture-project-1"');
    expect(html).not.toContain("data-homepage-runtime");
    await assertSemanticHomepage(page);
    await expect(page.locator("canvas, [data-homepage-runtime]")).toHaveCount(0);
    await expect(page.locator("html")).not.toHaveClass(/archive-enhanced/);
    await expect(page.locator(".archive-snapshot--identity")).toBeVisible();
    await page.locator(".home-project h3 a").first().click();
    await expect(page).toHaveURL(/\/en\/projects\/fixture-project-1$/);
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("A publishing system for resilient editorial workflows");
  } finally { await context.close(); }
});
