import { expect, type Page } from '@playwright/test';
import { homepageReady } from './homepage';

export async function visit(page: Page) {
  await page.goto('/', { waitUntil: 'networkidle' });
  await homepageReady(page);
}

export async function position(page: Page, scene: string, p: number) {
  await page.locator(`[data-scene="${scene}"]`).evaluate((node, p) => {
    const entry =
      Number.parseFloat((node as HTMLElement).style.getPropertyValue('--gallery-entry-distance')) ||
      0;
    const rect = node.getBoundingClientRect();
    scrollTo(0, rect.top + scrollY + entry + (rect.height - entry) * p);
  }, p);
}

export async function writingPosition(page: Page, scene: string, offset: number) {
  await page
    .locator(`[data-scene="${scene}"]`)
    .evaluate(
      (node, offset) => scrollTo(0, Math.ceil(node.getBoundingClientRect().top + scrollY + offset)),
      offset,
    );
  await settlePresentation(page);
}

/** Wait for cinematic catch-up at the measured native position, not a guessed delay. */
export async function settlePresentation(page: Page) {
  await expect
    .poll(() =>
      page.evaluate(() => {
        const root = document.querySelector<HTMLElement>('[data-home-v2]');
        if (root?.dataset.enhanced !== 'true' || root.dataset.reduced === 'true') return 0;
        const region = [...root.querySelectorAll<HTMLElement>('[data-scene]')].find((node) => {
          const box = node.getBoundingClientRect();
          return box.top <= 0 && box.bottom > 0;
        });
        if (!region) return Infinity;
        const box = region.getBoundingClientRect();
        const face = region.querySelector<HTMLElement>('.scene-face')!;
        const actual = Number.parseFloat(face.style.getPropertyValue('--local-progress'));
        return face.dataset.visible === 'true' && Number.isFinite(actual)
          ? Math.abs(actual - Math.max(0, Math.min(1, -box.top / box.height)))
          : Infinity;
      }),
    )
    .toBeLessThan(0.000001);
}

export async function selectFilm(page: Page) {
  await page.locator('[data-home-v2]').evaluate((node) => node.focus({ preventScroll: true }));
  await page.keyboard.press('f');
}

export async function presented(page: Page) {
  const expected = await page.evaluate(() => {
    const hero = document.querySelector('[data-scene="hero"]')!.getBoundingClientRect();
    const mark = document.querySelector('[data-scene="mark"]')!.getBoundingClientRect();
    const start = hero.top + scrollY,
      end = mark.top + scrollY + mark.height * 0.98;
    const index = Math.round(Math.max(0, Math.min(1, (scrollY - start) / (end - start))) * 191);
    const film = document.querySelector<HTMLElement>('[data-home-v2]')!.dataset.film;
    return `${film === 'tree' ? 'Tree' : 'Robotic arm'} · ${String(index + 1).padStart(3, '0')} / 192`;
  });
  // A valid old frame is not readiness for a newly requested scroll position.
  await expect(page.locator('[data-film-status]')).toHaveText(expected);
}
