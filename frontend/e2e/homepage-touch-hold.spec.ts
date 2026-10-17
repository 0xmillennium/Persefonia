import { expect, test, type Page } from '@playwright/test';

test.beforeEach(async ({ page }, info) => {
    test.skip(info.project.name !== 'mobile-390', 'Native touch input at phone size');
    await page.goto('/');
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
    await expect(page.locator('[data-home-loader]')).toBeHidden();
});

async function touch(page: Page) {
    const session = await page.context().newCDPSession(page);
    return {
        start: (x = 180, y = 400) => session.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x, y }] }),
        move: (x: number, y: number) => session.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x, y }] }),
        end: () => session.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] }),
        cancel: () => session.send('Input.dispatchTouchEvent', { type: 'touchCancel', touchPoints: [] }),
        detach: () => session.detach()
    };
}

test('settled hold tolerates finger drift and changes the film only once until release', async ({ page }) => {
    const finger = await touch(page), countdown = page.locator('[data-hold-countdown]');
    const initialY = await page.evaluate(() => scrollY);
    await finger.start();
    await expect(countdown).toBeVisible();
    await page.waitForTimeout(450);
    // A normal finger adjustment exceeded the previous 12px cancellation limit.
    await finger.move(180, 418);
    await expect(countdown).toBeVisible();
    await page.waitForTimeout(200);
    await expect(countdown).toBeVisible();
    expect(await page.evaluate(() => scrollY)).toBe(initialY);
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'tree', { timeout: 12000 });
    await page.waitForTimeout(500);
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'tree');
    await finger.end();
    await finger.detach();
});

test('film long-press suppresses selection and its menu while links retain their native menu', async ({ page }) => {
    const finger = await touch(page);
    await finger.start();
    await expect(page.locator('[data-hold-countdown]')).toBeVisible();
    const menus = await page.evaluate(() => {
        const fire = (target: Element, pointerType: string) => {
            const event = new PointerEvent('contextmenu', { bubbles: true, cancelable: true, pointerType });
            target.dispatchEvent(event);
            return event.defaultPrevented;
        };
        const writing = document.querySelector('.hero-writing-text')!;
        return { film: fire(writing, 'touch'), link: fire(document.querySelector('.home-nav a')!, 'touch'),
            selection: getComputedStyle(writing).userSelect };
    });
    expect(menus).toEqual({ film: true, link: false, selection: 'none' });
    await finger.end();
    // Ordinary mouse context menus remain available on the same film surface.
    expect(await page.locator('.hero-writing-text').first().evaluate(target => {
        const event = new PointerEvent('contextmenu', { bubbles: true, cancelable: true, pointerType: 'mouse' });
        target.dispatchEvent(event); return event.defaultPrevented;
    })).toBe(false);
    await finger.detach();
});

test('quick swipes retain native scrolling and do not complete a film hold', async ({ page }) => {
    const finger = await touch(page), before = await page.evaluate(() => scrollY);
    await finger.start(100, 620);
    for (let i = 1; i <= 6; i++) await finger.move(100, 620 - i * 24);
    await finger.end();
    await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(before + 30);
    await expect(page.locator('[data-hold-countdown]')).toBeHidden();
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'robotic-arm');
    await finger.detach();
});

test('release and browser cancellation stop an unfinished hold', async ({ page }) => {
    const finger = await touch(page), countdown = page.locator('[data-hold-countdown]');
    await finger.start();
    await expect(countdown).toBeVisible();
    await page.waitForTimeout(300);
    await finger.end();
    await expect(countdown).toBeHidden();
    await page.waitForTimeout(900);
    await finger.start();
    await expect(countdown).toBeVisible();
    await page.waitForTimeout(300);
    await finger.cancel();
    await expect(countdown).toBeHidden();
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'robotic-arm');
    await finger.detach();
});
