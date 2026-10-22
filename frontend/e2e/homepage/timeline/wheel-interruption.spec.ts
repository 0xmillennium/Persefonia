import { scrollToGalleryPreroll } from '../../support/gallery';
import { expect, test } from '../../support/test';

test('wheel input ends a gallery drag without a later release changing its destination', async ({
  page,
}) => {
  await page.goto('/');
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
  await expect(page.locator('[data-home-loader]')).toBeHidden();
  await page
    .locator('.home-nav a[href="#work"]')
    .evaluate((node) => (node as HTMLAnchorElement).click());
  const gallery = page.locator('[data-scene="gallery"]');
  const index = () =>
    gallery.evaluate((node) => Number((node as HTMLElement).dataset.galleryIndex));
  await expect.poll(index).toBe(0);
  await scrollToGalleryPreroll(page);
  await expect.poll(index).toBe(-3);
  await expect
    .poll(() => page.locator('.gallery-face').evaluate((node) => (node as HTMLElement).inert))
    .toBe(false);
  const box = (await page.locator('.gallery-space').boundingBox())!;
  const cover = (await page
    .locator('.gallery-category:not([hidden]) .gallery-card')
    .first()
    .locator('.gallery-cover')
    .boundingBox())!;
  const x = Math.max(box.x + 8, cover.x + cover.width / 2),
    yStart = cover.y + cover.height / 2;
  const spacing = await page
    .locator('.gallery-category:not([hidden]) .gallery-card')
    .evaluateAll((cards) =>
      Math.abs(
        new DOMMatrix(getComputedStyle(cards[1]!).transform).m41 -
          new DOMMatrix(getComputedStyle(cards[0]!).transform).m41,
      ),
    );
  await page.mouse.move(x, yStart);
  await expect
    .poll(() =>
      page.evaluate(({ x, y }) => !!document.elementFromPoint(x, y)?.closest('.gallery-space'), {
        x,
        y: yStart,
      }),
    )
    .toBe(true);
  await page.mouse.down();
  await page.mouse.move(x + spacing * 0.4, yStart, { steps: 5 });
  await expect(gallery).toHaveAttribute('data-dragging', 'true');
  await page.mouse.wheel(0, 120);
  await expect(gallery).not.toHaveAttribute('data-dragging', 'true');
  await expect.poll(index).toBe(-2);
  const y = await page.evaluate(() => scrollY);
  await page.mouse.up();
  await page.waitForTimeout(400);
  expect(await page.evaluate(() => scrollY)).toBe(y);
  expect(await index()).toBe(-2);
  await page.mouse.wheel(0, -120);
  await expect.poll(index).toBe(-3);
});
