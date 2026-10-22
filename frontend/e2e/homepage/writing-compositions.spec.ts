import { startOnFilm } from '../support/homepage';
import { test, expect } from '../support/test';
import { visit, writingPosition, position, settlePresentation } from '../support/scenes';

test('footer navigation keeps complete readable groups at intermediate stops and on reverse', async ({
  page,
}) => {
  await startOnFilm(page, 'tree');
  await visit(page);
  const states = [];
  for (const progress of [0.08, 0.2, 0.5, 0.2, 0.08]) {
    await position(page, 'location', progress);
    await settlePresentation(page);
    const read = () =>
      page.locator('.footer-rise').evaluateAll((ns) =>
        ns.map((n) => ({
          opacity: Number(getComputedStyle(n).opacity),
          y: new DOMMatrixReadOnly(getComputedStyle(n).transform).m42,
          height: n.getBoundingClientRect().height,
        })),
      );
    await expect
      .poll(async () =>
        (await read()).every((r) => r.opacity >= 0.7 && Math.abs(r.y) <= r.height * 0.15),
      )
      .toBe(true);
    const state = await read();
    await page.waitForTimeout(150);
    expect(await read()).toEqual(state);
    states.push(state);
  }
  expect(states[0]).toEqual(states[4]);
  expect(states[1]).toEqual(states[3]);
});

test('stopped semantic phrases stay readable, include late rows and reverse without settling', async ({
  page,
}) => {
  await startOnFilm(page, 'tree');
  await visit(page);
  for (const [scene, selector] of [
    ['statements', '.statement-word'],
    ['capabilities', '.capabilities li>span'],
    ['mark', '.brand-statement>span>span'],
  ] as const) {
    const states = [];
    for (const offset of [75, 180, 421, 180, 75]) {
      await writingPosition(page, scene, offset);
      const read = () =>
        page.locator(selector).evaluateAll((ns) =>
          ns.map((n) => {
            const style = getComputedStyle(n);
            const text = n.querySelector<HTMLElement>('.statement-word-text') ?? n;
            const y = new DOMMatrixReadOnly(getComputedStyle(text).transform).m42;
            const filter = (n as HTMLElement).style.filter;
            const id = filter.match(/#([\w-]+)/)?.[1];
            return {
              opacity: Number(style.opacity),
              y,
              height: text.getBoundingClientRect().height,
              blur: id
                ? Number(
                    document
                      .getElementById(id)
                      ?.querySelector('feGaussianBlur')
                      ?.getAttribute('stdDeviation'),
                  )
                : Number(filter.match(/blur\(([\d.]+)px\)/)?.[1] ?? 0),
            };
          }),
        );
      const state = await read();
      for (const row of state) {
        expect(row.opacity).toBeGreaterThanOrEqual(0.7);
        expect(Math.abs(row.y)).toBeLessThanOrEqual(row.height * 0.15);
        expect(row.blur).toBeLessThanOrEqual(0.65);
      }
      await page.waitForTimeout(150);
      expect(await read()).toEqual(state);
      states.push(state);
    }
    expect(states[0]).toEqual(states[4]);
    expect(states[1]).toEqual(states[3]);
    expect(states[0]).not.toEqual(states[2]);
  }
});
