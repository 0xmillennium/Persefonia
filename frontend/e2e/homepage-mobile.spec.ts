import { expect, test, type Page } from '@playwright/test';

const errors = new WeakMap<Page, string[]>();
test.beforeEach(({ page }) => {
  const failures: string[] = [];
  errors.set(page, failures);
  page.on('pageerror', (error) => failures.push(error.message));
});
test.afterEach(({ page }) => {
  expect(errors.get(page)).toEqual([]);
});

async function visit(page: Page) {
  await page.goto('/');
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
  await expect(page.locator('[data-home-loader]')).toBeHidden();
}
async function scene(page: Page, name: string) {
  await page
    .locator(`[data-scene="${name}"]`)
    .evaluate((node) => scrollTo(0, node.getBoundingClientRect().top + scrollY + 640));
  await expect(page.locator(`[data-scene="${name}"] .scene-face`)).toHaveAttribute(
    'data-visible',
    'true',
  );
}
async function fragmentPoint(page: Page) {
  return page.locator('[data-physical-identity] svg').evaluate((svg) => {
    const r = svg.getBoundingClientRect();
    for (let y = Math.max(100, r.top); y < Math.min(innerHeight - 20, r.bottom); y += 4)
      for (let x = Math.max(20, r.left); x < Math.min(innerWidth - 100, r.right); x += 4) {
        const target = document.elementFromPoint(x, y);
        if (target?.closest('[data-fragment]') && svg.contains(target)) return { x, y };
      }
    throw new Error('No visible identity fragment can receive a gesture');
  });
}

test('mobile menu contains focus, restores navigation, and closes on a desktop resize', async ({
  page,
}, info) => {
  test.skip(info.project.name === 'desktop-1440', 'Compact navigation at tablet and phone widths');
  await visit(page);
  const toggle = page.getByRole('button', { name: 'Open menu', exact: true });
  const dialog = page.getByRole('dialog');
  expect((await page.locator('.home-nav').boundingBox())!.height).toBeLessThanOrEqual(90);
  await scene(page, 'mark');
  const position = await page.evaluate(() => scrollY);
  await toggle.click();
  await expect(dialog).toBeVisible();
  await expect(toggle).toHaveAttribute('aria-expanded', 'true');
  await page.mouse.wheel(0, 100);
  await page.waitForTimeout(220);
  expect(await page.evaluate(() => scrollY)).toBe(position);
  for (let i = 0; i < 7; i++) {
    await page.keyboard.press('Tab');
    // Native dialogs allow tabbing into browser chrome (activeElement is body),
    // while the rest of this document remains unavailable to focus.
    expect(
      await dialog.evaluate(
        (node) => node.contains(document.activeElement) || document.activeElement === document.body,
      ),
    ).toBe(true);
  }
  await page.locator('main').evaluate((node) => node.focus());
  expect(await dialog.evaluate((node) => node.contains(document.activeElement))).toBe(true);
  await page.screenshot({ path: info.outputPath('mobile-menu.png') });
  await page.keyboard.press('Escape');
  await expect(dialog).toBeHidden();
  await expect(toggle).toBeFocused();
  await expect(page.locator('html')).not.toHaveAttribute('data-menu-open', 'true');
  await toggle.click();
  await page.setViewportSize({ width: 1200, height: 900 });
  await expect(dialog).toBeHidden();
  await expect(page.locator('.home-nav nav a').first()).toBeVisible();
  await expect(page.locator('.home-nav nav a').first()).toBeFocused();
  await expect(page.locator('html')).not.toHaveAttribute('data-menu-open', 'true');
});

test('drawer links select the semantic gallery section without trapping focus', async ({
  page,
}, info) => {
  test.skip(info.project.name === 'desktop-1440', 'Drawer navigation');
  await visit(page);
  await page.getByRole('button', { name: 'Open menu', exact: true }).click();
  await page.locator('dialog a[href="#writing"]').click();
  await expect(page.getByRole('dialog')).toBeHidden();
  await expect(page.locator('input[value="articles"]')).toBeChecked();
  await expect(page.locator('#writing')).toBeFocused();
  await expect(page.locator('.gallery-face')).toHaveAttribute('data-visible', 'true');
});

test('finger contact moves logo pieces and vertical swipes retain native scrolling', async ({
  page,
}, info) => {
  test.skip(info.project.name !== 'mobile-390', 'Native emulated touch events');
  await visit(page);
  await scene(page, 'mark');
  const pieces = page.locator('[data-physical-identity] [data-fragment]');
  const poses = () =>
    pieces.evaluateAll((nodes) => nodes.map((node) => node.getAttribute('transform')));
  const before = await poses(),
    point = await fragmentPoint(page),
    y = await page.evaluate(() => scrollY);
  const session = await page.context().newCDPSession(page);
  await session.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [point] });
  for (let i = 1; i <= 8; i++)
    await session.send('Input.dispatchTouchEvent', {
      type: 'touchMove',
      touchPoints: [{ x: point.x + i * 10, y: point.y }],
    });
  await expect.poll(poses).not.toEqual(before);
  expect(await page.evaluate(() => scrollY)).toBe(y);
  await expect(page.locator('[data-hold-countdown]')).toBeHidden();
  await page.screenshot({ path: info.outputPath('touch-logo.png') });
  await session.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
  await expect(page.locator('.physical-identity .navigator')).toHaveAttribute(
    'visibility',
    'hidden',
  );
  await page.waitForTimeout(700);
  const next = await fragmentPoint(page),
    start = await page.evaluate(() => scrollY);
  await session.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [next] });
  for (let i = 1; i <= 6; i++)
    await session.send('Input.dispatchTouchEvent', {
      type: 'touchMove',
      touchPoints: [{ x: next.x, y: next.y - i * 18 }],
    });
  await session.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
  await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(start + 20);
  await expect(page.locator('.physical-identity .navigator')).toHaveAttribute(
    'visibility',
    'hidden',
  );
  await session.detach();
});

test('phone-width mouse effects survive input changes and remain visible inside the menu', async ({
  page,
}, info) => {
  test.skip(
    info.project.name !== 'desktop-1440',
    'Fine pointer at phone width with a simulated device change',
  );
  await page.setViewportSize({ width: 390, height: 850 });
  await visit(page);
  await page.mouse.move(40, 250);
  await page.mouse.move(300, 260, { steps: 8 });
  await expect(page.locator('.home-pointer__dot')).toBeVisible();
  await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-gpu-sheet', 'true');
  const session = await page.context().newCDPSession(page);
  await session.send('Emulation.setTouchEmulationEnabled', { enabled: true, maxTouchPoints: 1 });
  await expect(page.locator('.home-pointer__dot')).toBeHidden();
  await session.send('Emulation.setTouchEmulationEnabled', { enabled: false });
  await page.mouse.move(240, 180);
  await expect(page.locator('.home-pointer__dot')).toBeVisible();
  await page.getByRole('button', { name: 'Open menu', exact: true }).click();
  await page.mouse.move(80, 240);
  await expect(page.locator('dialog .home-pointer__dot')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toBeHidden();
  await page.mouse.move(240, 180);
  await expect(page.locator('body>.home-pointer>.home-pointer__dot')).toBeVisible();
  await page.locator('[data-scene="gallery"]').evaluate((node) => {
    const entry =
      parseFloat((node as HTMLElement).style.getPropertyValue('--gallery-entry-distance')) || 0;
    const rect = node.getBoundingClientRect();
    scrollTo(0, rect.top + scrollY + entry + (rect.height - entry) * 0.4);
  });
  await expect(page.locator('.gallery-face')).toHaveAttribute('data-visible', 'true');
  await page.mouse.move(350, 480);
  await expect
    .poll(() =>
      page.locator('.gallery-category:not([hidden]) .gallery-group').evaluate((node) => {
        const matrix = new DOMMatrixReadOnly(getComputedStyle(node).transform);
        return Math.abs(matrix.m13) + Math.abs(matrix.m23);
      }),
    )
    .toBeGreaterThan(0.01);
  await session.detach();
});

test('portrait and landscape writing stays centered, ordered and inside the viewport', async ({
  page,
}, info) => {
  test.skip(
    info.project.name !== 'desktop-1440',
    'One responsive sweep avoids repeating the same geometry',
  );
  await visit(page);
  for (const viewport of [
    { width: 320, height: 700 },
    { width: 390, height: 850 },
    { width: 834, height: 900 },
    { width: 850, height: 390 },
  ]) {
    await page.setViewportSize(viewport);
    await page.evaluate(() => scrollTo(0, 0));
    await expect
      .poll(() =>
        page
          .locator('.hero-writing-row')
          .first()
          .evaluate((node) => {
            const r = node.getBoundingClientRect();
            return Math.abs(r.x + r.width / 2 - innerWidth / 2);
          }),
      )
      .toBeLessThan(2);
    for (const [name, selector] of [
      ['statements', '.statement-top'],
      ['capabilities', '.capabilities li>span'],
    ] as const) {
      await scene(page, name);
      const rects = await page
        .locator(selector)
        .evaluateAll((nodes) => nodes.map((node) => node.getBoundingClientRect().toJSON()));
      for (const r of rects) {
        expect(Math.abs(r.x + r.width / 2 - viewport.width / 2)).toBeLessThan(2);
        expect(r.x).toBeGreaterThanOrEqual(0);
        expect(r.right).toBeLessThanOrEqual(viewport.width + 1);
      }
    }
    await scene(page, 'mark');
    const rows = await page
      .locator('.brand-statement>span')
      .evaluateAll((nodes) => nodes.map((node) => node.getBoundingClientRect().toJSON()));
    const logo = (await page.locator('[data-physical-identity] svg').boundingBox())!;
    expect(rows[0]!.bottom).toBeLessThan(logo.y);
    expect(rows[1]!.top).toBeGreaterThan(logo.y + logo.height);
    expect(rows[1]!.bottom).toBeLessThan(viewport.height);
    expect(Math.abs(logo.x + logo.width / 2 - viewport.width / 2)).toBeLessThan(2);
    await page.screenshot({
      path: info.outputPath(`mobile-mark-${viewport.width}x${viewport.height}.png`),
    });
  }
});
