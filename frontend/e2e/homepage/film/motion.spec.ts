import { startOnFilm } from '../../support/homepage';
import { expect, test } from '../../support/test';
import { visit, presented } from '../../support/scenes';

test.beforeEach(async ({ page }) => startOnFilm(page));

// Scene-face phase/style reversibility is owned by homepage/presentation.test.ts.
// Ordinary native writing paint, gallery reversal/focus and decoded-frame probes remain required.
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
