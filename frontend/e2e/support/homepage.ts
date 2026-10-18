import { expect, type Page } from '@playwright/test';

export async function homepageReady(page: Page): Promise<void> {
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
  await expect(page.locator('[data-home-loader]')).toBeHidden();
}

export async function openHomepage(page: Page): Promise<void> {
  await page.goto('/');
  await homepageReady(page);
}
