// DOM-fallback gesture coverage: film WebGL is disabled; this does not qualify GPU frame delivery.
import { expect, test, type Page } from '@playwright/test';
import { openHomepage } from '../../support/homepage';

test.afterEach(({}, info) => {
  if (info.status !== info.expectedStatus)
    for (const error of info.errors) console.log(error.message);
});

const root = (page: Page) => page.locator('[data-scene="gallery"]');
const tuner = (page: Page) => page.locator('.collection-tuner');
const instrument = (page: Page) => page.locator('.gallery-instrument');

async function enter(page: Page) {
  // Isolate DOM gallery gestures from unrelated software WebGL texture work.
  // Native pointer arbitration remains real; full-renderer controls are checked separately.
  await page.addInitScript(() => {
    const original = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (
      this: HTMLCanvasElement,
      ...args: Parameters<typeof original>
    ) {
      return this.matches('[data-film-canvas]') ? null : Reflect.apply(original, this, args);
    } as typeof original;
  });
  await openHomepage(page);
  await root(page).evaluate((node) => {
    const box = node.getBoundingClientRect();
    const entry =
      parseFloat((node as HTMLElement).style.getPropertyValue('--gallery-entry-distance')) || 0;
    const count = node.querySelectorAll('.gallery-category:not([hidden]) .gallery-card').length;
    scrollTo(0, box.top + scrollY + entry + (box.height - entry) * (0.12 + 2.8 / (count + 7)));
  });
  await expect(root(page)).toHaveAttribute('data-gallery-index', '0');
  await expect(tuner(page)).toBeVisible();
}

async function pointer(page: Page, touch: boolean) {
  const session = touch ? await page.context().newCDPSession(page) : null;
  return {
    async down(x: number, y: number) {
      if (session)
        await session.send('Input.dispatchTouchEvent', {
          type: 'touchStart',
          touchPoints: [{ x, y, id: 1 }],
        });
      else {
        await page.mouse.move(x, y);
        await page.mouse.down();
      }
    },
    async move(x: number, y: number) {
      if (session)
        await session.send('Input.dispatchTouchEvent', {
          type: 'touchMove',
          touchPoints: [{ x, y, id: 1 }],
        });
      else await page.mouse.move(x, y);
    },
    async up() {
      if (session)
        await session.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
      else await page.mouse.up();
    },
    async dispose() {
      await session?.detach();
    },
  };
}

test('a tuner flick previews multiple names, then commits once at its landing without moving the page', async ({
  page,
}, info) => {
  await enter(page);
  const input = await pointer(page, !!info.project.use.hasTouch);
  const box = (await tuner(page).boundingBox())!;
  const y = await page.evaluate(() => scrollY);
  await root(page).evaluate((node) => {
    Reflect.set(window, 'collectionCommits', []);
    new MutationObserver((events) => {
      for (const event of events)
        if (event.attributeName === 'data-category')
          Reflect.get(window, 'collectionCommits').push((node as HTMLElement).dataset.category);
    }).observe(node, { attributes: true, attributeFilter: ['data-category'] });
  });
  try {
    await input.down(box.x + box.width * 0.85, box.y + 20);
    for (let i = 1; i <= 5; i++) {
      await input.move(box.x + box.width * (0.85 - (0.7 * i) / 5), box.y + 20);
      await page.waitForTimeout(12);
    }
    const released = Number(await instrument(page).getAttribute('data-tuner-position'));
    await input.up();
    await expect(instrument(page)).toHaveAttribute('data-tuner-motion', 'coasting');
    await expect(root(page)).toHaveAttribute('data-category', 'projects');
    await expect
      .poll(
        async () => Number(await instrument(page).getAttribute('data-tuner-position')) - released,
      )
      .toBeGreaterThan(0.5);
    await expect(instrument(page)).not.toHaveAttribute('data-tuner-motion');
    const position = Number(await instrument(page).getAttribute('data-tuner-position'));
    const category = ['projects', 'articles', 'notes', 'research'][
      ((Math.round(position) % 4) + 4) % 4
    ]!;
    await expect(root(page)).toHaveAttribute('data-category', category);
    expect(await page.evaluate(() => Reflect.get(window, 'collectionCommits'))).toEqual(
      category === 'projects' ? [] : [category],
    );
    await expect(root(page)).toHaveAttribute('data-gallery-index', '0');
    expect(await page.evaluate(() => scrollY)).toBe(y);
  } finally {
    await input.dispose();
  }
});

test('touching a spinning tuner catches it and a tap opens the menu without a delayed collection change', async ({
  page,
}, info) => {
  await enter(page);
  const input = await pointer(page, !!info.project.use.hasTouch);
  const box = (await tuner(page).boundingBox())!;
  const x = box.x + box.width * 0.8,
    y = box.y + 20;
  try {
    await input.down(x, y);
    await page.waitForTimeout(20);
    await input.move(x - box.width * 0.3, y);
    await input.up();
    await expect(instrument(page)).toHaveAttribute('data-tuner-motion', 'coasting');
    await input.down(x, y);
    const caught = await instrument(page).getAttribute('data-tuner-position');
    await page.waitForTimeout(150);
    expect(await instrument(page).getAttribute('data-tuner-position')).toBe(caught);
    await input.up();
    await expect(page.locator('.collection-options')).toBeVisible();
    await expect(root(page)).toHaveAttribute('data-category', 'projects');
    await page.waitForTimeout(1000);
    await expect(root(page)).toHaveAttribute('data-category', 'projects');
    await expect(instrument(page)).toHaveAttribute('data-tuner-position', '0');
  } finally {
    await input.dispose();
  }
});

test('a gallery flick advances with page position, brakes at the last card, and can be caught without opening a link', async ({
  page,
}, info) => {
  await enter(page);
  const input = await pointer(page, !!info.project.use.hasTouch);
  const cover = (await page
    .locator('.gallery-card[data-front="true"] .gallery-cover')
    .boundingBox())!;
  const spacing = await page
    .locator('.gallery-category:not([hidden]) .gallery-card')
    .evaluateAll((cards) =>
      Math.abs(
        new DOMMatrix(getComputedStyle(cards[1]!).transform).m41 -
          new DOMMatrix(getComputedStyle(cards[0]!).transform).m41,
      ),
    );
  const x = Math.max(
    30,
    Math.min(page.viewportSize()!.width - spacing - 30, cover.x + cover.width * 0.4),
  );
  const y = cover.y + cover.height * 0.5;
  try {
    const origin = await page.evaluate(() => scrollY);
    await input.down(x, y);
    for (let i = 1; i <= 4; i++) {
      await input.move(x + (spacing * 1.4 * i) / 4, y);
      await page.waitForTimeout(12);
    }
    await input.up();
    await expect(root(page)).toHaveAttribute('data-gallery-motion', 'coasting');
    const releasedY = await page.evaluate(() => scrollY);
    expect(releasedY).toBeGreaterThan(origin);
    await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(releasedY + 5);
    await expect(root(page)).not.toHaveAttribute('data-gallery-motion');
    await expect(root(page)).toHaveAttribute('data-gallery-index', '2');
    await expect(root(page)).toHaveAttribute('data-gallery-empty', 'false');
    const landed = await page.evaluate(() => scrollY);
    await page.waitForTimeout(250);
    expect(await page.evaluate(() => scrollY)).toBe(landed);
    // Start a reverse fling, catch it, and ensure the catching tap cannot navigate.
    await input.down(x + spacing, y);
    await page.waitForTimeout(20);
    await input.move(x + spacing * 0.2, y);
    await input.up();
    await expect(root(page)).toHaveAttribute('data-gallery-motion', 'coasting');
    await input.down(x, y);
    await expect(root(page)).not.toHaveAttribute('data-gallery-motion');
    const caught = await root(page).getAttribute('data-gallery-index');
    await page.waitForTimeout(150);
    expect(await root(page).getAttribute('data-gallery-index')).toBe(caught);
    await input.up();
    await expect(root(page)).not.toHaveAttribute('data-gallery-motion');
    await expect(page).toHaveURL('/');
  } finally {
    await input.dispose();
  }
});
