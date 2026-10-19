import { openHomepage } from '../../support/homepage';
import { expect, test, type Page } from '@playwright/test';

test.afterEach(({}, info) => {
  if (info.status !== info.expectedStatus)
    for (const error of info.errors) console.log(error.message);
});

const root = (page: Page) => page.locator('[data-scene="gallery"]');
const tuner = (page: Page) => page.locator('.collection-tuner');
async function visit(page: Page) {
  await openHomepage(page);
  await page
    .locator('.home-nav a[href="#work"]')
    .evaluate((node) => (node as HTMLAnchorElement).click());
  await at(page, -3);
  await page.mouse.move(20, 650);
  for (let i = 0; i < 3; i++) await page.mouse.wheel(0, 120);
  await at(page, 0);
  await page.waitForTimeout(250);
}
async function at(page: Page, index: number) {
  await expect
    .poll(() => root(page).evaluate((node) => Number((node as HTMLElement).dataset.galleryIndex)))
    .toBe(index);
}
async function choose(page: Page, value: string) {
  await tuner(page).click();
  await page.locator(`input[name="gallery-category"][value="${value}"]`).check();
  await expect(root(page)).toHaveAttribute('data-category', value);
  await at(page, 0);
  await expect(page.locator('.collection-options')).toBeHidden();
  await expect(page.locator('.gallery-category:not([hidden])')).toHaveCount(1);
}
async function curtain(page: Page) {
  return page.locator('[data-scene-wipe]').evaluate((node) => ({
    solid: (node as HTMLElement).style.getPropertyValue('--curtain-solid'),
    bands: [...node.children].map((child) => (child as HTMLElement).style.transform),
    y: scrollY,
  }));
}

test('tuning holds the page and curtain, resets each collection, and restores the selected article on back', async ({
  page,
}, info) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await visit(page);
  const before = await curtain(page);
  await expect(page.locator('.collection-carriage,.gallery-controls,.gallery-more')).toHaveCount(0);
  await page.screenshot({ path: info.outputPath('tuner-projects.png'), scale: 'css' });
  for (const category of ['articles', 'notes', 'research', 'projects', 'articles']) {
    await choose(page, category);
    expect(await curtain(page)).toEqual(before);
    const links = await page
      .locator(`.gallery-category[data-category="${category}"] h3 a`)
      .evaluateAll((nodes) => nodes.map((n) => n.getAttribute('href')));
    expect(links.length).toBeGreaterThan(0);
    expect(links.every((href) => href?.startsWith(`/en/${category}/`))).toBe(true);
  }
  await page.mouse.move(20, 650);
  await expect(page.locator('[data-gallery-position]')).toHaveText('1 / 2');
  const initialY = await page.evaluate(() => scrollY);
  await page.mouse.wheel(0, 120);
  await at(page, 1);
  await expect(page.locator('[data-gallery-position]')).toHaveText('2 / 2');
  await page.mouse.wheel(0, -120);
  await at(page, 0);
  expect(await page.evaluate(() => scrollY)).toBe(initialY);
  const article = page.locator('#articles .gallery-card').nth(1).locator('h3 a');
  await tuner(page).focus();
  await page.keyboard.press('Tab');
  await expect(page.locator('#articles h3 a').first()).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(article).toBeFocused();
  await expect(page.locator('[data-gallery-position]')).toHaveText('2 / 2');
  await expect(page.locator('#articles .gallery-card[data-front="true"] h3 a')).toHaveAttribute(
    'href',
    (await article.getAttribute('href'))!,
  );
  await page.waitForTimeout(300);
  const y = await page.evaluate(() => scrollY),
    href = await article.getAttribute('href');
  await page.screenshot({ path: info.outputPath('tuner-articles.png'), scale: 'css' });
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(new RegExp(`${href}$`));
  await page.goBack({ waitUntil: 'networkidle' });
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(y);
  await expect(root(page)).toHaveAttribute('data-category', 'articles');
  await expect(page.locator('#articles .gallery-card[data-front="true"] h3 a')).toHaveAttribute(
    'href',
    href!,
  );
  expect(errors).toEqual([]);
});

test('fresh tuner wheel gestures stay local, through wrapping, while page-started gestures keep their owner', async ({
  page,
}) => {
  await visit(page);
  const before = await curtain(page);
  await tuner(page).hover();
  for (const category of ['articles', 'notes', 'research', 'projects']) {
    await page.mouse.wheel(0, 120);
    await expect(root(page)).toHaveAttribute('data-category', category);
    expect(await curtain(page)).toEqual(before);
  }
  // Dispatch each crossing in one task so browser automation delays cannot
  // accidentally turn it into two distinct gestures (the idle cutoff is 180ms).
  await page.evaluate(() => {
    const button = document.querySelector('.collection-tuner')!;
    const outside = document.elementFromPoint(20, 650)!;
    button.dispatchEvent(new WheelEvent('wheel', { deltaY: 120, bubbles: true, cancelable: true }));
    outside.dispatchEvent(
      new WheelEvent('wheel', { deltaY: 120, bubbles: true, cancelable: true }),
    );
  });
  expect(await curtain(page)).toEqual(before);
  await page.waitForTimeout(210);
  await page.evaluate(() => {
    const outside = document.elementFromPoint(20, 650)!;
    outside.dispatchEvent(
      new WheelEvent('wheel', { deltaY: 120, bubbles: true, cancelable: true }),
    );
    document
      .querySelector('.collection-tuner')!
      .dispatchEvent(new WheelEvent('wheel', { deltaY: 120, bubbles: true, cancelable: true }));
  });
  await at(page, 2);
  await expect(root(page)).toHaveAttribute('data-category', 'notes');
  await page.waitForTimeout(210);
  const y = await page.evaluate(() => scrollY);
  await page.mouse.wheel(0, -120);
  await expect(root(page)).toHaveAttribute('data-category', 'articles');
  await at(page, 0);
  expect(await page.evaluate(() => scrollY)).toBe(y);
});

test('a paused drag commits the nearest name after settling, cancels cleanly, and keyboard/reduced motion remain usable', async ({
  page,
}, info) => {
  await visit(page);
  const before = await curtain(page),
    box = (await tuner(page).boundingBox())!;
  await page.mouse.move(box.x + box.width * 0.7, box.y + 28);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width * 0.35, box.y + 29, { steps: 8 });
  await expect(page.locator('.tuner-name')).toHaveText('Articles');
  await expect(root(page)).toHaveAttribute('data-category', 'projects');
  // A deliberate pause distinguishes precise placement from a momentum flick.
  await page.waitForTimeout(130);
  await page.mouse.up();
  await expect(root(page)).toHaveAttribute('data-category', 'articles');
  await expect(page.locator('.collection-options')).toBeHidden();
  expect(await curtain(page)).toEqual(before);
  await page.mouse.move(box.x + box.width * 0.7, box.y + 28);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width * 0.35, box.y + 29, { steps: 5 });
  await expect(page.locator('.tuner-name')).toHaveText('Notes');
  await tuner(page).dispatchEvent('pointercancel', { pointerId: 1 });
  await page.mouse.up();
  await expect(page.locator('.tuner-name')).toHaveText('Articles');
  await expect(root(page)).toHaveAttribute('data-category', 'articles');
  await tuner(page).focus();
  await page.keyboard.press('End');
  await expect(root(page)).toHaveAttribute('data-category', 'research');
  await page.keyboard.press('ArrowRight');
  await expect(root(page)).toHaveAttribute('data-category', 'projects');
  await page.keyboard.press('Home');
  await expect(root(page)).toHaveAttribute('data-category', 'projects');
  await page.keyboard.press('Enter');
  await expect(page.locator('.collection-options')).toBeVisible();
  await expect(page.locator('input[value="projects"]')).toBeFocused();
  await page.keyboard.press('ArrowRight');
  await expect(root(page)).toHaveAttribute('data-category', 'articles');
  await expect(tuner(page)).toBeFocused();
  await page.keyboard.press('Enter');
  await page.keyboard.press('Escape');
  await expect(page.locator('.collection-options')).toBeHidden();
  await expect(tuner(page)).toBeFocused();
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect(page.locator('.gallery-category:not([hidden])')).toHaveCount(1);
  await tuner(page).scrollIntoViewIfNeeded();
  await page.keyboard.press('ArrowRight');
  await expect(root(page)).toHaveAttribute('data-category', 'notes');
  expect(
    await page
      .locator('.gallery-category,.tuner-name')
      .evaluateAll((nodes) => nodes.flatMap((n) => n.getAnimations()).length),
  ).toBe(0);
  await expect(page.locator('.tuner-scale i').first()).toHaveCSS('transition-duration', '0s');
  await page.screenshot({ path: info.outputPath('tuner-reduced.png'), scale: 'css' });
});

test('the tuner and its choices fit intermediate widths and short viewports', async ({
  page,
}, info) => {
  test.skip(
    info.project.name !== 'desktop-1440',
    'Explicitly sweeps desktop, tablet, mobile, and short viewports',
  );
  await visit(page);
  for (const [width, height] of [
    [1440, 900],
    [1280, 850],
    [1100, 850],
    [960, 850],
    [834, 900],
    [390, 850],
    [360, 850],
    [1440, 500],
  ]) {
    await page.setViewportSize({ width: width!, height: height! });
    await page
      .locator('.home-nav a[href="#work"]')
      .evaluate((node) => (node as HTMLAnchorElement).click());
    await at(page, -3);
    await page.mouse.move(20, Math.min(650, height! - 20));
    for (let i = 0; i < 3; i++) {
      await page.mouse.wheel(0, 120);
      await at(page, i - 2);
    }
    await expect
      .poll(() =>
        page.locator('.gallery-space').evaluate((node) => {
          const cards = node.querySelectorAll('.gallery-category:not([hidden]) .gallery-card');
          const spacing = Math.abs(
            new DOMMatrix(getComputedStyle(cards[1]!).transform).m41 -
              new DOMMatrix(getComputedStyle(cards[0]!).transform).m41,
          );
          return spacing > 0 && spacing <= node.clientWidth / 6 + 0.5;
        }),
      )
      .toBe(true);
    const box = (await tuner(page).boundingBox())!;
    expect(box.height).toBeGreaterThanOrEqual(44);
    expect(box.x).toBeGreaterThanOrEqual(0);
    expect(box.x + box.width).toBeLessThanOrEqual(width!);
    expect(box.x + box.width / 2).toBeCloseTo(width! / 2, 0);
    await tuner(page).click();
    for (const choice of await page.locator('.collection-options label').all()) {
      const target = (await choice.boundingBox())!;
      expect(target.height).toBeGreaterThanOrEqual(44);
      expect(target.x).toBeGreaterThanOrEqual(0);
      expect(target.x + target.width).toBeLessThanOrEqual(width!);
      expect(target.y + target.height).toBeLessThanOrEqual(height!);
    }
    await page.keyboard.press('Escape');
    if (width === 390 || (width === 1440 && height === 900))
      await page.screenshot({ path: info.outputPath(`tuner-${width}.png`), scale: 'css' });
  }
});

test('touch drags tune horizontally while vertical swipes scroll the page', async ({
  page,
}, info) => {
  test.skip(!info.project.use.hasTouch, 'Native touch gesture arbitration');
  await visit(page);
  const cdp = await page.context().newCDPSession(page);
  const box = (await tuner(page).boundingBox())!,
    x = box.x + box.width * 0.7,
    y = box.y + 28;
  const before = await curtain(page);
  const touch = (
    type: 'touchStart' | 'touchMove' | 'touchEnd' | 'touchCancel',
    tx: number,
    ty: number,
  ) =>
    cdp.send('Input.dispatchTouchEvent', {
      type,
      touchPoints: type === 'touchEnd' || type === 'touchCancel' ? [] : [{ x: tx, y: ty, id: 1 }],
    });
  await touch('touchStart', x, y);
  for (let i = 1; i <= 8; i++) await touch('touchMove', x - (box.width * 0.35 * i) / 8, y + 1);
  await page.waitForTimeout(130);
  await touch('touchEnd', 0, 0);
  await expect(root(page)).toHaveAttribute('data-category', 'articles');
  await at(page, 0);
  expect(await curtain(page)).toEqual(before);
  // A cancelled touch must return to the committed category.
  await touch('touchStart', x, y);
  await touch('touchMove', x - box.width * 0.35, y);
  await expect(page.locator('.tuner-name')).toHaveText('Notes');
  await touch('touchCancel', 0, 0);
  await expect(page.locator('.tuner-name')).toHaveText('Articles');
  await touch('touchStart', x, y);
  for (let i = 1; i <= 6; i++) await touch('touchMove', x + 1, y - 20 * i);
  await touch('touchEnd', 0, 0);
  await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(before.y);
  await expect(root(page)).toHaveAttribute('data-category', 'articles');
});

test('late tuner changes center card one and reset Projects after either completed exit without moving the footer', async ({
  page,
}, info) => {
  test.skip(info.project.name !== 'desktop-1440', 'Shared measured tail geometry');
  await visit(page);
  await page.mouse.move(20, 650);
  for (let i = 0; i < 4; i++) await page.mouse.wheel(0, 120);
  await at(page, 4);
  const before = await curtain(page);
  await choose(page, 'articles');
  expect(await curtain(page)).toEqual(before);
  await page.mouse.move(20, 650);
  for (let index = 1; index <= 4; index++) {
    await page.mouse.wheel(0, 120);
    await at(page, index);
  }
  const footerY = await page
    .locator('[data-scene="location"]')
    .evaluate((node) => Math.round(node.getBoundingClientRect().top + scrollY));
  await page.mouse.wheel(0, 120);
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(footerY);
  await expect(root(page)).toHaveAttribute('data-category', 'projects');
  await page.waitForTimeout(250);
  expect(await page.evaluate(() => scrollY)).toBe(footerY);
  await page.mouse.wheel(0, -120);
  await at(page, 5);
  await choose(page, 'articles');
  const anchor = await curtain(page);
  await at(page, 0);
  expect(await curtain(page)).toEqual(anchor);
  await page.mouse.move(20, 650);
  const writingY = await page
    .locator('[data-scene="mark"]')
    .evaluate((node) => Math.round(node.getBoundingClientRect().top + scrollY + 1140));
  for (let i = 0; i < 5; i++) await page.mouse.wheel(0, -120);
  await expect(root(page)).toHaveAttribute('data-category', 'projects');
  await expect(root(page)).toHaveAttribute('data-gallery-empty', 'true');
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(writingY);
  await expect(page.locator('.mark-face')).toHaveAttribute('data-visible', 'true');
  for (let i = 0; i < 5; i++) await page.mouse.wheel(0, 120);
  await at(page, 0);
  await expect(root(page)).toHaveAttribute('data-category', 'projects');
  await expect(page.locator('#work .gallery-card').first()).toHaveAttribute('data-front', 'true');
});

test('the cylinder loops both ways with previous and next names, without resetting its motion at the seam', async ({
  page,
}, info) => {
  await visit(page);
  await tuner(page).focus();
  const before = await curtain(page),
    names = ['Projects', 'Articles', 'Notes', 'Research'];
  const stop = async (position: number) => {
    await expect
      .poll(() => page.locator('.gallery-instrument').getAttribute('data-tuner-position'))
      .toBe(String(position));
    const center = ((position % 4) + 4) % 4;
    await expect(page.locator('.tuner-label[data-offset="-1"]')).toHaveText(
      names[(center + 3) % 4]!,
    );
    await expect(page.locator('.tuner-label[data-offset="0"]')).toHaveText(names[center]!);
    await expect(page.locator('.tuner-label[data-offset="1"]')).toHaveText(
      names[(center + 1) % 4]!,
    );
    const neighbors = await page
      .locator('.tuner-label[data-offset="-1"],.tuner-label[data-offset="1"]')
      .evaluateAll((nodes) =>
        nodes.map((node) => ({
          opacity: Number(getComputedStyle(node).opacity),
          rotation: new DOMMatrix(getComputedStyle(node).transform).m13,
          left: node.getBoundingClientRect().left,
          right: node.getBoundingClientRect().right,
        })),
      );
    for (const neighbor of neighbors) {
      expect(neighbor.opacity).toBeGreaterThan(0.3);
      expect(neighbor.opacity).toBeLessThan(0.8);
      expect(Math.abs(neighbor.rotation)).toBeGreaterThan(0.4);
      expect(neighbor.left).toBeGreaterThanOrEqual(0);
      expect(neighbor.right).toBeLessThanOrEqual((await page.viewportSize())!.width);
    }
    expect(await curtain(page)).toEqual(before);
  };
  await stop(0);
  await page.screenshot({ path: info.outputPath('looping-tuner.png'), scale: 'css' });
  for (let position = 1; position <= 8; position++) {
    await page.keyboard.press('ArrowRight');
    await stop(position);
  }
  for (let position = 7; position >= -4; position--) {
    await page.keyboard.press('ArrowLeft');
    await stop(position);
  }
  // At a settled stop there is no continuing tuner animation work.
  const changes = await page.locator('.gallery-instrument').evaluate(
    (node) =>
      new Promise<number>((resolve) => {
        let count = 0;
        const observer = new MutationObserver(() => count++);
        observer.observe(node, { attributes: true, attributeFilter: ['data-tuner-position'] });
        setTimeout(() => {
          observer.disconnect();
          resolve(count);
        }, 120);
      }),
  );
  expect(changes).toBe(0);
  // A rightward drag from Projects crosses back to Research, then reverses.
  const box = (await tuner(page).boundingBox())!;
  await page.mouse.move(box.x + box.width * 0.3, box.y + 28);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width * 0.65, box.y + 28, { steps: 8 });
  await expect(page.locator('.tuner-name')).toHaveText('Research');
  await page.mouse.up();
  await expect(root(page)).toHaveAttribute('data-category', 'research');
  await stop(-5);
  await page.keyboard.press('ArrowRight');
  await stop(-4);
});

test('scroll reentry from either side defaults to Projects and preserves its five-notch entrance', async ({
  page,
}) => {
  await visit(page);
  await choose(page, 'articles');
  await page.mouse.move(20, 650);
  const states = [await page.evaluate(() => scrollY)];
  for (let index = 1; index <= 4; index++) {
    await page.mouse.wheel(0, 120);
    await at(page, index);
    states.push(await page.evaluate(() => scrollY));
  }
  for (let index = 3; index >= 0; index--) {
    await page.mouse.wheel(0, -120);
    await at(page, index);
    expect(await page.evaluate(() => scrollY)).toBe(states[index]);
  }
  for (let i = 0; i < 5; i++) await page.mouse.wheel(0, 120);
  await expect(root(page)).toHaveAttribute('data-category', 'projects');
  await expect
    .poll(() => page.locator('.gallery-face').evaluate((node) => (node as HTMLElement).inert))
    .toBe(true);
  await page.mouse.wheel(0, -120);
  await at(page, 5);
  await expect(root(page)).toHaveAttribute('data-category', 'projects');
  await page.mouse.wheel(0, -120);
  await page.mouse.wheel(0, -120);
  await page.mouse.wheel(0, -120);
  await at(page, 2);
  await expect(page.locator('#work .gallery-card').last()).toHaveAttribute('data-front', 'true');
  await choose(page, 'notes');
  await at(page, 0);
  await page.mouse.move(20, 650);
  for (let i = 0; i < 4; i++) await page.mouse.wheel(0, -120);
  await expect(root(page)).toHaveAttribute('data-category', 'projects');
  await expect(root(page)).toHaveAttribute('data-gallery-empty', 'true');
  await page.mouse.wheel(0, -120);
  await expect(page.locator('.mark-face')).toHaveAttribute('data-visible', 'true');
  for (const index of [-4, -3, -2, -1, 0]) {
    await page.mouse.wheel(0, 120);
    await at(page, index);
    await expect(root(page)).toHaveAttribute('data-category', 'projects');
  }
  await expect(page.locator('#work .gallery-card').first()).toHaveAttribute('data-front', 'true');
});
