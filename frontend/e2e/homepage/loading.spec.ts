import { expect, test } from '@playwright/test';

const root = '[data-home-v2]';
const loader = '[data-home-loader]';

test('waterfall is the default and measured progress bar waits for native playback', async ({
  page,
}, info) => {
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.route('**/home-loops/**/waterfall.mp4*', async (route) => {
    await gate;
    await route.continue();
  });
  try {
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    await expect(page.locator('.home-nav nav a[href="#work"]')).toContainText('Projects');
    await expect(page.locator(root)).toHaveAttribute('data-film', 'waterfall');
    await expect(page.locator(root)).toHaveAttribute('data-film-presentation', 'poster');
    await expect(page.locator(root)).toHaveAttribute('data-startup-state', 'loading');
    await expect(page.locator(root)).not.toHaveAttribute('data-ready', 'true');
    await expect(page.locator(loader)).toBeVisible();
    const coverage = await page.locator(`${loader} .home-staircase i`).evaluateAll((nodes) =>
      nodes.map((node) => {
        const box = node.getBoundingClientRect();
        return box.top <= 0 && box.bottom >= innerHeight;
      }),
    );
    expect(coverage.length).toBeGreaterThan(0);
    expect(coverage.every(Boolean)).toBe(true);
    await expect(page.locator('[data-loading-progress]')).toHaveAttribute('data-progress', '25');
    const bar = page.getByRole('progressbar', { name: 'Homepage preparation' });
    await expect(bar).toHaveAttribute('aria-valuenow', '25');
    await expect(
      page.locator('.loader-aperture,.loader-grid,.loading-digit,.loading-counter'),
    ).toHaveCount(0);
    await expect(page.locator('[data-loading-description]')).toContainText('0x3E8');
    const logo = await page.locator('[data-loading-identity]').boundingBox();
    const fact = await page.locator('.loader-info').boundingBox();
    const barBox = await bar.boundingBox();
    expect(fact!.y).toBeGreaterThan(logo!.y + logo!.height * 0.75);
    expect(barBox!.y).toBeLessThan(logo!.y);
    expect(barBox!.x).toBe(0);
    expect(barBox!.width).toBe(page.viewportSize()!.width);
    expect(Math.abs(fact!.x + fact!.width / 2 - page.viewportSize()!.width / 2)).toBeLessThan(2);
    await expect
      .poll(() =>
        page
          .locator('.loading-progress-fill')
          .evaluate((node) => new DOMMatrix(getComputedStyle(node).transform).a),
      )
      .toBeCloseTo(0.25, 2);
    await expect(page.locator('[data-loading-hint]')).toContainText(
      info.project.use.hasTouch ? 'Swipe to explore' : 'Scroll to explore',
      { timeout: 4500 },
    );
    await page.screenshot({ path: info.outputPath('opening-bar.png') });
    release();
    await expect(page.locator(root)).toHaveAttribute('data-startup-state', 'ready');
    await expect(page.locator('[data-loading-progress]')).toHaveAttribute('data-progress', '100');
    await expect
      .poll(() =>
        page
          .locator(`${loader} .home-staircase i`)
          .first()
          .evaluate((node) => node.getAnimations().length),
      )
      .toBeGreaterThan(0);
    await expect(page.locator(loader)).toBeVisible();
    await expect(page.locator('[data-loading-description]')).not.toBeInViewport();
    await expect(page.locator(loader)).toBeHidden();
    await expect(page.locator('[data-loading-description]')).toBeHidden();
    await expect(page.locator('video[data-loop-video]')).toBeVisible();
    expect(
      await page
        .locator('video[data-loop-video]')
        .evaluate((node) => (node as HTMLVideoElement).paused),
    ).toBe(false);
    await page.locator(root).focus();
    await page.keyboard.press('f');
    await expect(page.locator(root)).toHaveAttribute('data-film', 'robotic-arm');
    await page.keyboard.press('f');
    await expect(page.locator(root)).toHaveAttribute('data-film', 'tree');
    await page.keyboard.press('f');
    await expect(page.locator(root)).toHaveAttribute('data-film', 'waterfall');
    expect(errors).toEqual([]);
  } finally {
    release();
  }
});

test('a stalled waterfall opens explicit fallback and recovers without reload when video arrives late', async ({
  page,
}) => {
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route('**/home-loops/**/waterfall.mp4*', async (route) => {
    await gate;
    await route.continue();
  });
  try {
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await expect(page.locator(root)).toHaveAttribute('data-startup-state', 'fallback', {
      timeout: 10000,
    });
    await expect(page.locator(loader)).toBeHidden();
    await expect(page.locator('[data-loading-progress]')).toHaveAttribute('data-mode', 'fallback');
    await expect(page.locator('[data-loading-progress]')).not.toHaveAttribute(
      'data-progress',
      '100',
    );
    release();
    await expect(page.locator(root)).toHaveAttribute('data-startup-state', 'ready', {
      timeout: 10000,
    });
    await expect(page.locator(root)).toHaveAttribute('data-media-failure', 'false');
    await expect(page.locator('video[data-loop-video]')).toBeVisible();
  } finally {
    release();
  }
});

test('reduced motion accepts a static waterfall without requesting or creating a video', async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  const requests: string[] = [];
  page.on('request', (request) => {
    if (request.url().includes('.mp4')) requests.push(request.url());
  });
  await page.goto('/');
  await expect(page.locator(root)).toHaveAttribute('data-startup-state', 'static');
  await expect(page.locator(loader)).toBeHidden();
  await expect(page.locator('[data-loading-progress]')).toHaveAttribute('data-progress', '100');
  await expect(page.locator('video')).toHaveCount(0);
  expect(requests).toEqual([]);
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await expect(page.locator('video[data-loop-video]')).toBeVisible();
});

test('autoplay denial enters a usable still-image mode rather than claiming cinematic readiness', async ({
  page,
}) => {
  await page.addInitScript(() => {
    HTMLMediaElement.prototype.play = () =>
      Promise.reject(new DOMException('Denied', 'NotAllowedError'));
  });
  await page.goto('/');
  await expect(page.locator(root)).toHaveAttribute('data-startup-state', 'fallback');
  await expect(page.locator(loader)).toBeHidden();
  await expect(page.locator('[data-loading-progress]')).toHaveAttribute('data-mode', 'fallback');
  await expect(page.locator('.hero-statement')).toBeVisible();
});

test('leaving during the bar exit cancels every pending loader animation', async ({ page }) => {
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await expect(page.locator(root)).toHaveAttribute('data-ready', 'true');
  await page.evaluate(() =>
    dispatchEvent(new PageTransitionEvent('pagehide', { persisted: false })),
  );
  await expect(page.locator(root)).not.toHaveAttribute('data-enhanced');
  await page.waitForTimeout(350);
  expect(
    await page.locator(loader).evaluate((node) => node.getAnimations({ subtree: true }).length),
  ).toBe(0);
  await expect(page.locator('video')).toHaveCount(0);
});
