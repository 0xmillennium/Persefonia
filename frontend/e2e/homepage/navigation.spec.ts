import { expect, test } from '@playwright/test';
import { visit, position } from '../support/scenes';
import { settleScroll } from '../support/scroll';

test('ordinary navigation paints styled source and destination covers before history bypass', async ({
  page,
}, info) => {
  await visit(page);
  await expect(page.locator('[data-home-loader]')).toBeHidden();
  await position(page, 'gallery', 0.4);
  await expect
    .poll(() => page.locator('.gallery-face').evaluate((node) => (node as HTMLElement).inert))
    .toBe(false);
  await settleScroll(page);
  await expect(page.locator('[data-scene="gallery"]')).toHaveAttribute('data-gallery-index', '0');
  const destination = await page.locator('#work h3 a').first().getAttribute('href');
  const target = (await page.locator('#work h3 a').first().boundingBox())!;
  const point = { x: target.x + target.width / 2, y: target.y + target.height / 2 };
  await expect
    .poll(() =>
      page.evaluate(
        ({ x, y }) => document.elementFromPoint(x, y)?.closest('a')?.getAttribute('href'),
        point,
      ),
    )
    .toBe(destination);
  await page.evaluate(() => {
    const state = window as Window & {
      sourceRaf: typeof requestAnimationFrame;
      sourceCallbacks: FrameRequestCallback[];
    };
    state.sourceRaf = requestAnimationFrame;
    state.sourceCallbacks = [];
    window.requestAnimationFrame = (callback) => {
      state.sourceCallbacks.push(callback);
      return 0;
    };
  });
  // Hit the visible cover directly: scrollIntoView on a transformed mobile card
  // would move the scene while its animation clock is deliberately frozen.
  await page.mouse.click(point.x, point.y);
  const cover = page.locator('.navigation-cover');
  await expect(cover).toBeVisible();
  await expect
    .poll(() =>
      cover.evaluate((node) => ({
        position: getComputedStyle(node).position,
        width: node.getBoundingClientRect().width,
        height: node.getBoundingClientRect().height,
      })),
    )
    .toEqual({
      position: 'fixed',
      ...{ width: page.viewportSize()!.width, height: page.viewportSize()!.height },
    });
  await page.screenshot({ path: info.outputPath('styled-source-cover.png') });
  await page.evaluate(() => {
    const state = window as Window & {
      sourceRaf: typeof requestAnimationFrame;
      sourceCallbacks: FrameRequestCallback[];
    };
    window.requestAnimationFrame = state.sourceRaf;
    state.sourceCallbacks.forEach((callback) => requestAnimationFrame(callback));
  });
  await page.waitForURL(`**${destination}`, { waitUntil: 'domcontentloaded' });
  await expect(page.locator('.navigation-cover')).toHaveCSS('position', 'fixed');
  await expect(page.locator('.navigation-cover')).toHaveCount(0);
  await page.goBack({ waitUntil: 'networkidle' });
  await expect(page.locator('[data-home-loader]')).toBeHidden();
  await expect(page.locator('.navigation-cover')).toHaveCount(0);
  await page.goForward({ waitUntil: 'networkidle' });
  await expect(page.locator('.navigation-cover')).toHaveCount(0);
});
