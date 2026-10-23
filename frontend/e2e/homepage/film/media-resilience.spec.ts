import { startOnFilm } from '../../support/homepage';
import { expect, test } from '../../support/test';
import { visit, presented, position, selectFilm } from '../../support/scenes';

test.beforeEach(async ({ page }) => startOnFilm(page));

test('resolution changes retain the last native presentation until a new pose is ready', async ({
  page,
}, info) => {
  test.skip(
    info.project.name === 'tablet-834' || !!info.project.use.isMobile,
    'Live landscape/portrait resize',
  );
  await page.setViewportSize({ width: 960, height: 540 });
  await visit(page);
  await position(page, 'hero', 0.3);
  await presented(page);
  const old = page.locator('video[src*="/960/robotic-arm.mp4"]');
  expect(await old.evaluate((node) => (node as HTMLVideoElement).videoWidth)).toBe(960);
  const oldStatus = await page.locator('[data-film-status]').textContent();
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route('**/1600/robotic-arm.mp4*', async (route) => {
    await gate;
    await route.continue();
  });
  try {
    await page.setViewportSize({ width: 1440, height: 900 });
    await expect(page.locator('video:not([data-loop-video])')).toHaveCount(2);
    await expect(old).toBeVisible();
    await expect(page.locator('[data-film-status]')).toHaveText(oldStatus!);
    release();
    await expect(page.locator('video:not([data-loop-video])')).toHaveCount(1);
    await presented(page);
    expect(
      await page
        .locator('video:not([data-loop-video])')
        .evaluate((node) => (node as HTMLVideoElement).videoWidth),
    ).toBe(1600);
    await page.setViewportSize({ width: 960, height: 540 });
    await presented(page);
    await expect
      .poll(() =>
        page
          .locator('video:not([data-loop-video]):visible')
          .evaluate((node) => (node as HTMLVideoElement).videoWidth),
      )
      .toBe(960);
    await selectFilm(page);
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'tree');
    await selectFilm(page);
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'waterfall');
    const loop = page.locator('video[data-loop-video]:visible');
    await expect
      .poll(() => loop.evaluate((node) => (node as HTMLVideoElement).videoWidth))
      .toBe(1280);
    for (const [width, height, expected] of [
      [390, 850, 1280],
      [1440, 900, 1920],
      [390, 850, 1280],
    ]) {
      await page.setViewportSize({ width: width!, height: height! });
      await expect
        .poll(() => loop.evaluate((node) => (node as HTMLVideoElement).videoWidth))
        .toBe(expected);
      await expect(loop).toHaveAttribute('src', new RegExp(`/${expected}/waterfall\\.mp4$`));
    }
    await expect(page.locator('video[data-loop-video]')).toHaveCount(1);
    await expect.poll(() => loop.evaluate((node) => (node as HTMLVideoElement).paused)).toBe(false);
  } finally {
    release();
  }
});
