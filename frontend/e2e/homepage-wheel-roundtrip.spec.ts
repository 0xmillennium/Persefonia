import { expect, test, type Page } from '@playwright/test';

async function settled(page: Page) {
    await page.waitForTimeout(190);
    await page.evaluate(() => new Promise<void>(resolve => {
        let last = scrollY, unchanged = 0;
        function frame() {
            unchanged = scrollY === last ? unchanged + 1 : 0;
            last = scrollY;
            if (unchanged >= 8) resolve(); else requestAnimationFrame(frame);
        }
        requestAnimationFrame(frame);
    }));
    await expect.poll(() => page.locator('[data-scene="gallery"]').evaluate(node => {
        const index = Number((node as HTMLElement).dataset.galleryIndex);
        return Math.abs(index - Math.round(index));
    })).toBeLessThan(.001);
}

async function state(page: Page) {
    return page.evaluate(() => {
        const selectors = '.hero-writing-text,.hero-writing-visual:not([hidden])>span,.statement-word,.statement-word-text,.capabilities li>span,.brand-statement>span>span,[data-identity-slot]>g,[data-scene-wipe],[data-scene-wipe]>i,.gallery-face,.gallery-space,.gallery-writing-rise,.collection-tuner,.gallery-instrument,.footer-rise,.location-system,.location-strip';
        return {
            y: scrollY,
            effects: [...document.querySelectorAll<HTMLElement | SVGElement>(selectors)]
                .filter(node => node.closest<HTMLElement>('.scene-face')?.dataset.visible !== 'false').map(node => ({
                transform: node.style.transform, opacity: node.style.opacity, visibility: node.style.visibility,
                clip: node.style.clipPath, hidden: node.getAttribute('hidden'),
                curtain: node.style.getPropertyValue('--curtain-solid'),
                entry: node.style.getPropertyValue('--gallery-entry-opacity'),
                exit: node.style.getPropertyValue('--gallery-exit')
            })),
            index: Math.round(Number(document.querySelector<HTMLElement>('[data-scene="gallery"]')!.dataset.galleryIndex))
        };
    });
}

test('every notch from top to bottom retraces the same effects and positions on return', async ({ page }, info) => {
    test.setTimeout(180000);
    await page.goto('/');
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
    await expect(page.locator('[data-home-loader]')).toBeHidden();
    await page.mouse.move(20, 650);
    await settled(page);
    const forward = [await state(page)];
    const bottom = await page.evaluate(() => document.documentElement.scrollHeight - innerHeight);
    while (forward[forward.length - 1]!.y < bottom) {
        expect(forward.length).toBeLessThan(150);
        await page.mouse.wheel(0, 120);
        await settled(page);
        const next = await state(page);
        expect(next.y).toBeGreaterThan(forward[forward.length - 1]!.y);
        forward.push(next);
    }
    for (let i = forward.length - 2; i >= 0; i--) {
        await page.mouse.wheel(0, -120);
        await settled(page);
        expect(await state(page), `reverse notch ${forward.length - 1 - i}`).toEqual(forward[i]);
    }
    await info.attach('notch-positions', { body: JSON.stringify(forward.map(value => ({ y: value.y, index: value.index }))), contentType: 'application/json' });
});

test('rapid direction changes preserve pending notch counts through the writing handoff', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
    await expect(page.locator('[data-home-loader]')).toBeHidden();
    await page.locator('[data-scene="mark"]').evaluate(node => scrollTo(0, Math.round(node.getBoundingClientRect().top + scrollY + 1140)));
    await settled(page);
    const before = await state(page);
    await page.mouse.move(20, 650);
    for (const direction of [1, 1, 1, 1, -1, -1, -1, -1]) await page.mouse.wheel(0, direction * 120);
    await settled(page);
    expect(await state(page)).toEqual(before);
});

test('tuner wheel notches change collections while keeping the page position', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
    await expect(page.locator('[data-home-loader]')).toBeHidden();
    await page.locator('.home-nav a[href="#work"]').evaluate(node => (node as HTMLAnchorElement).click());
    await settled(page);
    await page.mouse.move(20, 650);
    for (let i = 0; i < 3; i++) { await page.mouse.wheel(0, 120); await settled(page); }
    const before = await state(page);
    await page.locator('.collection-tuner').hover();
    await page.mouse.wheel(0, 120);
    await settled(page);
    expect((await state(page)).y).toBe(before.y);
    await expect(page.locator('[data-scene="gallery"]')).toHaveAttribute('data-category', 'articles');
    await page.mouse.wheel(0, -120);
    await settled(page);
    expect((await state(page)).y).toBe(before.y);
    expect((await state(page)).index).toBe(0);
    await expect(page.locator('[data-scene="gallery"]')).toHaveAttribute('data-category', 'projects');
});
