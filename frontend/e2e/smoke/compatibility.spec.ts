import { expect, test, type Page } from '../support/test';
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
  await expect(page.locator('.hero-statement .hero-writing-text')).toHaveText([
    'Clarity is',
    'engineered',
  ]);
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
  let ownedPage: Page | undefined;
  let failed = false;
  const diagnosticErrors: string[] = [];
  const bounded = async (label: string, action: () => Promise<unknown>, milliseconds: number) => {
    let timer: ReturnType<typeof setTimeout> | undefined;
    try {
      await Promise.race([
        action(),
        new Promise((_, reject) => {
          timer = setTimeout(() => reject(new Error(`${label} deadline exceeded`)), milliseconds);
        }),
      ]);
      return true;
    } catch {
      diagnosticErrors.push(label);
      return false;
    } finally {
      clearTimeout(timer);
    }
  };
  try {
    await bounded(
      'trace start',
      () => context.tracing.start({ screenshots: true, snapshots: true }),
      1000,
    );
    const page = await context.newPage();
    ownedPage = page;
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
  } catch (error) {
    failed = true;
    const failedPage = ownedPage;
    if (failedPage) {
      const captured = await bounded(
        'failure screenshot',
        () => failedPage.screenshot({ path: info.outputPath('no-js-failure.png'), timeout: 2000 }),
        2500,
      );
      if (captured)
        await bounded(
          'screenshot attachment',
          () =>
            info.attach('no-js-failure', {
              path: info.outputPath('no-js-failure.png'),
              contentType: 'image/png',
            }),
          1000,
        );
    }
    throw error;
  } finally {
    const traceStopped = await bounded(
      'trace finalization',
      () => context.tracing.stop(failed ? { path: info.outputPath('no-js-trace.zip') } : {}),
      3000,
    );
    if (failed && traceStopped)
      await bounded(
        'trace attachment',
        () =>
          info.attach('no-js-trace', {
            path: info.outputPath('no-js-trace.zip'),
            contentType: 'application/zip',
          }),
        1000,
      );
    await bounded('context cleanup', () => context.close(), 2000);
    if (diagnosticErrors.length)
      await bounded(
        'diagnostic errors attachment',
        () =>
          info.attach('no-js-diagnostic-errors', {
            body: JSON.stringify({ failedOperations: diagnosticErrors }),
            contentType: 'application/json',
          }),
        500,
      );
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
      page.locator('.statement-word-text').evaluateAll(
        (nodes) =>
          nodes.length > 0 &&
          nodes.every((n) => {
            const style = getComputedStyle(n);
            return (
              (style.transform === 'none' || new DOMMatrixReadOnly(style.transform).m42 === 0) &&
              style.opacity === '1'
            );
          }),
      ),
    )
    .toBe(true);
  await expect(page.locator('.statement-word-slot').first()).toHaveCSS('overflow', 'hidden');
  await expect(page.locator('.statement-word').first()).toHaveCSS('filter', 'none');
});
