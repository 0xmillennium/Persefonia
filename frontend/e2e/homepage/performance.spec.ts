import { startOnFilm } from '../support/homepage';
import { expect, test } from '@playwright/test';
import { visit, presented } from '../support/scenes';

test('film scrolling bounds layout work and records task and frame intervals', async ({
  page,
}, testInfo) => {
  await startOnFilm(page);
  await visit(page);
  const session = await page.context().newCDPSession(page);
  await session.send('Performance.enable');
  const before = await session.send('Performance.getMetrics');
  const intervals = await page.evaluate(async () => {
    const node = document.querySelector('[data-scene="hero"]')!;
    const height = node.getBoundingClientRect().height;
    const values: number[] = [];
    let previous = performance.now();
    for (let i = 0; i < 90; i++) {
      await new Promise<void>((resolve) =>
        requestAnimationFrame((now) => {
          values.push(now - previous);
          previous = now;
          scrollTo(0, height * (0.15 + Math.sin((i / 90) * Math.PI) * 0.55));
          resolve();
        }),
      );
    }
    return values;
  });
  await presented(page);
  const after = await session.send('Performance.getMetrics');
  const m = (r: typeof before) => Object.fromEntries(r.metrics.map((m) => [m.name, m.value]));
  const start = m(before),
    end = m(after),
    sorted = intervals.slice(1).sort((a, b) => a - b);
  const evidence = {
    p50Ms: sorted[Math.floor(sorted.length * 0.5)],
    p95Ms: sorted[Math.floor(sorted.length * 0.95)],
    over50Ms: sorted.filter((n) => n > 50).length,
    taskSeconds: end.TaskDuration! - start.TaskDuration!,
    layouts: end.LayoutCount! - start.LayoutCount!,
    heapBefore: start.JSHeapUsedSize,
    heapAfter: end.JSHeapUsedSize,
    limitation:
      'Headless local browser; RAF cadence is not compositor FPS. JS heap excludes decoder/GPU allocations.',
  };
  await testInfo.attach('runtime-evidence', {
    body: JSON.stringify(evidence, null, 2),
    contentType: 'application/json',
  });
  expect(Number.isFinite(evidence.taskSeconds)).toBe(true);
  // Three desktop and three portrait samples used 3–4 layouts for these 90 steps.
  // Allow 3× the observed maximum; detect layout-per-frame regressions without
  // turning software-rendered RAF cadence or CPU timing into a hardware FPS gate.
  expect(evidence.layouts).toBeGreaterThanOrEqual(0);
  expect(evidence.layouts).toBeLessThanOrEqual(12);
  await session.detach();
});

test('healthy startup keeps optional renderers lazy and the resting page stops scheduling animation frames', async ({
  page,
}, info) => {
  test.skip(info.project.name === 'tablet-834', 'Desktop and portrait resource policy');
  await page.addInitScript(() => {
    const original = requestAnimationFrame;
    const state = window as Window & { homeFrames?: number };
    state.homeFrames = 0;
    window.requestAnimationFrame = (callback) =>
      original((time) => {
        state.homeFrames = (state.homeFrames ?? 0) + 1;
        callback(time);
      });
  });
  const requests: string[] = [];
  page.on('request', (request) => requests.push(request.url()));
  await visit(page);
  await page.waitForTimeout(500);
  const before = await page.evaluate(
    () => (window as Window & { homeFrames?: number }).homeFrames ?? 0,
  );
  await page.waitForTimeout(350);
  const frames =
    (await page.evaluate(() => (window as Window & { homeFrames?: number }).homeFrames ?? 0)) -
    before;
  expect(frames).toBeLessThanOrEqual(2);
  expect(await page.locator('video').count()).toBe(1);
  expect(requests.filter((url) => /\/(physical|controller)-[^/]+\.js/.test(url))).toEqual([]);
  expect(requests.some((url) => /\/home-loops\/.*waterfall\.mp4/.test(url))).toBe(true);
  expect(requests.filter((url) => /\/(home-videos|home-films)\//.test(url))).toEqual([]);
  await info.attach('startup-resource-budget', {
    body: JSON.stringify({ frames, videos: 1, requests }, null, 2),
    contentType: 'application/json',
  });
});
