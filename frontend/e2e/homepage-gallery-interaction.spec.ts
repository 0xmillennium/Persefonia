import { chooseCollection } from './support/gallery';
import { expect, test, type Page } from '@playwright/test';
import { settleScroll } from './support/scroll';

async function visit(page: Page) {
  await page.goto('/', { waitUntil: 'networkidle' });
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
  await expect(page.locator('[data-home-loader]')).toBeHidden();
}
async function gallery(page: Page, progress = 0) {
  const count = await page.locator('[data-scene="gallery"]').evaluate((node, p) => {
    const rect = node.getBoundingClientRect();
    const entry =
      parseFloat((node as HTMLElement).style.getPropertyValue('--gallery-entry-distance')) || 0;
    const count = node.querySelectorAll('.gallery-category:not([hidden]) .gallery-card').length;
    const travel = (4 + p * (count - 1)) / (count + 7);
    scrollTo({
      top: rect.top + scrollY + entry + (rect.height - entry) * (0.12 + travel * 0.7),
      behavior: 'instant',
    });
    return count;
  }, progress);
  await expect
    .poll(() => page.locator('.gallery-face').evaluate((node) => (node as HTMLElement).inert))
    .toBe(false);
  await settleScroll(page);
  await expect(page.locator('[data-scene="gallery"]')).toHaveAttribute(
    'data-gallery-index',
    String(Math.round(progress * (count - 1))),
  );
}
async function dragPoint(page: Page) {
  const cover = page
    .locator('.gallery-category:not([hidden]) .gallery-card[data-front="true"]')
    .first()
    .locator('.gallery-cover');
  const box = (await cover.boundingBox())!;
  const width = page.viewportSize()!.width;
  const spacing = await page
    .locator('.gallery-category:not([hidden]) .gallery-card')
    .evaluateAll(
      (cards) =>
        new DOMMatrix(getComputedStyle(cards[1]!).transform).m41 -
        new DOMMatrix(getComputedStyle(cards[0]!).transform).m41,
    );
  return {
    x: Math.min(width - 30, box.x + box.width * 0.65),
    y: box.y + box.height * 0.5,
    spacing,
  };
}

test('gallery drag follows the pointer, settles to a card and keeps the dragged link closed', async ({
  page,
}, info) => {
  await visit(page);
  await gallery(page);
  const initial = await page.evaluate(() => scrollY),
    point = await dragPoint(page);
  const session = info.project.use.hasTouch ? await page.context().newCDPSession(page) : null;
  const send = async (type: 'touchStart' | 'touchMove' | 'touchEnd', x = point.x) => {
    await session!.send('Input.dispatchTouchEvent', {
      type,
      touchPoints: type === 'touchEnd' ? [] : [{ x, y: point.y }],
    });
  };
  if (session) {
    await send('touchStart');
    await send('touchMove', point.x - point.spacing * 0.4);
  } else {
    await page.mouse.move(point.x, point.y);
    await page.mouse.down();
    await page.mouse.move(point.x - point.spacing * 0.4, point.y, { steps: 4 });
  }
  await expect(page.locator('[data-scene="gallery"]')).toHaveAttribute('data-dragging', 'true');
  await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(initial);
  if (session) await send('touchMove', point.x - point.spacing * 0.8);
  else await page.mouse.move(point.x - point.spacing * 0.8, point.y, { steps: 4 });
  await page.waitForTimeout(130);
  if (session) await send('touchEnd');
  else await page.mouse.up();
  await expect(page.locator('[data-gallery-position]')).toHaveText('2 / 3');
  await expect(page.locator('[data-scene="gallery"]')).not.toHaveAttribute('data-dragging');
  await expect(page).toHaveURL('/');
  await expect
    .poll(() =>
      page
        .locator('.gallery-category:not([hidden]) .gallery-card')
        .nth(1)
        .evaluate((node) =>
          Math.hypot(
            new DOMMatrix(getComputedStyle(node).transform).m41,
            new DOMMatrix(getComputedStyle(node).transform).m43,
          ),
        ),
    )
    .toBeLessThan(0.05);
  await page.screenshot({ path: info.outputPath('gallery-drag-settled.png') });
});

test('gallery scrolling, keyboard focus and depth settle together and stop updating at rest', async ({
  page,
}) => {
  await visit(page);
  await gallery(page);
  const cards = page.locator('.gallery-category:not([hidden]) .gallery-card');
  await expect.poll(() => cards.first().getAttribute('data-front')).toBe('true');
  await expect
    .poll(() =>
      cards
        .first()
        .locator('.gallery-cover')
        .evaluate((n) => getComputedStyle(n).filter),
    )
    .toBe('blur(0px)');
  expect(
    await cards
      .nth(1)
      .locator('.gallery-cover')
      .evaluate((n) => parseFloat(getComputedStyle(n).filter.slice(5))),
  ).toBeGreaterThan(0);
  expect(
    await cards
      .nth(1)
      .locator('h3')
      .evaluate((n) => getComputedStyle(n).filter),
  ).toBe('none');
  const before = await cards.nth(1).evaluate((n) => getComputedStyle(n).transform);
  await cards.nth(1).locator('h3 a').focus();
  await expect(page.locator('[data-gallery-position]')).toHaveText('2 / 3');
  await expect
    .poll(() => cards.nth(1).evaluate((n) => getComputedStyle(n).transform))
    .not.toBe(before);
  await expect
    .poll(() =>
      cards
        .nth(1)
        .evaluate((n) =>
          Math.hypot(
            new DOMMatrix(getComputedStyle(n).transform).m41,
            new DOMMatrix(getComputedStyle(n).transform).m43,
          ),
        ),
    )
    .toBeLessThan(0.05);
  await cards.nth(2).locator('h3 a').focus();
  await expect(page.locator('[data-gallery-position]')).toHaveText('3 / 3');
  await expect
    .poll(() =>
      cards
        .nth(2)
        .evaluate((n) =>
          Math.hypot(
            new DOMMatrix(getComputedStyle(n).transform).m41,
            new DOMMatrix(getComputedStyle(n).transform).m43,
          ),
        ),
    )
    .toBeLessThan(0.05);
  await page.keyboard.press('Shift+Tab');
  await page.mouse.move(5, 150);
  await page.waitForTimeout(600);
  const changes = await cards.nth(2).evaluate(
    (node) =>
      new Promise<number>((resolve) => {
        let count = 0;
        const observer = new MutationObserver((records) => {
          count += records.length;
        });
        observer.observe(node, { attributes: true });
        setTimeout(() => {
          observer.disconnect();
          resolve(count);
        }, 250);
      }),
  );
  expect(changes).toBe(0);
});

test('gallery interruption clears dragging and reduced motion removes depth treatment', async ({
  page,
}) => {
  await visit(page);
  await gallery(page);
  const point = await dragPoint(page);
  await page.mouse.move(point.x, point.y);
  await page.mouse.down();
  await page.mouse.move(point.x - point.spacing * 0.3, point.y);
  await expect(page.locator('[data-scene="gallery"]')).toHaveAttribute('data-dragging', 'true');
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect(page.locator('[data-scene="gallery"]')).not.toHaveAttribute('data-dragging');
  await page.mouse.up();
  for (const card of await page.locator('.gallery-category:not([hidden]) .gallery-card').all()) {
    expect(await card.locator('.gallery-cover').evaluate((n) => getComputedStyle(n).filter)).toBe(
      'none',
    );
    expect(await card.evaluate((n) => getComputedStyle(n).transform)).toBe('none');
  }
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-reduced', 'false');
  await gallery(page);
  await page.keyboard.press('Tab');
  await page
    .locator('.gallery-category:not([hidden]) .gallery-card')
    .nth(1)
    .locator('h3 a')
    .focus();
  await expect(page.locator('[data-gallery-position]')).toHaveText('2 / 3');
});

test('touch gallery preserves native vertical scrolling', async ({ page }, info) => {
  test.skip(!info.project.use.hasTouch, 'Native touch gesture');
  await visit(page);
  await gallery(page, 0.5);
  const before = await page.evaluate(() => scrollY),
    point = await dragPoint(page);
  const session = await page.context().newCDPSession(page);
  await session.send('Input.dispatchTouchEvent', {
    type: 'touchStart',
    touchPoints: [{ x: point.x, y: point.y }],
  });
  for (let i = 1; i <= 8; i++) {
    await session.send('Input.dispatchTouchEvent', {
      type: 'touchMove',
      touchPoints: [{ x: point.x, y: point.y - i * 16 }],
    });
  }
  await session.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
  await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(before);
  await expect(page.locator('[data-scene="gallery"]')).not.toHaveAttribute('data-dragging');
});

test('hold hint follows eligibility, disappears over links and during a press, and stays off in the gallery', async ({
  page,
}, info) => {
  test.skip(!!info.project.use.hasTouch, 'Fine-pointer discoverability');
  await visit(page);
  const hint = page.locator('.home-pointer__hint');
  await page.mouse.move(300, 170);
  await expect(hint).toHaveAttribute('data-visible', 'true');
  await page.screenshot({ path: info.outputPath('hold-hint.png') });
  await page.locator('[data-site-name]').hover();
  await expect(hint).toHaveAttribute('data-visible', 'false');
  await page.mouse.move(300, 170);
  await expect(hint).toHaveAttribute('data-visible', 'true');
  await page.mouse.down();
  await expect(hint).toHaveAttribute('data-visible', 'false');
  await page.mouse.up();
  await gallery(page);
  await page.mouse.move(300, 170);
  await page.waitForTimeout(350);
  await expect(hint).toHaveAttribute('data-visible', 'false');
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect(page.locator('.home-pointer')).toBeHidden();
});

test('a short fast gallery flick continues toward the next card after release', async ({
  page,
}, info) => {
  test.skip(!!info.project.use.hasTouch, 'Controlled fine-pointer release velocity');
  await page.clock.install();
  await visit(page);
  await gallery(page);
  const point = await dragPoint(page);
  await page.clock.pauseAt(await page.evaluate(() => Date.now() + 50));
  await page.mouse.move(point.x, point.y);
  await page.mouse.down();
  await page.mouse.move(point.x - point.spacing * 0.3, point.y);
  await expect(page.locator('[data-scene="gallery"]')).toHaveAttribute('data-dragging', 'true');
  await expect(page.locator('[data-gallery-position]')).toHaveText('1 / 3');
  await page.mouse.up();
  await page.clock.runFor(16);
  await expect(page.locator('[data-gallery-position]')).toHaveText('2 / 3');
  await page.clock.runFor(500);
  await expect
    .poll(() =>
      page
        .locator('.gallery-category:not([hidden]) .gallery-card')
        .nth(1)
        .evaluate((node) =>
          Math.hypot(
            new DOMMatrix(getComputedStyle(node).transform).m41,
            new DOMMatrix(getComputedStyle(node).transform).m43,
          ),
        ),
    )
    .toBeLessThan(0.05);
});

test('gallery titles stay inside clickable covers and reveal on hover or focus without extra controls', async ({
  page,
}, info) => {
  await visit(page);
  await gallery(page);
  await expect(
    page.locator('[data-gallery-prev],[data-gallery-next],.gallery-controls,.gallery-more'),
  ).toHaveCount(0);
  const cards = page.locator('.gallery-category:not([hidden]) .gallery-card');
  const cover = cards.first().locator('.gallery-cover');
  const title = cover.locator('h3 a>span');
  await expect(cover.locator('a')).toHaveCount(1);
  await page.mouse.move(5, 120);
  await expect(title).toHaveCSS('opacity', info.project.use.hasTouch ? '1' : '0');
  if (info.project.use.hasTouch) await cover.locator('a').focus();
  else await cover.hover();
  await expect(title).toHaveCSS('opacity', '1');
  const rect = (await cover.boundingBox())!,
    text = (await title.boundingBox())!;
  expect(text.x).toBeGreaterThanOrEqual(rect.x - 1);
  expect(text.y).toBeGreaterThanOrEqual(rect.y - 1);
  expect(text.x + text.width).toBeLessThanOrEqual(rect.x + rect.width + 1);
  expect(text.y + text.height).toBeLessThanOrEqual(rect.y + rect.height + 1);
  await page.screenshot({ path: info.outputPath('gallery-title-inside-cover.png') });
  await page.mouse.move(5, 120);
  await cover.locator('a').focus();
  await expect(title).toHaveCSS('opacity', '1');
  await chooseCollection(page, 'articles');
  const writing = page
    .locator('.gallery-category:not([hidden]) .gallery-card[data-front="true"] .gallery-cover')
    .first();
  if (info.project.use.hasTouch) await writing.locator('a').focus();
  else await writing.hover();
  await expect(writing.locator('h3 a>span')).toHaveCSS('opacity', '1');
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.mouse.move(5, 120);
  for (const label of await page
    .locator('.gallery-category:not([hidden]) .gallery-card__title a>span')
    .all())
    await expect(label).toHaveCSS('opacity', '1');
});
