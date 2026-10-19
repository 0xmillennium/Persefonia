import { startOnFilm } from '../support/homepage';
import { expect, test } from '@playwright/test';
import { visit, position } from '../support/scenes';
import { difference, pixels } from '../support/pixels';

test.beforeEach(async ({ page }) => startOnFilm(page));

test('desktop tree panes retain their frame and dividers through navigator rendering and scroll reversal', async ({
  page,
}, info) => {
  test.skip(info.project.name !== 'desktop-1440', 'Six-pane desktop tree composition');
  await page.addInitScript(() =>
    history.replaceState({ ...history.state, homepageV2: { film: 'tree' } }, ''),
  );
  await page.clock.install();
  await visit(page);
  await page.clock.pauseAt(await page.evaluate(() => Date.now() + 50));
  const sheet = page.locator('.home-cinema__image');
  const initial = await sheet.evaluate((node) => node.style.maskImage);
  const svg = decodeURIComponent(/data:image\/svg\+xml,([^"\)]+)/.exec(initial)![1]!);
  // Six identical arches, arranged in three columns and two rows.
  expect(svg).toContain('fill-rule="evenodd"');
  const paths = [...svg.matchAll(/M([\d.]+) ([\d.]+)H([\d.]+)(A|L)/g)];
  expect(paths).toHaveLength(6);
  expect(paths.map((path) => path[4])).toEqual(Array(6).fill('A'));
  expect(new Set(paths.map((path) => path[1])).size).toBe(3);
  expect(new Set(paths.map((path) => path[2])).size).toBe(2);
  const panes = [
    ...svg.matchAll(/M[\d.]+ ([\d.]+)H[\d.]+A([\d.]+) [\d.]+ 0 0 1 [\d.]+ [\d.]+V([\d.]+)/g),
  ];
  expect(panes).toHaveLength(6);
  const heights = panes.map((pane) => Number(pane[3]) - Number(pane[1]));
  for (const height of heights) expect(height).toBeCloseTo(heights[0]!);
  expect(new Set(panes.map((pane) => pane[2])).size).toBe(1);
  const nav = (await page.locator('.home-nav').boundingBox())!;
  const prompt = (await page.locator('.hero-scroll').boundingBox())!;
  expect(Math.min(...panes.map((pane) => Number(pane[1]))) - nav.y - nav.height).toBeCloseTo(16);
  expect(prompt.y - Math.max(...panes.map((pane) => Number(pane[3])))).toBeCloseTo(16);
  const clip = { x: 922, y: 410, width: 6, height: 30 };
  const video = page.locator('video.film-presentation:visible'),
    bounds = await video.boundingBox();
  await page.screenshot({ path: info.outputPath('tree-six-panes.png') });
  const clips = [
    clip,
    { x: 838, y: 60, width: 6, height: 6 },
    { x: 1172, y: 60, width: 6, height: 6 },
    { x: 700, y: 220, width: 6, height: 6 },
    { x: 700, y: 445, width: 6, height: 6 },
    { x: 720, y: 700, width: 6, height: 6 },
  ];
  const native = [];
  for (const sample of clips) native.push(await page.screenshot({ clip: sample }));
  await page.mouse.move(120, 160);
  await page.mouse.move(320, 170, { steps: 6 });
  await page.clock.runFor(32);
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-gpu-sheet', 'true');
  for (let i = 0; i < clips.length; i++)
    expect(difference(native[i]!, await page.screenshot({ clip: clips[i]! })).mean).toBeLessThan(2);
  await page.clock.fastForward(5000);
  await position(page, 'hero', 0.3);
  await page.evaluate(() => dispatchEvent(new Event('scroll')));
  // Advance the virtual clock through the bounded cinematic catch-up.
  await page.clock.runFor(320);
  await expect(sheet).not.toHaveCSS('mask-image', initial);
  expect(await video.boundingBox()).toEqual(bounds);
  await page.screenshot({ path: info.outputPath('tree-panes-expanding.png') });
  // Cross the completion boundary despite native scroll rounding.
  await position(page, 'hero', 0.43);
  await page.evaluate(() => dispatchEvent(new Event('scroll')));
  // Advance the virtual clock through the bounded cinematic catch-up.
  await page.clock.runFor(320);
  await expect(sheet).toHaveCSS('mask-image', 'none');
  expect(await video.boundingBox()).toEqual(bounds);
  await position(page, 'hero', 0);
  await page.evaluate(() => dispatchEvent(new Event('scroll')));
  // Advance the virtual clock through the bounded cinematic catch-up.
  await page.clock.runFor(320);
  await expect(sheet).toHaveCSS('mask-image', initial);
});

test('mobile tree has equal centered windows above and below the writing in native and navigator presentation', async ({
  page,
}, info) => {
  test.skip(info.project.name !== 'mobile-390', 'Paired portrait phone composition');
  await page.addInitScript(() =>
    history.replaceState({ ...history.state, homepageV2: { film: 'tree' } }, ''),
  );
  await page.clock.install();
  await visit(page);
  await expect(page.locator('[data-home-loader]')).toBeHidden();
  await page.clock.pauseAt(await page.evaluate(() => Date.now() + 50));
  const sheet = page.locator('.home-cinema__image');
  const initial = await sheet.evaluate((node) => node.style.maskImage);
  const holes = [...initial.matchAll(/data:image\/svg\+xml,([^"\)]+)/g)].map((match) => {
    const svg = decodeURIComponent(match[1]!);
    const path = /d="([^"]+)"/.exec(svg)![1]!;
    const points =
      /Z M([\d.]+) ([\d.]+)H[\d.]+A([\d.]+) [\d.]+ 0 0 1 ([\d.]+) [\d.]+V([\d.]+)/.exec(path)!;
    return {
      center: Number(points[1]),
      top: Number(points[2]),
      width: Number(points[3]) * 2,
      right: Number(points[4]),
      bottom: Number(points[5]),
    };
  });
  expect(holes).toHaveLength(2);
  const [lower, upper] = holes;
  for (const hole of holes) expect(hole.center).toBeCloseTo(195);
  expect(lower!.width).toBeCloseTo(upper!.width);
  expect(lower!.bottom - lower!.top).toBeCloseTo(upper!.bottom - upper!.top);
  expect(lower!.bottom - lower!.top).toBeGreaterThan(250);
  await expect(
    page.getByText('Independent thinking / deliberate engineering', { exact: true }),
  ).toHaveCount(0);
  const nav = (await page.locator('.home-nav').boundingBox())!;
  expect(upper!.top - nav.y - nav.height).toBeGreaterThanOrEqual(16);
  const writing = (await page.locator('.hero-statement').boundingBox())!;
  expect(lower!.top - writing.y - writing.height).toBeCloseTo(16);
  expect(writing.y - upper!.bottom).toBeCloseTo(16);
  const video = page.locator('video.film-presentation:visible'),
    bounds = await video.boundingBox();
  await page.screenshot({ path: info.outputPath('tree-paired-windows.png') });
  // Use flat sky away from branch edges, whose downsampling differs by renderer.
  const clip = {
    x: Math.ceil(lower!.right - lower!.width + 16),
    y: Math.floor(lower!.bottom - 30),
    width: 12,
    height: 12,
  };
  const native = await page.screenshot({ clip, scale: 'css' });
  await page.mouse.move(20, 110);
  await page.mouse.move(70, 120, { steps: 4 });
  await page.clock.runFor(32);
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-gpu-sheet', 'true');
  expect(difference(native, await page.screenshot({ clip, scale: 'css' })).mean).toBeLessThan(2);
  await page.clock.fastForward(5000);
  await position(page, 'hero', 0.3);
  await page.evaluate(() => dispatchEvent(new Event('scroll')));
  // Advance the virtual clock through the bounded cinematic catch-up.
  await page.clock.runFor(320);
  await expect(sheet).not.toHaveCSS('mask-image', initial);
  expect(await video.boundingBox()).toEqual(bounds);
  await position(page, 'hero', 0);
  await page.evaluate(() => dispatchEvent(new Event('scroll')));
  // Advance the virtual clock through the bounded cinematic catch-up.
  await page.clock.runFor(320);
  await expect(sheet).toHaveCSS('mask-image', initial);
});

test('constellation circles expose their stars and overlapping openings stay open', async ({
  page,
}, info) => {
  await visit(page);
  const video = page.locator('video.film-presentation').first();
  const background = page.locator('.home-cinema__image');
  const initialMask = await background.evaluate((node) => getComputedStyle(node).maskImage);
  expect(initialMask.match(/radial-gradient/g)).toHaveLength(7);
  const bounds = await video.boundingBox();
  expect(bounds).not.toBeNull();
  const sample = async (sx: number, sy: number) => {
    const clip = {
      x: Math.round(bounds!.x + (sx / 1600) * bounds!.width) - 3,
      y: Math.round(bounds!.y + (sy / 900) * bounds!.height) - 3,
      width: 6,
      height: 6,
    };
    const expected = await video.evaluate((node, clip) => {
      const video = node as HTMLVideoElement,
        rect = video.getBoundingClientRect();
      const canvas = document.createElement('canvas');
      canvas.width = canvas.height = 6;
      const context = canvas.getContext('2d')!;
      context.drawImage(
        video,
        ((clip.x - rect.x) / rect.width) * video.videoWidth,
        ((clip.y - rect.y) / rect.height) * video.videoHeight,
        (clip.width / rect.width) * video.videoWidth,
        (clip.height / rect.height) * video.videoHeight,
        0,
        0,
        6,
        6,
      );
      return [...context.getImageData(0, 0, 6, 6).data];
    }, clip);
    const actual = pixels(await page.screenshot({ clip, scale: 'css' }));
    let error = 0;
    for (let i = 0; i < 36; i++)
      for (let c = 0; c < 3; c++)
        error += Math.abs(actual.data[i * actual.channels + c]! - expected[i * 4 + c]!);
    expect(error / (36 * 3)).toBeLessThan(8);
  };
  // Sample the glow below the top star, clear of the tablet navigation button.
  await sample(1011, 130);
  await sample(1228, 222);
  // The intentionally overlapping lower pair must expose film, not refill with photo.
  await sample(1137.5, 594);
  await page.screenshot({ path: info.outputPath('constellation-opening.png') });
  await position(page, 'hero', 0.15);
  await expect(background).not.toHaveCSS('mask-image', initialMask);
  // Shared projection preserves the intentional overlap of the two close circles.
  const circles = await background.evaluate((node) =>
    [
      ...node.style.maskImage.matchAll(
        /circle at ([\d.e+-]+)px ([\d.e+-]+)px, (?:transparent|rgba\([^)]*\)) ([\d.e+-]+)px/g,
      ),
    ].map((m) => [Number(m[1]), Number(m[2]), Number(m[3]) + 0.5]),
  );
  expect(circles).toHaveLength(7);
  const a = circles[4]!,
    b = circles[5]!;
  expect(Math.hypot(a[0]! - b[0]!, a[1]! - b[1]!)).toBeLessThan(a[2]! + b[2]!);
  await expect(page.locator('[data-film-status]')).not.toHaveText('Robotic arm · 001 / 192');
  expect(await video.boundingBox()).toEqual(bounds);
  await page.screenshot({ path: info.outputPath('constellation-overlap.png') });
  await position(page, 'hero', 0);
  await expect(background).toHaveCSS('mask-image', initialMask);
  expect(await video.boundingBox()).toEqual(bounds);
});
