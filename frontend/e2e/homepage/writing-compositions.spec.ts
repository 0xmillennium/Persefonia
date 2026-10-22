import { startOnFilm } from '../support/homepage';
import { test, expect } from '../support/test';
import { visit, writingPosition, position, settlePresentation } from '../support/scenes';

test('footer slit entrance completes late details after input stops and reconciles reverse', async ({
  page,
}) => {
  await startOnFilm(page, 'tree');
  await visit(page);
  const read = () =>
    page.locator('.footer-rise').evaluateAll((nodes) =>
      nodes.map((n) => ({
        y: new DOMMatrixReadOnly(getComputedStyle(n).transform).m42,
        opacity: Number(getComputedStyle(n).opacity),
      })),
    );
  for (const progress of [0.08, 0.2, 0.5]) {
    await position(page, 'location', progress);
    await settlePresentation(page);
    await expect
      .poll(async () => (await read()).every((r) => r.y === 0 && r.opacity === 1))
      .toBe(true);
  }
  await position(page, 'location', 0.08);
  await settlePresentation(page);
  const exiting = await read();
  expect(exiting.some((r) => r.y > 1)).toBe(true);
  await page.waitForTimeout(1400);
  expect(await read()).toEqual(exiting);
  await position(page, 'location', 0.1);
  await expect
    .poll(async () => (await read()).every((r) => r.y === 0 && r.opacity === 1))
    .toBe(true);
  await writingPosition(page, 'location', -2);
  await expect.poll(async () => (await read()).every((r) => r.y > 0 && r.opacity === 0)).toBe(true);
  for (const slot of await page.locator('.footer-slot').all())
    await expect(slot).toHaveCSS('overflow', 'hidden');
});

test('stopped entrances complete all rows and interrupted exits retain ownership through reverse and resume', async ({
  page,
}) => {
  await startOnFilm(page, 'tree');
  await visit(page);
  for (const [scene, selector] of [
    ['statements', '.statement-word-text'],
    ['capabilities', '.capabilities li>span'],
    ['mark', '.brand-statement>span>span'],
  ] as const) {
    const read = () =>
      page.locator(selector).evaluateAll((nodes) =>
        nodes.map((n) => ({
          y: new DOMMatrixReadOnly(getComputedStyle(n).transform).m42,
          opacity: Number(getComputedStyle(n).opacity),
        })),
      );
    await writingPosition(page, scene, 1);
    await expect
      .poll(async () => (await read()).every((r) => r.y === 0 && r.opacity === 1))
      .toBe(true);
    if (scene === 'statements')
      await expect
        .poll(() =>
          page
            .locator('.statement-word')
            .evaluateAll((ns) => ns.every((n) => (n as HTMLElement).style.filter === '')),
        )
        .toBe(true);
    await writingPosition(page, scene, 0);
    await writingPosition(page, scene, 10);
    await writingPosition(page, scene, 1450);
    const exit = await read();
    expect(exit.some((r) => r.y < -1)).toBe(true);
    await page.waitForTimeout(1600);
    expect(await read()).toEqual(exit);
    await writingPosition(page, scene, 1250);
    expect((await read()).some((r, i) => r.y > exit[i]!.y)).toBe(true);
    await writingPosition(page, scene, 75);
    await page.evaluate(() => {
      dispatchEvent(new PageTransitionEvent('pagehide', { persisted: true }));
      dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true }));
    });
    await expect
      .poll(async () => (await read()).every((r) => r.y === 0 && r.opacity === 1))
      .toBe(true);
    await page.setViewportSize({
      width: page.viewportSize()!.width + 1,
      height: page.viewportSize()!.height,
    });
    expect((await read()).every((r) => r.y === 0)).toBe(true);
  }
});

test('gallery and footer retain staggered slit geometry before finite completion', async ({
  page,
}) => {
  await startOnFilm(page, 'tree');
  await visit(page);
  await page.clock.install({ time: new Date('2026-01-01T00:00:00Z') });
  await page.clock.pauseAt(new Date('2026-01-01T00:00:01Z'));
  const move = async (scene: string, offset: number) => {
    await page.locator(`[data-scene="${scene}"]`).evaluate((n, offset) => {
      scrollTo(0, Math.ceil(n.getBoundingClientRect().top + scrollY + offset));
      dispatchEvent(new Event('scroll'));
    }, offset);
    await page.clock.runFor(300);
  };
  await move('location', -2);
  await move('location', 1);
  const rows = () =>
    page
      .locator('.footer-rise')
      .evaluateAll((ns) =>
        ns.map(
          (n) =>
            new DOMMatrixReadOnly(getComputedStyle(n).transform).m42 /
            n.getBoundingClientRect().height,
        ),
      );
  // Sample the authored stagger after the third menu row starts. Native RAF
  // delivery differs across engines even with the same controlled clock step.
  await expect
    .poll(async () => {
      await page.clock.runFor(32);
      return (await rows())[2]!;
    })
    .toBeLessThan(1);
  const partial = await rows();
  expect(partial[0]!).toBeGreaterThan(0);
  expect(partial[0]!).toBeLessThan(partial[2]!);
  expect(partial[4]!).toBeGreaterThan(partial[2]!);
  await page.clock.runFor(1400);
  expect(await rows()).toEqual([0, 0, 0, 0, 0]);
  const entry = await page.locator('[data-scene="gallery"]').evaluate((n) => {
    const box = n.getBoundingClientRect();
    const distance = parseFloat(
      (n as HTMLElement).style.getPropertyValue('--gallery-entry-distance'),
    );
    const count = n.querySelectorAll('#work .gallery-card').length;
    const step = (0.7 * (box.height - distance)) / (count + 7);
    return box.top + scrollY + distance + (box.height - distance) * 0.12 + step * 3;
  });
  await page.evaluate((y) => {
    scrollTo(0, Math.round(y) - 2);
    dispatchEvent(new Event('scroll'));
  }, entry);
  await page.clock.runFor(800);
  await page.evaluate((y) => {
    scrollTo(0, Math.round(y) + 1);
    dispatchEvent(new Event('scroll'));
  }, entry);
  await page.clock.runFor(300);
  const rises = () =>
    page.locator('.gallery-writing-rise').evaluateAll((ns) =>
      ns.map((n) => ({
        y: new DOMMatrixReadOnly(getComputedStyle(n).transform).m42,
        h: n.getBoundingClientRect().height,
      })),
    );
  const rising = await rises();
  expect(rising[0]!.y).toBeGreaterThan(0);
  expect(rising[0]!.y).toBeLessThan(rising[0]!.h);
  expect(rising[1]!.y / rising[1]!.h).toBeGreaterThan(rising[0]!.y / rising[0]!.h);
  await page.clock.runFor(1400);
  expect((await rises()).map((r) => r.y)).toEqual([0, 0]);
  for (const slot of await page.locator('.gallery-writing-slot').all())
    await expect(slot).toHaveCSS('overflow', 'clip');
});
