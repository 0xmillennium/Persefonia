import { scrollToGalleryPreroll } from '../../support/gallery';
import { settleScroll } from '../../support/scroll';
import { expect, test, type Page } from '../../support/test';

async function settle(page: Page) {
  await settleScroll(page);
  await expect
    .poll(() =>
      page.locator('[data-scene="gallery"]').evaluate((node) => {
        const index = Number((node as HTMLElement).dataset.galleryIndex);
        return Math.abs(index - Math.round(index));
      }),
    )
    .toBeLessThan(0.001);
}
async function snapshot(page: Page) {
  return page.evaluate(() => {
    const gallery = document.querySelector<HTMLElement>('[data-scene="gallery"]')!;
    const wipe = document.querySelector<HTMLElement>('[data-scene-wipe]')!;
    const cards = [
      ...gallery.querySelectorAll<HTMLElement>('.gallery-category:not([hidden]) .gallery-card'),
    ];
    return {
      y: scrollY,
      index: Math.round(Number(gallery.dataset.galleryIndex)),
      bands: [...wipe.querySelectorAll<HTMLElement>('i')]
        .filter((node) => getComputedStyle(node).display !== 'none')
        .map((node) => parseFloat(node.style.transform.slice('translateY('.length))),
      heading: [...gallery.querySelectorAll<HTMLElement>('.gallery-writing-rise')].map((node) => ({
        text: node.textContent!.trim(),
        opacity: Number(getComputedStyle(node).opacity),
        slotOverflow: getComputedStyle(node.parentElement!).overflow,
      })),
      writing: [
        ...gallery.querySelectorAll<HTMLElement>('.gallery-writing-rise,.collection-tuner'),
      ].map((node) => node.style.transform),
      tunerHidden:
        getComputedStyle(gallery.querySelector('.gallery-instrument')!).visibility === 'hidden',
      tunerInert: gallery.querySelector<HTMLElement>('.gallery-instrument')!.inert,
      darkness: Number(wipe.style.getPropertyValue('--curtain-solid')),
      cards: cards.map((node) => {
        const box = node.getBoundingClientRect();
        return {
          x: box.x + box.width / 2,
          left: box.left,
          right: box.right,
          visible: getComputedStyle(node).visibility !== 'hidden',
        };
      }),
    };
  });
}

test('five notches grow the curtain and bring the first card from the left to center, then three darken it', async ({
  page,
}, info) => {
  await page.goto('/');
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
  await expect(page.locator('[data-home-loader]')).toBeHidden();
  await page
    .locator('[data-scene="mark"]')
    .evaluate((node) => scrollTo(0, Math.round(node.getBoundingClientRect().top + scrollY + 1140)));
  await page.mouse.move(page.viewportSize()!.width / 2, page.viewportSize()!.height / 2);
  await settle(page);
  const states = [await snapshot(page)];
  for (let notch = 1; notch <= 8; notch++) {
    await page.mouse.wheel(0, 120);
    await settle(page);
    const state = await snapshot(page);
    states.push(state);
    expect(state.heading.map((row) => row.text)).toEqual(['Building systems', 'worth explaining']);
    expect(
      state.heading.every((row) => row.opacity >= 0.72 && row.slotOverflow === 'visible'),
    ).toBe(true);
    if (notch <= 4) {
      expect(state.writing).toEqual(['translateY(8%)', 'translateY(8%)', 'translateY(100%)']);
      expect(state.tunerHidden).toBe(true);
      expect(state.tunerInert).toBe(true);
    } else {
      expect(state.writing).toEqual(Array(3).fill('translateY(0%)'));
      expect(state.tunerHidden).toBe(false);
      expect(state.tunerInert).toBe(false);
    }
    if (notch === 1) {
      expect(state.index).toBe(-4);
      expect(state.cards.every((card) => !card.visible)).toBe(true);
    }
    if (notch >= 2 && notch <= 5) {
      expect(state.index).toBe(notch - 5);
      expect(state.cards.map((card) => card.visible)).toEqual(
        state.cards.map((_, i) => i < notch - 1),
      );
      for (const card of state.cards.filter((card) => card.visible)) {
        expect(card.right).toBeGreaterThan(0);
        expect(card.left).toBeLessThan(page.viewportSize()!.width);
      }
      if (notch < 5) expect(state.cards[0]!.x).toBeLessThan(page.viewportSize()!.width / 2);
      if (notch > 2) expect(state.cards[0]!.x).toBeGreaterThan(states[notch - 1]!.cards[0]!.x);
      for (let i = 1; i < Math.min(notch - 1, state.cards.length); i++)
        expect(state.cards[i]!.x).toBeLessThan(state.cards[i - 1]!.x);
    }
    if (notch <= 3) {
      expect(state.bands.some((y) => y > 0)).toBe(true);
      expect(state.bands.some((y) => y < 100)).toBe(true);
    } else expect(state.bands).toEqual(state.bands.map(() => 0));
    if (notch <= 4) expect(state.darkness).toBe(0);
    else expect(state.darkness).toBeCloseTo((notch - 4) / 4, 2);
    if (notch === 5) expect(state.cards[0]!.x).toBeCloseTo(page.viewportSize()!.width / 2, 0);
    if (notch <= 5)
      await page.screenshot({ path: info.outputPath(`notch-${notch}.png`), scale: 'css' });
  }
  // Sample inside notch five as well as its stops: the curtain is solid while
  // heading rows and the strip pass through their individual gates.
  await page.evaluate((y) => scrollTo(0, y), Math.round((states[4]!.y + states[5]!.y) / 2));
  await page.waitForTimeout(250);
  const halfway = await snapshot(page);
  expect(halfway.bands.every((y) => y === 0)).toBe(true);
  expect(
    halfway.writing.every((value, i) => {
      const y = parseFloat(value.slice('translateY('.length));
      return y > 0 && y < (i < 2 ? 8 : 100);
    }),
  ).toBe(true);
  expect(halfway.tunerHidden).toBe(false);
  await page.screenshot({ path: info.outputPath('notch-five-in-progress.png'), scale: 'css' });
  await page.evaluate((y) => scrollTo(0, y), states[8]!.y);
  await settle(page);
  for (let notch = 7; notch >= 0; notch--) {
    await page.mouse.wheel(0, -120);
    await settle(page);
    expect(await snapshot(page), `return to notch ${notch}`).toEqual(states[notch]);
  }
  await info.attach('entrance-states', {
    body: JSON.stringify(states),
    contentType: 'application/json',
  });
});

test('the gallery writing and tuner leave through their gates and retrace the footer exit', async ({
  page,
}, info) => {
  await page.goto('/');
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
  await expect(page.locator('[data-home-loader]')).toBeHidden();
  await page
    .locator('.home-nav a[href="#work"]')
    .evaluate((node) => (node as HTMLAnchorElement).click());
  await scrollToGalleryPreroll(page);
  await settle(page);
  await page.mouse.move(20, 650);
  const count = await page
    .locator('.gallery-category[data-category="projects"] .gallery-card')
    .count();
  const firstY = await page.evaluate(() => scrollY);
  let step = 0;
  for (let n = 0; n < count + 5; n++) {
    await page.mouse.wheel(0, 120);
    await settle(page);
    if (n === 0) step = (await page.evaluate(() => scrollY)) - firstY;
  }
  const before = await snapshot(page);
  expect(before.index).toBe(count + 2);
  expect(before.writing).toEqual(Array(3).fill('translateY(0%)'));
  const footer = await page
    .locator('[data-scene="location"]')
    .evaluate((node) => Math.round(node.getBoundingClientRect().top + scrollY));
  // The last card clears during the final glide, before the footer gates run.
  const travelEnd = before.y + step;
  const halfwayY = Math.round(travelEnd + (footer - travelEnd) * 0.25);
  await page.evaluate((y) => scrollTo(0, y), halfwayY);
  await settle(page);
  const halfway = await snapshot(page);
  expect(
    halfway.writing.every((value, i) => {
      const y = parseFloat(value.slice('translateY('.length));
      return y < 0 && y > (i < 2 ? -10 : -100);
    }),
  ).toBe(true);
  await page.screenshot({ path: info.outputPath('footer-exit-in-progress.png'), scale: 'css' });
  await page.evaluate((y) => scrollTo(0, y), footer);
  await settle(page);
  const after = await snapshot(page);
  expect(after.writing).toEqual(['translateY(-10%)', 'translateY(-10%)', 'translateY(-100%)']);
  expect(after.heading.every((row) => row.opacity === 0)).toBe(true);
  expect(after.tunerHidden).toBe(true);
  expect(after.tunerInert).toBe(true);
  await page.evaluate((y) => scrollTo(0, y), halfwayY);
  await settle(page);
  expect(await snapshot(page)).toEqual(halfway);
  await page.evaluate((y) => scrollTo(0, y), before.y);
  await settle(page);
  expect(await snapshot(page)).toEqual(before);
});
