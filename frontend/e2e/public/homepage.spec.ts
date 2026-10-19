import { chooseCollection } from '../support/gallery';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { expect, test, type Page } from '@playwright/test';
const databaseContainer = process.env.PERSEFONIA_BROWSER_DB_CONTAINER;
const chapters = ['identity', 'work', 'writing', 'contact'];
let fixtureDirty = true;
function setSettings(sql: string): void {
  if (!databaseContainer) throw new Error('browserCheck must provide an isolated fixture database');
  fixtureDirty = true;
  execFileSync(
    'docker',
    [
      'exec',
      databaseContainer,
      'psql',
      '--set',
      'ON_ERROR_STOP=1',
      '-U',
      'persefonia_browser',
      '-d',
      'persefonia_browser',
      '-c',
      sql,
    ],
    { stdio: 'pipe' },
  );
}
async function visit(page: Page): Promise<void> {
  const response = await page.goto('/', { waitUntil: 'networkidle' });
  expect(response?.status()).toBe(200);
  await page.evaluate(() => document.fonts.ready);
}
function contrast(foreground: string, background: string): number {
  const luminance = (color: string): number => {
    const channels = color.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+)/);
    if (!channels) throw new Error(`Expected an opaque computed RGB color: ${color}`);
    const linear = channels.slice(1, 4).map((value) => {
      const channel = Number(value) / 255;
      return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
    });
    return linear[0]! * 0.2126 + linear[1]! * 0.7152 + linear[2]! * 0.0722;
  };
  const values = [luminance(foreground), luminance(background)].sort((a, b) => b - a);
  return (values[0]! + 0.05) / (values[1]! + 0.05);
}
test.describe.configure({ mode: 'serial' });
function resetFixture(): void {
  if (!databaseContainer) throw new Error('browserCheck must provide an isolated fixture database');
  execFileSync(
    'docker',
    [
      'exec',
      '-i',
      databaseContainer,
      'psql',
      '--set',
      'ON_ERROR_STOP=1',
      '-U',
      'persefonia_browser',
      '-d',
      'persefonia_browser',
    ],
    {
      input: readFileSync(new URL('../fixtures/content.sql', import.meta.url)),
      stdio: ['pipe', 'pipe', 'pipe'],
    },
  );
  fixtureDirty = false;
}

test.beforeEach(() => {
  if (fixtureDirty) resetFixture();
});
test.afterAll(() => {
  if (fixtureDirty) resetFixture();
});
async function assertStructure(page: Page, language: string) {
  await expect(page.locator('html')).toHaveAttribute('lang', language.toLowerCase());
  for (const id of chapters) await expect(page.locator(`#${id}`)).toHaveCount(1);
  await expect(page.locator('h1')).toHaveText('0xmillennium');
  await expect(page.locator('[data-scene]')).toHaveCount(7);
  await expect(page.locator("meta[name='color-scheme']")).toHaveAttribute('content', 'dark');
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
    await page.evaluate(() => innerWidth + 1),
  );
}
async function scene(page: Page, name: string, p = name === 'gallery' ? 0.55 : 0.3) {
  await page
    .locator(`[data-scene="${name}"]`)
    .evaluate(
      (node, p) =>
        scrollTo(
          0,
          node.getBoundingClientRect().top + scrollY + node.getBoundingClientRect().height * p,
        ),
      p,
    );
  await page.waitForTimeout(100);
}
test('SSR publications, metadata, public destinations, CSP and self-hosted assets', async ({
  page,
}, testInfo) => {
  const errors: string[] = [],
    bad: string[] = [],
    requests: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('response', (r) => {
    if (r.status() >= 400) bad.push(r.url());
  });
  page.on('request', (r) => requests.push(r.url()));
  await page.addInitScript(() => {
    (
      window as Window & {
        violations: string[];
      }
    ).violations = [];
    document.addEventListener('securitypolicyviolation', (e) =>
      (
        window as Window & {
          violations: string[];
        }
      ).violations.push(e.violatedDirective),
    );
  });
  const response = await page.request.get('/');
  expect(response.headers()['content-security-policy']).toContain(
    "script-src 'self'; style-src 'self'",
  );
  expect(response.headers()['content-security-policy']).not.toMatch(/unsafe-inline|unsafe-eval/);
  await visit(page);
  await assertStructure(page, 'EN');
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
  await expect(page.locator('.home-projects__item')).toHaveCount(3);
  await expect(page.locator('.home-writing__list>.gallery-card')).toHaveCount(6);
  await expect(page.locator('.home-support, #architecture, #about')).toHaveCount(0);
  await expect(
    page.locator(
      '.film-controls, [data-film-switch], [data-film-retry], .location-system .controls',
    ),
  ).toHaveCount(0);
  await expect(page.locator("link[rel='canonical']")).toHaveAttribute(
    'href',
    `${process.env.PERSEFONIA_BROWSER_URL}/`,
  );
  await expect(page.locator("meta[property='og:url']")).toHaveAttribute(
    'content',
    `${process.env.PERSEFONIA_BROWSER_URL}/`,
  );
  for (const selector of [
    '.home-project h3 a',
    '.home-writing__list h3 a',
    '.location-links nav a',
    '.contact-entry',
  ]) {
    const href = await page.locator(selector).first().getAttribute('href');
    expect((await page.request.get(href!)).status()).toBe(200);
  }
  expect(
    requests.every(
      (url) => new URL(url).origin === new URL(process.env.PERSEFONIA_BROWSER_URL!).origin,
    ),
  ).toBe(true);
  expect(requests.some((url) => /pixi|mermaid/.test(url))).toBe(false);
  const videos = requests.filter((url) => new URL(url).pathname.endsWith('.mp4'));
  expect(videos.length).toBeGreaterThan(0);
  expect(
    videos.every((url) =>
      /^\/assets\/(?:home-videos\/[a-f0-9]{64}\/(?:960|1600)\/(?:robotic-arm|tree)|home-loops\/[a-f0-9]{64}\/(?:1280|1920)\/waterfall)\.mp4$/.test(
        new URL(url).pathname,
      ),
    ),
  ).toBe(true);
  for (const name of ['hero', 'statements', 'capabilities', 'mark', 'gallery', 'location']) {
    await scene(page, name);
    await page.screenshot({ path: testInfo.outputPath(`${name}.png`) });
  }
  expect(errors).toEqual([]);
  expect(bad).toEqual([]);
  expect(
    await page.evaluate(
      () =>
        (
          window as Window & {
            violations: string[];
          }
        ).violations,
    ),
  ).toEqual([]);
});
test('gallery covers use eligible variants and revoke independently of the publication', async ({
  page,
}, testInfo) => {
  test.skip(
    testInfo.project.name === 'tablet-834' || testInfo.project.name === 'mobile-390',
    'Viewport-invariant media revocation SSR contract',
  );
  const url = '/media/assets/10000000-0000-0000-0000-0000000000c2/variants/medium';
  await visit(page);
  await expect(page.locator('.gallery-cover img')).toHaveCount(2);
  expect((await page.request.get(url)).status()).toBe(200);
  setSettings(
    "UPDATE media.assets SET visibility='PRIVATE' WHERE id='10000000-0000-0000-0000-0000000000c2'",
  );
  await page.reload({ waitUntil: 'networkidle' });
  await expect(page.locator('.gallery-cover img')).toHaveCount(0);
  expect((await page.request.get(url)).status()).toBe(404);
  setSettings(
    "UPDATE media.assets SET visibility='PUBLIC' WHERE id='10000000-0000-0000-0000-0000000000c2'; UPDATE media.asset_variants SET storage_path='variants/missing-cover.webp' WHERE asset_id='10000000-0000-0000-0000-0000000000c2'",
  );
  await page.reload({ waitUntil: 'networkidle' });
  await expect(page.locator('.gallery-cover img')).toHaveCount(0);
  await scene(page, 'gallery');
  await expect(page.locator('.home-project h3 a').first()).toBeVisible();
});
test('configured Turkish content and routes remain localized alongside locked identity copy', async ({
  page,
}, testInfo) => {
  test.skip(
    testInfo.project.name === 'tablet-834' || testInfo.project.name === 'mobile-390',
    'Viewport-invariant localization SSR contract',
  );
  setSettings("UPDATE portfolio.site_presentation_settings SET default_language='TR'");
  await visit(page);
  await assertStructure(page, 'TR');
  await expect(page.locator('.hero-statement')).toContainText('Clarity is');
  await expect(page.locator('.home-project h3 a').first()).toHaveAttribute(
    'href',
    /\/tr\/projects\//,
  );
  await expect(page.locator('.location-links')).toContainText('Istanbul');
  await expect(page.locator('.contact-entry')).toContainText('İletişime geç');
});
test('standard theme covers projects, content TOC, and CV in both system preferences', async ({
  page,
}, testInfo) => {
  test.skip(
    testInfo.project.name === 'tablet-834' || testInfo.project.name === 'mobile-390',
    'Viewport-invariant theme stylesheet and token contract',
  );
  test.setTimeout(120000);
  for (const [theme, preference, expectedBackground, expectedSurface] of [
    ['LIGHT', 'dark', 'rgb(247, 249, 248)', 'rgb(255, 255, 255)'],
    ['DARK', 'light', 'rgb(16, 22, 27)', 'rgb(27, 37, 43)'],
    ['SYSTEM', 'light', 'rgb(247, 249, 248)', 'rgb(255, 255, 255)'],
    ['SYSTEM', 'dark', 'rgb(16, 22, 27)', 'rgb(27, 37, 43)'],
  ] as const) {
    setSettings(`UPDATE portfolio.site_presentation_settings SET standard_public_theme='${theme}'`);
    await page.emulateMedia({ colorScheme: preference });
    const routes =
      theme === 'SYSTEM'
        ? (['/en/articles/fixture-writing-1'] as const)
        : (['/en/projects', '/en/articles/fixture-writing-1', '/cv', '/cv/en', '/cv/tr'] as const);
    for (const route of routes) {
      const response = await page.goto(route, { waitUntil: 'networkidle' });
      expect(response?.status(), route).toBe(200);
      const links = await page
        .locator("head link[rel='stylesheet']")
        .evaluateAll((nodes) => nodes.map((node) => node.getAttribute('href')));
      expect(links.at(-1), route).toBe('/theme.css');
      expect(
        links.some((href) => href?.startsWith('/assets/main-')),
        route,
      ).toBe(true);
      expect(
        await page.locator('body').evaluate((node) => getComputedStyle(node).backgroundColor),
        route,
      ).toBe(expectedBackground);
      expect(
        await page.locator('body').evaluate((node) => getComputedStyle(node).color),
        route,
      ).not.toBe(expectedBackground);
      if (route === '/cv/tr') await expect(page.locator('html')).toHaveAttribute('lang', 'tr');
      if (route.includes('articles')) {
        await expect(page.locator('.public-toc')).toBeVisible();
        const colors = await page.locator('.public-toc').evaluate((node) => ({
          background: getComputedStyle(node).backgroundColor,
          text: getComputedStyle(node).color,
          link: getComputedStyle(node.querySelector('a')!).color,
        }));
        expect(colors.background).toBe(expectedSurface);
        const textContrast = contrast(colors.text, colors.background);
        expect(textContrast).toBeGreaterThanOrEqual(4.5);
        expect(contrast(colors.link, colors.background)).toBeGreaterThanOrEqual(4.5);
        console.log(`${theme}/${preference} TOC text contrast ${textContrast.toFixed(2)}:1`);
        await page.locator('.public-toc a').focus();
        expect(
          await page
            .locator('.public-toc a')
            .evaluate((node) => getComputedStyle(node).outlineStyle),
        ).not.toBe('none');
        if (testInfo.project.name === 'desktop-1440' && theme !== 'SYSTEM')
          await page.screenshot({ path: testInfo.outputPath(`toc-${theme.toLowerCase()}.png`) });
      }
      if (route === '/cv/en' && testInfo.project.name === 'desktop-1440' && theme !== 'SYSTEM')
        await page.screenshot({ path: testInfo.outputPath(`cv-${theme.toLowerCase()}.png`) });
    }
    const stylesheet = await page.request.get('/theme.css');
    expect(stylesheet.status()).toBe(200);
    expect(stylesheet.headers()['content-type']).toContain('text/css');
    expect(stylesheet.headers()['cache-control']).toBe('public, no-cache, must-revalidate');
    await page.goto('/', { waitUntil: 'networkidle' });
    await expect(page.locator("link[href='/theme.css']")).toHaveCount(0);
    expect(
      await page.locator('body').evaluate((node) => getComputedStyle(node).backgroundColor),
    ).toBe('rgb(8, 14, 17)');
  }
});
test('standard public CSS remains coherently light when the theme resource fails', async ({
  browser,
}, testInfo) => {
  const context = await browser.newContext({
    viewport: testInfo.project.use.viewport as {
      width: number;
      height: number;
    },
    colorScheme: 'dark',
    javaScriptEnabled: false,
  });
  const page = await context.newPage();
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.route('**/theme.css', (route) => route.abort());
  const response = await page.goto('/contact', { waitUntil: 'networkidle' });
  expect(response?.status()).toBe(200);
  await expect(page.getByRole('heading', { name: 'Contact' })).toBeVisible();
  expect(await page.locator('html').evaluate((node) => getComputedStyle(node).colorScheme)).toBe(
    'light',
  );
  expect(
    await page.locator('body').evaluate((node) => getComputedStyle(node).backgroundColor),
  ).toBe('rgb(247, 249, 248)');
  expect(await page.locator('body').evaluate((node) => getComputedStyle(node).color)).toBe(
    'rgb(31, 41, 51)',
  );
  await expect(page.getByLabel('Name')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Send message' })).toBeVisible();
  await page.keyboard.press('Tab');
  await expect(page.getByRole('link', { name: 'Skip to main content' })).toBeFocused();
  await page.getByLabel('Name').focus();
  await page.keyboard.type('Ada');
  await expect(page.getByLabel('Name')).toHaveValue('Ada');
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
    testInfo.project.use.viewport!.width,
  );
  expect(errors).toEqual([]);
  await context.close();
});
test('enabled empty and disabled promotion retain honest content states', async ({ page }) => {
  setSettings(
    "DELETE FROM discovery.discoverable_resources WHERE source_context='CONTENT_PUBLISHING'; DELETE FROM portfolio.project_localizations; DELETE FROM portfolio.projects",
  );
  await visit(page);
  await assertStructure(page, 'EN');
  await expect(page.locator('.home-empty')).toHaveCount(4);
  await scene(page, 'gallery', 0.55);
  for (const category of ['projects', 'articles', 'notes', 'research']) {
    await chooseCollection(page, category);
    await expect(
      page.locator(`.gallery-category[data-category="${category}"] .home-empty`),
    ).toBeVisible();
  }
  setSettings(
    'UPDATE portfolio.site_presentation_settings SET promote_featured_projects=false,promote_latest_writing=false,promote_research_highlights=false',
  );
  await page.reload({ waitUntil: 'networkidle' });
  await expect(page.locator('.home-empty')).toHaveCount(0);
  await assertStructure(page, 'EN');
});
test('no JS and unavailable enhancement retain publications and contact', async ({
  browser,
}, testInfo) => {
  const failures =
    testInfo.project.name === 'tablet-834' || testInfo.project.name === 'mobile-390'
      ? (['disabled', 'load'] as const)
      : (['disabled', 'load', 'init', 'observer', 'observe'] as const);
  for (const failure of failures) {
    const context = await browser.newContext({
      viewport: testInfo.project.use.viewport,
      javaScriptEnabled: failure !== 'disabled',
    });
    const page = await context.newPage();
    if (failure === 'load') await page.route(/\/assets\/home-[^/]+\.js$/, (r) => r.abort());
    if (failure === 'init')
      await page.addInitScript(() => {
        window.matchMedia = () => {
          throw new Error('simulated matchMedia failure');
        };
      });
    if (failure === 'observer')
      await page.addInitScript(() => {
        window.ResizeObserver = class {
          constructor() {
            throw new Error('simulated observer failure');
          }
          observe() {}
          unobserve() {}
          disconnect() {}
        } as typeof ResizeObserver;
      });
    if (failure === 'observe')
      await page.addInitScript(() => {
        window.ResizeObserver = class {
          observe(target: Element) {
            if (target.hasAttribute('data-home-v2'))
              throw new Error('simulated late observer failure');
          }
          unobserve() {}
          disconnect() {}
        } as typeof ResizeObserver;
      });
    await visit(page);
    await assertStructure(page, 'EN');
    await expect(page.locator('[data-home-v2]')).not.toHaveAttribute('data-enhanced', 'true');
    await expect(page.locator('.gallery-instrument')).toBeHidden();
    await expect(page.locator('.gallery-category:not([hidden])')).toHaveCount(4);
    await expect(page.locator('.home-project h3 a')).toHaveCount(3);
    await expect(page.locator('.home-writing__list h3 a')).toHaveCount(6);
    await expect(
      page.locator('.home-support, .film-controls, .location-system .controls'),
    ).toHaveCount(0);
    await page.locator('.contact-entry').click();
    await expect(page).toHaveURL(/\/contact$/);
    await expect(page.getByLabel('Name')).toBeVisible();
    await context.close();
  }
});
test('keyboard navigation, contrast and category input', async ({ page }) => {
  await visit(page);
  await page.keyboard.press('Tab');
  await expect(page.getByRole('link', { name: 'Skip to main content' })).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.locator('main')).toBeFocused();
  const colors = await page.evaluate(() => ({
    background: getComputedStyle(document.body).backgroundColor,
    text: getComputedStyle(document.body).color,
    muted: getComputedStyle(document.querySelector('.hero-scroll')!).color,
  }));
  expect(contrast(colors.text, colors.background)).toBeGreaterThanOrEqual(4.5);
  expect(contrast(colors.muted, colors.background)).toBeGreaterThanOrEqual(4.5);
  expect(
    await page
      .locator('[tabindex]')
      .evaluateAll(
        (nodes) => nodes.filter((node) => Number(node.getAttribute('tabindex')) > 0).length,
      ),
  ).toBe(0);
  await scene(page, 'gallery');
  await page.locator('.collection-tuner').focus();
  await page.keyboard.press('ArrowRight');
  await expect(page.locator('input[value="articles"]')).toBeChecked();
  await expect(page.locator('#articles h3 a').first()).toBeVisible();
  const writing = page.locator('#articles h3 a').first();
  const destination = await writing.getAttribute('href');
  await writing.focus();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(new URL(destination!, process.env.PERSEFONIA_BROWSER_URL!).href);
});
test('continuous resize and orientation preserve composition and all routes', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop-1440');
  await visit(page);
  for (const [width, height] of [
    [320, 640],
    [390, 850],
    [600, 900],
    [760, 850],
    [768, 1024],
    [932, 430],
    [1280, 800],
    [1440, 900],
    [1920, 1080],
    [2560, 1080],
  ]) {
    await page.setViewportSize({ width: width!, height: height! });
    await assertStructure(page, 'EN');
    await expect(page.locator('.hero-statement')).toBeVisible();
    await page.screenshot({ path: testInfo.outputPath(`resize-${width}x${height}.png`) });
  }
});
