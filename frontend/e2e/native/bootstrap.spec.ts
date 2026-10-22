import { expect, test } from '../support/test';
import { startOnFilm, openHomepage } from '../support/homepage';

test('the checked artifact boots production assets and exposes unique semantic chapters', async ({
  page,
}) => {
  await startOnFilm(page);
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await openHomepage(page);
  const response = await page.request.get('/');
  expect(response.headers()['content-security-policy']).toContain("script-src 'self'");
  for (const id of ['identity', 'work', 'writing', 'contact'])
    await expect(page.locator(`#${id}`)).toHaveCount(1);
  await expect(page.locator('.hero-statement')).toHaveText('Clarity isengineered');
  expect(
    await page.locator('.hero-statement').evaluate((n) => getComputedStyle(n).fontFamily),
  ).not.toBe('');
  const assets = await page.evaluate(() =>
    [...document.querySelectorAll<HTMLScriptElement>('script[src]')].map((s) => s.src),
  );
  expect(assets.length).toBeGreaterThan(0);
  expect(assets.every((s) => new URL(s).origin === new URL(page.url()).origin)).toBe(true);
  expect(errors).toEqual([]);
});
