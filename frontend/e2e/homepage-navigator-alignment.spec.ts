import { expect, test } from '@playwright/test';
import { difference } from '../experiments/scroll-film-navigator/tests/pixels';

for (const [label, progress] of [['photo and opening', 0], ['shared window zoom', .2], ['full-screen film', .45]] as const) {
    test(`navigator preserves ${label} framing with a scrollbar gutter`, async ({ page }, info) => {
        // Reserve a real layout gutter even on hosts that use overlay scrollbars.
        // Appending to the served stylesheet preserves the production CSP.
        await page.route('**/*.css', async route => {
            const response = await route.fetch();
            await route.fulfill({ response, body: `${await response.text()}\nhtml{scrollbar-gutter:stable}` });
        });
        await page.clock.install();
        await page.goto('/');
        const root = page.locator('[data-home-v2]');
        await expect(root).toHaveAttribute('data-ready', 'true');
        await expect(page.locator('[data-home-loader]')).toBeHidden();
        if (progress > 0) {
            await page.locator('[data-scene="hero"]').evaluate((node, progress) => scrollTo(0, node.getBoundingClientRect().top + scrollY + node.getBoundingClientRect().height * progress), progress);
            await expect(page.locator('[data-film-status]')).not.toHaveText('Robotic arm · 001 / 192');
        }
        await page.clock.pauseAt(await page.evaluate(() => Date.now() + 50));
        const bounds = (await page.locator('.home-environment').boundingBox())!;
        if (!info.project.use.isMobile) expect(bounds.width).toBeLessThan(page.viewportSize()!.width);
        const canvas = page.locator('[data-film-canvas]');
        const dimensions = await canvas.evaluate(node => ({ buffer: (node as HTMLCanvasElement).width, width: node.getBoundingClientRect().width, dpr: devicePixelRatio }));
        expect(dimensions.buffer).toBe(Math.round(dimensions.width * Math.min(dimensions.dpr, 1.5, 2560 / Math.max(bounds.width, bounds.height))));
        // Far from the pointer stroke: refraction must stay local rather than
        // squeezing the entire picture when the canvas replaces native media.
        const clip = { x: Math.floor(bounds.width * .78), y: 350, width: Math.floor(bounds.width * .18), height: 150 };
        const before = await page.screenshot({ clip, scale: 'css' });
        await page.mouse.move(30, 130);
        await page.mouse.move(90, 140, { steps: 4 });
        await page.clock.runFor(32);
        await expect(root).toHaveAttribute('data-gpu-sheet', 'true');
        const during = await page.screenshot({ clip, scale: 'css' });
        expect(difference(before, during).mean).toBeLessThan(1.5);
        await page.clock.fastForward(5000);
        await expect(canvas).toHaveCSS('opacity', '0');
        expect(difference(before, await page.screenshot({ clip, scale: 'css' })).mean).toBeLessThan(.1);
    });
}
