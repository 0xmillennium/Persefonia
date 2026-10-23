import { expect, test, type Page } from '../support/test';
import { startOnFilm, openHomepage } from '../support/homepage';
async function touch(page: Page) {
  const session = await page.context().newCDPSession(page);
  return {
    send: (type: 'touchStart' | 'touchMove' | 'touchEnd' | 'touchCancel', x = 0, y = 0) =>
      session.send('Input.dispatchTouchEvent', {
        type,
        touchPoints: type === 'touchEnd' || type === 'touchCancel' ? [] : [{ x, y, id: 1 }],
      }),
    detach: () => session.detach(),
  };
}

test('native pan yields while a settled hold changes film once', async ({ page }) => {
  await startOnFilm(page);
  await openHomepage(page);
  const finger = await touch(page);
  try {
    await finger.send('touchStart', 100, 620);
    for (let i = 1; i <= 6; i++) await finger.send('touchMove', 100 + i, 620 - i * 28);
    await finger.send('touchEnd');
    await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(30);
    await expect(page.locator('[data-hold-countdown]')).toBeHidden();
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'robotic-arm');
    await page.evaluate(() => scrollTo(0, 0));
    await expect.poll(() => page.evaluate(() => scrollY)).toBe(0);
    await finger.send('touchStart', 180, 400);
    await expect(page.locator('[data-hold-countdown]')).toBeVisible();
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'tree', {
      timeout: 6000,
    });
    await page.waitForTimeout(350);
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'tree');
    expect(await page.evaluate(() => scrollY)).toBe(0);
    await finger.send('touchEnd');
  } finally {
    await finger.detach();
  }
});

test('tuner horizontal touch stays local and vertical touch scrolls', async ({ page }) => {
  await openHomepage(page);
  const menu = page.locator('#home-mobile-menu');
  await page.getByRole('button', { name: 'Open menu', exact: true }).click();
  await expect(menu).toBeVisible();
  await menu.locator('a[href="#work"]').click();
  await expect(menu).toBeHidden();
  await expect(page).toHaveURL(/#work$/);
  const root = page.locator('[data-scene="gallery"]');
  await expect(root).toHaveAttribute('data-gallery-index', '0');
  const tuner = page.locator('.collection-tuner');
  const box = (await tuner.boundingBox())!,
    x = box.x + box.width * 0.7,
    y = box.y + 28;
  const before = await page.evaluate(() => scrollY);
  const finger = await touch(page);
  try {
    await finger.send('touchStart', x, y);
    for (let i = 1; i <= 8; i++)
      await finger.send('touchMove', x - (box.width * 0.35 * i) / 8, y + 1);
    await page.waitForTimeout(130);
    await finger.send('touchEnd');
    await expect(root).toHaveAttribute('data-category', 'articles');
    expect(await page.evaluate(() => scrollY)).toBe(before);
    await finger.send('touchStart', x, y);
    for (let i = 1; i <= 6; i++) await finger.send('touchMove', x + 1, y - 20 * i);
    await finger.send('touchEnd');
    await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(before);
    await expect(root).toHaveAttribute('data-category', 'articles');
  } finally {
    await finger.detach();
  }
});
