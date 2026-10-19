import { expect, test } from '@playwright/test';
import { openHomepage } from '../support/homepage';

test('destruction releases the active controllers and restores usable semantic content', async ({
  page,
}) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await openHomepage(page);
  await page
    .locator('.home-nav a[href="#work"]')
    .evaluate((node) => (node as HTMLAnchorElement).click());
  await page.mouse.move(20, 650);
  for (let i = 0; i < 3; i++) await page.mouse.wheel(0, 120);
  await expect(page.locator('.collection-tuner')).toBeVisible();
  await page.evaluate(() =>
    dispatchEvent(new PageTransitionEvent('pagehide', { persisted: false })),
  );
  await expect(page.locator('[data-home-v2]')).not.toHaveAttribute('data-enhanced');
  await expect(page.locator('[data-home-v2]')).not.toHaveAttribute('data-ready');
  await expect(page.locator('.gallery-instrument')).toBeHidden();
  await expect(page.locator('video')).toHaveCount(0);
  await expect(
    page.locator('.home-pointer,.rolling-letters,.footer-rise,[data-identity-slot]'),
  ).toHaveCount(0);
  for (const node of await page.locator('.gallery-writing-rise,.collection-tuner').all()) {
    expect(await node.evaluate((element) => (element as HTMLElement).style.transform)).toBe('');
  }
  for (const node of await page.locator('.scene-face,.gallery-category').all()) {
    expect(await node.evaluate((element) => (element as HTMLElement).inert)).toBe(false);
  }
  await expect(page.locator('.gallery-category')).toHaveCount(4);
  const writes = await page.locator('[data-home-v2]').evaluate(
    (root) =>
      new Promise<number>((resolve) => {
        let count = 0;
        const observer = new MutationObserver((records) => (count += records.length));
        observer.observe(root, { attributes: true, subtree: true });
        setTimeout(() => {
          observer.disconnect();
          resolve(count);
        }, 300);
      }),
  );
  expect(writes).toBe(0);
  expect(errors).toEqual([]);
});
