import { expect, test, type Page } from '@playwright/test';
import { difference } from './support/pixels';

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

test('desktop waterfall has four capsules aligned at their centers and preserves the fixed film during expansion', async ({
  page,
}, info) => {
  test.skip(info.project.name !== 'desktop-1440', 'Four-window desktop composition');
  await page.addInitScript(() =>
    history.replaceState({ ...history.state, homepageV2: { film: 'waterfall' } }, ''),
  );
  await page.clock.install();
  await visit(page);
  const video = page.locator('video[data-loop-video]');
  await expect(page.locator('[data-loop-toggle]')).toHaveCount(0);
  // Freeze only this geometry comparison; production playback remains automatic.
  await expect.poll(() => video.evaluate((node) => !(node as HTMLVideoElement).paused)).toBe(true);
  await video.evaluate((node) => (node as HTMLVideoElement).pause());
  await expect.poll(() => video.evaluate((node) => (node as HTMLVideoElement).paused)).toBe(true);
  await page.clock.pauseAt(await page.evaluate(() => Date.now() + 50));
  const sheet = page.locator('.home-cinema__image');
  const mask = await sheet.evaluate((node) => node.style.maskImage);
  const windows = [...mask.matchAll(/data:image\/svg\+xml,([^"\)]+)/g)].map((match) => {
    const svg = decodeURIComponent(match[1]!);
    const path =
      /Z M([\d.]+) ([\d.]+)H[\d.]+A([\d.]+) [\d.]+ 0 0 1 ([\d.]+) [\d.]+V[\d.]+A[\d.]+ [\d.]+ 0 0 1 [\d.]+ ([\d.]+)/.exec(
        svg,
      )!;
    return {
      x: Number(path[1]),
      top: Number(path[2]),
      width: Number(path[3]) * 2,
      bottom: Number(path[5]),
    };
  });
  expect(windows).toHaveLength(4);
  const [middle, right, farRight, last] = windows;
  expect(middle!.top).toBe(144);
  expect(middle!.bottom).toBe(810);
  expect(middle!.width).toBeCloseTo(275.4);
  const writingRight = await page
    .locator('.hero-writing-text')
    .evaluateAll((nodes) => Math.max(...nodes.map((node) => node.getBoundingClientRect().right)));
  expect(middle!.x - middle!.width / 2 - writingRight).toBeCloseTo(20);
  for (const selector of ['.hero-writing-text', '.statement-word-text', '.brand-statement'])
    expect((await page.locator(selector).allTextContents()).join('')).not.toContain('.');
  for (const side of [right!, farRight!, last!]) {
    expect((side.top + side.bottom) / 2).toBeCloseTo(477);
    expect(side.width).toBeLessThan(middle!.width);
    expect(side.bottom - side.top).toBeLessThan(666);
  }
  expect(right!.x).toBeGreaterThan(middle!.x);
  expect(farRight!.x).toBeGreaterThan(right!.x);
  expect(farRight!.width).toBeLessThan(right!.width);
  expect(last!.x).toBeGreaterThan(farRight!.x);
  expect(last!.width).toBeLessThan(farRight!.width);
  expect(last!.x + last!.width / 2).toBeLessThanOrEqual(1416);
  const bounds = await video.boundingBox();
  await page.screenshot({ path: info.outputPath('waterfall-four-windows.png') });
  // The lower corners are covered by the photo with the new rounded bases.
  const clips = windows.map((window) => ({
    x: Math.ceil(window.x - window.width / 2 + 4),
    y: Math.floor(window.bottom - 12),
    width: 6,
    height: 6,
  }));
  const native = [];
  for (const clip of clips) native.push(await page.screenshot({ clip }));
  await page.mouse.move(1300, 120);
  await page.mouse.move(1360, 130, { steps: 4 });
  await page.clock.runFor(32);
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-gpu-sheet', 'true');
  for (let i = 0; i < clips.length; i++)
    expect(difference(native[i]!, await page.screenshot({ clip: clips[i]! })).mean).toBeLessThan(2);
  await page.clock.fastForward(5000);
  await page
    .locator('[data-scene="hero"]')
    .evaluate((node) =>
      scrollTo(
        0,
        node.getBoundingClientRect().top + scrollY + node.getBoundingClientRect().height * 0.3,
      ),
    );
  await page.evaluate(() => dispatchEvent(new Event('scroll')));
  await page.clock.runFor(64);
  await expect(sheet).not.toHaveCSS('mask-image', mask);
  expect(await video.boundingBox()).toEqual(bounds);
  await page.screenshot({ path: info.outputPath('waterfall-four-expanding.png') });
  await page.evaluate(() => {
    scrollTo(0, 0);
    dispatchEvent(new Event('scroll'));
  });
  await page.clock.runFor(64);
  await expect(sheet).toHaveCSS('mask-image', mask);
});

test('waterfall joins the hold cycle, loops independently of scrolling, and pauses behind the gallery', async ({
  page,
}, info) => {
  test.skip(info.project.name === 'tablet-834', 'Desktop and phone delivery tiers');
  const loopRequests: string[] = [];
  page.on('request', (request) => {
    if (request.url().includes('/home-loops/')) loopRequests.push(request.url());
  });
  await visit(page);
  expect(loopRequests).toEqual([]);
  await select(page, 'tree');
  const point = { x: page.viewportSize()!.width * 0.85, y: page.viewportSize()!.height * 0.68 };
  const touch = info.project.use.hasTouch ? await page.context().newCDPSession(page) : null;
  if (touch)
    await touch.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [point] });
  else {
    await page.mouse.move(point.x, point.y);
    await page.mouse.down();
  }
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'waterfall', {
    timeout: 12000,
  });
  if (touch) {
    await touch.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
    await touch.detach();
  } else await page.mouse.up();
  const video = page.locator('video[data-loop-video]');
  await expect.poll(() => video.evaluate((node) => !(node as HTMLVideoElement).paused)).toBe(true);
  expect(
    await video.evaluate((node) => {
      const v = node as HTMLVideoElement;
      return {
        loop: v.loop,
        muted: v.muted,
        inline: v.playsInline,
        width: v.videoWidth,
        duration: v.duration,
      };
    }),
  ).toEqual({
    loop: true,
    muted: true,
    inline: true,
    width: 1920,
    duration: 8,
  });
  const bounds = await video.boundingBox();
  await page.screenshot({ path: info.outputPath('waterfall-opening.png'), scale: 'css' });
  await scene(page, 'statements');
  await expect(video).toBeVisible();
  expect(await video.boundingBox()).toEqual(bounds);
  const time = await video.evaluate((node) => (node as HTMLVideoElement).currentTime);
  await expect
    .poll(() => video.evaluate((node) => (node as HTMLVideoElement).currentTime))
    .not.toBe(time);
  await expect(page.locator('[data-loop-toggle]')).toHaveCount(0);
  await scene(page, 'mark');
  await expect.poll(() => video.evaluate((node) => !(node as HTMLVideoElement).paused)).toBe(true);
  // Exercise a real native wrap without waiting a full eight-second cycle.
  await video.evaluate((node) => {
    (node as HTMLVideoElement).currentTime = 7.85;
  });
  await expect
    .poll(() => video.evaluate((node) => (node as HTMLVideoElement).currentTime))
    .toBeLessThan(2);
  await scene(page, 'gallery');
  await expect.poll(() => video.evaluate((node) => (node as HTMLVideoElement).paused)).toBe(true);
  await expect(page.locator('[data-loop-toggle]')).toHaveCount(0);
  const covered = await video.evaluate((node) => (node as HTMLVideoElement).currentTime);
  await page.waitForTimeout(200);
  expect(await video.evaluate((node) => (node as HTMLVideoElement).currentTime)).toBe(covered);
  await scene(page, 'statements');
  await expect.poll(() => video.evaluate((node) => !(node as HTMLVideoElement).paused)).toBe(true);
  await select(page, 'robotic-arm');
  const index = await page.evaluate(() => {
    const hero = document.querySelector('[data-scene="hero"]')!.getBoundingClientRect();
    const mark = document.querySelector('[data-scene="mark"]')!.getBoundingClientRect();
    const start = hero.top + scrollY,
      end = mark.top + scrollY + mark.height * 0.98;
    return Math.round(((scrollY - start) / (end - start)) * 191);
  });
  await expect(page.locator('[data-film-status]')).toHaveText(
    `Robotic arm · ${String(index + 1).padStart(3, '0')} / 192`,
  );
  await expect.poll(() => video.evaluate((node) => (node as HTMLVideoElement).paused)).toBe(true);
});

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

test('loop failures retain a poster and retry, menu, hidden-page and reduced-motion states stop playback', async ({
  page,
}, info) => {
  test.skip(info.project.name === 'desktop-1440', 'Phone and tablet menu lifecycle');
  let fail = true;
  await page.route('**/waterfall.mp4*', (route) =>
    fail ? route.fulfill({ status: 503, body: 'unavailable video' }) : route.continue(),
  );
  await visit(page);
  const video = await waterfall(page);
  await expect(page.locator('[data-loop-toggle]')).toHaveCount(0);
  await expect(page.locator('[data-film-status]')).toContainText('still frame retained');
  await page.screenshot({ path: info.outputPath('waterfall-poster.png'), scale: 'css' });
  fail = false;
  await page.mouse.click(30, 300);
  await expect.poll(() => video.evaluate((node) => !(node as HTMLVideoElement).paused)).toBe(true);
  await page.getByRole('button', { name: 'Open menu', exact: true }).click();
  await expect.poll(() => video.evaluate((node) => (node as HTMLVideoElement).paused)).toBe(true);
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toBeHidden();
  await expect.poll(() => video.evaluate((node) => !(node as HTMLVideoElement).paused)).toBe(true);
  await page.evaluate(() => {
    Object.defineProperty(document, 'hidden', { configurable: true, value: true });
    document.dispatchEvent(new Event('visibilitychange'));
  });
  await expect.poll(() => video.evaluate((node) => (node as HTMLVideoElement).paused)).toBe(true);
  await page.evaluate(() => {
    Reflect.deleteProperty(document, 'hidden');
    document.dispatchEvent(new Event('visibilitychange'));
  });
  await expect.poll(() => video.evaluate((node) => !(node as HTMLVideoElement).paused)).toBe(true);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect.poll(() => video.evaluate((node) => (node as HTMLVideoElement).paused)).toBe(true);
  await expect(page.locator('[data-loop-toggle]')).toHaveCount(0);
  await expect(page.locator('[data-scene="mark"] .scene-face')).not.toHaveAttribute('inert');
  await select(page, 'robotic-arm');
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await expect(page.locator('[data-loop-toggle]')).toHaveCount(0);
});

test('a refused play promise retains a still image and retries through the existing background gesture', async ({
  page,
}, info) => {
  test.skip(
    info.project.name !== 'desktop-1440',
    'Playback policy is independent of viewport size',
  );
  await page.addInitScript(() => {
    const play = HTMLMediaElement.prototype.play;
    let refuse = true;
    HTMLMediaElement.prototype.play = function () {
      if (this.hasAttribute('data-loop-video') && refuse) {
        refuse = false;
        return Promise.reject(
          new DOMException('Playback refused by browser policy', 'NotAllowedError'),
        );
      }
      return play.call(this);
    };
  });
  await visit(page);
  const video = await waterfall(page);
  await expect(page.locator('[data-film-status]')).toContainText('still frame retained');
  await expect(video).toBeVisible();
  await page.mouse.click(30, 300);
  await expect.poll(() => video.evaluate((node) => !(node as HTMLVideoElement).paused)).toBe(true);
  await expect(page.locator('[data-film-status]')).toHaveText('Waterfall · loop');
});

test('a menu interruption while play is pending resumes without treating cancellation as denial', async ({
  page,
}, info) => {
  test.skip(info.project.name !== 'tablet-834', 'Compact menu with a delayed native play promise');
  await page.addInitScript(() => {
    const play = HTMLMediaElement.prototype.play;
    let delay = true;
    HTMLMediaElement.prototype.play = function () {
      if (this.hasAttribute('data-loop-video') && delay) {
        delay = false;
        return new Promise<void>((_resolve, reject) => {
          (window as Window & { cancelPendingPlay: () => void }).cancelPendingPlay = () =>
            reject(new DOMException('Interrupted play', 'AbortError'));
        });
      }
      return play.call(this);
    };
  });
  await visit(page);
  const video = await waterfall(page);
  await expect
    .poll(() =>
      page.evaluate(
        () => typeof (window as Window & { cancelPendingPlay?: () => void }).cancelPendingPlay,
      ),
    )
    .toBe('function');
  await page.getByRole('button', { name: 'Open menu', exact: true }).click();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toBeHidden();
  await page.evaluate(() =>
    (window as Window & { cancelPendingPlay: () => void }).cancelPendingPlay(),
  );
  await expect.poll(() => video.evaluate((node) => !(node as HTMLVideoElement).paused)).toBe(true);
  await expect(page.locator('[data-film-status]')).toHaveText('Waterfall · loop');
});
