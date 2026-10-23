import { expect, test } from '../support/test';
import { startOnFilm, openHomepage } from '../support/homepage';
import { difference } from '../support/pixels';

test('production assets boot without browser errors', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  const failed: string[] = [];
  page.on('requestfailed', (request) => {
    if (request.resourceType() === 'script' || request.resourceType() === 'stylesheet')
      failed.push(new URL(request.url()).pathname);
  });
  await startOnFilm(page);
  await openHomepage(page);
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-enhanced', 'true');
  await expect(page.locator('.hero-statement')).toHaveText('Clarity isengineered');
  expect(
    await page.locator('.hero-statement').evaluate((n) => getComputedStyle(n).fontFamily),
  ).not.toBe('');
  expect(await page.locator('script[src]').count()).toBeGreaterThan(0);
  expect(errors).toEqual([]);
  expect(failed).toEqual([]);
});

test('base writing paints without JavaScript', async ({ browser }, info) => {
  const context = await browser.newContext({
    javaScriptEnabled: false,
    viewport: { width: 1440, height: 900 },
  });
  try {
    const page = await context.newPage();
    await page.goto('/');
    await expect.poll(() => page.evaluate(() => document.fonts.status)).toBe('loaded');
    const heading = page.locator('.hero-statement');
    await heading.scrollIntoViewIfNeeded();
    const clip = (await heading.boundingBox())!;
    const visible = await page.screenshot({ clip, scale: 'css' });
    await page.evaluate(() => {
      const sheet = [...document.styleSheets].find((s) => s.href?.startsWith(location.origin));
      if (!sheet) throw new Error('Same-origin CSS missing');
      sheet.insertRule('.hero-statement { visibility: hidden !important; }', sheet.cssRules.length);
    });
    const delta = difference(visible, await page.screenshot({ clip, scale: 'css' }));
    expect(delta.changed).toBeGreaterThan(100);
    expect(delta.fraction).toBeGreaterThan(0.015);
    await info.attach('base-writing-paint', { body: visible, contentType: 'image/png' });
    await page.setViewportSize({ width: 320, height: 720 });
    await context.route('**/assets/*.css', async (route) => {
      const response = await route.fetch();
      await route.fulfill({
        response,
        body: (await response.text()) + '\nhtml { font-size: 200% !important; }',
      });
    });
    await page.goto('/');
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
      321,
    );
    await expect(page.locator('.hero-statement')).toHaveText('Clarity isengineered');
    await page.goto('/en/projects');
    await expect(page.locator('.public-header nav')).toBeVisible();
    expect(await page.evaluate(() => getComputedStyle(document.documentElement).fontSize)).toBe(
      '32px',
    );
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
      321,
    );
    for (const link of await page.locator('.public-header a').all()) {
      const box = (await link.boundingBox())!;
      expect(box.x).toBeGreaterThanOrEqual(0);
      expect(box.x + box.width).toBeLessThanOrEqual(321);
    }
  } finally {
    await context.close();
  }
});

test('Writing B completes its clipped presentation', async ({ page }) => {
  await startOnFilm(page, 'tree');
  await openHomepage(page);
  await page
    .locator('[data-scene="statements"]')
    .evaluate((n) => scrollTo(0, Math.ceil(n.getBoundingClientRect().top + scrollY + 1)));
  await expect
    .poll(() =>
      page
        .locator('.statement-word-text')
        .evaluateAll((nodes) =>
          nodes.every(
            (n) =>
              new DOMMatrixReadOnly(getComputedStyle(n).transform).m42 === 0 &&
              getComputedStyle(n).opacity === '1',
          ),
        ),
    )
    .toBe(true);
  await expect(page.locator('.statement-word-slot').first()).toHaveCSS('overflow', 'hidden');
  await expect(page.locator('.statement-word').first()).toHaveCSS('filter', 'none');
});
