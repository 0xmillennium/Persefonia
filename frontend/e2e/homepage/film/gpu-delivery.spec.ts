import { startOnFilm } from '../../support/homepage';
import { expect, test } from '../../support/test';
import { visit, position, presented } from '../../support/scenes';

test.beforeEach(async ({ page }) => startOnFilm(page));

test('the navigator uploads a replacement source even when the logical film pose is unchanged', async ({
  page,
}, info) => {
  test.skip(info.project.name !== 'desktop-1440', 'Same texture cache for every composition');
  await page.addInitScript(() => {
    const state = window as Window & { filmWidths?: number[]; filmPaintWidths?: number[] };
    state.filmWidths = [];
    state.filmPaintWidths = [];
    const widths = new WeakMap<WebGLTexture, number>();
    for (const name of ['texImage2D', 'texSubImage2D'] as const) {
      const original = WebGL2RenderingContext.prototype[name];
      (WebGL2RenderingContext.prototype[name] as unknown) = function (
        this: WebGL2RenderingContext,
        ...args: unknown[]
      ) {
        const image = args.at(-1);
        if (image instanceof HTMLVideoElement && !image.hasAttribute('data-loop-video')) {
          state.filmWidths!.push(image.videoWidth);
          const texture = this.getParameter(this.TEXTURE_BINDING_2D) as WebGLTexture | null;
          if (texture) widths.set(texture, image.videoWidth);
        }
        return (original as (...args: unknown[]) => void).apply(this, args);
      };
    }
    const draw = WebGL2RenderingContext.prototype.drawArrays;
    WebGL2RenderingContext.prototype.drawArrays = function (...args) {
      draw.apply(this, args);
      if (
        !(this.canvas instanceof HTMLCanvasElement) ||
        !this.canvas.hasAttribute('data-film-canvas') ||
        this.getParameter(this.FRAMEBUFFER_BINDING)
      )
        return;
      const active = this.getParameter(this.ACTIVE_TEXTURE) as number;
      this.activeTexture(this.TEXTURE0);
      const width = widths.get(this.getParameter(this.TEXTURE_BINDING_2D) as WebGLTexture);
      this.activeTexture(active);
      if (!width || state.filmPaintWidths!.includes(width)) return;
      const colors = new Set<string>();
      const pixel = new Uint8Array(4);
      for (let y = 1; y <= 4; y++)
        for (let x = 1; x <= 4; x++) {
          this.readPixels(
            Math.floor((this.drawingBufferWidth * x) / 5),
            Math.floor((this.drawingBufferHeight * y) / 5),
            1,
            1,
            this.RGBA,
            this.UNSIGNED_BYTE,
            pixel,
          );
          if (pixel[3]! > 0) colors.add(Array.from(pixel.subarray(0, 3)).join(','));
        }
      if (this.getError() !== this.NO_ERROR)
        throw new Error('Required rendered GPU readback failed');
      if (colors.size > 2) state.filmPaintWidths!.push(width);
    };
  });
  await page.setViewportSize({ width: 960, height: 540 });
  await visit(page);
  await position(page, 'hero', 0.3);
  await presented(page);
  const status = await page.locator('[data-film-status]').textContent();
  await page.mouse.move(100, 250);
  await page.mouse.move(300, 260, { steps: 3 });
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-gpu-sheet', 'true');
  expect(
    await page.evaluate(
      () => (window as Window & { filmWidths?: number[]; filmPaintWidths?: number[] }).filmWidths,
    ),
  ).toContain(960);
  await expect
    .poll(() =>
      page.evaluate(() => (window as Window & { filmPaintWidths?: number[] }).filmPaintWidths),
    )
    .toContain(960);
  await page.setViewportSize({ width: 1440, height: 900 });
  await expect
    .poll(() =>
      page
        .locator('video:not([data-loop-video]):visible')
        .evaluate((node) => (node as HTMLVideoElement).videoWidth),
    )
    .toBe(1600);
  await expect(page.locator('[data-film-status]')).toHaveText(status!);
  await page.mouse.move(1100, 500);
  await page.mouse.move(1300, 600, { steps: 3 });
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-gpu-sheet', 'true');
  await expect
    .poll(() =>
      page.evaluate(() =>
        (window as Window & { filmWidths?: number[]; filmPaintWidths?: number[] }).filmWidths?.at(
          -1,
        ),
      ),
    )
    .toBe(1600);
  await expect
    .poll(() =>
      page.evaluate(() => (window as Window & { filmPaintWidths?: number[] }).filmPaintWidths),
    )
    .toContain(1600);
});
