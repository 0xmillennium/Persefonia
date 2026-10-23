import { expect, test } from '../support/test';
import { startOnFilm } from '../support/homepage';

// Explicit multi-viewport diagnostic: real sphere/strip overlap has no pure-state equivalent.
test('globe alternatives keep their targets clear of footer links at narrow and short sizes', async ({
  page,
}) => {
  await startOnFilm(page, 'tree');
  for (const size of [
    { width: 320, height: 850 },
    { width: 390, height: 850 },
    { width: 850, height: 390 },
    { width: 640, height: 450 },
  ]) {
    await page.setViewportSize(size);
    await page.goto('/#contact');
    await expect(page.locator('[data-home-loader]')).toBeHidden();
    await expect(page.locator('.globe-controls,[data-background-motion]')).toHaveCount(0);
    const globe = (await page.locator('.location-globe').boundingBox())!;
    const strip = (await page.locator('.location-strip').boundingBox())!;
    // The interactive target is the visible sphere, not transparent canvas corners.
    const radius = Math.min(globe.width, globe.height) * 0.42;
    expect(globe.y + globe.height / 2 + radius + 8, JSON.stringify(size)).toBeLessThanOrEqual(
      strip.y,
    );
    expect(globe.x).toBeGreaterThanOrEqual(0);
    expect(globe.x + globe.width).toBeLessThanOrEqual(size.width);
  }
});
