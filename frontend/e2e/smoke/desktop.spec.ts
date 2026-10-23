import { readFileSync } from 'node:fs';
import { expect, test } from '../support/test';
import { startOnFilm, openHomepage } from '../support/homepage';

test('keyboard hash navigation transfers focus', async ({ page }) => {
  await openHomepage(page);
  const link = page.locator('.home-nav a[href="#work"]');
  await link.focus();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/#work$/);
  await expect(page.locator('[data-scene="gallery"]')).toHaveAttribute('data-category', 'projects');
  // The native focus event and inert filtering must cooperate with hash navigation.
  const tuner = page.locator('.collection-tuner');
  await tuner.focus();
  await page.keyboard.press('Tab');
  await expect(page.locator('#work h3 a').first()).toBeFocused();
});

test('native film presents and seeks a decoded frame', async ({ page }) => {
  await startOnFilm(page);
  await openHomepage(page);
  const video = page.locator('video[src*="/robotic-arm.mp4"]');
  await expect(video).toBeVisible();
  await page.evaluate(() => {
    const hero = document.querySelector('[data-scene="hero"]')!.getBoundingClientRect();
    const mark = document.querySelector('[data-scene="mark"]')!.getBoundingClientRect();
    const start = hero.top + scrollY,
      end = mark.top + scrollY + mark.height * 0.98;
    scrollTo(0, Math.round(start + ((end - start) * 20) / 191));
  });
  await expect(page.locator('[data-film-status]')).toHaveText('Robotic arm · 021 / 192');
  const sample = await video.evaluate((node) => {
    const v = node as HTMLVideoElement;
    const canvas = document.createElement('canvas');
    canvas.width = 32;
    canvas.height = 32;
    const ctx = canvas.getContext('2d')!;
    ctx.drawImage(v, 0, 0, 32, 32);
    return {
      width: v.videoWidth,
      paused: v.paused,
      seeking: v.seeking,
      time: v.currentTime,
      painted: ctx.getImageData(0, 0, 32, 32).data.some((value, i) => i % 4 !== 3 && value > 0),
    };
  });
  expect(sample.width).toBeGreaterThan(0);
  expect(sample.paused).toBe(true);
  expect(sample.seeking).toBe(false);
  expect(sample.time).toBeGreaterThanOrEqual(20 / 30 - 0.001);
  expect(sample.time).toBeLessThan(21 / 30);
  expect(sample.painted).toBe(true);
});

test('isolated diagrams render under the production CSP', async ({ page }) => {
  const manifest = JSON.parse(
    readFileSync(
      new URL(
        '../../../app/build/generated/vite/static/assets/.vite/manifest.json',
        import.meta.url,
      ),
      'utf8',
    ),
  );
  const entry = `/assets/${manifest['src/mermaid-loader.ts'].file}`;
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.goto('/en/projects');
  await page.evaluate(async (entry) => {
    for (const code of [
      'graph TD; A[Client] --> B[Server]',
      'sequenceDiagram\nClient->>Server: Request\nServer-->>Client: Response',
    ]) {
      const diagram = document.createElement('pre');
      diagram.className = 'mermaid';
      diagram.textContent = code;
      document.body.append(diagram);
    }
    await import(entry);
  }, entry);
  await expect(page.locator('.mermaid svg')).toHaveCount(2);
  await expect(page.locator('.mermaid').first()).toContainText('Client');
  await expect(page.locator('.mermaid').nth(1)).toContainText('Response');
  expect(errors).toEqual([]);
});
