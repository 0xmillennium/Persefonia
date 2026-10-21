import { startOnFilm } from '../../support/homepage';
import { expect, test } from '../../support/test';
import { visit, position, selectFilm } from '../../support/scenes';
import { HOLD } from '../../../src/homepage/film/hold/state';
import { difference, pixels } from '../../support/pixels';

test.beforeEach(async ({ page }) => startOnFilm(page));

test('navigator paints and then releases the photo outside the opening doorway', async ({
  page,
}, testInfo) => {
  test.skip(
    testInfo.project.name !== 'desktop-1440',
    'Fine-pointer photo composition at the desktop review size',
  );
  await page.clock.install();
  await visit(page);
  await expect(page.locator('video.film-presentation').first()).toBeVisible();
  await expect(page.locator('[data-film-status]')).toHaveText('Robotic arm · 001 / 192');
  await page.clock.pauseAt(await page.evaluate(() => Date.now() + 50));
  // This empty region is outside the openings and excludes all text and controls.
  const clip = { x: 120, y: 100, width: 480, height: 100 };
  const clean = await page.screenshot({ clip });
  await page.mouse.move(160, 145);
  await page.mouse.move(550, 155, { steps: 12 });
  await page.clock.runFor(32);
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-gpu-sheet', 'true');
  const painted = await page.screenshot({ clip, path: testInfo.outputPath('photo-navigator.png') });
  expect(difference(clean, painted).changed).toBeGreaterThan(500);
  await page.mouse.move(700, 250);
  await page.clock.runFor(32);
  await page.clock.fastForward(4700);
  const settled = await page.screenshot({ clip });
  expect(difference(clean, settled).changed).toBeLessThan(100);
  await expect(page.locator('[data-film-status]')).toHaveText('Robotic arm · 001 / 192');
});

test('hold opens the photo to the current frame and restores it after cancel or switch', async ({
  page,
}, info) => {
  test.skip(
    info.project.name === 'tablet-834',
    'Desktop and native touch exercise the same composition',
  );
  await page.clock.install();
  await visit(page);
  await expect(page.locator('video.film-presentation').first()).toBeVisible();
  await expect(page.locator('[data-home-loader]')).toBeHidden();
  // Capture overhead must not turn an early-release gesture into a completed hold.
  await page.clock.pauseAt(await page.evaluate(() => Date.now() + 50));
  const touch = info.project.name.startsWith('mobile');
  const point = touch ? { x: 78, y: 382 } : { x: 330, y: 180 };
  // These crops stay outside both scroll films' openings, over charcoal in arm frame 001.
  // The phone sample sits below the centered writing, away from controls/countdown/boundary.
  const clip = touch
    ? { x: 80, y: 510, width: 15, height: 15 }
    : { x: 260, y: 115, width: 50, height: 30 };
  const session = touch ? await page.context().newCDPSession(page) : null;
  if (!touch) await page.mouse.move(point.x, point.y);
  const press = async () => {
    if (session)
      await session.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [point] });
    else await page.mouse.down();
  };
  const release = async () => {
    if (session)
      await session.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
    else await page.mouse.up();
  };
  const clean = await page.screenshot({ clip });
  const constellation = await page
    .locator('.home-cinema__image')
    .evaluate((node) => getComputedStyle(node).maskImage);
  expect(constellation.match(/radial-gradient/g)).toHaveLength(7);
  await press();
  await page.clock.fastForward(1300);
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-gpu-sheet', 'true');
  await expect(page.locator('[data-hold-countdown]')).toBeVisible();
  const revealed = await page.screenshot({ clip });
  await page.screenshot({ path: info.outputPath('hold-current-frame.png') });
  const sample = pixels(revealed);
  let variation = 0;
  for (let i = 0; i < sample.data.length; i += sample.channels)
    for (let c = 0; c < 3; c++)
      variation = Math.max(variation, Math.abs(sample.data[i + c]! - sample.data[c]!));
  expect(variation).toBeLessThan(8);
  expect(difference(clean, revealed).fraction).toBeGreaterThan(0.4);
  await expect(page.locator('[data-film-status]')).toHaveText('Robotic arm · 001 / 192');
  await release();
  await page.clock.fastForward(1800);
  expect(difference(clean, await page.screenshot({ clip })).changed).toBeLessThan(5);
  await page.screenshot({ path: info.outputPath('hold-canceled.png') });
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'robotic-arm');
  await press();
  await page.clock.fastForward(3300);
  await page.screenshot({ path: info.outputPath('hold-full-current-frame.png') });
  await expect(page.locator('[data-film-status]')).toHaveText('Robotic arm · 001 / 192');
  await page.clock.fastForward(80);
  await page.clock.fastForward(180);
  await page.screenshot({ path: info.outputPath('hold-incoming-restore.png') });
  await page.clock.fastForward(220);
  await expect(page.locator('[data-film-status]')).toHaveText('Tree · 001 / 192', {
    timeout: 12000,
  });
  await release();
  await page.clock.fastForward(32);
  expect(difference(clean, await page.screenshot({ clip })).changed).toBeLessThan(5);
  await expect(page.locator('.home-cinema__image')).toHaveCSS('clip-path', 'none');
  await expect(page.locator('.home-cinema__image')).toHaveCSS('mask-image', /data:image\/svg\+xml/);
  await page.screenshot({ path: info.outputPath('hold-switched.png') });
  await session?.detach();
});

test('gallery cover hides the live navigator trail and a quick reversal reveals it until it fades', async ({
  page,
}, info) => {
  await page.clock.install();
  await visit(page);
  const canvas = page.locator('[data-film-canvas]');
  const coveredByGallery = () =>
    canvas.evaluate((node) => {
      const environment = node.closest('.home-environment');
      const cover = document.querySelector('[data-scene-wipe]')!;
      return (
        !!environment &&
        Number(getComputedStyle(environment).zIndex) < Number(getComputedStyle(cover).zIndex)
      );
    });
  // The final film pose is the same on both sides of the cover. This lets
  // pixel differences identify the trail, rather than a changed film frame.
  await position(page, 'mark', 0.979);
  await expect(page.locator('[data-film-status]')).toHaveText('Robotic arm · 192 / 192');
  await page.clock.pauseAt(await page.evaluate(() => Date.now() + 50));
  await expect(page.locator('[data-scene-wipe]')).toBeHidden();
  // Empty left margin: gallery tilt, labels and the pointer cannot affect it.
  const clip = { x: 0, y: 400, width: 16, height: 120 };
  const clean = await page.screenshot({ clip, scale: 'css' });
  await position(page, 'gallery', 0.7);
  await page.evaluate(() => dispatchEvent(new Event('scroll')));
  await page.clock.runFor(64);
  await expect(page.locator('[data-scene-wipe]')).toHaveCSS('--curtain-solid', '1');
  const covered = await page.screenshot({ clip, scale: 'css' });
  await page.mouse.move(30, 300);
  await page.mouse.move(30, 600, { steps: 6 });
  await page.mouse.move(100, 630);
  await page.clock.runFor(32);
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-gpu-sheet', 'true');
  await expect(canvas).toHaveCSS('opacity', '1');
  expect(await coveredByGallery()).toBe(true);
  expect(difference(covered, await page.screenshot({ clip, scale: 'css' })).changed).toBe(0);
  await page.screenshot({ path: info.outputPath('navigator-hidden-under-gallery.png') });
  await position(page, 'mark', 0.979);
  await page.evaluate(() => dispatchEvent(new Event('scroll')));
  await page.clock.runFor(64);
  await expect(page.locator('[data-scene-wipe]')).toBeHidden();
  expect(difference(clean, await page.screenshot({ clip, scale: 'css' })).changed).toBeGreaterThan(
    100,
  );
  await page.screenshot({ path: info.outputPath('navigator-revealed-on-reversal.png') });
  await page.clock.fastForward(5000);
  await expect(canvas).toHaveCSS('opacity', '0');
  expect(difference(clean, await page.screenshot({ clip, scale: 'css' })).mean).toBeLessThan(0.1);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.clock.runFor(64);
  const quiet = await page.screenshot({ clip, scale: 'css' });
  await page.mouse.move(30, 300);
  await page.mouse.move(30, 600, { steps: 6 });
  await page.clock.runFor(64);
  expect(difference(quiet, await page.screenshot({ clip, scale: 'css' })).changed).toBe(0);
});

test('failed incoming video and poster retain the outgoing film and recover through explicit retry', async ({
  page,
}) => {
  let fail = true;
  await page.route('**/home-posters/**/tree/*.webp*', (route) =>
    fail ? route.fulfill({ status: 503, body: 'temporarily unavailable' }) : route.continue(),
  );
  await page.route('**/tree.mp4*', (route) =>
    fail ? route.fulfill({ status: 503, body: 'temporarily unavailable' }) : route.continue(),
  );
  await visit(page);
  await position(page, 'hero', 0.3);
  await selectFilm(page);
  await expect(page.locator('[data-film-status]')).toContainText('Incoming film not ready', {
    timeout: 18000,
  });
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'robotic-arm');
  fail = false;
  await selectFilm(page);
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'tree', {
    timeout: (HOLD.readinessTimeout + HOLD.revealDuration + 2) * 1000,
  });
  await expect(page.locator('[data-film-status]')).toHaveText(/Tree · \d{3} \/ 192/);
  await expect(page.locator('[data-film-status]')).not.toContainText('unavailable');
});

test('environment hold cancels, resumes and switches once; touch scrolling stays native', async ({
  page,
}, info) => {
  const controlled = !info.project.name.startsWith('mobile');
  if (controlled) await page.clock.install();
  await visit(page);
  await position(page, 'hero', 0.3);
  const viewport = page.viewportSize()!;
  const point = { x: viewport.width * 0.8, y: viewport.height * 0.72 };
  const film = page.locator('[data-home-v2]');
  const countdown = page.locator('[data-hold-countdown]');
  if (info.project.name.startsWith('mobile')) {
    const session = await page.context().newCDPSession(page);
    const before = await page.evaluate(() => scrollY);
    // Preserve an 80ms swipe even when software rendering delays protocol replies.
    const swipeStarted = Date.now() / 1000;
    await session.send('Input.dispatchTouchEvent', {
      type: 'touchStart',
      touchPoints: [point],
      timestamp: swipeStarted,
    });
    for (let i = 1; i <= 8; i++)
      await session.send('Input.dispatchTouchEvent', {
        type: 'touchMove',
        touchPoints: [{ x: point.x, y: point.y - i * 15 }],
        timestamp: swipeStarted + i * 0.01,
      });
    await session.send('Input.dispatchTouchEvent', {
      type: 'touchEnd',
      touchPoints: [],
      timestamp: swipeStarted + 0.09,
    });
    await expect(countdown).toBeHidden();
    await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(before);
    await expect(film).toHaveAttribute('data-film', 'robotic-arm');
    await session.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [point] });
    await expect(film).toHaveAttribute('data-film', 'tree', { timeout: 10000 });
    await page.waitForTimeout(700);
    await expect(film).toHaveAttribute('data-film', 'tree');
    await session.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
    await session.detach();
  } else {
    await page.clock.pauseAt(await page.evaluate(() => Date.now() + 50));
    await page.mouse.move(point.x, point.y);
    await page.mouse.down();
    await page.clock.runFor(32);
    await expect(countdown).toBeVisible();
    // Decode readiness and software rendering must not consume the short press.
    await expect
      .poll(() =>
        page.locator('video[src*="/tree.mp4"]').evaluate((node) => {
          const video = node as HTMLVideoElement;
          return video.readyState >= 2 && !video.seeking && video.currentTime > 0;
        }),
      )
      .toBe(true);
    await page.clock.fastForward(318);
    await page.mouse.up();
    await page.clock.runFor(32);
    await expect(countdown).toBeHidden();
    await expect(film).toHaveAttribute('data-film', 'robotic-arm');
    await page.mouse.down();
    await page.clock.fastForward(3500);
    await page.clock.runFor(32);
    await page.clock.fastForward(500);
    await page.clock.runFor(32);
    await expect(film).toHaveAttribute('data-film', 'tree', { timeout: 10000 });
    await page.clock.fastForward(700);
    await expect(film).toHaveAttribute('data-film', 'tree');
    await page.mouse.up();
  }
});
