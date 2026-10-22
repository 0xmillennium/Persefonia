import { expect, test } from '../support/test';
import { visit, position, settlePresentation } from '../support/scenes';

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

test('logo entrance completes with stopped input, preserves stagger and gives authored exits priority', async ({
  page,
}, info) => {
  await visit(page);
  await page.clock.install({ time: new Date('2026-01-01T00:00:00Z') });
  await page.clock.pauseAt(new Date('2026-01-01T00:00:01Z'));
  const slots = page.locator('[data-identity-slot]');
  const state = () =>
    slots.evaluateAll((nodes) =>
      nodes.map((slot) => ({
        y: new DOMMatrixReadOnly(getComputedStyle(slot.firstElementChild!).transform).m42,
        clipped: slot.hasAttribute('clip-path'),
      })),
    );
  const move = async (p: number) => {
    await page.locator('[data-scene="mark"]').evaluate((node, p) => {
      const gap = Number.parseFloat(
        document
          .querySelector<HTMLElement>('[data-home-v2]')!
          .style.getPropertyValue('--writing-gap'),
      );
      const y = node.getBoundingClientRect().top + scrollY - gap + gap * p;
      scrollTo(0, p === 0 ? Math.floor(y) : Math.ceil(y));
      dispatchEvent(new Event('scroll'));
    }, p);
    await page.clock.runFor(300);
  };
  const formed = () =>
    state().then((pieces) => pieces.every((piece) => piece.y === 0 && !piece.clipped));
  const noRunningAnimation = () =>
    page
      .locator('[data-physical-identity] svg')
      .evaluate((node) =>
        node
          .getAnimations({ subtree: true })
          .every((animation) => animation.playState !== 'running'),
      );
  await move(0);
  const closed = await state();
  expect(closed).toHaveLength(8);
  expect(closed.every((piece) => piece.clipped && piece.y > 0)).toBe(true);
  await move(0.2);
  const partial = await state();
  expect(partial.some((piece, i) => piece.y > 0 && piece.y < closed[i]!.y)).toBe(true);
  const relative = partial.map((piece, i) => piece.y / closed[i]!.y);
  expect(Math.max(...relative) - Math.min(...relative)).toBeGreaterThan(0.1);
  expect(await noRunningAnimation()).toBe(true);
  await page.screenshot({ path: info.outputPath('logo-entry-partial.png') });
  await page.clock.runFor(300);
  const advancing = await state();
  expect(advancing.every((piece, i) => piece.y <= partial[i]!.y)).toBe(true);
  expect(advancing.some((piece, i) => piece.y < partial[i]!.y)).toBe(true);
  await page.clock.runFor(1800);
  expect(await formed()).toBe(true);
  await move(0.1);
  expect(await formed()).toBe(true);
  await move(0);
  expect(await state()).toEqual(closed);
  await move(0.2);
  expect((await state()).some((piece) => piece.y > 0)).toBe(true);
  const exitAt = async (offset: number) => {
    await page.locator('[data-scene="mark"]').evaluate((node, offset) => {
      scrollTo(0, Math.ceil(node.getBoundingClientRect().top + scrollY + offset));
      dispatchEvent(new Event('scroll'));
    }, offset);
    await page.clock.runFor(300);
  };
  await exitAt(1450);
  const exit = await state();
  expect(exit.every((piece) => piece.clipped && piece.y > 0)).toBe(true);
  const travel = exit.map((piece, i) => piece.y / closed[i]!.y);
  expect(Math.max(...travel) - Math.min(...travel)).toBeLessThan(0.00001);
  await page.clock.runFor(1800);
  expect(await state()).toEqual(exit);
  await exitAt(1250);
  expect((await state()).every((piece, i) => piece.y < exit[i]!.y)).toBe(true);
  await exitAt(1450);
  expect(await state()).toEqual(exit);
  await page.screenshot({ path: info.outputPath('logo-exit-reversed.png') });
  await move(0.2);
  expect(await formed()).toBe(true);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.clock.runFor(100);
  expect(await formed()).toBe(true);
  expect(
    await page
      .locator('[data-physical-identity] svg')
      .evaluate((node) => node.getAnimations({ subtree: true }).length),
  ).toBe(0);
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.clock.runFor(100);
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-reduced', 'false');
  await move(0);
  expect(await state()).toEqual(closed);
  await move(0.2);
  await page.clock.runFor(1800);
  expect(await formed()).toBe(true);
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
  // Physics preloads before the scene is interactive. Wait for cinematic
  // catch-up and the unclipped entrance before measuring and sending input.
  await settlePresentation(page);
  await expect(page.locator('[data-physical-identity] .navigator')).toBeAttached();
  await expect(page.locator('[data-identity-slot][clip-path]')).toHaveCount(0);
  const fragments = page.locator('[data-physical-identity] polygon');
  await expect(fragments).toHaveCount(8);
  const transforms = () =>
    fragments.evaluateAll((nodes) => nodes.map((node) => node.getAttribute('transform')));
  const before = await transforms();
  const strike = async () => {
    const box = (await fragments.nth(6).boundingBox())!;
    await page.mouse.move(box.x - 35, box.y + box.height / 2);
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps: 5 });
    await expect(page.locator('[data-physical-identity] .navigator')).toHaveAttribute(
      'visibility',
      'visible',
    );
    await expect.poll(transforms).not.toEqual(before);
  };
  await strike();
  await expect(page.locator('[data-hold-countdown]')).toBeHidden();
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect.poll(transforms).toEqual(before);
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-reduced', 'false');
  await settlePresentation(page);
  // Displace the logo again so departure must perform its own reset.
  await strike();
  await position(page, 'gallery', 0.3);
  await settlePresentation(page);
  await expect.poll(transforms).toEqual(before);
});
