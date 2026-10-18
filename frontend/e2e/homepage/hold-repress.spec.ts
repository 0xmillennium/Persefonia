import { expect, test } from '@playwright/test';
import { visit } from '../support/scenes';
import { radiusFor } from '../../src/homepage/film/rendering/shaders';
import { HOLD } from '../../src/homepage/film/hold/state';

type Mask = { width: number; height: number; pixels: Uint8Array };

test('a distant interrupted hold keeps its uploaded boundary bounded and cancels cleanly', async ({
  page,
}, info) => {
  await page.clock.install();
  await page.addInitScript(() => {
    const state = window as Window & { holdMask?: Mask };
    for (const name of ['texImage2D', 'texSubImage2D'] as const) {
      const original = WebGL2RenderingContext.prototype[name];
      (WebGL2RenderingContext.prototype[name] as unknown) = function (
        this: WebGL2RenderingContext,
        ...args: unknown[]
      ) {
        const pixels = args.at(-1);
        if (
          pixels instanceof Uint8Array &&
          this.getParameter(this.ACTIVE_TEXTURE) === this.TEXTURE3
        ) {
          const offset = name === 'texImage2D' ? 3 : 4;
          const width = args[offset] as number,
            height = args[offset + 1] as number;
          if (width === 192 && pixels.length === width * height * 4)
            state.holdMask = { width, height, pixels: pixels.slice() };
        }
        return (original as (...args: unknown[]) => void).apply(this, args);
      };
    }
  });
  await visit(page);
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
    await press(second);
    let previous = 0;
    for (const elapsed of [160, 360]) {
      await page.clock.runFor(elapsed - previous);
      previous = elapsed;
      const sample = await page.evaluate(() => {
        const mask = (window as Window & { holdMask?: Mask }).holdMask!;
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
      });
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
      await page.screenshot({ path: info.outputPath(`repress-${elapsed}.png`), scale: 'css' });
    }
    await release();
    await page.clock.fastForward(1800);
    await page.clock.runFor(32);
    await expect(page.locator('[data-hold-countdown]')).toBeHidden();
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'robotic-arm');
    expect(
      await page.evaluate(() => {
        const mask = (window as Window & { holdMask?: Mask }).holdMask!;
        // An idle renderer may retain its unused texture. A visible GPU surface
        // must never retain the canceled aperture.
        const visible =
          (document.querySelector('[data-home-v2]') as HTMLElement).dataset.gpuSheet === 'true';
        return visible && mask.pixels.some((value, i) => i % 4 === 0 && value > 0);
      }),
    ).toBe(false);
  } finally {
    await session?.detach();
  }
});
