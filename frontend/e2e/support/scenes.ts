import { expect, type Page } from '@playwright/test';
import { homepageReady } from './homepage';

export async function visit(page: Page) {
  await page.goto('/', { waitUntil: 'networkidle' });
  await homepageReady(page);
}

export async function position(page: Page, scene: string, p: number) {
  await page.locator(`[data-scene="${scene}"]`).evaluate(async (node, p) => {
    const entry =
      Number.parseFloat((node as HTMLElement).style.getPropertyValue('--gallery-entry-distance')) ||
      0;
    const rect = node.getBoundingClientRect();
    scrollTo({
      top: rect.top + scrollY + entry + (rect.height - entry) * p,
      behavior: 'instant',
    });
    await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
  }, p);
}

export async function writingPosition(page: Page, scene: string, offset: number) {
  await page.locator(`[data-scene="${scene}"]`).evaluate(async (node, offset) => {
    scrollTo({
      top: Math.ceil(node.getBoundingClientRect().top + scrollY + offset),
      behavior: 'instant',
    });
    await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
  }, offset);
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
