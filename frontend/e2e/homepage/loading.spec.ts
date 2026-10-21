import { expect, test } from '../support/test';
import { visit } from '../support/scenes';

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
    // Playwright waits for document font readiness when capturing. A held media
    // request can keep that promise pending in WebKit; capture after release.
    await page.screenshot({ path: info.outputPath('opening-ready.png') });
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

for (const reduced of [false, true]) {
  for (const failure of ['abort', '404', 'corrupt'] as const) {
    test(`settled hero ${failure} releases usable ${reduced ? 'static' : 'video'} fallback promptly`, async ({
      page,
    }) => {
      await page.emulateMedia({ reducedMotion: reduced ? 'reduce' : 'no-preference' });
      await page.route('**/assets/cloudy-turquoise-*.webp', async (route) => {
        if (failure === 'abort') await route.abort();
        else
          await route.fulfill({
            status: failure === '404' ? 404 : 200,
            contentType: 'image/webp',
            body: 'invalid image',
          });
      });
      await page.goto('/', { waitUntil: 'domcontentloaded' });
      // Native alternative has actually prepared; the hero failure must not add the 12s deadline.
      if (reduced) await expect(page.locator('img[src$="/waterfall.webp"]')).toBeVisible();
      else
        await expect
          .poll(() =>
            page.locator('video[data-loop-video]').evaluate((node) => {
              const video = node as HTMLVideoElement;
              return video.readyState >= 2 && !video.paused;
            }),
          )
          .toBe(true);
      await expect(page.locator(root)).toHaveAttribute('data-startup-state', 'fallback', {
        timeout: 1500,
      });
      await expect(page.locator(loader)).toBeHidden({ timeout: 3000 });
      await expect(page.locator(root)).not.toHaveAttribute('data-ready', 'true');
      await expect(page.locator(root)).toHaveAttribute('data-media-failure', 'true');
      await expect(page.locator('.hero-statement')).toBeVisible();
    });
  }
}

test('pending hero decode stays loading and a delayed healthy response completes normally', async ({
  page,
}) => {
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.route('**/assets/cloudy-turquoise-*.webp', async (route) => {
    await gate;
    await route.continue();
  });
  try {
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('img[src$="/waterfall.webp"]')).toBeVisible();
    await expect(page.locator(root)).toHaveAttribute('data-startup-state', 'loading');
    await expect(page.locator(loader)).toBeVisible();
    release();
    await expect(page.locator(root)).toHaveAttribute('data-startup-state', 'static');
    await expect(page.locator(loader)).toBeHidden();
  } finally {
    release();
  }
});

test('homepage visits use actual readiness while unrelated network work remains pending', async ({
  page,
}) => {
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route('**/robots.txt?qualification-pending', async (route) => {
    await gate;
    await route.continue();
  });
  await page.addInitScript(() =>
    document.addEventListener('DOMContentLoaded', () => {
      void fetch('/robots.txt?qualification-pending');
    }),
  );
  let complete = false;
  const visiting = visit(page).then(() => {
    complete = true;
  });
  try {
    await expect.poll(() => complete).toBe(true);
    await expect(page.locator(root)).toHaveAttribute('data-ready', 'true');
    await expect(page.locator(loader)).toBeHidden();
  } finally {
    release();
    await visiting;
  }
});

test('homepage visits do not accept a stationary but unprepared hero', async ({ page }) => {
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route('**/assets/cloudy-turquoise-*.webp', async (route) => {
    await gate;
    await route.continue();
  });
  let complete = false;
  const visiting = visit(page).then(() => {
    complete = true;
  });
  try {
    await expect(page.locator(root)).toHaveAttribute('data-startup-state', 'loading');
    await expect(page.locator(loader)).toBeVisible();
    expect(complete).toBe(false);
  } finally {
    release();
    await visiting;
  }
  expect(complete).toBe(true);
});
