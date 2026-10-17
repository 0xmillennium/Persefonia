import { expect, test } from '@playwright/test';
import { visit, position } from '../support/scenes';

test('globe retains real geography and exposes keyboard controls without triggering film hold', async ({
  page,
}) => {
  await visit(page);
  await position(page, 'location', 0.6);
  await expect(page.locator('.location-system')).toHaveAttribute('data-ready', 'true');
  await page.locator('#globe').focus();
  await page.keyboard.press('Home');
  await expect(page.locator('#location-panel')).toHaveAttribute('data-open', 'true');
  await page.keyboard.press('ArrowUp');
  await page.keyboard.press('Space');
  await page.locator('#globe').focus();
  await page.keyboard.press('ArrowRight');
  await expect(page.locator('[data-hold-countdown]')).toBeHidden();
});

test('pointer focus stays quiet and keyboard focus follows the visible globe', async ({
  page,
}, info) => {
  test.skip(!!info.project.use.isMobile, 'Mouse-to-keyboard modality; touch is covered separately');
  await visit(page);
  await position(page, 'location', 0.6);
  const globe = page.locator('#globe');
  const stage = page.locator('.location-globe');
  await expect(page.locator('.location-system')).toHaveAttribute('data-ready', 'true');
  const ring = () =>
    stage.evaluate((node) => {
      const style = getComputedStyle(node, '::after');
      return {
        display: style.display,
        width: parseFloat(style.width),
        height: parseFloat(style.height),
        radius: style.borderRadius,
      };
    });
  const bounds = (await globe.boundingBox())!;
  await page.mouse.click(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
  await expect(globe).toBeFocused();
  await expect(globe).toHaveCSS('outline-style', 'none');
  expect((await ring()).display).toBe('none');
  await page.keyboard.press('ArrowRight');
  expect(await ring()).toEqual({
    display: 'block',
    width: expect.any(Number),
    height: expect.any(Number),
    radius: '50%',
  });
  expect((await ring()).width).toBeCloseTo(Math.min(bounds.width, bounds.height) * 0.84 + 16, 1);
  expect((await ring()).height).toBe((await ring()).width);
  await page.keyboard.press('Tab');
  expect((await ring()).display).toBe('none');
  await page.keyboard.press('Shift+Tab');
  await expect(globe).toBeFocused();
  expect((await ring()).display).toBe('block');
  await page.screenshot({ path: info.outputPath('globe-keyboard-focus.png') });
  await page.emulateMedia({ forcedColors: 'active' });
  expect((await ring()).display).toBe('block');
  await page.screenshot({ path: info.outputPath('globe-forced-colors.png') });
});

test('globe draws the bounded land and ocean point sets and preserves framing after resize', async ({
  page,
}, info) => {
  await page.addInitScript(() => {
    const state = window as Window & { globePointCount?: number };
    const original = WebGL2RenderingContext.prototype.drawArrays;
    WebGL2RenderingContext.prototype.drawArrays = function (mode, first, count) {
      if (mode === this.POINTS) state.globePointCount = count;
      return original.call(this, mode, first, count);
    };
  });
  await visit(page);
  await position(page, 'location', 0.6);
  await expect(page.locator('.location-system')).toHaveAttribute('data-ready', 'true');
  await expect
    .poll(() =>
      page.evaluate(() => (window as Window & { globePointCount?: number }).globePointCount ?? 0),
    )
    .toBeGreaterThan(10_000);
  expect(
    await page.evaluate(() => (window as Window & { globePointCount?: number }).globePointCount),
  ).toBeLessThan(20_000);
  await page.mouse.move(0, 0);
  await page.waitForTimeout(3200); // Let the authored entrance complete before comparing the dots.
  await page.screenshot({ path: info.outputPath('globe-dots.png'), scale: 'css' });
  if (info.project.use.isMobile) return;
  await page.setViewportSize({ width: 1100, height: 680 });
  await position(page, 'location', 0.6);
  await page.locator('#globe').focus();
  await page.keyboard.press('ArrowRight');
  await expect
    .poll(() =>
      page.locator('.location-globe').evaluate((node) => {
        const rect = node.getBoundingClientRect();
        return Math.abs(
          parseFloat(getComputedStyle(node, '::after').width) -
            (Math.min(rect.width, rect.height) * 0.84 + 16),
        );
      }),
    )
    .toBeLessThan(1);
});

test('touch turns the globe without a focus box and yields vertical swipes to the page', async ({
  page,
}, info) => {
  test.skip(!info.project.use.isMobile, 'Real touch dispatch on the phone composition');
  await visit(page);
  await position(page, 'location', 0.6);
  const stage = page.locator('.location-system');
  await expect(stage).toHaveAttribute('data-ready', 'true');
  const bounds = (await page.locator('#globe').boundingBox())!;
  const x = bounds.x + bounds.width / 2,
    y = bounds.y + bounds.height / 2;
  const touch = await page.context().newCDPSession(page);
  const contact = async (type: 'touchStart' | 'touchMove' | 'touchEnd', px = x, py = y) =>
    touch.send('Input.dispatchTouchEvent', {
      type,
      touchPoints: type === 'touchEnd' ? [] : [{ x: px, y: py }],
    });
  try {
    await contact('touchStart');
    await expect(stage).toHaveAttribute('data-pressed', 'true');
    await contact('touchMove', x + 45);
    await contact('touchEnd');
    await expect(stage).toHaveAttribute('data-pressed', 'false');
    expect(
      await page.locator('.location-globe').evaluate((n) => getComputedStyle(n, '::after').display),
    ).toBe('none');
    const before = await page.evaluate(() => scrollY);
    await contact('touchStart');
    await contact('touchMove', x, y + 70);
    await contact('touchMove', x, y + 120);
    await contact('touchEnd');
    await expect.poll(() => page.evaluate(() => scrollY)).toBeLessThan(before - 20);
    await expect(page.locator('[data-hold-countdown]')).toBeHidden();
  } finally {
    await touch.detach();
  }
});

test('graphics interruption retains location content and restoration and teardown release focus styling', async ({
  page,
}) => {
  await visit(page);
  await position(page, 'location', 0.6);
  const stage = page.locator('.location-system');
  await expect(stage).toHaveAttribute('data-ready', 'true');
  const supported = await page.locator('#globe').evaluate((node) => {
    const state = window as Window & { globeRecovery?: WEBGL_lose_context };
    state.globeRecovery =
      (node as HTMLCanvasElement).getContext('webgl2')?.getExtension('WEBGL_lose_context') ??
      undefined;
    state.globeRecovery?.loseContext();
    return !!state.globeRecovery;
  });
  test.skip(!supported, 'Context-loss extension unavailable in this browser');
  await expect(stage).toHaveAttribute('data-ready', 'false');
  await expect(page.locator('#fallback')).toBeVisible();
  await expect(page.locator('#globe')).toHaveAttribute('tabindex', '-1');
  await expect(page.locator('#fallback')).toContainText('Istanbul, Türkiye');
  expect(
    await page.locator('.location-globe').evaluate((n) => getComputedStyle(n, '::after').display),
  ).toBe('none');
  await page.waitForTimeout(200); // Browser context restoration has a native cooldown.
  await page.evaluate(() =>
    (window as Window & { globeRecovery?: WEBGL_lose_context }).globeRecovery?.restoreContext(),
  );
  await expect(stage).toHaveAttribute('data-ready', 'true');
  await expect(page.locator('#fallback')).toBeHidden();
  await page.evaluate(() =>
    dispatchEvent(new PageTransitionEvent('pagehide', { persisted: false })),
  );
  expect(
    await stage.evaluate((n) => (n as HTMLElement).style.getPropertyValue('--globe-focus-size')),
  ).toBe('');
  await expect(page.locator('#globe')).not.toHaveAttribute('data-focus-input');
  await expect(page.locator('#fallback')).toBeVisible();
  await expect(page.locator('#globe')).toHaveAttribute('tabindex', '-1');
});
