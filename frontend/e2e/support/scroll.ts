import type { Page } from '@playwright/test';

/** Distinct physical notches are sampled after the native scroll/glide has settled. */
export async function settleScroll(page: Page): Promise<void> {
  await page.waitForTimeout(200);
  await page.evaluate(
    () =>
      new Promise<void>((resolve) => {
        let previous = scrollY,
          still = 0;
        function frame() {
          still = previous === scrollY ? still + 1 : 0;
          previous = scrollY;
          if (still >= 8) resolve();
          else requestAnimationFrame(frame);
        }
        requestAnimationFrame(frame);
      }),
  );
}
