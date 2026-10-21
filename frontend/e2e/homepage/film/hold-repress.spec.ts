import { startOnFilm } from '../../support/homepage';
import { expect, test } from '../../support/test';
import { visit } from '../../support/scenes';
import { radiusFor } from '../../../src/homepage/film/rendering/shaders';
import { HOLD } from '../../../src/homepage/film/hold/state';
import { installHoldObserver, requireFreshMask } from '../../support/hold-observer';

test.beforeEach(async ({ page }) => startOnFilm(page));

test('a distant interrupted hold keeps its uploaded boundary bounded and cancels cleanly', async ({
  page,
}, info) => {
  await page.clock.install();
  await page.addInitScript(installHoldObserver);
  await visit(page);
  await expect
    .poll(
      () =>
        page.evaluate(() => ({
          installed: window.__holdObserver.installed,
          context: window.__holdObserver.context > 0,
          sourceReady: [...document.querySelectorAll('video')].some(
            (video) => video.src.includes('/robotic-arm.mp4') && video.readyState >= 2,
          ),
        })),
      { message: 'Required target WebGL2 context and decoded robotic-arm source' },
    )
    .toEqual({ installed: true, context: true, sourceReady: true });
  await page.clock.pauseAt(await page.evaluate(() => Date.now() + 1000));
  const viewport = page.viewportSize()!;
  const first = { x: viewport.width * 0.14, y: viewport.height * 0.62 };
  const second = { x: viewport.width * 0.88, y: viewport.height * 0.32 };
  const session = info.project.use.isMobile ? await page.context().newCDPSession(page) : null;
  const press = async (point: { x: number; y: number }) => {
    if (session)
      await session.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [point] });
    else {
      await page.mouse.move(point.x, point.y);
      await page.mouse.down();
    }
  };
  const release = async () => {
    if (session)
      await session.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
    else await page.mouse.up();
  };
  try {
    await press(first);
    await page.clock.fastForward(1500);
    await page.clock.runFor(16);
    await release();
    await page.clock.runFor(80);
    const boundary = await page.evaluate(() => {
      const state = window.__holdObserver;
      state.action++;
      const pose = new DOMMatrix(
        (document.querySelector('[data-hold-countdown]') as HTMLElement).style.transform,
      );
      return { after: state.sequence, action: state.action, center: { x: pose.m41, y: pose.m42 } };
    });
    expect(boundary.after, 'The first gesture must produce a target hold upload').toBeGreaterThan(
      0,
    );
    await press(second);
    const actualPress = await page.evaluate(() => window.__holdObserver.lastPress);
    expect(actualPress?.action).toBe(boundary.action);
    expect(actualPress!.x).toBeCloseTo(second.x, 0);
    expect(actualPress!.y).toBeCloseTo(second.y, 0);
    let previous = 0;
    for (const elapsed of [160, 360]) {
      await page.clock.runFor(elapsed - previous);
      previous = elapsed;
      const observation = await page.evaluate(() => {
        const state = window.__holdObserver;
        return {
          ...state,
          mask: state.mask && { ...state.mask, pixels: Array.from(state.mask.pixels) },
        };
      });
      const mask = requireFreshMask(
        observation as unknown as Parameters<typeof requireFreshMask>[0],
        boundary.after,
        boundary.action,
      );
      expect(mask.height).toBe(
        Math.min(256, Math.max(48, Math.round((192 * viewport.height) / viewport.width))),
      );
      boundary.after = mask.sequence;
      const sample = await page.evaluate(
        (mask) => {
          const pose = new DOMMatrix(
            (document.querySelector('[data-hold-countdown]') as HTMLElement).style.transform,
          );
          const points = [];
          for (let i = 0; i < mask.pixels.length; i += 4)
            if (mask.pixels[i]! > 127) points.push(i / 4);
          return {
            width: mask.width,
            height: mask.height,
            points,
            center: { x: pose.m41, y: pose.m42 },
          };
        },
        { width: mask.width, height: mask.height, pixels: Array.from(mask.pixels) },
      );
      // Compare the center at the observed upload's virtual time, not at a presumed RAF count.
      // Native decoding/compositing still have their own readiness observations above.
      const seconds = (mask.clock - actualPress!.clock) / 1000;
      expect(seconds).toBeGreaterThan(0);
      expect(seconds).toBeLessThanOrEqual(elapsed / 1000 + 0.001);
      const follow = 1 - Math.exp(-9 * seconds);
      for (const axis of ['x', 'y'] as const) {
        const expected =
          boundary.center[axis] + (actualPress![axis] - boundary.center[axis]) * follow;
        expect(Math.abs(sample.center[axis] - expected)).toBeLessThan(2);
      }
      // Small timing headroom; the upper radius still rejects the old extrapolated fingers.
      const upperProgress =
        1.5 / HOLD.duration -
        0.08 * HOLD.contractionRate +
        elapsed / (HOLD.duration * 1000) +
        0.025;
      const radius = radiusFor(
        upperProgress ** 1.6,
        sample.center,
        viewport.width,
        viewport.height,
      );
      const limit =
        radius * 1.12 +
        Math.hypot(viewport.width, viewport.height) * 0.045 +
        viewport.width / sample.width;
      const outside = sample.points.filter(
        (i) =>
          Math.hypot(
            (((i % sample.width) + 0.5) / sample.width) * viewport.width - sample.center.x,
            ((Math.floor(i / sample.width) + 0.5) / sample.height) * viewport.height -
              sample.center.y,
          ) > limit,
      );
      expect(sample.points.length).toBeGreaterThan(0);
      expect(outside).toHaveLength(0);
      await expect(page.locator('[data-hold-countdown]')).toHaveText('2');
      await page.screenshot({
        path: info.outputPath(`repress-${elapsed}.png`),
        scale: 'css',
        timeout: 5000,
        animations: 'disabled',
      });
    }
    await release();
    await page.clock.fastForward(1800);
    await page.clock.runFor(32);
    await expect(page.locator('[data-hold-countdown]')).toBeHidden();
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'robotic-arm');
    expect(
      await page.evaluate(() => {
        const mask = window.__holdObserver.mask;
        if (!mask) throw new Error('Previously observed target mask disappeared');
        // An idle renderer may retain its unused texture. A visible GPU surface
        // must never retain the canceled aperture.
        const visible =
          (document.querySelector('[data-home-v2]') as HTMLElement).dataset.gpuSheet === 'true';
        return visible && mask.pixels.some((value, i) => i % 4 === 0 && value > 0);
      }),
    ).toBe(false);
  } finally {
    // Failure screenshots must not wait for a virtual RAF on a paused clock.
    try {
      await page.clock.resume();
      await session?.detach();
    } catch (error) {
      await info.attach('hold-cleanup-error', { body: String(error), contentType: 'text/plain' });
    }
  }
});
