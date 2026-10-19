import { startOnFilm } from '../../support/homepage';
import { settleScroll } from '../../support/scroll';
import { chooseCollection } from '../../support/gallery';
import { expect, test } from '@playwright/test';
import { visit, position, selectFilm } from '../../support/scenes';
import { difference } from '../../support/pixels';

test.beforeEach(async ({ page }) => startOnFilm(page));

test('gallery wheel pulses select one adjacent item and release both boundaries', async ({
  page,
}, info) => {
  // Chromium mobile emulation divides synthetic wheel deltas by device scale.
  const wheelScale = info.project.use.isMobile ? await page.evaluate(() => devicePixelRatio) : 1;
  const wheel = (delta: number) => page.mouse.wheel(0, delta * wheelScale);
  await visit(page);
  await expect(page.locator('[data-home-loader]')).toBeHidden();
  await position(page, 'gallery', 0.4);
  const counter = page.locator('[data-gallery-position]');
  await expect(counter).toHaveText('1 / 3');
  const galleryHeight = await page
    .locator('[data-scene="gallery"]')
    .evaluate(
      (node) =>
        node.getBoundingClientRect().height -
        (Number.parseFloat(
          (node as HTMLElement).style.getPropertyValue('--gallery-entry-distance'),
        ) || 0),
    );
  expect(galleryHeight).toBeCloseTo(
    page.viewportSize()!.height * (info.project.use.hasTouch ? 2.5 : 4.6),
    1,
  );
  for (const [delta, label] of [
    [120, '2 / 3'],
    [240, '3 / 3'],
    [-100, '2 / 3'],
    [-120, '1 / 3'],
  ] as const) {
    await wheel(delta);
    await expect(counter).toHaveText(label);
  }
  const first = await page.evaluate(() => scrollY);
  await wheel(-120);
  await expect.poll(() => page.evaluate(() => scrollY)).toBeLessThan(first);
  await position(page, 'gallery', 0.54);
  await expect(counter).toHaveText('3 / 3');
  const last = await page.evaluate(() => scrollY);
  await wheel(120);
  await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(last);
  await position(page, 'gallery', 0.4);
  await expect(counter).toHaveText('1 / 3');
  await chooseCollection(page, 'articles');
  await page.mouse.move(page.viewportSize()!.width / 2, page.viewportSize()!.height / 2);
  await page.locator('#articles h3 a').first().focus();
  await wheel(120);
  await expect(counter).toHaveText('2 / 2');
  await expect(page.locator('input[value="articles"]')).toBeChecked();
  await expect
    .poll(() =>
      page
        .locator('.gallery-card')
        .evaluateAll((nodes) => nodes.flatMap((n) => n.getAnimations()).length),
    )
    .toBe(0);
});

test('wheel reversal returns from the gallery through the film boundary to the top', async ({
  page,
}, info) => {
  test.skip(info.project.name === 'tablet-834', 'Desktop and portrait native wheel boundary');
  await visit(page);
  await expect(page.locator('[data-home-loader]')).toBeHidden();
  await position(page, 'gallery', 0.3);
  await expect(page.locator('[data-film-status]')).toHaveText('Robotic arm · 192 / 192');
  const pulses = Math.ceil((await page.evaluate(() => scrollY)) / 120) + 20;
  for (let i = 0; i < pulses; i++) await page.mouse.wheel(0, -120);
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(0);
  await expect(page.locator('[data-film-status]')).toHaveText('Robotic arm · 001 / 192');
  await expect(page.locator('.hero-writing-text').first()).toHaveCSS('opacity', '1');
  await expect(page.locator('[data-scene-wipe]')).toBeHidden();
  // Also exercise a single-pixel position immediately above the start.
  const boundary = await page
    .locator('[data-scene="hero"]')
    .evaluate((node) => node.getBoundingClientRect().top + scrollY + 1);
  await page.evaluate((y) => scrollTo(0, Math.ceil(y)), boundary);
  await page.mouse.wheel(0, -120);
  await expect.poll(() => page.evaluate(() => scrollY)).toBeLessThan(boundary);
});

test('gallery departure removes hidden keyboard targets and transfers focus visibly', async ({
  page,
}) => {
  await visit(page);
  await position(page, 'gallery', 0.7);
  await expect
    .poll(() => page.locator('.gallery-face').evaluate((node) => (node as HTMLElement).inert))
    .toBe(false);
  await page.locator('.collection-tuner').focus();
  await expect(page.locator('.collection-tuner')).toBeFocused();
  const wordmarkClip = (await page.locator('.home-sitename').boundingBox())!;
  const wordmark = await page.screenshot({ clip: wordmarkClip });
  await position(page, 'gallery', 0.945);
  await expect
    .poll(() => page.locator('.gallery-face').evaluate((node) => (node as HTMLElement).inert))
    .toBe(true);
  expect(difference(wordmark, await page.screenshot({ clip: wordmarkClip })).changed).toBeLessThan(
    30,
  );
  const departure = () =>
    page.locator('.gallery-space').evaluate((node) => {
      const style = getComputedStyle(node),
        matrix = new DOMMatrixReadOnly(style.transform);
      return {
        x: matrix.m41,
        scaleX: matrix.m11,
        scaleY: matrix.m22,
        opacity: style.opacity,
        clip: style.clipPath,
      };
    });
  const outgoing = await departure();
  expect(outgoing.x).toBe(0);
  await expect(page.locator('[data-scene="gallery"]')).toHaveAttribute(
    'data-gallery-empty',
    'true',
  );
  expect(outgoing.scaleX).toBe(1);
  expect(outgoing.scaleY).toBe(1);
  expect(outgoing.opacity).toBe('1');
  await expect(page.locator('.location-links nav .footer-rise').first()).toHaveCSS('opacity', '0');
  await position(page, 'location', 0.075);
  await expect
    .poll(async () => {
      const rises = await page
        .locator('.location-links nav .footer-rise')
        .evaluateAll((nodes) =>
          nodes.map((node) => new DOMMatrixReadOnly(getComputedStyle(node).transform).m42),
        );
      return rises[0]! < rises[1]! && rises[1]! < rises[2]!;
    })
    .toBe(true);
  await expect(page.locator('.location-system')).toHaveCSS('opacity', '0');
  await position(page, 'location', 0.55);
  await expect(page.locator('.location-system')).toHaveCSS('opacity', '1');
  await expect(page.locator('.location-links nav a').first()).toHaveJSProperty('inert', false);
  await position(page, 'gallery', 0.945);
  await expect.poll(departure).toEqual(outgoing);
  const menuToggle = page.locator('[data-menu-toggle]');
  await expect(
    (await menuToggle.isVisible()) ? menuToggle : page.locator('.home-nav nav a').first(),
  ).toBeFocused();
  await position(page, 'gallery', 0.7);
  await expect
    .poll(() => page.locator('.gallery-face').evaluate((node) => (node as HTMLElement).inert))
    .toBe(false);
  await chooseCollection(page, 'articles');
});

test('gallery arrives on staggered dark columns above the retained film', async ({
  page,
}, info) => {
  test.skip(
    info.project.name === 'tablet-834',
    'Desktop and portrait exercise twelve and six columns.',
  );
  await visit(page);
  await position(page, 'mark', 0.5);
  await expect(page.locator('[data-scene-wipe]')).toBeHidden();
  const coverStart = await page.locator('[data-scene="mark"]').evaluate((node) => {
    const rect = node.getBoundingClientRect();
    return Math.ceil(rect.top + scrollY + rect.height * 0.98);
  });
  await page.evaluate((start) => scrollTo(0, start + innerHeight * 0.1), coverStart);
  const bars = page.locator('[data-scene-wipe] i:visible');
  await expect(bars).toHaveCount(info.project.name.startsWith('mobile') ? 6 : 12);
  const translations = () =>
    bars.evaluateAll((nodes) =>
      nodes.map((node) => new DOMMatrixReadOnly(getComputedStyle(node).transform).m42),
    );
  await expect
    .poll(async () => (await translations())[0])
    .toBeLessThan(page.viewportSize()!.height);
  const steps = await translations();
  expect(steps[0]).toBeLessThan(steps.at(-1)!);
  expect(
    await page
      .locator('[data-film-canvas]')
      .evaluate((node) => !!node.closest('.home-environment')),
  ).toBe(true);
  await page.screenshot({ path: info.outputPath('gallery-curtain.png') });
  const middle = await translations();
  // The approved five-notch entrance replaced the earlier fixed viewport wipe.
  await page
    .locator('[data-scene="mark"]')
    .evaluate((node) => scrollTo(0, Math.round(node.getBoundingClientRect().top + scrollY + 1140)));
  await page.mouse.move(20, 650);
  await settleScroll(page);
  for (let notch = 0; notch < 4; notch++) {
    await page.mouse.wheel(0, 120);
    await settleScroll(page);
  }
  await expect
    .poll(translations)
    .toEqual(Array(info.project.name.startsWith('mobile') ? 6 : 12).fill(0));
  await expect(page.locator('[data-gallery-position]')).toHaveText('0 / 3');
  await expect(page.locator('.gallery-face')).toHaveCSS('opacity', '1');
  expect(
    await page
      .locator('[data-film-canvas]')
      .evaluate((node) => !!node.closest('.home-environment')),
  ).toBe(true);
  await page.evaluate((start) => scrollTo(0, start + innerHeight * 0.1), coverStart);
  await expect.poll(translations).toEqual(middle);
  await position(page, 'gallery', 0.7);
  await expect
    .poll(() => translations())
    .toEqual(Array(info.project.name.startsWith('mobile') ? 6 : 12).fill(0));
  expect(
    await page
      .locator('[data-film-canvas]')
      .evaluate((node) => !!node.closest('.home-environment')),
  ).toBe(true);
  // Native presentation stays behind the opaque curtain; only inactive posters are hidden.
  await expect(page.locator('video.film-presentation:visible')).toHaveCount(1);
  await expect(page.locator('img.film-presentation:visible')).toHaveCount(0);
  await position(page, 'mark', 0.5);
  expect(
    await page
      .locator('[data-film-canvas]')
      .evaluate((node) => !!node.closest('.home-environment')),
  ).toBe(true);
  await expect(page.locator('[data-scene-wipe]')).toBeHidden();
});

test('gallery category, navigation and history restore film and scene', async ({ page }) => {
  await visit(page);
  await selectFilm(page);
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'tree');
  await position(page, 'gallery', 0.5);
  await chooseCollection(page, 'articles');
  await page.waitForTimeout(350);
  const y = await page.evaluate(() => scrollY);
  await page.locator('#articles a[href="/en/articles/fixture-writing-2"]').focus();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/en\/articles\/fixture-writing-2$/);
  await expect(page.locator('.navigation-cover')).toHaveCount(0);
  await page.goBack({ waitUntil: 'networkidle' });
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'tree');
  await expect(page.locator('input[value="articles"]')).toBeChecked();
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(y);
  await expect(page.locator('[data-home-loader]')).toBeHidden();
  await page.goForward({ waitUntil: 'networkidle' });
  await expect(page.locator('.navigation-cover')).toHaveCount(0);
});
