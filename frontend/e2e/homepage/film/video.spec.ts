import { startOnFilm } from '../../support/homepage';
import { expect, test, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { pixels } from '../../support/pixels';

test.beforeEach(async ({ page }) => startOnFilm(page));

const posterVersion = JSON.parse(
  readFileSync(new URL('../../../public/home-posters/manifest.json', import.meta.url), 'utf8'),
).identity;
async function frame(page: Page, index: number, wait = true) {
  await page.evaluate((index) => {
    const hero = document.querySelector('[data-scene="hero"]')!.getBoundingClientRect();
    const mark = document.querySelector('[data-scene="mark"]')!.getBoundingClientRect();
    const start = hero.top + scrollY,
      end = mark.top + scrollY + mark.height * 0.98;
    scrollTo(0, Math.round(start + ((end - start) * index) / 191));
  }, index);
  if (wait)
    await expect(page.locator('[data-film-status]')).toHaveText(
      new RegExp(` · ${String(index + 1).padStart(3, '0')} / 192$`),
    );
}
async function visit(page: Page) {
  await page.goto('/');
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
  await expect(page.locator('[data-home-loader]')).toBeHidden();
}
test('paused native films seek both ways, retain exact end poses and sleep at rest', async ({
  page,
}, info) => {
  test.skip(info.project.name === 'tablet-834', 'Desktop and portrait video tiers');
  await visit(page);
  for (const film of ['robotic-arm', 'tree']) {
    if (film === 'tree') {
      await page.locator('[data-home-v2]').evaluate((node) => node.focus({ preventScroll: true }));
      await page.keyboard.press('f');
      await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'tree');
    }
    const video = page.locator(`video[src*="/${film}.mp4"]`);
    for (const index of [20, 104, 191, 20, 0]) {
      await frame(page, index);
      await expect(video).toBeVisible();
      const sample = await video.evaluate(
        async (node, { index, film, version }) => {
          const video = node as HTMLVideoElement;
          const source = new Image();
          const endpoint = index === 0 || index === 191;
          if (endpoint) {
            source.src = `/assets/home-posters/${version}/${video.videoWidth}/${film}/${index === 0 ? 'opening' : 'final'}.webp`;
            await source.decode();
          }
          const canvas = document.createElement('canvas');
          canvas.width = video.videoWidth;
          canvas.height = video.videoHeight;
          const context = canvas.getContext('2d', { willReadFrequently: true })!;
          context.drawImage(video, 0, 0);
          const actual = context.getImageData(0, 0, canvas.width, canvas.height).data;
          if (index === 0 || index === 191) context.drawImage(source, 0, 0);
          const expected = context.getImageData(0, 0, canvas.width, canvas.height).data;
          let error = 0;
          for (let i = 0; i < actual.length; i += 4)
            for (let c = 0; c < 3; c++) error += Math.abs(actual[i + c]! - expected[i + c]!);
          return {
            error: endpoint ? error / (canvas.width * canvas.height * 3) : null,
            paused: video.paused,
            seeking: video.seeking,
            time: video.currentTime,
          };
        },
        { index, film, version: posterVersion },
      );
      expect(sample.paused).toBe(true);
      expect(sample.seeking).toBe(false);
      expect(sample.time).toBeGreaterThanOrEqual(index / 30 - 0.001);
      expect(sample.time).toBeLessThan((index + 1) / 30);
      if (sample.error !== null) expect(sample.error).toBeLessThan(4);
      if (index === 0) {
        const clip = {
          x: 4,
          y: Math.round(page.viewportSize()!.height * 0.65),
          width: 8,
          height: 8,
        };
        // At the opening pose this margin belongs to the photograph above
        // the fixed video. Native frame pixels are verified independently above.
        const expected = await page.locator('[data-hero-image]').evaluate((node, clip) => {
          const image = node as HTMLImageElement;
          const bounds = image.getBoundingClientRect();
          const scale = Math.max(
            bounds.width / image.naturalWidth,
            bounds.height / image.naturalHeight,
          );
          const width = image.naturalWidth * scale,
            height = image.naturalHeight * scale;
          const left = bounds.x + (bounds.width - width) / 2;
          const top = bounds.y + (bounds.height - height) / 2;
          const canvas = document.createElement('canvas');
          canvas.width = canvas.height = 8;
          const context = canvas.getContext('2d')!;
          context.drawImage(
            image,
            (clip.x - left) / scale,
            (clip.y - top) / scale,
            clip.width / scale,
            clip.height / scale,
            0,
            0,
            8,
            8,
          );
          return [...context.getImageData(0, 0, 8, 8).data];
        }, clip);
        const actual = pixels(await page.screenshot({ clip, scale: 'css' }));
        let error = 0;
        for (let i = 0; i < 64; i++)
          for (let c = 0; c < 3; c++)
            error += Math.abs(actual.data[i * actual.channels + c]! - expected[i * 4 + c]!);
        expect(error / (64 * 3)).toBeLessThan(4);
      }
    }
    const time = await video.evaluate((node) => (node as HTMLVideoElement).currentTime);
    await page.waitForTimeout(300);
    expect(await video.evaluate((node) => (node as HTMLVideoElement).currentTime)).toBe(time);
  }
  await page.screenshot({ path: info.outputPath('native-door-tree.png') });
});
test('unavailable videos use static posters without WebGL and retry at the current scroll pose', async ({
  page,
}) => {
  let blocked = true;
  const requests: string[] = [];
  page.on('request', (request) => requests.push(request.url()));
  await page.route('**/assets/home-videos/**/*.mp4*', (route) =>
    blocked ? route.fulfill({ status: 503, body: 'unavailable' }) : route.continue(),
  );
  await page.addInitScript(() => {
    const original = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (
      this: HTMLCanvasElement,
      type: string,
      ...args: unknown[]
    ) {
      if (type.startsWith('webgl')) return null;
      return original.apply(this, [type, ...args] as Parameters<typeof original>);
    } as typeof original;
  });
  await visit(page);
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film-presentation', 'poster');
  await expect(page.locator('img[data-film-poster="opening"]')).toBeVisible();
  await frame(page, 100, false);
  await expect(page.locator('img[data-film-poster="final"]')).toBeVisible();
  await expect(page.locator('[data-film-status]')).toContainText('static final poster');
  expect(requests.some((url) => url.includes('/home-films/'))).toBe(false);
  const y = await page.evaluate(() => scrollY);
  blocked = false;
  await page.locator('[data-home-v2]').focus();
  // F retries the selected failed media and selects the next film as before.
  await page.keyboard.press('f');
  await expect(page.locator('[data-film-status]')).toHaveText('Tree · 101 / 192');
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film-presentation', 'video');
  expect(await page.evaluate(() => scrollY)).toBe(y);
});
test('incoming video failure reveals its static poster without losing scroll position', async ({
  page,
}) => {
  await page.route('**/tree.mp4*', (route) => route.fulfill({ status: 503, body: 'unavailable' }));
  await visit(page);
  await frame(page, 80);
  await page.locator('[data-home-v2]').focus();
  await page.keyboard.press('f');
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'tree');
  await expect(page.locator('[data-film-status]')).toContainText('Tree · static final poster');
});

test('complete video and poster failure releases the loader and leaves semantic navigation usable', async ({
  page,
}) => {
  await page.route('**/assets/home-videos/**/*.mp4*', (route) => route.abort());
  await page.route('**/assets/home-posters/**/*.webp*', (route) => route.abort());
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await expect(page.locator('[data-home-loader]')).toBeHidden({ timeout: 15000 });
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-media-failure', 'true');
  await expect(page.locator('[data-film-notice]')).toContainText('Film unavailable');
  await page
    .locator('.home-nav a[href="#work"]')
    .evaluate((node) => (node as HTMLAnchorElement).click());
  await expect(page.locator('.gallery-card__title a').first()).toBeVisible();
});

test('late video failure settles to a poster and explicit retry preserves the current target', async ({
  page,
}) => {
  await visit(page);
  await frame(page, 100);
  await page
    .locator('video[src*="robotic-arm.mp4"]')
    .evaluate((node) => node.dispatchEvent(new Event('error')));
  await expect(page.locator('[data-film-status]')).toContainText('static final poster');
  await frame(page, 191, false);
  await expect(page.locator('img[data-film-poster="final"]')).toBeVisible();
  await page.evaluate(() => scrollTo(0, 0));
  await expect(page.locator('img[data-film-poster="opening"]')).toBeVisible();
  await expect(page.locator('[data-film-status]')).toContainText('static opening poster');
});
