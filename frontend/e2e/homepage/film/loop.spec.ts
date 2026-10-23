import { startOnFilm } from '../../support/homepage';
import { expect, test, type Page } from '../../support/test';

test.beforeEach(async ({ page }) => startOnFilm(page));

const errors = new WeakMap<Page, string[]>();
test.beforeEach(({ page }) => {
  const failures: string[] = [];
  errors.set(page, failures);
  page.on('pageerror', (error) => failures.push(error.message));
});
test.afterEach(({ page }) => {
  expect(errors.get(page)).toEqual([]);
});
async function visit(page: Page) {
  await page.goto('/');
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
  await expect(page.locator('[data-home-loader]')).toBeHidden();
}
async function select(page: Page, film: string) {
  await page.locator('main').focus();
  await page.keyboard.press('f');
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', film);
}
async function waterfall(page: Page) {
  await select(page, 'tree');
  await select(page, 'waterfall');
  return page.locator('video[data-loop-video]');
}
async function scene(page: Page, name: string) {
  await page.locator(`[data-scene="${name}"]`).evaluate((node, name) => {
    const rect = node.getBoundingClientRect();
    const entry =
      parseFloat((node as HTMLElement).style.getPropertyValue('--gallery-entry-distance')) || 0;
    scrollTo(
      0,
      rect.top + scrollY + (name === 'gallery' ? entry + (rect.height - entry) * 0.4 : 640),
    );
  }, name);
  await expect(page.locator(`[data-scene="${name}"] .scene-face`)).toHaveAttribute(
    'data-visible',
    'true',
  );
}

test('liquid effect uploads new waterfall frames and native playback survives context loss', async ({
  page,
}, info) => {
  test.skip(info.project.name === 'tablet-834', 'Same renderer with both viewport compositions');
  await page.clock.install();
  await page.addInitScript(() => {
    (window as Window & { loopUploads: number[] }).loopUploads = [];
    for (const name of ['texImage2D', 'texSubImage2D'] as const) {
      const original = WebGL2RenderingContext.prototype[name];
      (WebGL2RenderingContext.prototype[name] as unknown) = function (
        this: WebGL2RenderingContext,
        ...args: unknown[]
      ) {
        const source = args.at(-1);
        if (source instanceof HTMLVideoElement && source.hasAttribute('data-loop-video'))
          (window as Window & { loopUploads: number[] }).loopUploads.push(source.currentTime);
        return (original as (...args: unknown[]) => void).apply(this, args);
      };
    }
  });
  await visit(page);
  const video = await waterfall(page);
  await scene(page, 'statements');
  await expect.poll(() => video.evaluate((node) => !(node as HTMLVideoElement).paused)).toBe(true);
  await page.clock.pauseAt(await page.evaluate(() => Date.now() + 50));
  const { width, height } = page.viewportSize()!;
  await page.mouse.move(width * 0.1, height * 0.4);
  await page.mouse.move(width * 0.9, height * 0.6, { steps: 12 });
  await page.clock.runFor(64);
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-gpu-sheet', 'true');
  await expect
    .poll(async () => {
      await page.clock.runFor(32);
      return page.evaluate(
        () =>
          new Set(
            (window as Window & { loopUploads: number[] }).loopUploads.map((t) =>
              Math.floor(t * 24),
            ),
          ).size,
      );
    })
    .toBeGreaterThanOrEqual(3);
  await page.screenshot({ path: info.outputPath('waterfall-liquid.png'), scale: 'css' });
  await page
    .locator('[data-film-canvas]')
    .evaluate((node) =>
      (node as HTMLCanvasElement)
        .getContext('webgl2')!
        .getExtension('WEBGL_lose_context')!
        .loseContext(),
    );
  await expect
    .poll(async () => {
      await page.clock.runFor(32);
      return video.isVisible();
    })
    .toBe(true);
  const time = await video.evaluate((node) => (node as HTMLVideoElement).currentTime);
  await expect
    .poll(() => video.evaluate((node) => (node as HTMLVideoElement).currentTime))
    .not.toBe(time);
});
