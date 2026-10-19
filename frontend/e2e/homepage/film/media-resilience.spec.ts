import { expect, test } from '@playwright/test';
import { visit, presented, position, selectFilm } from '../../support/scenes';

test('a hung native film recovers through a static poster within the readiness deadline', async ({
  page,
}, info) => {
  test.skip(info.project.name === 'tablet-834', 'Desktop and portrait readiness');
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route('**/robotic-arm.mp4*', async (route) => {
    await gate;
    await route.abort();
  });
  try {
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true', {
      timeout: 12000,
    });
    await expect(page.locator('[data-home-loader]')).toBeHidden();
    await expect(page.locator('[data-film-status]')).toContainText(
      'Robotic arm · static opening poster',
    );
    expect(
      await page.evaluate(() =>
        performance
          .getEntriesByType('resource')
          .some((entry) => entry.name.includes('/home-posters/')),
      ),
    ).toBe(true);
    await position(page, 'hero', 0.3);
    await expect(page.locator('[data-home-v2]')).toHaveAttribute(
      'data-film-presentation',
      'poster',
    );
  } finally {
    release();
  }
});

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
    await page.setViewportSize({ width: 390, height: 850 });
    await expect
      .poll(() => loop.evaluate((node) => (node as HTMLVideoElement).videoWidth))
      .toBe(1920);
    await expect(page.locator('video[data-loop-video]')).toHaveCount(1);
    await expect.poll(() => loop.evaluate((node) => (node as HTMLVideoElement).paused)).toBe(false);
  } finally {
    release();
  }
});

test('reduced motion skips GPU and physics; static presentations contain no empty cinematic spacer', async ({
  page,
}, info) => {
  test.skip(
    info.project.name === 'tablet-834' || !!info.project.use.isMobile,
    'Capability policy independent of viewport',
  );
  await page.emulateMedia({ reducedMotion: 'reduce' });
  const requests: string[] = [];
  page.on('request', (request) => requests.push(request.url()));
  await visit(page);
  await expect(page.locator('video')).toHaveCount(0);
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film-presentation', 'poster');
  await expect(page.locator('[data-film-canvas]')).toBeHidden();
  await expect(page.locator('[data-scene="breath"]')).toHaveCSS('display', 'none');
  await position(page, 'mark', 0.1);
  expect(requests.some((url) => /\/physical-[^/]+\.js/.test(url))).toBe(false);
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await expect(page.locator('[data-scene="breath"]')).not.toHaveCSS('display', 'none');
  await expect.poll(() => requests.some((url) => /\/physical-[^/]+\.js/.test(url))).toBe(true);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect(page.locator('[data-film-canvas]')).toBeHidden();
});

test('a hung loop retains its poster and explicitly retries native playback', async ({
  page,
}, info) => {
  test.skip(
    info.project.name === 'tablet-834' || !!info.project.use.isMobile,
    'Shared loop readiness policy',
  );
  let release!: () => void;
  let blocked = true;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route('**/waterfall.mp4*', async (route) => {
    if (blocked) await gate;
    if (blocked) await route.abort();
    else await route.continue();
  });
  try {
    await visit(page);
    await selectFilm(page);
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'tree');
    await selectFilm(page);
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'waterfall');
    await expect(page.locator('[data-film-status]')).toContainText('still frame retained', {
      timeout: 10000,
    });
    await expect(page.locator('img[src$="/waterfall.webp"]')).toBeVisible();
    blocked = false;
    release();
    await page.mouse.click(30, 300);
    await expect
      .poll(() =>
        page
          .locator('video[data-loop-video]')
          .evaluate((node) => (node as HTMLVideoElement).paused),
      )
      .toBe(false);
    await expect(page.locator('[data-film-status]')).toHaveText('Waterfall · loop');
  } finally {
    release();
  }
});
