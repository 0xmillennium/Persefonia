import { expect, type Page } from '@playwright/test';

export async function homepageReady(page: Page): Promise<void> {
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
  await expect(page.locator('[data-home-loader]')).toBeHidden();
}

export async function openHomepage(page: Page): Promise<void> {
  await page.goto('/');
  await homepageReady(page);
}

/** A source-specific regression must select its fixture explicitly, independent of the production default. */
export async function startOnFilm(
  page: Page,
  film: 'robotic-arm' | 'tree' | 'waterfall' = 'robotic-arm',
) {
  await page.addInitScript((film) => {
    if (!history.state?.homepageV2?.film)
      history.replaceState(
        { ...history.state, homepageV2: { ...history.state?.homepageV2, film } },
        '',
      );
  }, film);
}
