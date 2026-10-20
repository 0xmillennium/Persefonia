import { expect, test } from '@playwright/test';
import { difference } from '../support/pixels';

for (const failure of ['no JavaScript', 'entry download failure'] as const) {
  test(`base hero writing actually paints with ${failure}`, async ({ browser }, info) => {
    const context = await browser.newContext({
      javaScriptEnabled: failure !== 'no JavaScript',
      viewport: info.project.use.viewport,
    });
    if (failure === 'entry download failure')
      await context.route('**/assets/home-*.js', (route) => route.abort());
    const page = await context.newPage();
    await page.goto('/');
    // Poll from the runner: Firefox suppresses page promise callbacks when JS is disabled.
    await expect.poll(() => page.evaluate(() => document.fonts.status)).toBe('loaded');
    const heading = page.locator('.hero-statement');
    await heading.scrollIntoViewIfNeeded();
    const clip = (await heading.boundingBox())!;
    const visible = await page.screenshot({ clip, scale: 'css' });
    await page.evaluate(() => {
      const sheet = [...document.styleSheets].find((sheet) =>
        sheet.href?.startsWith(location.origin),
      );
      if (!sheet) throw new Error('Same-origin stylesheet missing');
      sheet.insertRule('.hero-statement { visibility: hidden !important; }', sheet.cssRules.length);
    });
    const hidden = await page.screenshot({ clip, scale: 'css' });
    const delta = difference(visible, hidden);
    // DOM visibility alone cannot detect a decorative image painting over the heading.
    expect(delta.changed).toBeGreaterThan(100);
    expect(delta.fraction).toBeGreaterThan(0.015);
    await info.attach(`fallback-${failure}.png`, { body: visible, contentType: 'image/png' });
    await context.close();
  });
}

test('base homepage remains readable at narrow width with enlarged text', async ({
  browser,
}, info) => {
  test.skip(info.project.name !== 'desktop-1440', 'One representative layout per run');
  const context = await browser.newContext({
    javaScriptEnabled: false,
    viewport: { width: 320, height: 640 },
  });
  await context.route('**/assets/*.css', async (route) => {
    const response = await route.fetch();
    await route.fulfill({
      response,
      body: (await response.text()) + '\nhtml { font-size: 200% !important; }',
    });
  });
  const page = await context.newPage();
  await page.goto('/');
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(321);
  await expect(page.locator('.hero-statement')).toHaveText('Clarity isengineered');
  await expect(page.locator('.location-strip a[href="/search"]')).toHaveAttribute(
    'href',
    '/search',
  );
  await info.attach('homepage-enlarged.png', {
    body: await page.screenshot({ fullPage: true, scale: 'css' }),
    contentType: 'image/png',
  });
  await context.close();
});
