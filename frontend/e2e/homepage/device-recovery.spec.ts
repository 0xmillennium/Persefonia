import { startOnFilm } from '../support/homepage';
import { expect, test } from '@playwright/test';

test.beforeEach(async ({ page }) => startOnFilm(page));

test('a slow cold scroll film stays behind the opening screen until its video is ready', async ({
  page,
}) => {
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route('**/home-videos/**/robotic-arm.mp4*', async (route) => {
    await gate;
    await route.continue();
  });
  try {
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('[data-film-poster="opening"]')).toBeVisible();
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-startup-state', 'loading');
    await expect(page.locator('[data-home-v2]')).not.toHaveAttribute('data-ready', 'true');
    await expect(page.locator('[data-home-loader]')).toBeVisible();
    release();
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-startup-state', 'ready');
    await expect(page.locator('[data-home-loader]')).toBeHidden();
    await expect(page.locator('[data-film-poster="opening"]')).toBeHidden();
  } finally {
    release();
  }
});

test('toolbar-sized height changes fill the screen without changing story distances', async ({
  page,
}) => {
  await page.goto('/');
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
  const before = await page.evaluate(() => {
    const probe = document.querySelector<HTMLElement>('.home-viewport-probe')!;
    // Emulate a stable small viewport while the visible viewport expands.
    probe.style.height = `${probe.clientHeight}px`;
    return [...document.querySelectorAll('[data-scene]')].map(
      (n) => n.getBoundingClientRect().height,
    );
  });
  const viewport = page.viewportSize()!;
  await page.setViewportSize({ width: viewport.width, height: viewport.height + 100 });
  await expect
    .poll(() =>
      page
        .locator('.home-environment')
        .evaluate((n) => Math.round(n.getBoundingClientRect().height)),
    )
    .toBe(viewport.height + 100);
  expect(
    await page
      .locator('[data-scene]')
      .evaluateAll((nodes) => nodes.map((n) => n.getBoundingClientRect().height)),
  ).toEqual(before);
});

test('touch gallery centers its first card after one viewport and reverses', async ({
  page,
}, info) => {
  test.skip(!info.project.use.isMobile, 'Touch pacing');
  await page.goto('/');
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
  const position = await page.evaluate(() => {
    const gallery = document.querySelector<HTMLElement>('[data-scene="gallery"]')!;
    const height = parseFloat(
      document
        .querySelector<HTMLElement>('[data-home-v2]')!
        .style.getPropertyValue('--story-viewport-height'),
    );
    const top = gallery.getBoundingClientRect().top + scrollY;
    scrollTo(0, Math.round(top + height));
    return { top, height };
  });
  await expect
    .poll(() => page.locator('[data-scene="gallery"]').getAttribute('data-gallery-index'))
    .toBe('0');
  await expect(page.locator('.gallery-face')).toHaveCSS('opacity', '1');
  await page.evaluate((top) => scrollTo(0, top - 10), position.top);
  await expect(page.locator('[data-scene="gallery"]')).toHaveAttribute(
    'data-gallery-empty',
    'true',
  );
  await page.evaluate(({ top, height }) => scrollTo(0, Math.round(top + height)), position);
  await expect
    .poll(() => page.locator('[data-scene="gallery"]').getAttribute('data-gallery-index'))
    .toBe('0');
});
