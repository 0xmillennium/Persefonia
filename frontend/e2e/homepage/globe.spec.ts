import { expect, test } from '../support/test';
import { visit, position } from '../support/scenes';

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
