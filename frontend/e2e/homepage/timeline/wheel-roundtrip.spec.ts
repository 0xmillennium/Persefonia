import { settleScroll } from '../../support/scroll';
import { expect, test, type Page } from '../../support/test';

async function settled(page: Page, completeEntrance = false) {
  await settleScroll(page);
  await expect
    .poll(() =>
      page.locator('[data-scene="gallery"]').evaluate((node) => {
        const index = Number((node as HTMLElement).dataset.galleryIndex);
        return Math.abs(index - Math.round(index));
      }),
    )
    .toBeLessThan(0.001);
  if (completeEntrance) await page.clock.fastForward(2000);
}

async function state(page: Page) {
  return page.evaluate(() => {
    const selectors =
      '.hero-writing-text,.hero-writing-visual:not([hidden])>span,.statement-word,.statement-word-text,.capabilities li>span,.brand-statement>span>span,[data-identity-slot]>g,[data-scene-wipe],[data-scene-wipe]>i,.gallery-face,.gallery-space,.gallery-writing-rise,.collection-tuner,.gallery-instrument,.location-system,.location-strip';
    return {
      y: scrollY,
      footer: [...document.querySelectorAll<HTMLElement>('.footer-rise')].map((node) => ({
        y:
          (new DOMMatrixReadOnly(getComputedStyle(node).transform).m42 /
            node.getBoundingClientRect().height) *
          100,
        opacity: node.style.opacity,
        inert: node.parentElement!.inert,
        overflow: getComputedStyle(node.parentElement!).overflow,
      })),
      footerPhase: (() => {
        const box = document.querySelector('[data-scene="location"]')!.getBoundingClientRect();
        return (
          (scrollY - Math.floor(box.top + scrollY)) /
          Math.max(1, Math.floor(Math.min(innerHeight, box.height - innerHeight)))
        );
      })(),
      effects: [...document.querySelectorAll<HTMLElement | SVGElement>(selectors)]
        .filter((node) => node.closest<HTMLElement>('.scene-face')?.dataset.visible !== 'false')
        .map((node) => ({
          transform: node.style.transform,
          opacity: node.style.opacity,
          visibility: node.style.visibility,
          clip: node.style.clipPath,
          hidden: node.getAttribute('hidden'),
          curtain: node.style.getPropertyValue('--curtain-solid'),
          entry: node.style.getPropertyValue('--gallery-entry-opacity'),
          exit: node.style.getPropertyValue('--gallery-exit'),
        })),
      index: Math.round(
        Number(document.querySelector<HTMLElement>('[data-scene="gallery"]')!.dataset.galleryIndex),
      ),
    };
  });
}

test('every notch retraces scene effects and exits while finite entrances and reverse footer slits retain ownership', async ({
  page,
}, info) => {
  test.setTimeout(180000);
  await page.goto('/');
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
  await expect(page.locator('[data-home-loader]')).toBeHidden();
  await page.clock.install();
  await page.mouse.move(20, 650);
  await settled(page, true);
  const forward = [await state(page)];
  const bottom = await page.evaluate(() => document.documentElement.scrollHeight - innerHeight);
  while (forward[forward.length - 1]!.y < bottom) {
    expect(forward.length).toBeLessThan(150);
    await page.mouse.wheel(0, 120);
    await settled(page, true);
    const next = await state(page);
    expect(next.y).toBeGreaterThan(forward[forward.length - 1]!.y);
    if (next.footerPhase > 0) {
      expect(next.footer).toHaveLength(5);
      expect(next.footer.every((row) => row.y === 0 && row.opacity === '1' && !row.inert)).toBe(
        true,
      );
    }
    forward.push(next);
  }
  for (let i = forward.length - 2; i >= 0; i--) {
    await page.mouse.wheel(0, -120);
    await settled(page, true);
    const actual = await state(page),
      expected = forward[i]!;
    const { footer, ...scene } = actual;
    const { footer: forwardFooter, ...forwardScene } = expected;
    expect(scene, `reverse notch ${forward.length - 1 - i}`).toEqual(forwardScene);
    // B completes forward entrance. Backward departure holds the authored slit
    // at the same input coordinate, instead of replaying a completed entrance.
    expect(footer).toHaveLength(forwardFooter.length);
    const phase = Math.max(0, Math.min(1, actual.footerPhase / forward.at(-1)!.footerPhase));
    footer.forEach((row, index) => {
      const detail = index >= 3,
        ordinal = detail ? index - 3 : index;
      const start = (detail ? 0.45 : 0) + ordinal * (detail ? 0.05 : 0.08);
      const p = Math.max(0, Math.min(1, (phase - start) / (detail ? 0.35 : 0.45)));
      expect(row.y, `footer row ${index} reverse notch ${forward.length - 1 - i}`).toBeCloseTo(
        Math.pow(1 - p, 5) * 100,
        // CSS transform matrices serialize to six significant digits. This
        // is below 0.0005% of a row, rather than demanding unavailable precision.
        3,
      );
      expect(row.opacity).toBe(p === 0 ? '0' : '1');
      expect(row.inert).toBe(p !== 1);
      expect(row.overflow).toBe('hidden');
    });
  }
  await info.attach('notch-positions', {
    body: JSON.stringify(forward.map((value) => ({ y: value.y, index: value.index }))),
    contentType: 'application/json',
  });
});
