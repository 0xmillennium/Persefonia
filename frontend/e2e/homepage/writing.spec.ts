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

async function freeze(page: Page) {
  await page.clock.install({ time: new Date('2026-01-01T00:00:00Z') });
  await page.clock.pauseAt(new Date('2026-01-01T00:00:01Z'));
}
async function pose(page: Page, scene: string, offset: number) {
  await page.locator(`[data-scene="${scene}"]`).evaluate((n, offset) => {
    scrollTo(0, Math.ceil(n.getBoundingClientRect().top + scrollY + offset));
    dispatchEvent(new Event('scroll'));
  }, offset);
  await page.clock.runFor(300);
}

test('second statement restores word goo and staggered clipped exits with finite entrance', async ({
  page,
}, info) => {
  await visit(page);
  await freeze(page);
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
        const id = (node as HTMLElement).style.filter.match(/#([\w-]+)/)?.[1];
        return {
          filter: (node as HTMLElement).style.filter,
          blur: id
            ? Number(
                document
                  .getElementById(id)
                  ?.querySelector('feGaussianBlur')
                  ?.getAttribute('stdDeviation'),
              )
            : 0,
          matrix: id
            ? document.getElementById(id)?.querySelector('feColorMatrix')?.getAttribute('values')
            : null,
          y: new DOMMatrixReadOnly(getComputedStyle(text).transform).m42,
          height: text.getBoundingClientRect().height,
          hidden: text.style.opacity,
        };
      }),
    );
  await pose(page, 'statements', 75);
  const partial = await reading();
  expect(partial[5]!.filter).toContain('url(');
  expect(partial[5]!.blur).toBeGreaterThan(1);
  expect(partial[5]!.matrix).toContain('12.5 -1.5');
  expect(partial[0]!.blur).toBeLessThan(partial[5]!.blur);
  await page.screenshot({ path: info.outputPath('second-goo-entry.png') });
  await page.clock.runFor(1400);
  expect((await reading()).every((w) => w.filter === '' && w.y === 0 && w.hidden === '')).toBe(
    true,
  );
  await pose(page, 'statements', 1140 + 420 * 0.64);
  const exit = await reading();
  expect(exit[0]!.y).toBeLessThan(-exit[0]!.height * 0.95);
  expect(exit[0]!.hidden).toBe('0');
  expect(exit[5]!.y).toBeGreaterThan(-exit[5]!.height * 0.1);
  expect(exit[5]!.y).toBeLessThan(0);
  await expect(page.locator('.statement-word-slot').first()).toHaveCSS('overflow', 'hidden');
  await page.clock.runFor(1400);
  expect(await reading()).toEqual(exit);
  await pose(page, 'statements', 1561);
  expect((await reading()).every((w) => w.hidden === '0')).toBe(true);
  await pose(page, 'statements', 1140 + 420 * 0.64);
  expect(await reading()).toEqual(exit);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  // CSS media emulation can land before the change listener's queued RAF.
  // Advance that owned frame until the application has reconciled the mode.
  await expect
    .poll(async () => {
      await page.clock.runFor(32);
      return page.locator('[data-home-v2]').getAttribute('data-reduced');
    })
    .toBe('true');
  expect((await reading()).every((w) => w.filter === '' && w.y === 0 && w.hidden === '')).toBe(
    true,
  );
});

test('first writing restores character pull, clipping and reversible high-travel exits', async ({
  page,
}, info) => {
  await visit(page);
  await freeze(page);
  const rows = page.locator('.hero-writing-text');
  await expect(rows).toHaveText(['Clarity is', 'engineered']);
  await expect(rows.last()).toHaveCSS('text-transform', 'uppercase');
  await expect(rows.last()).toHaveCSS('font-weight', '650');
  await expect(rows.last()).toHaveCSS('font-style', 'normal');
  await expect(rows.last()).toHaveCSS('font-variation-settings', '"wdth" 85, "wght" 650');
  await expect(page.locator('.hero-writing-visual')).toHaveCount(2);
  for (const visual of await page.locator('.hero-writing-visual').all())
    await expect(visual).toHaveAttribute('aria-hidden', 'true');
  await expect(page.locator('.hero-writing-row').first()).toHaveCSS('overflow', 'hidden');
  const reading = () =>
    page.locator('.hero-writing-visual').evaluateAll((nodes) =>
      nodes.map((node) =>
        [...node.children].map((glyph) => {
          const matrix = new DOMMatrixReadOnly(getComputedStyle(glyph).transform);
          return { x: matrix.m41, y: matrix.m42 };
        }),
      ),
    );
  await pose(page, 'hero', 147);
  const partial = await reading();
  await expect(page.getByRole('heading', { name: /^Clarity is engineered$/i })).toHaveCount(1);
  expect(partial[0]![0]!.y).toBeLessThan(partial[0]!.at(-1)!.y);
  expect(partial[0]![0]!.x).toBeGreaterThan(0);
  expect(partial[0]![0]!.y).toBeLessThan(partial[1]![0]!.y);
  await page.screenshot({ path: info.outputPath('hero-character-pull.png') });
  await page.clock.runFor(1200);
  expect(await reading()).toEqual(partial);
  await pose(page, 'hero', 336);
  expect((await reading())[0]![0]!.y).toBeLessThan(partial[0]![0]!.y);
  await pose(page, 'hero', 147);
  expect(await reading()).toEqual(partial);
  await pose(page, 'hero', 421);
  await expect(page.locator('.hero-writing-visual').first()).toBeHidden();
  expect(await rows.first().evaluate((n) => getComputedStyle(n).opacity)).toBe('0');
  await pose(page, 'hero', 0);
  expect(await rows.first().evaluate((n) => getComputedStyle(n).opacity)).toBe('1');
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.clock.runFor(100);
  expect(await rows.first().evaluate((n) => getComputedStyle(n).opacity)).toBe('1');
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
    ).toBeGreaterThanOrEqual(0);
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
