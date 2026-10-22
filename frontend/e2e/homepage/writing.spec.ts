import { startOnFilm } from '../support/homepage';
import { expect, test, type Page } from '../support/test';
import { visit, position, writingPosition, presented } from '../support/scenes';
import { difference } from '../support/pixels';

test.beforeEach(async ({ page }) => startOnFilm(page));

async function decodedFilm(page: Page) {
  const png = await page.evaluate(() => {
    const film = document.querySelector<HTMLElement>('[data-home-v2]')!.dataset.film;
    const video = [...document.querySelectorAll<HTMLVideoElement>('video.film-presentation')].find(
      (v) => v.currentSrc.includes(`/${film}.mp4`) && v.readyState >= 2 && !v.seeking,
    );
    if (!video) throw new Error('The selected film has no decoded native frame');
    const canvas = document.createElement('canvas');
    canvas.width = 160;
    canvas.height = 90;
    canvas.getContext('2d')!.drawImage(video, 0, 0, canvas.width, canvas.height);
    return canvas.toDataURL('image/png').split(',')[1]!;
  });
  return Buffer.from(png, 'base64');
}

test('second statement resolves readable phrase groups and fades complete phrases on exit', async ({
  page,
}, info) => {
  await visit(page);
  await expect(page.locator('.statement-word-text')).toHaveText([
    'Curious',
    'by',
    'nature',
    'Engineering',
    'by',
    'choice',
  ]);
  const reading = () =>
    page.locator('.statement-word').evaluateAll((nodes) =>
      nodes.map((node) => {
        const text = node.querySelector<HTMLElement>('.statement-word-text')!;
        return {
          opacity: Number(getComputedStyle(node).opacity),
          filter: (node as HTMLElement).style.filter,
          y: new DOMMatrixReadOnly(getComputedStyle(text).transform).m42,
          hidden: text.style.opacity,
        };
      }),
    );
  await writingPosition(page, 'statements', 420 * 0.18);
  const partial = await reading();
  for (const word of partial) expect(word.opacity).toBeGreaterThanOrEqual(0.7);
  expect(partial[0]).toEqual(partial[2]);
  expect(partial[3]).toEqual(partial[5]);
  expect(partial[0]!.opacity).toBeGreaterThan(partial[5]!.opacity);
  await page.screenshot({ path: info.outputPath('second-writing-readable-entry.png') });
  await page.waitForTimeout(250);
  expect(await reading()).toEqual(partial);
  await writingPosition(page, 'statements', 421);
  await expect
    .poll(async () =>
      (await reading()).every((w) => w.opacity === 1 && w.filter === '' && w.y === 0),
    )
    .toBe(true);
  await writingPosition(page, 'statements', 420 * 0.18);
  expect(await reading()).toEqual(partial);
  await writingPosition(page, 'statements', 1140 + 420 * 0.64);
  const exit = await reading();
  expect(exit.every((w) => w.opacity > 0 && w.opacity < 1 && w.y < 0 && w.hidden === '')).toBe(
    true,
  );
  expect(exit[0]).toEqual(exit[2]);
  await expect(page.locator('.statement-word-slot').first()).toHaveCSS('overflow', 'visible');
  await page.screenshot({ path: info.outputPath('second-writing-composed-exit.png') });
  await page.waitForTimeout(250);
  expect(await reading()).toEqual(exit);
  await writingPosition(page, 'statements', 1561);
  await expect
    .poll(async () => (await reading()).every((w) => w.opacity === 0 && w.hidden === '0'))
    .toBe(true);
  await writingPosition(page, 'statements', 1140 + 420 * 0.64);
  expect(await reading()).toEqual(exit);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect
    .poll(async () =>
      (await reading()).every((w) => w.opacity === 1 && w.y === 0 && w.filter === ''),
    )
    .toBe(true);
});

test('first writing preserves whole semantic lines at stopped and reversed poses', async ({
  page,
}, info) => {
  await visit(page);
  const rows = page.locator('.hero-writing-text');
  await expect(rows).toHaveText(['Clarity is', 'engineered']);
  await expect(rows.last()).toHaveCSS('text-transform', 'uppercase');
  await expect(rows.last()).toHaveCSS('font-weight', '650');
  await expect(rows.last()).toHaveCSS('font-style', 'normal');
  await expect(rows.last()).toHaveCSS('font-variation-settings', '"wdth" 85, "wght" 650');
  await expect(page.locator('.hero-writing-visual')).toHaveCount(0);
  const reading = () =>
    rows.evaluateAll((nodes) =>
      nodes.map((n) => ({
        opacity: Number(getComputedStyle(n).opacity),
        y: new DOMMatrixReadOnly(getComputedStyle(n).transform).m42,
        text: n.textContent,
        height: n.getBoundingClientRect().height,
      })),
    );
  await position(page, 'hero', 0);
  await expect
    .poll(async () => (await reading()).every((r) => r.opacity === 1 && r.y === 0))
    .toBe(true);
  await page.screenshot({ path: info.outputPath('writing-open.png') });
  await writingPosition(page, 'hero', 420 * 0.35);
  const partial = await reading();
  for (const row of partial) {
    expect(row.opacity).toBeGreaterThan(0.7);
    expect(row.opacity).toBeLessThan(1);
    expect(row.y).toBeLessThan(0);
    expect(Math.abs(row.y)).toBeLessThan(row.height * 0.15);
  }
  await page.waitForTimeout(250);
  expect(await reading()).toEqual(partial);
  await page.screenshot({ path: info.outputPath('writing-composed-departure.png') });
  await writingPosition(page, 'hero', 420 * 0.8);
  expect((await reading())[0]!.opacity).toBeLessThan(partial[0]!.opacity);
  await writingPosition(page, 'hero', 420 * 0.35);
  expect(await reading()).toEqual(partial);
  await writingPosition(page, 'hero', 421);
  await expect.poll(async () => (await reading()).every((r) => r.opacity === 0)).toBe(true);
  await position(page, 'hero', 0);
  await expect
    .poll(async () => (await reading()).every((r) => r.opacity === 1 && r.y === 0))
    .toBe(true);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await position(page, 'hero', 0.25);
  await expect
    .poll(async () => (await reading()).every((r) => r.opacity === 1 && r.y === 0))
    .toBe(true);
});

test('later writing uses upward row exits while the film stays at its original scale', async ({
  page,
}, info) => {
  test.skip(
    info.project.name === 'tablet-834',
    'Desktop and portrait exercise the two compositions.',
  );
  await page.addInitScript(() => history.replaceState({ homepageV2: { film: 'tree' } }, ''));
  await visit(page);
  const third = page.locator('.capabilities li>span'),
    fourth = page.locator('.brand-statement>span>span');
  const y = (lines: typeof third, index = 0) =>
    lines
      .nth(index)
      .evaluate((node) => new DOMMatrixReadOnly(getComputedStyle(node).transform).m42);
  await writingPosition(page, 'capabilities', 420 * 0.1);
  await expect.poll(() => y(third)).toBeGreaterThan(0);
  for (let i = 0; i < 4; i++) {
    expect(await y(third, i)).toBeGreaterThan(0);
    expect(
      await third.nth(i).evaluate((n) => Number(getComputedStyle(n).opacity)),
    ).toBeGreaterThanOrEqual(0.7);
  }
  await writingPosition(page, 'capabilities', 780);
  await expect.poll(() => y(third, 3)).toBe(0);
  await presented(page);
  const thirdFrame = await page.locator('[data-film-status]').textContent();
  expect(thirdFrame).not.toContain('192 / 192');
  const bounds = await page.locator('[data-film-canvas]').boundingBox();
  // Inspect the complete decoded subject; a small sky crop can legitimately stay identical.
  const filmBefore = await decodedFilm(page);
  await writingPosition(page, 'capabilities', 1140 + 420 * 0.6);
  await expect.poll(() => y(third)).toBeLessThan(0);
  await writingPosition(page, 'mark', 420 * 0.2);
  await expect.poll(() => y(fourth)).toBeGreaterThan(0);
  await position(page, 'mark', 0.5);
  await expect.poll(() => y(fourth, 1)).toBe(0);
  await expect
    .poll(() =>
      page
        .locator('[data-identity-slot]')
        .evaluateAll((nodes) => nodes.filter((node) => node.hasAttribute('clip-path')).length),
    )
    .toBe(0);
  await presented(page);
  expect(await page.locator('[data-film-canvas]').boundingBox()).toEqual(bounds);
  expect(difference(filmBefore, await decodedFilm(page)).changed).toBeGreaterThan(0);
  expect(await page.locator('[data-film-status]').textContent()).not.toBe(thirdFrame);
  await page.screenshot({ path: info.outputPath('fourth-writing.png') });
  await writingPosition(page, 'mark', 1140 + 420 * 0.8);
  await expect.poll(() => y(fourth)).toBeLessThan(0);
  await expect
    .poll(() =>
      page
        .locator('[data-identity-slot] > g')
        .first()
        .evaluate((node) => new DOMMatrixReadOnly(getComputedStyle(node).transform).m42),
    )
    .toBeGreaterThan(0);
  await page.screenshot({ path: info.outputPath('fourth-writing-exit.png') });
  await position(page, 'mark', 0.5);
  await expect.poll(() => y(fourth)).toBe(0);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect.poll(() => y(third)).toBe(0);
  await expect
    .poll(() => fourth.first().evaluate((node) => getComputedStyle(node).opacity))
    .toBe('1');
  await expect(page.locator('[data-scene-wipe]')).toBeHidden();
  await expect
    .poll(() =>
      page
        .locator('[data-identity-slot]')
        .evaluateAll((nodes) => nodes.filter((node) => node.hasAttribute('clip-path')).length),
    )
    .toBe(0);
});
