import { expect, type Page } from '@playwright/test';

/** Select through the public tuner menu, retaining visibility and focus checks. */
export async function chooseCollection(page: Page, category: string): Promise<void> {
  const choice = page.locator(`input[name="gallery-category"][value="${category}"]`);
  if (await choice.isChecked()) return;
  const options = page.locator('.collection-options');
  if (await options.isHidden()) await page.locator('.collection-tuner').click();
  await choice.check();
  await expect(page.locator('[data-scene="gallery"]')).toHaveAttribute('data-category', category);
  await expect(options).toBeHidden();
  await expect(page.locator('.gallery-category:not([hidden])')).toHaveCount(1);
  await expect
    .poll(() =>
      page
        .locator('[data-scene="gallery"]')
        .evaluate((node) => Number((node as HTMLElement).dataset.galleryIndex)),
    )
    .toBe(0);
}
