import { openHomepage } from '../../support/homepage';
import { expect, test, type Page } from '@playwright/test';

const root = (page: Page) => page.locator('[data-scene="gallery"]');
async function enter(page: Page, category: string) {
  await page
    .locator('.home-nav a[href="#work"]')
    .evaluate((node) => (node as HTMLAnchorElement).click());
  await expect
    .poll(() => page.locator('.gallery-face').evaluate((node) => (node as HTMLElement).inert))
    .toBe(false);
  await at(page, -3);
  if (category !== 'projects') {
    await notch(page, 1, 3);
    await at(page, 0);
    await page.locator('.collection-tuner').click();
    await page.locator(`input[value="${category}"]`).check();
    await expect(page.locator('.gallery-category:not([hidden])')).toHaveCount(1);
    await at(page, 0);
    await notch(page, -1, 3);
  }
  await at(page, -3);
}
async function at(page: Page, index: number) {
  await expect
    .poll(() => root(page).evaluate((node) => Number((node as HTMLElement).dataset.galleryIndex)))
    .toBe(index);
}
async function notch(page: Page, direction: number, count = 1) {
  await page.mouse.move(20, 650);
  for (let i = 0; i < count; i++) await page.mouse.wheel(0, direction * 120);
}
async function observeExit(page: Page) {
  await root(page).evaluate((node) => {
    const states: { empty: boolean; inert: boolean }[] = [];
    (window as unknown as { galleryTravelStates: typeof states }).galleryTravelStates = states;
    const face = node.querySelector<HTMLElement>('.gallery-face')!;
    new MutationObserver(() =>
      states.push({
        empty: (node as HTMLElement).dataset.galleryEmpty === 'true',
        inert: face.inert,
      }),
    ).observe(node, { attributes: true, subtree: true });
  });
}
async function clearedBeforeExit(page: Page) {
  await expect
    .poll(() => page.locator('.gallery-face').evaluate((node) => (node as HTMLElement).inert))
    .toBe(true);
  const states = await page.evaluate(
    () =>
      (window as unknown as { galleryTravelStates: { empty: boolean; inert: boolean }[] })
        .galleryTravelStates,
  );
  const cleared = states.findIndex((state) => state.empty),
    left = states.findIndex((state) => state.inert);
  expect(cleared).toBeGreaterThanOrEqual(0);
  expect(left).toBeGreaterThan(cleared);
  await expect(root(page)).toHaveAttribute('data-gallery-empty', 'true');
}

test.beforeEach(async ({ page }) => {
  await openHomepage(page);
});

test('each collection centers its first card after three entrance-slot notches, clears on the fourth reverse notch and returns on the fifth', async ({
  page,
}, info) => {
  for (const category of ['projects', 'articles', 'notes', 'research']) {
    await enter(page, category);
    await notch(page, 1, 3);
    await at(page, 0);
    const cards = page.locator('.gallery-category:not([hidden]) .gallery-card');
    await expect(cards.first()).toHaveAttribute('data-front', 'true');
    const visibility = await cards.evaluateAll((nodes) =>
      nodes.map((node) => getComputedStyle(node).visibility !== 'hidden'),
    );
    expect(visibility).toEqual(visibility.map((_, i) => i <= 3));
    if (info.project.name === 'desktop-1440')
      await expect(page.locator('.gallery-space')).toHaveCSS('clip-path', 'none');
    await page.screenshot({
      path: info.outputPath(`${category}-first-centered.png`),
      scale: 'css',
    });
    await notch(page, -1, 3);
    await at(page, -3);
    expect(
      await page.locator('.gallery-face').evaluate((node) => (node as HTMLElement).inert),
    ).toBe(false);
    await observeExit(page);
    await notch(page, -1);
    await clearedBeforeExit(page);
    await notch(page, -1);
    await expect(page.locator('.mark-face')).toHaveAttribute('data-visible', 'true');
  }
});

test('last card takes four more notches to clear before the footer, with no wrapped cards', async ({
  page,
}, info) => {
  for (const category of ['projects', 'articles', 'notes', 'research']) {
    await enter(page, category);
    const cards = page.locator('.gallery-category:not([hidden]) .gallery-card'),
      count = await cards.count();
    await notch(page, 1, count + 2);
    await at(page, count - 1);
    const visibility = await cards.evaluateAll((nodes) =>
      nodes.map((node) => getComputedStyle(node).visibility !== 'hidden'),
    );
    expect(visibility).toEqual(visibility.map((_, i) => i >= count - 4));
    if (info.project.name === 'desktop-1440')
      await expect(page.locator('.gallery-space')).toHaveCSS('clip-path', 'none');
    await page.screenshot({ path: info.outputPath(`${category}-last-centered.png`), scale: 'css' });
    await notch(page, 1, 3);
    await at(page, count + 2);
    expect(
      await page.locator('.gallery-face').evaluate((node) => (node as HTMLElement).inert),
    ).toBe(false);
    await observeExit(page);
    await notch(page, 1);
    await clearedBeforeExit(page);
    await expect(page.locator('.location-face')).toHaveAttribute('data-visible', 'true');
  }
});

test('reversing the clearing notch cancels departure', async ({ page }) => {
  await enter(page, 'projects');
  await notch(page, 1, 3);
  await at(page, 0);
  await notch(page, -1, 3);
  await at(page, -3);
  await notch(page, -1);
  await notch(page, 1);
  await at(page, -3);
  await page.waitForTimeout(600);
  expect(await page.locator('.gallery-face').evaluate((node) => (node as HTMLElement).inert)).toBe(
    false,
  );
  await expect(root(page)).toHaveAttribute('data-gallery-empty', 'false');
});

test('collections retrace their internal stops and Projects retraces both completed section handoffs', async ({
  page,
}) => {
  test.setTimeout(180000);
  const capture = () =>
    page.locator('.gallery-category:not([hidden]) .gallery-card').evaluateAll((nodes) =>
      nodes.map((node) => ({
        transform: getComputedStyle(node).transform,
        visibility: getComputedStyle(node).visibility,
        blur: node.style.getPropertyValue('--gallery-blur'),
        opacity: node.style.getPropertyValue('--gallery-depth-opacity'),
      })),
    );
  for (const category of ['projects', 'articles', 'notes', 'research']) {
    await enter(page, category);
    const count = await page.locator('.gallery-category:not([hidden]) .gallery-card').count();
    const states = new Map<number, { y: number; cards: Awaited<ReturnType<typeof capture>> }>();
    const remember = async (index: number) => {
      await at(page, index);
      states.set(index, { y: await page.evaluate(() => scrollY), cards: await capture() });
    };
    await remember(-3);
    for (let index = -2; index <= count + 2; index++) {
      await notch(page, 1);
      await remember(index);
    }
    if (category === 'projects') {
      await notch(page, 1);
      await expect
        .poll(() => page.locator('.gallery-face').evaluate((node) => (node as HTMLElement).inert))
        .toBe(true);
      await expect(root(page)).toHaveAttribute('data-gallery-empty', 'true');
    }
    for (let index = category === 'projects' ? count + 2 : count + 1; index >= -3; index--) {
      await notch(page, -1);
      await at(page, index);
      const expected = states.get(index)!;
      expect(await page.evaluate(() => scrollY)).toBe(expected.y);
      expect(await capture()).toEqual(expected.cards);
    }
    if (category !== 'projects') continue;
    await notch(page, -1);
    await expect
      .poll(() => page.locator('.gallery-face').evaluate((node) => (node as HTMLElement).inert))
      .toBe(true);
    await expect(root(page)).toHaveAttribute('data-gallery-empty', 'true');
    await notch(page, 1);
    await at(page, -3);
    expect(await page.evaluate(() => scrollY)).toBe(states.get(-3)!.y);
    expect(await capture()).toEqual(states.get(-3)!.cards);
    await notch(page, 1, 3);
    await at(page, 0);
    await notch(page, -1, 7);
    await expect
      .poll(() => page.locator('.gallery-face').evaluate((node) => (node as HTMLElement).inert))
      .toBe(true);
    await expect(root(page)).toHaveAttribute('data-gallery-empty', 'true');
    await notch(page, 1, 7);
    await at(page, 0);
    expect(await page.evaluate(() => scrollY)).toBe(states.get(0)!.y);
    expect(await capture()).toEqual(states.get(0)!.cards);
    await notch(page, -1, 3);
    await at(page, -3);
  }
});

test('wheel entry and return traverse the fourth writing and staggered gallery curtain', async ({
  page,
}, info) => {
  await root(page).evaluate(() => {
    const mark = document.querySelector<HTMLElement>('[data-scene="mark"]')!;
    scrollTo(0, mark.getBoundingClientRect().top + scrollY + 1140);
  });
  await page.mouse.move(20, 650);
  const record = () =>
    page.evaluate(
      () =>
        new Promise<{ y: number; bands: number[] }[]>((resolve) => {
          const start = performance.now(),
            samples: { y: number; bands: number[] }[] = [];
          function frame(now: number) {
            samples.push({
              y: scrollY,
              bands: [...document.querySelectorAll<HTMLElement>('[data-scene-wipe] i')]
                .filter((node) => getComputedStyle(node).display !== 'none')
                .map((node) => new DOMMatrix(getComputedStyle(node).transform).m42),
            });
            if (now - start < 1050) requestAnimationFrame(frame);
            else resolve(samples);
          }
          requestAnimationFrame(frame);
        }),
    );
  const before = await page.evaluate(() => scrollY);
  const entering = record();
  await notch(page, 1, 2);
  const entry = await entering;
  await at(page, -3);
  expect(new Set(entry.map((sample) => sample.y)).size).toBeGreaterThan(6);
  expect(
    entry.some((sample) => sample.bands.some((y) => y > 1 && y < page.viewportSize()!.height - 1)),
  ).toBe(true);
  expect(entry.some((sample) => sample.bands[0]! < sample.bands.at(-1)! - 1)).toBe(true);
  const returning = record();
  await notch(page, -1, 2);
  const exit = await returning;
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(before);
  expect(new Set(exit.map((sample) => sample.y)).size).toBeGreaterThan(6);
  expect(
    exit.some((sample) => sample.bands.some((y) => y > 1 && y < page.viewportSize()!.height - 1)),
  ).toBe(true);
  await info.attach('handoff-samples', {
    body: JSON.stringify({ entry, exit }),
    contentType: 'application/json',
  });
  await expect(page.locator('.mark-face')).toHaveAttribute('data-visible', 'true');
  await page.screenshot({ path: info.outputPath('fourth-writing-returned.png'), scale: 'css' });
});

test('reversing during the writing handoff returns without first completing entry', async ({
  page,
}) => {
  await page
    .locator('[data-scene="mark"]')
    .evaluate((node) => scrollTo(0, node.getBoundingClientRect().top + scrollY + 1140));
  const start = await page.evaluate(() => scrollY);
  await page.mouse.move(20, 650);
  await page.mouse.wheel(0, 120);
  await expect
    .poll(() => page.evaluate(() => scrollY), { intervals: [16] })
    .toBeGreaterThan(start + 20);
  const turned = await page.evaluate(() => scrollY);
  await page.mouse.wheel(0, -120);
  await expect.poll(() => page.evaluate(() => scrollY), { intervals: [16] }).toBeLessThan(turned);
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(start);
  await expect(page.locator('.mark-face')).toHaveAttribute('data-visible', 'true');
});
