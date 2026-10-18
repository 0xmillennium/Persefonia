import { expect, test } from '@playwright/test';
import { visit, position } from '../support/scenes';

test('compact identity activation reaches the authored M and resets accessibly', async ({
  page,
}, info) => {
  await visit(page);
  const button = page.getByRole('button', { name: 'Inspect 0xmillennium identity' });
  if (info.project.name.startsWith('mobile')) await button.tap();
  else {
    await button.focus();
    await page.keyboard.press('Enter');
  }
  const mark = page.locator('.home-brand .m-mark');
  await expect
    .poll(() => mark.evaluate((node) => Number(getComputedStyle(node).opacity)), { timeout: 12000 })
    .toBe(1);
  await page.keyboard.press('Escape');
  await expect.poll(() => mark.evaluate((node) => Number(getComputedStyle(node).opacity))).toBe(0);
});

test('logo entrance retraces each piece when scrolling backward and stops with the scroll', async ({
  page,
}, info) => {
  await visit(page);
  const slots = page.locator('[data-identity-slot]');
  const state = () =>
    slots.evaluateAll((nodes) =>
      nodes.map((slot) => ({
        y: new DOMMatrixReadOnly(getComputedStyle(slot.firstElementChild!).transform).m42,
        clipped: slot.hasAttribute('clip-path'),
      })),
    );
  const at = async (p: number) => {
    await page.locator('[data-scene="mark"]').evaluate((node, p) => {
      const gap = Number.parseFloat(
        document
          .querySelector<HTMLElement>('[data-home-v2]')!
          .style.getPropertyValue('--writing-gap'),
      );
      const y = node.getBoundingClientRect().top + scrollY - gap + gap * p;
      scrollTo(0, p === 0 ? Math.floor(y) : Math.ceil(y));
    }, p);
    await page.waitForTimeout(50);
  };
  await at(0);
  const closed = await state();
  expect(closed.every((piece) => piece.clipped && piece.y > 0)).toBe(true);
  await at(0.45);
  await expect
    .poll(async () => (await state()).some((piece, i) => piece.y > 0 && piece.y < closed[i]!.y))
    .toBe(true);
  const partial = await state();
  const relative = partial.map((piece, i) => piece.y / closed[i]!.y);
  expect(Math.max(...relative) - Math.min(...relative)).toBeGreaterThan(0.1);
  await page.waitForTimeout(250);
  expect(await state()).toEqual(partial);
  expect(
    await page
      .locator('[data-physical-identity] svg')
      .evaluate((node) =>
        node
          .getAnimations({ subtree: true })
          .some((animation) => animation.playState === 'running'),
      ),
  ).toBe(false);
  await page.screenshot({ path: info.outputPath('logo-entry-partial.png') });
  await at(1);
  await expect
    .poll(async () => (await state()).every((piece) => piece.y === 0 && !piece.clipped))
    .toBe(true);
  await at(0.45);
  await expect.poll(state).toEqual(partial);
  await page.screenshot({ path: info.outputPath('logo-entry-reversed.png') });
  await at(0.2);
  const earlier = await state();
  expect(earlier.every((piece, i) => piece.y >= partial[i]!.y)).toBe(true);
  expect(earlier.some((piece, i) => piece.y > partial[i]!.y)).toBe(true);
  await at(0.45);
  await expect.poll(state).toEqual(partial);
  await at(0);
  await expect.poll(state).toEqual(closed);
  await at(0.45);
  await expect.poll(state).toEqual(partial);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect
    .poll(async () => (await state()).every((piece) => piece.y === 0 && !piece.clipped))
    .toBe(true);
  expect(
    await page
      .locator('[data-physical-identity] svg')
      .evaluate((node) => node.getAnimations({ subtree: true }).length),
  ).toBe(0);
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await at(0.45);
  await expect.poll(state).toEqual(partial);
});

test('physical identity responds to pointer contact and restores on scene exit', async ({
  page,
}, info) => {
  test.skip(
    info.project.name.startsWith('mobile'),
    'Mouse-contact case; touch interaction is covered by mobile input checks.',
  );
  await visit(page);
  await position(page, 'mark', 0.5);
  await expect(page.locator('[data-physical-identity] .navigator')).toBeAttached();
  const fragments = page.locator('[data-physical-identity] polygon');
  await expect(fragments).toHaveCount(8);
  const transforms = () =>
    fragments.evaluateAll((nodes) => nodes.map((node) => node.getAttribute('transform')));
  const before = await transforms();
  const box = (await fragments.nth(6).boundingBox())!;
  await page.mouse.move(box.x - 35, box.y + box.height / 2);
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps: 5 });
  await expect.poll(transforms).not.toEqual(before);
  await expect(page.locator('[data-hold-countdown]')).toBeHidden();
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect.poll(transforms).toEqual(before);
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await position(page, 'gallery', 0.3);
  await expect.poll(transforms).toEqual(before);
});
