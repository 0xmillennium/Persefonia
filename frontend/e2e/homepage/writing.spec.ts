import { expect, test } from '@playwright/test';
import { visit, position, writingPosition, presented } from '../support/scenes';
import { difference } from '../support/pixels';

test('second statement resolves from word blobs and exits through individual slits', async ({
  page,
}, info) => {
  await visit(page);
  await expect(page.locator('[data-home-loader]')).toBeHidden();
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
        const filter = (node as HTMLElement).style.filter;
        const id = filter.match(/#([\w-]+)/)?.[1];
        return {
          filter,
          opacity: (node as HTMLElement).style.opacity,
          blur: id
            ? Number(
                document
                  .getElementById(id)
                  ?.querySelector('feGaussianBlur')
                  ?.getAttribute('stdDeviation'),
              )
            : 0,
          transform: (node.querySelector('.statement-word-text') as HTMLElement).style.transform,
          hidden: (node.querySelector('.statement-word-text') as HTMLElement).style.opacity,
        };
      }),
    );
  await writingPosition(page, 'statements', -1);
  await expect
    .poll(async () =>
      (await reading()).every(
        (word) => word.filter.startsWith('url(') && word.opacity === '0.3' && word.blur > 0,
      ),
    )
    .toBe(true);
  await writingPosition(page, 'statements', 0);
  await expect(page.locator('.statement-face')).toBeVisible();
  await writingPosition(page, 'statements', 420 * 0.18);
  await expect
    .poll(async () => {
      const words = await reading();
      return words[0]!.blur < words[5]!.blur;
    })
    .toBe(true);
  const first = await reading();
  await page.screenshot({ path: info.outputPath('second-writing-entry.png') });
  await page.waitForTimeout(250);
  expect(await reading()).toEqual(first);
  await writingPosition(page, 'statements', 421);
  await expect
    .poll(async () =>
      (await reading()).every(
        (word) => word.filter === '' && word.opacity === '' && word.transform === '',
      ),
    )
    .toBe(true);
  await writingPosition(page, 'statements', 420 * 0.18);
  await expect.poll(reading).toEqual(first);
  await writingPosition(page, 'statements', 1140 + 420 * 0.64);
  await expect
    .poll(async () => {
      const words = await reading();
      return words[0]!.hidden === '0' && words[5]!.hidden !== '0';
    })
    .toBe(true);
  const exit = await reading();
  await expect(page.locator('.statement-word-slot').first()).toHaveCSS('overflow', 'hidden');
  await expect(page.locator('.statement-copy')).toHaveCSS('transform', 'none');
  await page.screenshot({ path: info.outputPath('second-writing-exit.png') });
  await page.waitForTimeout(250);
  expect(await reading()).toEqual(exit);
  await writingPosition(page, 'statements', 1561);
  await expect(page.locator('.statement-bottom')).toHaveCount(0);
  await expect
    .poll(async () =>
      (await reading()).every(
        (word) => word.hidden === '0' && word.transform === 'translateY(-100%)',
      ),
    )
    .toBe(true);
  await writingPosition(page, 'statements', 1140 + 420 * 0.64);
  await expect.poll(reading).toEqual(exit);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect
    .poll(async () =>
      (await reading()).every(
        (word) =>
          word.filter === '' && word.opacity === '' && word.hidden === '' && word.transform === '',
      ),
    )
    .toBe(true);
});

test('first writing enters and exits through per-row slits from left to right', async ({
  page,
}, info) => {
  await visit(page);
  await expect(page.locator('[data-home-loader]')).toBeHidden();
  await expect(page.locator('.hero-writing-text')).toHaveText(['Clarity is', 'engineered']);
  await expect(page.locator('.hero-writing-text').first()).toHaveCSS('text-transform', 'none');
  await expect(page.locator('.hero-writing-text').last()).toHaveCSS('text-transform', 'uppercase');
  for (const selector of ['.hero-writing-text', '.hero-writing-visual']) {
    await expect(page.locator(selector).last()).toHaveCSS('font-weight', '650');
    await expect(page.locator(selector).last()).toHaveCSS('font-style', 'normal');
    await expect(page.locator(selector).last()).toHaveCSS(
      'font-variation-settings',
      '"wdth" 85, "wght" 650',
    );
  }
  await expect(page.locator('.hero-writing-visual').last()).toHaveCSS(
    'text-transform',
    'uppercase',
  );
  const reading = () =>
    page.locator('.hero-writing-row').evaluateAll((nodes) =>
      nodes.map((node) => ({
        opacity: (node.querySelector('.hero-writing-text') as HTMLElement).style.opacity,
        hidden: (node.querySelector('.hero-writing-visual') as HTMLElement).hidden,
        transforms: [...node.querySelectorAll<HTMLElement>('.hero-writing-visual>span')].map(
          (glyph) => glyph.style.transform,
        ),
      })),
    );
  await position(page, 'hero', 0);
  await expect
    .poll(async () => (await reading()).every((row) => row.hidden && row.opacity === ''))
    .toBe(true);
  await page.screenshot({ path: info.outputPath('writing-open.png') });
  await writingPosition(page, 'hero', 420 * 0.35);
  await expect.poll(async () => (await reading()).every((row) => !row.hidden)).toBe(true);
  const partial = await reading();
  const vertical = (value: string) => Number(value.match(/,\s*(-?[\d.]+)px/)![1]);
  expect(vertical(partial[0]!.transforms[0]!)).toBeLessThan(
    vertical(partial[0]!.transforms.at(-1)!),
  );
  expect(vertical(partial[0]!.transforms[0]!)).toBeLessThan(vertical(partial[1]!.transforms[0]!));
  await page.screenshot({ path: info.outputPath('writing-pulled.png') });
  expect(await reading()).toEqual(partial);
  await writingPosition(page, 'hero', 420 * 0.8);
  await page.screenshot({ path: info.outputPath('writing-closing.png') });
  await writingPosition(page, 'hero', 420 * 0.35);
  await expect.poll(reading).toEqual(partial);
  await writingPosition(page, 'hero', 421);
  await expect
    .poll(async () => (await reading()).every((row) => row.hidden && row.opacity === '0'))
    .toBe(true);
  await position(page, 'hero', 0);
  await expect
    .poll(async () => (await reading()).every((row) => row.hidden && row.opacity === ''))
    .toBe(true);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await position(page, 'hero', 0.25);
  await expect
    .poll(async () => (await reading()).every((row) => row.hidden && row.opacity === ''))
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
  expect(await y(third, 3)).toBeGreaterThan(await y(third));
  await writingPosition(page, 'capabilities', 780);
  await expect.poll(() => y(third, 3)).toBe(0);
  await presented(page);
  const thirdFrame = await page.locator('[data-film-status]').textContent();
  expect(thirdFrame).not.toContain('192 / 192');
  const bounds = await page.locator('[data-film-canvas]').boundingBox();
  const clip = { x: page.viewportSize()!.width - 80, y: 130, width: 60, height: 60 };
  const filmBefore = await page.screenshot({ clip });
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
  expect(difference(filmBefore, await page.screenshot({ clip })).changed).toBeGreaterThan(0);
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
