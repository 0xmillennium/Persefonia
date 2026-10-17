import { expect, test, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { pixels } from '../experiments/scroll-film-navigator/tests/pixels';
const frameVersion = JSON.parse(readFileSync(new URL('../public/home-films/manifest.json', import.meta.url), 'utf8')).identity;
async function frame(page: Page, index: number, wait = true) {
    await page.evaluate(index => {
        const hero = document.querySelector('[data-scene="hero"]')!.getBoundingClientRect();
        const mark = document.querySelector('[data-scene="mark"]')!.getBoundingClientRect();
        const start = hero.top + scrollY, end = mark.top + scrollY + mark.height * .98;
        scrollTo(0, Math.round(start + (end - start) * index / 191));
    }, index);
    if (wait) await expect(page.locator('[data-film-status]')).toHaveText(new RegExp(` · ${String(index + 1).padStart(3, '0')} / 192$`));
}
async function visit(page: Page) {
    await page.goto('/');
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
    await expect(page.locator('[data-home-loader]')).toBeHidden();
}
test('paused native films seek both ways, retain exact end poses and sleep at rest', async ({ page }, info) => {
    test.skip(info.project.name === 'tablet-834', 'Desktop and portrait video tiers');
    await visit(page);
    for (const film of ['robotic-arm', 'tree']) {
        if (film === 'tree') {
            await page.locator('[data-home-v2]').evaluate(node => node.focus({ preventScroll: true }));
            await page.keyboard.press('f');
            await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'tree');
        }
        const video = page.locator(`video[src*="/${film}.mp4"]`);
        for (const index of [20, 104, 191, 20, 0]) {
            await frame(page, index);
            await expect(video).toBeVisible();
            const sample = await video.evaluate(async (node, { index, film, version }) => {
                const video = node as HTMLVideoElement;
                const source = new Image();
                source.src = `/assets/home-films/${version}/${video.videoWidth}/${film}/frame_${String(index + 1).padStart(5, '0')}.webp`;
                await source.decode();
                const canvas = document.createElement('canvas');
                canvas.width = video.videoWidth;
                canvas.height = video.videoHeight;
                const context = canvas.getContext('2d', { willReadFrequently: true })!;
                context.drawImage(video, 0, 0);
                const actual = context.getImageData(0, 0, canvas.width, canvas.height).data;
                context.drawImage(source, 0, 0);
                const expected = context.getImageData(0, 0, canvas.width, canvas.height).data;
                let error = 0;
                for (let i = 0; i < actual.length; i += 4) for (let c = 0; c < 3; c++) error += Math.abs(actual[i + c]! - expected[i + c]!);
                return { error: error / (canvas.width * canvas.height * 3), paused: video.paused, seeking: video.seeking, time: video.currentTime };
            }, { index, film, version: frameVersion });
            expect(sample.paused).toBe(true);
            expect(sample.seeking).toBe(false);
            expect(sample.time).toBeGreaterThanOrEqual(index / 30 - .001);
            expect(sample.time).toBeLessThan((index + 1) / 30);
            expect(sample.error).toBeLessThan(4);
            if (index === 0) {
                const clip = { x: 4, y: Math.round(page.viewportSize()!.height * .65), width: 8, height: 8 };
                const expected = await video.evaluate((node, clip) => {
                    const video = node as HTMLVideoElement, bounds = video.getBoundingClientRect();
                    const canvas = document.createElement('canvas');
                    canvas.width = canvas.height = 8;
                    const context = canvas.getContext('2d')!;
                    context.drawImage(video, (clip.x - bounds.x) / bounds.width * video.videoWidth,
                        (clip.y - bounds.y) / bounds.height * video.videoHeight,
                        clip.width / bounds.width * video.videoWidth, clip.height / bounds.height * video.videoHeight, 0, 0, 8, 8);
                    return [...context.getImageData(0, 0, 8, 8).data];
                }, clip);
                const actual = pixels(await page.screenshot({ clip, scale: 'css' }));
                let error = 0;
                for (let i = 0; i < 64; i++) for (let c = 0; c < 3; c++) error += Math.abs(actual.data[i * actual.channels + c]! - expected[i * 4 + c]!);
                expect(error / (64 * 3)).toBeLessThan(4);
            }
        }
        const time = await video.evaluate(node => (node as HTMLVideoElement).currentTime);
        await page.waitForTimeout(300);
        expect(await video.evaluate(node => (node as HTMLVideoElement).currentTime)).toBe(time);
    }
    await page.screenshot({ path: info.outputPath('native-door-tree.png') });
});
test('unavailable video falls back to qualified frames without WebGL or stale async images', async ({ page }) => {
    await page.route('**/assets/home-videos/**/*.mp4*', route => route.fulfill({ status: 503, body: 'unavailable video' }));
    await page.addInitScript(() => {
        HTMLCanvasElement.prototype.getContext = (() => null) as typeof HTMLCanvasElement.prototype.getContext;
        const state = window as Window & { fallbackWrites: string[] };
        state.fallbackWrites = [];
        const source = Object.getOwnPropertyDescriptor(HTMLImageElement.prototype, 'src')!;
        Object.defineProperty(HTMLImageElement.prototype, 'src', { ...source, set(this: HTMLImageElement, value: string) {
            if (this.hasAttribute('data-film-fallback')) state.fallbackWrites.push(value);
            source.set!.call(this, value);
        } });
        const decode = HTMLImageElement.prototype.decode;
        HTMLImageElement.prototype.decode = function () {
            return this.hasAttribute('data-film-fallback')
                ? new Promise<void>(resolve => setTimeout(resolve, 120)).then(() => decode.call(this)) : decode.call(this);
        };
    });
    await visit(page);
    await frame(page, 20);
    await page.mouse.wheel(0, 120);
    await expect(page.locator('[data-film-status]')).toHaveText('Robotic arm · 025 / 192');
    await frame(page, 100, false);
    await page.waitForFunction(() => (window as Window & { fallbackWrites: string[] }).fallbackWrites.some(source => source.endsWith('frame_00101.webp')));
    await frame(page, 20);
    await page.waitForTimeout(200);
    await expect(page.locator('[data-film-fallback]')).toBeVisible();
    await expect(page.locator('[data-film-fallback]')).toHaveAttribute('src', /frame_00021.webp$/);
    const writes = await page.evaluate(() => (window as Window & { fallbackWrites: string[] }).fallbackWrites);
    for (const source of new Set(writes)) expect(writes.filter(value => value === source)).toHaveLength(source.endsWith('frame_00021.webp') ? 2 : 1);
});
test('incoming video failure uses frame fallback while preserving selected progress', async ({ page }) => {
    await page.route('**/tree.mp4*', route => route.fulfill({ status: 503, body: 'unavailable video' }));
    await visit(page);
    await frame(page, 80);
    await page.locator('[data-home-v2]').evaluate(node => node.focus({ preventScroll: true }));
    await page.keyboard.press('f');
    await expect(page.locator('[data-film-status]')).toHaveText('Tree · 081 / 192');
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'tree');
});
