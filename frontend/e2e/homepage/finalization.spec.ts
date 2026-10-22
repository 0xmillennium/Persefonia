import { expect, test, type Page } from '../support/test';
import { openHomepage, startOnFilm } from '../support/homepage';
import { settlePresentation } from '../support/scenes';

// Native readback can be subpixel; compare the painted device-pixel destination.
const nativeY = (page: Page) => page.evaluate(() => Math.round(scrollY * devicePixelRatio));

for (const [hash, category] of [
  ['work', 'projects'],
  ['writing', 'articles'],
] as const) {
  test(`cold ${hash} and clicked navigation share category, focus, URL and history`, async ({
    page,
  }) => {
    await startOnFilm(page);
    await page.goto('/#' + hash);
    await expect(page.locator('[data-home-loader]')).toBeHidden();
    await expect(page.locator('[data-scene="gallery"]')).toHaveAttribute('data-category', category);
    await expect(page.locator('#' + hash)).toBeFocused();
    const cold = await nativeY(page);
    await page.goto('/');
    await expect(page.locator('[data-home-loader]')).toBeHidden();
    await page
      .locator(`.home-nav a[href="#${hash}"]`)
      .evaluate((n) => (n as HTMLAnchorElement).click());
    await expect.poll(() => page.evaluate(() => location.hash)).toBe('#' + hash);
    await expect(page.locator('#' + hash)).toBeFocused();
    await expect.poll(() => nativeY(page)).toBe(cold);
    const other = hash === 'work' ? 'writing' : 'work';
    await page
      .locator(`.home-nav a[href="#${other}"]`)
      .evaluate((n) => (n as HTMLAnchorElement).click());
    await expect.poll(() => page.evaluate(() => location.hash)).toBe('#' + other);
    await page.goBack();
    await expect.poll(() => page.evaluate(() => location.hash)).toBe('#' + hash);
    await expect.poll(() => nativeY(page)).toBe(cold);
    await expect(page.locator('[data-scene="gallery"]')).toHaveAttribute('data-category', category);
    await settlePresentation(page);
    const reloaded = page.waitForEvent('domcontentloaded');
    await page.evaluate(() => location.reload());
    await reloaded;
    await expect(page.locator('[data-home-loader]')).toBeHidden();
    await expect.poll(() => nativeY(page)).toBe(cold);
    expect(
      await page.locator(`.home-nav a[href="#${other}"]`).evaluate((n) => {
        const event = new MouseEvent('click', { bubbles: true, cancelable: true, ctrlKey: true });
        n.dispatchEvent(event);
        return event.defaultPrevented;
      }),
    ).toBe(false);
  });
}

test('cold contact and hashchange reach the same fully readable footer and focus', async ({
  page,
}) => {
  await startOnFilm(page);
  await page.goto('/#contact');
  await expect(page.locator('[data-home-loader]')).toBeHidden();
  await expect(page.locator('#contact')).toBeFocused();
  await expect(page.locator('.location-links .contact-entry')).toBeVisible();
  const cold = await nativeY(page);
  await page.goto('/');
  await expect(page.locator('[data-home-loader]')).toBeHidden();
  await page.evaluate(() => {
    location.hash = 'contact';
  });
  await expect(page.locator('#contact')).toBeFocused();
  await expect.poll(() => nativeY(page)).toBe(cold);
});

test('explicit background pause survives page suspension and resumes only on request', async ({
  page,
}) => {
  await startOnFilm(page, 'waterfall');
  await openHomepage(page);
  const button = page.getByRole('button', { name: 'Pause background motion', exact: true });
  await expect(button).toBeVisible();
  await button.click();
  const resume = page.getByRole('button', { name: 'Resume background motion', exact: true });
  await expect(resume).toHaveAttribute('aria-pressed', 'true');
  await expect
    .poll(() => page.locator('[data-loop-video]').evaluate((v: HTMLVideoElement) => v.paused))
    .toBe(true);
  await page.evaluate(() => {
    dispatchEvent(new PageTransitionEvent('pagehide', { persisted: true }));
    dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true }));
  });
  await expect(resume).toBeVisible();
  await page.waitForTimeout(250);
  expect(await page.locator('[data-loop-video]').evaluate((v: HTMLVideoElement) => v.paused)).toBe(
    true,
  );
  await resume.press('Enter');
  await expect(button).toHaveAttribute('aria-pressed', 'false');
  await expect
    .poll(() => page.locator('[data-loop-video]').evaluate((v: HTMLVideoElement) => v.paused))
    .toBe(false);
});

test('a changing aperture reveals a stable composition without scaling media or story travel', async ({
  page,
}) => {
  await startOnFilm(page, 'waterfall');
  await page.route(/\/assets\/home-[^/]+\.css$/, async (route) => {
    const response = await route.fetch();
    await route.fulfill({
      response,
      body: `${await response.text()}\n.home-composition-probe {height: 1000px !important}`,
    });
  });
  await page.addInitScript(() => {
    const getContext = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (this: HTMLCanvasElement, type, ...args) {
      return String(type).includes('webgl') ? null : getContext.call(this, type, ...args);
    } as typeof getContext;
  });
  await openHomepage(page);
  await page.evaluate(() => {
    const probe = document.querySelector<HTMLElement>('.home-viewport-probe')!;
    probe.style.height = `${probe.clientHeight}px`;
    scrollTo(0, 100);
  });
  const video = page.locator('[data-loop-video]');
  await expect(video).toBeVisible();
  const before = await video.boundingBox();
  const regions = await page
    .locator('[data-scene]')
    .evaluateAll((ns) => ns.map((n) => n.getBoundingClientRect().height));
  const size = page.viewportSize()!;
  for (const height of [size.height + 100, size.height]) {
    await page.setViewportSize({ width: size.width, height });
    await expect(page.locator('.home-environment')).toHaveCSS('height', `${height}px`);
    await expect.poll(async () => (await video.boundingBox())!.width).toBeCloseTo(before!.width, 1);
    expect(await page.evaluate(() => scrollY)).toBe(100);
    expect(
      await page
        .locator('[data-scene]')
        .evaluateAll((ns) => ns.map((n) => n.getBoundingClientRect().height)),
    ).toEqual(regions);
    const box = await video.boundingBox();
    expect(box!.y).toBeLessThanOrEqual(0);
    expect(box!.y + box!.height).toBeGreaterThanOrEqual(height);
  }
});

test('translation opt-out composes with localized public indexing and language metadata', async ({
  page,
}) => {
  for (const path of ['/', '/contact', '/en/projects', '/tr/projects', '/missing-finalization']) {
    await page.goto(path);
    await expect(page.locator('html')).toHaveAttribute('translate', 'no');
    await expect(page.locator('meta[name="google"]')).toHaveAttribute('content', 'notranslate');
    await expect(page.locator('meta[name="googlebot"]')).toHaveAttribute('content', 'notranslate');
    if (path.startsWith('/tr/')) await expect(page.locator('html')).toHaveAttribute('lang', 'tr');
    if (path === '/missing-finalization')
      await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', /noindex/);
  }
});

test('foreign wrapped, split and replaced Unicode text survives resize and re-entry', async ({
  page,
}) => {
  await startOnFilm(page);
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await openHomepage(page);
  for (const content of ['A🙂', 'A much longer translated phrase — Türkçe']) {
    await page
      .locator('.hero-writing-text')
      .first()
      .evaluate((node, content) => {
        const wrapper = document.createElement('font');
        wrapper.append(
          document.createTextNode(content.slice(0, 1)),
          document.createTextNode(content.slice(1)),
        );
        node.replaceChildren(wrapper);
      }, content);
    const size = page.viewportSize()!;
    await page.setViewportSize({ width: size.width + 1, height: size.height });
    await page.evaluate(() => scrollTo(0, 150));
    await expect(page.locator('.hero-writing-text').first()).toBeVisible();
    await expect(page.locator('.hero-writing-text').first()).toHaveText(content);
    await expect(page.locator('.hero-writing-visual').first()).toBeHidden();
    expect(errors).toEqual([]);
    await page.evaluate(() => scrollTo(0, 0));
  }
});

test('globe alternatives keep their targets clear of footer links at narrow and short sizes', async ({
  page,
}) => {
  await startOnFilm(page, 'tree');
  for (const size of [
    { width: 320, height: 850 },
    { width: 390, height: 850 },
    { width: 850, height: 390 },
    { width: 640, height: 450 },
  ]) {
    await page.setViewportSize(size);
    await page.goto('/#contact');
    await expect(page.locator('[data-home-loader]')).toBeHidden();
    const controls = (await page.locator('.globe-controls').boundingBox())!;
    const strip = (await page.locator('.location-strip').boundingBox())!;
    expect(controls.y + controls.height, JSON.stringify(size)).toBeLessThanOrEqual(strip.y - 8);
    for (const button of await page.locator('.globe-controls button').all()) {
      const bounds = (await button.boundingBox())!;
      expect(bounds.width).toBeGreaterThanOrEqual(44);
      expect(bounds.height).toBeGreaterThanOrEqual(44);
      expect(bounds.x).toBeGreaterThanOrEqual(0);
      expect(bounds.x + bounds.width).toBeLessThanOrEqual(size.width);
    }
  }
});
