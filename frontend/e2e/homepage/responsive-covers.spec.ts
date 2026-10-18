import { expect, test } from '@playwright/test';

test('responsive covers do not undersize the wider unenhanced reading layout', async ({
  browser,
  baseURL,
}, info) => {
  test.skip(info.project.name !== 'desktop-1440', 'One controlled DPR-1 reading layout');
  const context = await browser.newContext({
    javaScriptEnabled: false,
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 1,
  });
  // Source eligibility is qualified by Media Library tests; provide three deterministic
  // same-origin image candidates to exercise browser sizing without changing stored media.
  await context.route('**/__cover-test__/*', async (route) => {
    const width = Number(/cover-(\d+)\.svg/.exec(route.request().url())![1]);
    await route.fulfill({
      contentType: 'image/svg+xml',
      body: `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${width / 2}"><rect width="100%" height="100%" fill="#163c3d"/></svg>`,
    });
  });
  await context.route(baseURL! + '/', async (route) => {
    const response = await route.fetch();
    await route.fulfill({
      response,
      body: (await response.text()).replace(
        /srcset="[^"]*\/media\/assets\/[^"]*"/g,
        'srcset="/__cover-test__/cover-320.svg 320w, /__cover-test__/cover-960.svg 960w, /__cover-test__/cover-1600.svg 1600w"',
      ),
    });
  });
  const page = await context.newPage();
  await page.goto(baseURL! + '/');
  const image = page.locator('.home-writing__list .gallery-cover img').first();
  await image.scrollIntoViewIfNeeded();
  expect((await image.boundingBox())!.width).toBeGreaterThan(960);
  await expect(image).toHaveAttribute('sizes', 'auto, 100vw');
  await expect
    .poll(() => image.evaluate((image) => (image as HTMLImageElement).currentSrc))
    .toContain('/cover-1600.svg');
  await context.close();
});
