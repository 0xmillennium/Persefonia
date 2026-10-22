import { startOnFilm } from '../../support/homepage';
import { expect, test, type Page } from '../../support/test';

test.beforeEach(async ({ page }) => startOnFilm(page));

test.beforeEach(async ({ page }, info) => {
  test.skip(info.project.name !== 'mobile-390', 'Native touch input at phone size');
  await page.goto('/');
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
  await expect(page.locator('[data-home-loader]')).toBeHidden();
});

async function touch(page: Page) {
  const session = await page.context().newCDPSession(page);
  return {
    start: (x = 180, y = 400) =>
      session.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x, y }] }),
    move: (x: number, y: number) =>
      session.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x, y }] }),
    end: () => session.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] }),
    cancel: () =>
      session.send('Input.dispatchTouchEvent', { type: 'touchCancel', touchPoints: [] }),
    detach: () => session.detach(),
  };
}

test('settled hold tolerates finger drift and changes the film only once until release', async ({
  page,
}) => {
  const finger = await touch(page),
    countdown = page.locator('[data-hold-countdown]');
  const initialY = await page.evaluate(() => {
    const probe = { prevented: false, canceled: false };
    Object.assign(window, { holdTouchProbe: probe });
    window.addEventListener('touchmove', (event) => (probe.prevented = event.defaultPrevented), {
      passive: true,
    });
    window.addEventListener('pointercancel', () => (probe.canceled = true));
    return scrollY;
  });
  await finger.start();
  await expect(countdown).toBeVisible();
  await page.waitForTimeout(450);
  // Small jitter stays a hold candidate; directional motion remains a native pan.
  await finger.move(181, 402);
  // Native input/trace delivery can take longer than the countdown on a software
  // renderer. Check ownership directly instead of requiring a transient digit.
  expect(await page.evaluate(() => Reflect.get(window, 'holdTouchProbe'))).toEqual({
    prevented: false,
    canceled: false,
  });
  expect(await page.evaluate(() => scrollY)).toBe(initialY);
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'tree', {
    timeout: 12000,
  });
  await page.waitForTimeout(500);
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'tree');
  await finger.end();
  await finger.detach();
});

async function hesitantSwipe(page: Page) {
  // Own the application clock: software-GPU/CDP delivery can turn a requested
  // 220ms wait into several seconds, which would qualify a different gesture.
  await page.clock.install();
  await page.clock.pauseAt(await page.evaluate(() => Date.now() + 1000));
  await page.evaluate(() => {
    const times = { start: 0, move: 0 };
    Reflect.set(window, 'hesitantTouchTiming', times);
    window.addEventListener(
      'touchstart',
      () => {
        times.start = performance.now();
      },
      { once: true },
    );
    window.addEventListener(
      'touchmove',
      () => {
        times.move = performance.now();
      },
      { once: true },
    );
  });
  const finger = await touch(page);
  const before = await page.evaluate(() => scrollY);
  await finger.start(100, 620);
  await page.clock.runFor(220);
  for (let i = 1; i <= 6; i++) {
    await finger.move(100 + i, 620 - i * 28);
    await page.clock.runFor(16);
  }
  await finger.end();
  const times = await page.evaluate(
    () => Reflect.get(window, 'hesitantTouchTiming') as { start: number; move: number },
  );
  expect(times.move - times.start).toBeGreaterThanOrEqual(220);
  expect(times.move - times.start).toBeLessThanOrEqual(252);
  await page.clock.resume();
  await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(before + 30);
  await expect(page.locator('[data-hold-countdown]')).toBeHidden();
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'robotic-arm');
  await finger.detach();
}

test('film long-press suppresses selection and its menu while links retain their native menu', async ({
  page,
}) => {
  const finger = await touch(page);
  await finger.start();
  await expect(page.locator('[data-hold-countdown]')).toBeVisible();
  const menus = await page.evaluate(() => {
    const fire = (target: Element, pointerType: string) => {
      const event = new PointerEvent('contextmenu', {
        bubbles: true,
        cancelable: true,
        pointerType,
      });
      target.dispatchEvent(event);
      return event.defaultPrevented;
    };
    const writing = document.querySelector('.hero-writing-text')!;
    return {
      film: fire(writing, 'touch'),
      link: fire(document.querySelector('.home-nav a')!, 'touch'),
      selection: getComputedStyle(writing).userSelect,
    };
  });
  expect(menus).toEqual({ film: true, link: false, selection: 'none' });
  await finger.end();
  // Ordinary mouse context menus remain available on the same film surface.
  expect(
    await page
      .locator('.hero-writing-text')
      .first()
      .evaluate((target) => {
        const event = new PointerEvent('contextmenu', {
          bubbles: true,
          cancelable: true,
          pointerType: 'mouse',
        });
        target.dispatchEvent(event);
        return event.defaultPrevented;
      }),
  ).toBe(false);
  await finger.detach();
});

test('quick swipes retain native scrolling and do not complete a film hold', async ({ page }) => {
  const finger = await touch(page),
    before = await page.evaluate(() => scrollY);
  await finger.start(100, 620);
  for (let i = 1; i <= 6; i++) await finger.move(100, 620 - i * 24);
  await finger.end();
  await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(before + 30);
  await expect(page.locator('[data-hold-countdown]')).toBeHidden();
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'robotic-arm');
  await finger.detach();
  // Exercise the delayed intent boundary under the same native-phone policy.
  await page.goto('/');
  await expect(page.locator('[data-home-loader]')).toBeHidden();
  await hesitantSwipe(page);
});

test('release and browser cancellation stop an unfinished hold', async ({ page }) => {
  // Native input and trace round trips can outlast an unfinished press on a busy
  // runner. Own the gesture clock while retaining actual browser touch events.
  await page.clock.install();
  await page.clock.pauseAt(await page.evaluate(() => Date.now() + 1000));
  const finger = await touch(page),
    countdown = page.locator('[data-hold-countdown]');
  await finger.start();
  await page.clock.runFor(300);
  await expect(countdown).toBeVisible();
  await expect(countdown).toHaveText('3');
  await finger.end();
  await page.clock.fastForward(4000);
  await page.clock.runFor(32);
  await expect(countdown).toBeHidden();
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'robotic-arm');
  await finger.start();
  await page.clock.runFor(300);
  await expect(countdown).toBeVisible();
  await expect(countdown).toHaveText('3');
  await finger.cancel();
  await page.clock.fastForward(4000);
  await page.clock.runFor(32);
  await expect(countdown).toBeHidden();
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'robotic-arm');
  await finger.detach();
});
