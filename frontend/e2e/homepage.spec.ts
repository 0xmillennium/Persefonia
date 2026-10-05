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
  await expect(page.locator("[data-home-section]")).toHaveCount(7);
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
  await expect(page.locator("meta[name='color-scheme']")).toHaveAttribute("content", "light");
  expect(await page.locator("html").evaluate(node => getComputedStyle(node).colorScheme)).toBe("light");
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

test("populated SSR content, public routes, metadata and clean asset loading", async ({ page }, testInfo) => {
  const errors: string[] = [];
  const badResponses: string[] = [];
  const requests: string[] = [];
  await page.addInitScript(() => {
    const state = window as Window & { homeCspViolations: string[] };
    state.homeCspViolations = [];
    document.addEventListener("securitypolicyviolation", event => state.homeCspViolations.push(`${event.violatedDirective}: ${event.blockedURI}`));
  });
  const headers = await page.request.get("/");
  expect(headers.headers()["content-security-policy"]).toContain("script-src 'self'; style-src 'self'");
  expect(headers.headers()["content-security-policy"]).not.toMatch(/unsafe-inline|unsafe-eval/);
  page.on("pageerror", error => errors.push(error.message));
  page.on("response", response => { if (response.status() >= 400) badResponses.push(`${response.status()} ${response.url()}`); });
  page.on("request", request => requests.push(request.url()));
  await visit(page);
  await assertStructure(page, "EN");
  await expect(page.locator("h1")).toHaveText("0xmillennium");
  await expect(page.locator(".home-projects__item")).toHaveCount(3);
  await expect(page.locator(".home-writing__list li")).toHaveCount(3);
  await expect(page.locator(".home-research__list li")).toHaveCount(3);
  await expect(page.locator("#about")).toContainText("Fixture Engineer");
  await expect(page.locator(".home-layer")).toHaveCount(4);
  await expect(page.locator(".home-writing .home-section__more")).toHaveAttribute("href", "/search");
  await expect(page.locator(".home-research .home-section__more")).toHaveAttribute("href", "/search");
  await expect(page.locator("link[rel='canonical']")).toHaveAttribute("href", `${process.env.PERSEFONIA_BROWSER_URL}/`);
  await expect(page.locator("meta[property='og:url']")).toHaveAttribute("content", `${process.env.PERSEFONIA_BROWSER_URL}/`);
  expect(await page.evaluate(() => ["Archivo", "IBM Plex Mono"].every(font => document.fonts.check(`16px "${font}"`)))).toBe(true);
  expect(requests.filter(url => /\.mp4(?:$|\?)/.test(url))).toEqual([]);
  expect(requests.every(url => new URL(url).origin === new URL(process.env.PERSEFONIA_BROWSER_URL!).origin)).toBe(true);
  expect(requests.some(url => /pixi|mermaid|instrument|inter-/.test(url))).toBe(false);
  for (const selector of [".home-project h3 a", ".home-writing__list h3 a", ".home-research__list h3 a", ".home-section__more", ".home-writing .home-section__more", ".home-research .home-section__more", ".home-contact__link"]) {
    const href = await page.locator(selector).first().getAttribute("href");
    expect((await page.request.get(href!)).status(), href!).toBe(200);
  }
  for (const section of ["identity", "work", "writing", "research", "architecture", "about", "contact"]) {
    await scrollToChapter(page, section);
    await page.screenshot({ path: testInfo.outputPath(`${section}.png`) });
  }
  await page.screenshot({ path: testInfo.outputPath("full-page.png"), fullPage: true });
  expect(errors).toEqual([]);
  expect(badResponses).toEqual([]);
  expect(await page.evaluate(() => (window as Window & { homeCspViolations: string[] }).homeCspViolations)).toEqual([]);
});

test("Turkish copy, profile and published content agree with document language", async ({ page }) => {
  setSettings("UPDATE portfolio.site_presentation_settings SET default_language='TR'");
  await visit(page);
  await assertStructure(page, "TR");
  await expect(page.locator("h1")).toHaveText("0xmillennium");
  await expect(page.locator(".home-identity__statement")).toContainText("Anlatmaya değer");
  await expect(page.locator(".home-projects__item")).toHaveCount(3);
  await expect(page.locator(".home-writing__list li")).toHaveCount(3);
  await expect(page.locator(".home-research__list li")).toHaveCount(3);
  await expect(page.locator("#about")).toContainText("İstanbul");
  await expect(page.getByRole("link", { name: /İletişime geç/ })).toHaveCount(1);
  await expect(page.locator(".home-project h3 a").first()).toHaveAttribute("href", /\/tr\/projects\//);
  await expect(page.locator(".home-motion")).toBeHidden();
  await page.locator("#identity").evaluate(node => {
    const rect = node.getBoundingClientRect();
    const height = node.querySelector(".home-scene__stage")!.getBoundingClientRect().height;
    window.scrollTo(0, rect.top + scrollY + (rect.height - height) * .4);
  });
  await expect(page.locator(".home-motion")).toBeVisible();
  await expect(page.locator(".home-motion")).toHaveText("Hareketi durdur");
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
    expect(await page.locator("body").evaluate(node => getComputedStyle(node).backgroundColor)).toBe("rgb(240, 238, 231)");
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

test("enabled empty and disabled promotion preserve distinct honest states", async ({ page }) => {
  setSettings("DELETE FROM discovery.discoverable_resources WHERE source_context='CONTENT_PUBLISHING'; DELETE FROM portfolio.project_localizations; DELETE FROM portfolio.projects");
  await visit(page);
  await assertStructure(page, "EN");
  await expect(page.locator(".home-projects__item")).toHaveCount(0);
  await expect(page.locator(".home-empty")).toHaveCount(3);
  setSettings("UPDATE portfolio.site_presentation_settings SET promote_featured_projects=false, promote_latest_writing=false, promote_research_highlights=false");
  await page.reload({ waitUntil: "networkidle" });
  await expect(page.locator("[data-home-section]")).toHaveCount(7);
  await expect(page.locator(".home-empty")).toHaveCount(0);
});

test("no JS, failed module and failed initialization retain semantic actions", async ({ browser }, testInfo) => {
  for (const failure of ["disabled", "load", "init", "observer"]) {
    const context = await browser.newContext({ viewport: testInfo.project.use.viewport, javaScriptEnabled: failure !== "disabled" });
    const page = await context.newPage();
    if (failure === "load") await page.route(/\/assets\/home-[^/]+\.js$/, route => route.abort());
    if (failure === "init") await page.addInitScript(() => { window.matchMedia = () => { throw new Error("simulated matchMedia failure"); }; });
    if (failure === "observer") await page.addInitScript(() => { window.ResizeObserver = class { constructor() { throw new Error("simulated observer failure"); } } as typeof ResizeObserver; });
    await visit(page);
    await assertStructure(page, "EN");
    await expect(page.locator(".home-motion")).toBeHidden();
    await expect(page.locator(".home-cinema__video")).toBeHidden();
    await expect(page.locator(".home-project h3 a")).toHaveCount(3);
    const layer = page.locator(".home-layer").first();
    await layer.locator("summary").click();
    await expect(layer.locator("p")).toBeVisible();
    await expect(layer).toContainText("Spring MVC");
    if (testInfo.project.name === "mobile-390") {
      await page.locator(".home-nav__mobile summary").click();
      await page.locator(".home-nav__mobile a[href='#research']").click();
      await expect(page).toHaveURL(/#research$/);
    }
    await page.locator(".home-contact__link").click();
    await expect(page).toHaveURL(/\/contact$/);
    await expect(page.getByLabel("Name")).toBeVisible();
    await context.close();
  }
});

test("keyboard, native disclosures, labels and text contrast", async ({ page }, testInfo) => {
  await visit(page);
  await page.keyboard.press("Tab");
  await expect(page.getByRole("link", { name: "Skip to main content" })).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page.locator("main")).toBeFocused();
  if (testInfo.project.name === "mobile-390") {
    const summary = page.locator(".home-nav__mobile summary");
    await summary.focus();
    await page.keyboard.press("Enter");
    await expect(page.locator(".home-nav__mobile")).toHaveAttribute("open", "");
    await page.keyboard.press("Escape");
    await expect(summary).toBeFocused();
    await expect(page.locator(".home-nav__mobile")).not.toHaveAttribute("open", "");
    await page.keyboard.press("Enter");
    await page.locator(".home-nav__mobile a[href='#architecture']").focus();
    await page.keyboard.press("Enter");
    await expect(page.locator("#architecture")).toBeFocused();
    await expect(page.locator(".home-nav__mobile")).not.toHaveAttribute("open", "");
  }
  const disclosure = page.locator(".home-layer summary").first();
  await disclosure.focus();
  await page.keyboard.press("Enter");
  await expect(page.locator(".home-layer").first().locator("p")).toBeVisible();
  expect(await disclosure.evaluate(node => getComputedStyle(node).outlineWidth)).toBe("3px");
  const audit = await page.evaluate(() => {
    const bg = getComputedStyle(document.body).backgroundColor;
    const research = getComputedStyle(document.querySelector(".home-research")!).backgroundColor;
    return {
      unnamed: [...document.querySelectorAll("a,button,summary")].filter(node => !node.textContent?.trim() && !node.getAttribute("aria-label")).length,
      invalidLabels: [...document.querySelectorAll("[data-home-section]")].filter(node => !document.getElementById(node.getAttribute("aria-labelledby") ?? "")?.textContent).length,
      positiveTab: [...document.querySelectorAll("[tabindex]")].filter(node => Number(node.getAttribute("tabindex")) > 0).length,
      colors: [
        [getComputedStyle(document.querySelector("h1")!).color, bg],
        [getComputedStyle(document.querySelector(".home-identity__intro")!).color, bg],
        [getComputedStyle(document.querySelector(".home-button")!).color, getComputedStyle(document.querySelector(".home-button")!).backgroundColor],
        [getComputedStyle(document.querySelector(".home-research__list p")!).color, research]
      ]
    };
  });
  expect(audit.unnamed).toBe(0); expect(audit.invalidLabels).toBe(0); expect(audit.positiveTab).toBe(0);
  for (const [foreground, background] of audit.colors) expect(contrast(foreground!, background!)).toBeGreaterThanOrEqual(4.5);
  expect(await page.locator("video").getAttribute("tabindex")).toBe("-1");
  await page.locator(".home-project h3 a").first().focus();
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/\/en\/projects\/fixture-project-1$/);
});

test("continuous resizing, short landscape and wide layouts remain usable", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop-1440");
  await visit(page);
  for (const [width, height] of [[320,640],[360,780],[390,850],[600,900],[699,850],[700,850],[701,850],[768,1024],[834,900],[932,430],[1050,800],[1051,800],[1280,800],[1440,900],[1920,1080],[2560,1080]]) {
    await page.setViewportSize({ width: width!, height: height! });
    await assertStructure(page, "EN");
    const heading = await page.locator("h1").boundingBox();
    expect(heading!.x).toBeGreaterThanOrEqual(0);
    expect(heading!.x + heading!.width).toBeLessThanOrEqual(width!);
    await page.screenshot({ path: testInfo.outputPath(`resize-${width}x${height}.png`) });
  }
});
