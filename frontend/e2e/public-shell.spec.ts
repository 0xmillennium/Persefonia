import { expect, test } from '@playwright/test';

test('direct public visits retain a shared SSR shell without downloading the navigation animation', async ({
  page,
}) => {
  const requests: string[] = [];
  page.on('request', (r) => requests.push(r.url()));
  for (const url of ['/en/projects', '/search', '/contact', '/en/projects/missing-project']) {
    await page.goto(url, { waitUntil: 'networkidle' });
    await expect(page.locator('.public-header')).toBeVisible();
    await expect(page.locator('.public-brand')).toHaveAttribute('href', '/');
    await expect(page.locator('.public-header nav a[href="/en/projects"]')).toBeVisible();
    await expect(page.locator('.public-footer a[href="/"]')).toBeVisible();
  }
  expect(requests.filter((url) => /\/cover-[^/]+\.js/.test(url))).toEqual([]);
});

test('SSR shell and reading content reflow at narrow widths and enlarged text without JavaScript', async ({
  browser,
}, info) => {
  test.skip(
    info.project.name === 'mobile-390' || info.project.name === 'tablet-834',
    'Layout contract sampled once per supported engine',
  );
  const context = await browser.newContext({
    javaScriptEnabled: false,
    viewport: { width: 320, height: 720 },
  });
  // Enlarge the same-origin stylesheet response; CSP continues to reject inline styles.
  await context.route('**/assets/*.css', async (route) => {
    const response = await route.fetch();
    await route.fulfill({
      response,
      body: (await response.text()) + '\nhtml { font-size: 200% !important; }',
    });
  });
  const page = await context.newPage();
  for (const url of ['/en/projects', '/search', '/contact']) {
    await page.goto(url);
    expect(await page.evaluate(() => getComputedStyle(document.documentElement).fontSize)).toBe(
      '32px',
    );
    await expect(page.locator('.public-header nav')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
      321,
    );
    for (const link of await page.locator('.public-header a').all()) {
      const bounds = await link.boundingBox();
      expect(bounds!.x).toBeGreaterThanOrEqual(0);
      expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(321);
    }
  }
  await context.close();
});

test('only a fresh matching navigation marker loads and dismisses the optional cover', async ({
  page,
}) => {
  await page.goto('/search');
  await page.evaluate(() =>
    sessionStorage.setItem(
      'persefonia-navigation-cover',
      JSON.stringify({ url: new URL('/contact', location.href).href, time: Date.now() }),
    ),
  );
  const cover = page.waitForRequest((r) => /\/cover-[^/]+\.js/.test(r.url()));
  await page.goto('/contact');
  await cover;
  await expect(page.locator('.navigation-cover')).toHaveCount(0, { timeout: 15000 });
  await expect(page.locator('.public-header')).toBeVisible();
  expect(
    await page.evaluate(() => sessionStorage.getItem('persefonia-navigation-cover')),
  ).toBeNull();
});
