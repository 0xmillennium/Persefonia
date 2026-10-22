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

/** Set up wheel entrance tests independently of semantic links' first reading stop. */
export async function scrollToGalleryPreroll(page: Page): Promise<void> {
  await page.locator('[data-scene="gallery"]').evaluate((node) => {
    const bounds = node.getBoundingClientRect();
    const entry =
      parseFloat((node as HTMLElement).style.getPropertyValue('--gallery-entry-distance')) || 0;
    const count = Math.max(
      1,
      node.querySelectorAll('.gallery-category:not([hidden]) .gallery-card').length,
    );
    const range = bounds.height - entry;
    scrollTo({
      top: Math.round(bounds.top + scrollY + entry + range * (0.12 + 0.7 / (count + 7))),
      behavior: 'instant',
    });
  });
}
