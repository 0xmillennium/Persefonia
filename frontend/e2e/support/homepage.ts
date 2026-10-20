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
    const select = () => {
      if (!history.state?.homepageV2?.film)
        history.replaceState(
          { ...history.state, homepageV2: { ...history.state?.homepageV2, film } },
          '',
        );
    };
    // Firefox can replace the initial history entry after document creation.
    // Select after parsing, before the deferred application modules execute.
    if (document.readyState === 'loading')
      document.addEventListener('readystatechange', select, { once: true });
    else select();
  }, film);
}
