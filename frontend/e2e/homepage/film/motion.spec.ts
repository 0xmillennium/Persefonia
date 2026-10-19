import { startOnFilm } from '../../support/homepage';
import { expect, test } from '@playwright/test';
import { visit, position, selectFilm, presented, settlePresentation } from '../../support/scenes';

test.beforeEach(async ({ page }) => startOnFilm(page));

test('film scrubs from the first scroll, reverses and keeps selection progress', async ({
  page,
}, testInfo) => {
  await visit(page);
  const status = page.locator('[data-film-status]');
  const background = page.locator('.home-cinema__image');
  await expect
    .poll(() =>
      background.evaluate((node) => ({
        x: node.getBoundingClientRect().x,
        y: node.getBoundingClientRect().y,
        width: node.getBoundingClientRect().width,
        height: node.getBoundingClientRect().height,
      })),
    )
    .toEqual({
      x: 0,
      y: 0,
      width: page.viewportSize()!.width,
      height: page.viewportSize()!.height,
    });
  await expect(background).toHaveCSS('mask-image', /radial-gradient/);
  await page.screenshot({ path: testInfo.outputPath('constellation-arm.png') });
  for (const p of [0, 0.15, 0.3, 0.42, 0.5, 0.75, 0.5]) {
    await position(page, 'hero', p);
    await presented(page);
    const expected = await page.evaluate(() => {
      const hero = document.querySelector('[data-scene="hero"]')!.getBoundingClientRect();
      const mark = document.querySelector('[data-scene="mark"]')!.getBoundingClientRect();
      const start = hero.top + scrollY,
        end = mark.top + scrollY + mark.height * 0.98;
      return Math.round(Math.max(0, Math.min(1, (scrollY - start) / (end - start))) * 191);
    });
    await expect(status).toHaveText(`Robotic arm · ${String(expected + 1).padStart(3, '0')} / 192`);
  }
  const before = await status.textContent();
  await page.waitForTimeout(500);
  await expect(status).toHaveText(before!);
  await selectFilm(page);
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'tree');
  await presented(page);
  await expect(status).toHaveText(before!.replace('Robotic arm', 'Tree'));
  await position(page, 'hero', 0.3);
  await presented(page);
  await expect(status).not.toHaveText('Tree · 001 / 192');
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'tree');
  await position(page, 'hero', 0);
  const masked = testInfo.project.name !== 'tablet-834';
  await expect(background).toHaveCSS('clip-path', masked ? 'none' : /path\(/);
  await expect(background).toHaveCSS('mask-image', masked ? /data:image\/svg\+xml/ : 'none');
  await page.waitForTimeout(500);
  await page.screenshot({ path: testInfo.outputPath('tree-door.png') });
});

test('film wheel steps advance four frames and preserve the last pose at the authored gallery handoff', async ({
  page,
}, info) => {
  test.skip(info.project.name === 'tablet-834', 'Desktop and portrait playback geometry');
  // Chromium mobile emulation divides synthetic wheel deltas by device scale.
  const wheelScale = info.project.use.isMobile ? await page.evaluate(() => devicePixelRatio) : 1;
  const wheel = (delta: number) => page.mouse.wheel(0, delta * wheelScale);
  await visit(page);
  await expect(page.locator('[data-home-loader]')).toBeHidden();
  const range = await page.evaluate(() => {
    const hero = document.querySelector('[data-scene="hero"]')!.getBoundingClientRect();
    const mark = document.querySelector('[data-scene="mark"]')!.getBoundingClientRect();
    const start = hero.top + scrollY,
      end = mark.top + scrollY + mark.height * 0.98;
    return { start, end, step: (end - start) / 191 };
  });
  expect(range.step).toBeCloseTo(30, 1);
  await page.evaluate(({ start, step }) => scrollTo(0, start + 20 * step), range);
  const status = page.locator('[data-film-status]');
  await expect(status).toHaveText('Robotic arm · 021 / 192');
  let frame = 21;
  for (const notch of [120, 100, 240, -120, -240]) {
    await wheel(notch);
    frame += 4 * Math.sign(notch);
    await expect(status).toHaveText(`Robotic arm · ${String(frame).padStart(3, '0')} / 192`);
  }
  await position(page, 'mark', 0.5);
  await presented(page);
  expect(await status.textContent()).not.toContain('192 / 192');
  await position(page, 'mark', 0.979);
  await expect(status).toHaveText('Robotic arm · 192 / 192');
  await expect(page.locator('[data-scene-wipe]')).toBeHidden();
  await page.evaluate((end) => scrollTo(0, end), range.end);
  await expect(status).toHaveText('Robotic arm · 192 / 192');
  await expect(page.locator('[data-scene-wipe]')).toBeHidden();
  await page.screenshot({ path: info.outputPath('last-frame-before-gallery.png') });
  const readingStop = await page
    .locator('[data-scene="mark"]')
    .evaluate((node) => Math.round(node.getBoundingClientRect().top + scrollY + 1140));
  await wheel(-120);
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(readingStop);
  const previousFrame =
    Math.round(((readingStop - range.start) / (range.end - range.start)) * 191) + 1;
  await expect(status).toHaveText(`Robotic arm · ${String(previousFrame).padStart(3, '0')} / 192`);
  await expect(page.locator('[data-scene-wipe]')).toBeHidden();
  await wheel(120);
  await expect(page.locator('[data-scene-wipe]')).toBeVisible();
  await expect(status).toHaveText('Robotic arm · 192 / 192');
});

test('film wheel glides through intermediate positions and yields to native input', async ({
  page,
}, info) => {
  const wheelScale = info.project.use.isMobile ? await page.evaluate(() => devicePixelRatio) : 1;
  await visit(page);
  await expect(page.locator('[data-home-loader]')).toBeHidden();
  const start = await page.locator('[data-scene="hero"]').evaluate((node) => {
    const rect = node.getBoundingClientRect();
    return rect.top + scrollY + 20 * 30;
  });
  await page.evaluate((y) => scrollTo(0, y), start);
  await expect(page.locator('[data-film-status]')).toHaveText('Robotic arm · 021 / 192');
  const origin = await page.evaluate(() => scrollY);
  const recording = page.evaluate(
    () =>
      new Promise<{
        samples: Array<{ y: number; frame: string | null }>;
        presented: number[];
        native: boolean;
      }>((resolve) => {
        const samples: Array<{ y: number; frame: string | null }> = [];
        const video = document.querySelector<HTMLVideoElement>(
          'video.film-presentation:not([hidden])',
        );
        const presented: number[] = [];
        let callback = 0;
        const onFrame: VideoFrameRequestCallback = (_now, metadata) => {
          presented.push(1 + Math.round(metadata.mediaTime * 30));
          callback = video!.requestVideoFrameCallback(onFrame);
        };
        if (video) callback = video.requestVideoFrameCallback(onFrame);
        const started = performance.now();
        const sample = () => {
          samples.push({
            y: scrollY,
            frame: document.querySelector('[data-film-status]')!.textContent,
          });
          if (performance.now() - started < 450) requestAnimationFrame(sample);
          else {
            if (video) video.cancelVideoFrameCallback(callback);
            resolve({ samples, presented, native: !!video });
          }
        };
        requestAnimationFrame(sample);
      }),
  );
  await page.mouse.wheel(0, 120 * wheelScale);
  const { samples, presented, native } = await recording;
  const positions = samples.map((sample) => sample.y);
  expect(new Set(positions).size).toBeGreaterThan(3);
  expect(positions.every((value, index) => index === 0 || value >= positions[index - 1]!)).toBe(
    true,
  );
  expect(positions.at(-1)).toBeCloseTo(origin + 120, 0);
  expect(
    native
      ? presented.some((frame) => frame >= 22 && frame <= 24)
      : samples.some((sample) => /02[234] \/ 192/.test(sample.frame ?? '')),
  ).toBe(true);
  await expect(page.locator('[data-film-status]')).toHaveText('Robotic arm · 025 / 192');
  await page.mouse.wheel(0, 120 * wheelScale);
  await page.evaluate(() => scrollTo(0, 300));
  await page.waitForTimeout(250);
  expect(await page.evaluate(() => scrollY)).toBe(300);
  await page.evaluate((y) => scrollTo(0, y), start);
  await page.mouse.wheel(0, 120 * wheelScale);
  await page.keyboard.press('Home');
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(0);
  await page.waitForTimeout(250);
  expect(await page.evaluate(() => scrollY)).toBe(0);
});

test('all canonical scene states reverse without one-shot progression', async ({
  page,
}, testInfo) => {
  await visit(page);
  for (const name of ['statements', 'capabilities', 'mark', 'gallery']) {
    await position(page, name, 0.35);
    await settlePresentation(page);
    const face = page.locator(`[data-scene="${name}"] .scene-face`);
    const first = await face.getAttribute('style');
    await position(page, name, 0.7);
    await settlePresentation(page);
    await position(page, name, 0.35);
    await settlePresentation(page);
    expect(await face.getAttribute('style')).toBe(first);
    await page.screenshot({ path: testInfo.outputPath(`${name}-reversible.png`) });
  }
});

test('presented media-time probes cover cold, warm and reverse wheel travel', async ({
  page,
}, info) => {
  test.skip(info.project.name === 'tablet-834', 'Desktop and portrait decoder paths');
  await visit(page);
  const scale = info.project.use.isMobile ? await page.evaluate(() => devicePixelRatio) : 1;
  const start = await page
    .locator('[data-scene="hero"]')
    .evaluate((node) => node.getBoundingClientRect().top + scrollY + 600);
  const evidence = [];
  for (const [label, direction] of [
    ['cold-forward', 1],
    ['reverse', -1],
    ['warm-forward', 1],
  ] as const) {
    if (direction > 0) await page.evaluate((y) => scrollTo(0, y), start);
    await presented(page);
    const recording = page.evaluate(
      () =>
        new Promise<{ times: number[]; elapsed: number }>((resolve) => {
          const video = document.querySelector<HTMLVideoElement>(
            'video.film-presentation:not([hidden])',
          )!;
          const times: number[] = [],
            began = performance.now();
          let callback = 0;
          const frame: VideoFrameRequestCallback = (_time, metadata) => {
            times.push(metadata.mediaTime);
            callback = video.requestVideoFrameCallback(frame);
          };
          callback = video.requestVideoFrameCallback(frame);
          setTimeout(() => {
            video.cancelVideoFrameCallback(callback);
            resolve({ times, elapsed: performance.now() - began });
          }, 500);
        }),
    );
    await page.mouse.wheel(0, direction * 120 * scale);
    const sample = await recording;
    await presented(page);
    const unique = [...new Set(sample.times)];
    const directed = unique
      .slice(1)
      .every((time, index) => direction * (time - unique[index]!) >= -0.001);
    evidence.push({ label, ...sample, unique: unique.length, directed });
    expect(unique.length).toBeGreaterThan(0);
    expect(directed).toBe(true);
    expect(await page.evaluate(() => scrollY)).toBe(start + (direction > 0 ? 120 : 0));
  }
  await info.attach('cold-warm-reverse-media-time', {
    body: JSON.stringify(
      {
        evidence,
        limitation:
          'Presented native mediaTime; headless decoder sampling, not a physical-display FPS guarantee.',
      },
      null,
      2,
    ),
    contentType: 'application/json',
  });
});
