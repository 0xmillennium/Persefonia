import { chooseCollection } from '../support/gallery';
import { expect, test } from '../support/test';
import { visit, position } from '../support/scenes';

test('reduced motion uses readable static sections on initial and live preference changes', async ({
  page,
}) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await visit(page);
  await position(page, 'gallery', 0.945);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect
    .poll(async () => ({
      ...(await page.evaluate(() => ({
        preference: matchMedia('(prefers-reduced-motion: reduce)').matches,
        presentation: document.querySelector<HTMLElement>('[data-home-v2]')!.dataset.reduced,
      }))),
      errors,
    }))
    .toEqual({ preference: true, presentation: 'true', errors: [] });
  for (const scene of ['hero', 'statements', 'capabilities', 'mark', 'gallery', 'location']) {
    const face = page.locator(`[data-scene="${scene}"] .scene-face`);
    await expect
      .poll(() =>
        face.evaluate((node) => ({
          position: getComputedStyle(node).position,
          clip: getComputedStyle(node).clipPath,
          inert: (node as HTMLElement).inert,
        })),
      )
      .toEqual({ position: 'relative', clip: 'none', inert: false });
  }
  for (const category of ['articles', 'notes', 'research']) {
    await chooseCollection(page, category);
    for (const link of await page.locator(`#${category} h3 a`).all())
      await expect(link).toBeVisible();
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
    await page.evaluate(() => innerWidth + 1),
  );
  await page.reload({ waitUntil: 'networkidle' });
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
  await expect
    .poll(() => page.locator('.statement-face').evaluate((node) => getComputedStyle(node).position))
    .toBe('relative');
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await position(page, 'gallery', 0.3);
  await expect
    .poll(() => page.locator('.gallery-face').evaluate((node) => getComputedStyle(node).position))
    .toBe('fixed');
  await expect
    .poll(() => page.locator('.gallery-face').evaluate((node) => (node as HTMLElement).inert))
    .toBe(false);
});

test('scene updates reconcile reduced motion when a preference notification is missed', async ({
  page,
}) => {
  await page.addInitScript(() => {
    const original = window.matchMedia.bind(window);
    let first = true;
    window.matchMedia = (query: string) => {
      const media = original(query);
      if (first && query === '(prefers-reduced-motion: reduce)') {
        first = false;
        const add = media.addEventListener.bind(media);
        media.addEventListener = ((
          type: string,
          listener: EventListenerOrEventListenerObject,
          options?: boolean | AddEventListenerOptions,
        ) => {
          if (type !== 'change') add(type, listener, options);
        }) as typeof media.addEventListener;
      }
      return media;
    };
  });
  await visit(page);
  await position(page, 'gallery', 0.7);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.evaluate(() => dispatchEvent(new Event('scroll')));
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-reduced', 'true');
  await expect(page.locator('.gallery-face')).toHaveCSS('position', 'relative');
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.evaluate(() => dispatchEvent(new Event('scroll')));
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-reduced', 'false');
  await expect(page.locator('.gallery-face')).toHaveCSS('position', 'fixed');
});

test('missing critical hero media releases the loader to readable content without claiming readiness', async ({
  page,
}) => {
  await page.route('**/assets/cloudy-turquoise-*.webp', (route) => route.abort());
  await page.goto('/');
  await expect(page.locator('[data-home-loader]')).toBeHidden({ timeout: 15000 });
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-media-failure', 'true');
  await expect(page.locator('[data-home-v2]')).not.toHaveAttribute('data-ready', 'true');
  await position(page, 'gallery', 0.2);
  await expect(page.locator('.home-project h3 a').first()).toBeVisible();
});

test('reduced motion and blocked WebGL preserve semantic content and controls', async ({
  browser,
}, testInfo) => {
  for (const mode of ['reduced', 'webgl']) {
    const context = await browser.newContext({
      viewport: testInfo.project.use.viewport,
      reducedMotion: mode === 'reduced' ? 'reduce' : 'no-preference',
    });
    const page = await context.newPage();
    if (mode === 'webgl')
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
    await position(page, 'gallery', 0.2);
    await expect(page.locator('.home-project h3 a').first()).toBeVisible();
    await position(page, 'gallery', 0.4);
    await chooseCollection(page, 'articles');
    await expect(page.locator('#articles h3 a').first()).toBeVisible();
    await page.screenshot({ path: testInfo.outputPath(`${mode}.png`) });
    await context.close();
  }
});
