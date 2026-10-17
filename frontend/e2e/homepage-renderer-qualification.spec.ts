import { expect, test, type Page } from '@playwright/test';
import { difference, pixels } from '../experiments/scroll-film-navigator/tests/pixels';
async function visit(page: Page) {
    await page.goto('/', { waitUntil: 'networkidle' });
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
    await expect(page.locator('[data-home-loader]')).toBeHidden();
}
async function position(page: Page, scene: string, p: number) { await page.locator(`[data-scene="${scene}"]`).evaluate((node, p) => {
    const entry = Number.parseFloat((node as HTMLElement).style.getPropertyValue('--gallery-entry-distance')) || 0;
    const rect = node.getBoundingClientRect();
    scrollTo(0, rect.top + scrollY + entry + (rect.height - entry) * p);
}, p); }
async function writingPosition(page: Page, scene: string, offset: number) { await page.locator(`[data-scene="${scene}"]`).evaluate((node, offset) => scrollTo(0, Math.ceil(node.getBoundingClientRect().top + scrollY + offset)), offset); }
async function selectFilm(page: Page) { await page.locator('[data-home-v2]').evaluate(node => node.focus({ preventScroll: true })); await page.keyboard.press('f'); }
async function presented(page: Page) { await expect(page.locator('[data-film-status]')).toHaveText(/(?:Tree|Robotic arm) · \d{3} \/ 192/); }
test('desktop tree panes retain their frame and dividers through navigator rendering and scroll reversal', async ({ page }, info) => {
    test.skip(info.project.name !== 'desktop-1440', 'Six-pane desktop tree composition');
    await page.addInitScript(() => history.replaceState({ ...history.state, homepageV2: { film: 'tree' } }, ''));
    await page.clock.install();
    await visit(page);
    await page.clock.pauseAt(await page.evaluate(() => Date.now() + 50));
    const sheet = page.locator('.home-cinema__image');
    const initial = await sheet.evaluate(node => node.style.maskImage);
    const svg = decodeURIComponent(/data:image\/svg\+xml,([^"\)]+)/.exec(initial)![1]!);
    // Six identical arches, arranged in three columns and two rows.
    expect(svg).toContain('fill-rule="evenodd"');
    const paths = [...svg.matchAll(/M([\d.]+) ([\d.]+)H([\d.]+)(A|L)/g)];
    expect(paths).toHaveLength(6);
    expect(paths.map(path => path[4])).toEqual(Array(6).fill('A'));
    expect(new Set(paths.map(path => path[1])).size).toBe(3);
    expect(new Set(paths.map(path => path[2])).size).toBe(2);
    const panes = [...svg.matchAll(/M[\d.]+ ([\d.]+)H[\d.]+A([\d.]+) [\d.]+ 0 0 1 [\d.]+ [\d.]+V([\d.]+)/g)];
    expect(panes).toHaveLength(6);
    const heights = panes.map(pane => Number(pane[3]) - Number(pane[1]));
    for (const height of heights) expect(height).toBeCloseTo(heights[0]!);
    expect(new Set(panes.map(pane => pane[2])).size).toBe(1);
    const nav = (await page.locator('.home-nav').boundingBox())!;
    const prompt = (await page.locator('.hero-scroll').boundingBox())!;
    expect(Math.min(...panes.map(pane => Number(pane[1]))) - nav.y - nav.height).toBeCloseTo(16);
    expect(prompt.y - Math.max(...panes.map(pane => Number(pane[3])))).toBeCloseTo(16);
    const clip = { x: 922, y: 410, width: 6, height: 30 };
    const video = page.locator('video.film-fallback:visible'), bounds = await video.boundingBox();
    await page.screenshot({ path: info.outputPath('tree-six-panes.png') });
    const clips = [clip, { x: 838, y: 60, width: 6, height: 6 },
        { x: 1172, y: 60, width: 6, height: 6 }, { x: 700, y: 220, width: 6, height: 6 },
        { x: 700, y: 445, width: 6, height: 6 }, { x: 720, y: 700, width: 6, height: 6 }];
    const native = [];
    for (const sample of clips) native.push(await page.screenshot({ clip: sample }));
    await page.mouse.move(120, 160);
    await page.mouse.move(320, 170, { steps: 6 });
    await page.clock.runFor(32);
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-gpu-sheet', 'true');
    for (let i = 0; i < clips.length; i++)
        expect(difference(native[i]!, await page.screenshot({ clip: clips[i]! })).mean).toBeLessThan(2);
    await page.clock.fastForward(5000);
    await position(page, 'hero', .3);
    await page.evaluate(() => dispatchEvent(new Event('scroll')));
    await page.clock.runFor(64);
    await expect(sheet).not.toHaveCSS('mask-image', initial);
    expect(await video.boundingBox()).toEqual(bounds);
    await page.screenshot({ path: info.outputPath('tree-panes-expanding.png') });
    // Cross the completion boundary despite native scroll rounding.
    await position(page, 'hero', .43);
    await page.evaluate(() => dispatchEvent(new Event('scroll')));
    await page.clock.runFor(64);
    await expect(sheet).toHaveCSS('mask-image', 'none');
    expect(await video.boundingBox()).toEqual(bounds);
    await position(page, 'hero', 0);
    await page.evaluate(() => dispatchEvent(new Event('scroll')));
    await page.clock.runFor(64);
    await expect(sheet).toHaveCSS('mask-image', initial);
});
test('mobile tree has equal centered windows above and below the writing in native and navigator presentation', async ({ page }, info) => {
    test.skip(info.project.name !== 'mobile-390', 'Paired portrait phone composition');
    await page.addInitScript(() => history.replaceState({ ...history.state, homepageV2: { film: 'tree' } }, ''));
    await page.clock.install();
    await visit(page);
    await expect(page.locator('[data-home-loader]')).toBeHidden();
    await page.clock.pauseAt(await page.evaluate(() => Date.now() + 50));
    const sheet = page.locator('.home-cinema__image');
    const initial = await sheet.evaluate(node => node.style.maskImage);
    const holes = [...initial.matchAll(/data:image\/svg\+xml,([^"\)]+)/g)].map(match => {
        const svg = decodeURIComponent(match[1]!);
        const path = /d="([^"]+)"/.exec(svg)![1]!;
        const points = /Z M([\d.]+) ([\d.]+)H[\d.]+A([\d.]+) [\d.]+ 0 0 1 ([\d.]+) [\d.]+V([\d.]+)/.exec(path)!;
        return { center: Number(points[1]), top: Number(points[2]), width: Number(points[3]) * 2, right: Number(points[4]), bottom: Number(points[5]) };
    });
    expect(holes).toHaveLength(2);
    const [lower, upper] = holes;
    for (const hole of holes) expect(hole.center).toBeCloseTo(195);
    expect(lower!.width).toBeCloseTo(upper!.width);
    expect(lower!.bottom - lower!.top).toBeCloseTo(upper!.bottom - upper!.top);
    expect(lower!.bottom - lower!.top).toBeGreaterThan(250);
    await expect(page.getByText('Independent thinking / deliberate engineering', { exact: true })).toHaveCount(0);
    const nav = (await page.locator('.home-nav').boundingBox())!;
    expect(upper!.top - nav.y - nav.height).toBeGreaterThanOrEqual(16);
    const writing = (await page.locator('.hero-statement').boundingBox())!;
    expect(lower!.top - writing.y - writing.height).toBeCloseTo(16);
    expect(writing.y - upper!.bottom).toBeCloseTo(16);
    const video = page.locator('video.film-fallback:visible'), bounds = await video.boundingBox();
    await page.screenshot({ path: info.outputPath('tree-paired-windows.png') });
    // Use flat sky away from branch edges, whose downsampling differs by renderer.
    const clip = { x: Math.ceil(lower!.right - lower!.width + 16), y: Math.floor(lower!.bottom - 30), width: 12, height: 12 };
    const native = await page.screenshot({ clip, scale: 'css' });
    await page.mouse.move(20, 110);
    await page.mouse.move(70, 120, { steps: 4 });
    await page.clock.runFor(32);
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-gpu-sheet', 'true');
    expect(difference(native, await page.screenshot({ clip, scale: 'css' })).mean).toBeLessThan(2);
    await page.clock.fastForward(5000);
    await position(page, 'hero', .3);
    await page.evaluate(() => dispatchEvent(new Event('scroll')));
    await page.clock.runFor(64);
    await expect(sheet).not.toHaveCSS('mask-image', initial);
    expect(await video.boundingBox()).toEqual(bounds);
    await position(page, 'hero', 0);
    await page.evaluate(() => dispatchEvent(new Event('scroll')));
    await page.clock.runFor(64);
    await expect(sheet).toHaveCSS('mask-image', initial);
});
test('constellation circles expose their stars and overlapping openings stay open', async ({ page }, info) => {
    await visit(page);
    const video = page.locator('video.film-fallback').first();
    const background = page.locator('.home-cinema__image');
    const initialMask = await background.evaluate(node => getComputedStyle(node).maskImage);
    expect(initialMask.match(/radial-gradient/g)).toHaveLength(7);
    const bounds = await video.boundingBox();
    expect(bounds).not.toBeNull();
    const sample = async (sx: number, sy: number) => {
        const clip = { x: Math.round(bounds!.x + sx / 1600 * bounds!.width) - 3,
            y: Math.round(bounds!.y + sy / 900 * bounds!.height) - 3, width: 6, height: 6 };
        const expected = await video.evaluate((node, clip) => {
            const video = node as HTMLVideoElement, rect = video.getBoundingClientRect();
            const canvas = document.createElement('canvas');
            canvas.width = canvas.height = 6;
            const context = canvas.getContext('2d')!;
            context.drawImage(video, (clip.x - rect.x) / rect.width * video.videoWidth,
                (clip.y - rect.y) / rect.height * video.videoHeight,
                clip.width / rect.width * video.videoWidth, clip.height / rect.height * video.videoHeight, 0, 0, 6, 6);
            return [...context.getImageData(0, 0, 6, 6).data];
        }, clip);
        const actual = pixels(await page.screenshot({ clip, scale: 'css' }));
        let error = 0;
        for (let i = 0; i < 36; i++) for (let c = 0; c < 3; c++) error += Math.abs(actual.data[i * actual.channels + c]! - expected[i * 4 + c]!);
        expect(error / (36 * 3)).toBeLessThan(8);
    };
    // Sample the glow below the top star, clear of the tablet navigation button.
    await sample(1011, 130);
    await sample(1228, 222);
    // The intentionally overlapping lower pair must expose film, not refill with photo.
    await sample(1137.5, 594);
    await page.screenshot({ path: info.outputPath('constellation-opening.png') });
    await position(page, 'hero', .15);
    await expect(background).not.toHaveCSS('mask-image', initialMask);
    // Shared projection preserves the intentional overlap of the two close circles.
    const circles = await background.evaluate(node => [...node.style.maskImage.matchAll(/circle at ([\d.e+-]+)px ([\d.e+-]+)px, (?:transparent|rgba\([^)]*\)) ([\d.e+-]+)px/g)].map(m => [Number(m[1]), Number(m[2]), Number(m[3]) + .5]));
    expect(circles).toHaveLength(7);
    const a = circles[4]!, b = circles[5]!;
    expect(Math.hypot(a[0]! - b[0]!, a[1]! - b[1]!)).toBeLessThan(a[2]! + b[2]!);
    await expect(page.locator('[data-film-status]')).not.toHaveText('Robotic arm · 001 / 192');
    expect(await video.boundingBox()).toEqual(bounds);
    await page.screenshot({ path: info.outputPath('constellation-overlap.png') });
    await position(page, 'hero', 0);
    await expect(background).toHaveCSS('mask-image', initialMask);
    expect(await video.boundingBox()).toEqual(bounds);
});
test('navigator paints and then releases the photo outside the opening doorway', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'desktop-1440', 'Fine-pointer photo composition at the desktop review size');
    await page.clock.install();
    await visit(page);
    await expect(page.locator('video.film-fallback').first()).toBeVisible();
    await expect(page.locator('[data-film-status]')).toHaveText('Robotic arm · 001 / 192');
    await page.clock.pauseAt(await page.evaluate(() => Date.now() + 50));
    // This empty region is outside the openings and excludes all text and controls.
    const clip = { x: 120, y: 100, width: 480, height: 100 };
    const clean = await page.screenshot({ clip });
    await page.mouse.move(160, 145);
    await page.mouse.move(550, 155, { steps: 12 });
    await page.clock.runFor(32);
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-gpu-sheet', 'true');
    const painted = await page.screenshot({ clip, path: testInfo.outputPath('photo-navigator.png') });
    expect(difference(clean, painted).changed).toBeGreaterThan(500);
    await page.mouse.move(700, 250);
    await page.clock.runFor(32);
    await page.clock.fastForward(4700);
    const settled = await page.screenshot({ clip });
    expect(difference(clean, settled).changed).toBeLessThan(100);
    await expect(page.locator('[data-film-status]')).toHaveText('Robotic arm · 001 / 192');
});
test('hold opens the photo to the current frame and restores it after cancel or switch', async ({ page }, info) => {
    test.skip(info.project.name === 'tablet-834', 'Desktop and native touch exercise the same composition');
    await page.clock.install();
    await visit(page);
    await expect(page.locator('video.film-fallback').first()).toBeVisible();
    await expect(page.locator('[data-home-loader]')).toBeHidden();
    // Capture overhead must not turn an early-release gesture into a completed hold.
    await page.clock.pauseAt(await page.evaluate(() => Date.now() + 50));
    const touch = info.project.name.startsWith('mobile');
    const point = touch ? { x: 78, y: 382 } : { x: 330, y: 180 };
    // These crops stay outside both scroll films' openings, over charcoal in arm frame 001.
    // The phone sample sits below the centered writing, away from controls/countdown/boundary.
    const clip = touch ? { x: 80, y: 510, width: 15, height: 15 } : { x: 260, y: 115, width: 50, height: 30 };
    const session = touch ? await page.context().newCDPSession(page) : null;
    if (!touch) await page.mouse.move(point.x, point.y);
    const press = async () => { if (session) await session.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [point] }); else await page.mouse.down(); };
    const release = async () => { if (session) await session.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] }); else await page.mouse.up(); };
    const clean = await page.screenshot({ clip });
    const constellation = await page.locator('.home-cinema__image').evaluate(node => getComputedStyle(node).maskImage);
    expect(constellation.match(/radial-gradient/g)).toHaveLength(7);
    await press();
    await page.clock.fastForward(1300);
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-gpu-sheet', 'true');
    await expect(page.locator('[data-hold-countdown]')).toBeVisible();
    const revealed = await page.screenshot({ clip });
    await page.screenshot({ path: info.outputPath('hold-current-frame.png') });
    const sample = pixels(revealed);
    let variation = 0;
    for (let i = 0; i < sample.data.length; i += sample.channels)
        for (let c = 0; c < 3; c++) variation = Math.max(variation, Math.abs(sample.data[i + c]! - sample.data[c]!));
    expect(variation).toBeLessThan(8);
    expect(difference(clean, revealed).fraction).toBeGreaterThan(.4);
    await expect(page.locator('[data-film-status]')).toHaveText('Robotic arm · 001 / 192');
    await release();
    await page.clock.fastForward(1800);
    expect(difference(clean, await page.screenshot({ clip })).changed).toBeLessThan(5);
    await page.screenshot({ path: info.outputPath('hold-canceled.png') });
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'robotic-arm');
    await press();
    await page.clock.fastForward(3300);
    await page.screenshot({ path: info.outputPath('hold-full-current-frame.png') });
    await expect(page.locator('[data-film-status]')).toHaveText('Robotic arm · 001 / 192');
    await page.clock.fastForward(80);
    await page.clock.fastForward(180);
    await page.screenshot({ path: info.outputPath('hold-incoming-restore.png') });
    await page.clock.fastForward(220);
    await expect(page.locator('[data-film-status]')).toHaveText('Tree · 001 / 192', { timeout: 12000 });
    await release();
    await page.clock.fastForward(32);
    expect(difference(clean, await page.screenshot({ clip })).changed).toBeLessThan(5);
    await expect(page.locator('.home-cinema__image')).toHaveCSS('clip-path', 'none');
    await expect(page.locator('.home-cinema__image')).toHaveCSS('mask-image', /data:image\/svg\+xml/);
    await page.screenshot({ path: info.outputPath('hold-switched.png') });
    await session?.detach();
});
test('film scrubs from the first scroll, reverses and keeps selection progress', async ({ page }, testInfo) => {
    await visit(page);
    const status = page.locator('[data-film-status]');
    const background = page.locator('.home-cinema__image');
    await expect.poll(() => background.evaluate(node => ({ x:node.getBoundingClientRect().x,y:node.getBoundingClientRect().y,width:node.getBoundingClientRect().width,height:node.getBoundingClientRect().height }))).toEqual({x:0,y:0,width:page.viewportSize()!.width,height:page.viewportSize()!.height});
    await expect(background).toHaveCSS('mask-image', /radial-gradient/);
    await page.screenshot({ path: testInfo.outputPath('constellation-arm.png') });
    for (const p of [0, .15, .3, .42, .5, .75, .5]) {
        await position(page, 'hero', p);
        await presented(page);
        const expected = await page.evaluate(() => {
            const hero = document.querySelector('[data-scene="hero"]')!.getBoundingClientRect();
            const mark = document.querySelector('[data-scene="mark"]')!.getBoundingClientRect();
            const start = hero.top + scrollY, end = mark.top + scrollY + mark.height * .98;
            return Math.round(Math.max(0, Math.min(1, (scrollY - start) / (end - start))) * 191);
        });
        await expect(status).toHaveText(`Robotic arm · ${String(expected + 1).padStart(3, '0')} / 192`);
    }
    const before = await status.textContent();
    await page.waitForTimeout(500);
    await expect(status).toHaveText(before!);
    await selectFilm(page);
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'tree');
    await presented(page);
    await expect(status).toHaveText(before!.replace('Robotic arm', 'Tree'));
    await position(page, 'hero', .3);
    await presented(page);
    await expect(status).not.toHaveText('Tree · 001 / 192');
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'tree');
    await position(page, 'hero', 0);
    const masked = testInfo.project.name !== 'tablet-834';
    await expect(background).toHaveCSS('clip-path', masked ? 'none' : /path\(/);
    await expect(background).toHaveCSS('mask-image', masked ? /data:image\/svg\+xml/ : 'none');
    await page.waitForTimeout(500);
    await page.screenshot({ path: testInfo.outputPath('tree-door.png') });
});
test('each wheel notch advances four frames and the film finishes immediately before the gallery cover', async ({ page }, info) => {
    test.skip(info.project.name === 'tablet-834', 'Desktop and portrait playback geometry');
    // Chromium mobile emulation divides synthetic wheel deltas by device scale.
    const wheelScale = info.project.use.isMobile ? await page.evaluate(() => devicePixelRatio) : 1;
    const wheel = (delta: number) => page.mouse.wheel(0, delta * wheelScale);
    await visit(page);
    await expect(page.locator('[data-home-loader]')).toBeHidden();
    const range = await page.evaluate(() => {
        const hero = document.querySelector('[data-scene="hero"]')!.getBoundingClientRect();
        const mark = document.querySelector('[data-scene="mark"]')!.getBoundingClientRect();
        const start = hero.top + scrollY, end = mark.top + scrollY + mark.height * .98;
        return { start, end, step: (end - start) / 191 };
    });
    expect(range.step).toBeCloseTo(30, 1);
    await page.evaluate(({ start, step }) => scrollTo(0, start + 20 * step), range);
    const status = page.locator('[data-film-status]');
    await expect(status).toHaveText('Robotic arm · 021 / 192');
    let frame = 21;
    for (const notch of [120, 100, 240, -120, -240]) {
        await wheel(notch);
        frame += 4 * Math.sign(notch);
        await expect(status).toHaveText(`Robotic arm · ${String(frame).padStart(3, '0')} / 192`);
    }
    await position(page, 'mark', .5);
    await presented(page);
    expect(await status.textContent()).not.toContain('192 / 192');
    await position(page, 'mark', .979);
    await expect(status).toHaveText('Robotic arm · 192 / 192');
    await expect(page.locator('[data-scene-wipe]')).toBeHidden();
    await page.evaluate(end => scrollTo(0, end), range.end);
    await expect(status).toHaveText('Robotic arm · 192 / 192');
    await expect(page.locator('[data-scene-wipe]')).toBeHidden();
    await page.screenshot({ path: info.outputPath('last-frame-before-gallery.png') });
    await wheel(-120);
    await expect(status).toHaveText('Robotic arm · 188 / 192');
    await wheel(120);
    await expect(status).toHaveText('Robotic arm · 192 / 192');
    await wheel(120);
    await expect(page.locator('[data-scene-wipe]')).toBeVisible();
    await expect(status).toHaveText('Robotic arm · 192 / 192');
});
test('film wheel glides through intermediate positions and yields to native input', async ({ page }, info) => {
    const wheelScale = info.project.use.isMobile ? await page.evaluate(() => devicePixelRatio) : 1;
    await visit(page);
    await expect(page.locator('[data-home-loader]')).toBeHidden();
    const start = await page.locator('[data-scene="hero"]').evaluate(node => {
        const rect = node.getBoundingClientRect();
        return rect.top + scrollY + 20 * 30;
    });
    await page.evaluate(y => scrollTo(0, y), start);
    await expect(page.locator('[data-film-status]')).toHaveText('Robotic arm · 021 / 192');
    const origin = await page.evaluate(() => scrollY);
    const recording = page.evaluate(() => new Promise<{ samples: Array<{ y: number; frame: string | null }>; presented: number[]; native: boolean }>(resolve => {
        const samples: Array<{ y: number; frame: string | null }> = [];
        const video = document.querySelector<HTMLVideoElement>('video.film-fallback:not([hidden])');
        const presented: number[] = [];
        let callback = 0;
        const onFrame: VideoFrameRequestCallback = (_now, metadata) => {
            presented.push(1 + Math.round(metadata.mediaTime * 30));
            callback = video!.requestVideoFrameCallback(onFrame);
        };
        if (video) callback = video.requestVideoFrameCallback(onFrame);
        const started = performance.now();
        const sample = () => {
            samples.push({ y: scrollY, frame: document.querySelector('[data-film-status]')!.textContent });
            if (performance.now() - started < 450) requestAnimationFrame(sample);
            else { if (video) video.cancelVideoFrameCallback(callback); resolve({ samples, presented, native: !!video }); }
        };
        requestAnimationFrame(sample);
    }));
    await page.mouse.wheel(0, 120 * wheelScale);
    const { samples, presented, native } = await recording;
    const positions = samples.map(sample => sample.y);
    expect(new Set(positions).size).toBeGreaterThan(3);
    expect(positions.every((value, index) => index === 0 || value >= positions[index - 1]!)).toBe(true);
    expect(positions.at(-1)).toBeCloseTo(origin + 120, 0);
    expect(native ? presented.some(frame => frame >= 22 && frame <= 24) : samples.some(sample => /02[234] \/ 192/.test(sample.frame ?? ''))).toBe(true);
    await expect(page.locator('[data-film-status]')).toHaveText('Robotic arm · 025 / 192');
    await page.mouse.wheel(0, 120 * wheelScale);
    await page.evaluate(() => scrollTo(0, 300));
    await page.waitForTimeout(250);
    expect(await page.evaluate(() => scrollY)).toBe(300);
    await page.evaluate(y => scrollTo(0, y), start);
    await page.mouse.wheel(0, 120 * wheelScale);
    await page.keyboard.press('Home');
    await expect.poll(() => page.evaluate(() => scrollY)).toBe(0);
    await page.waitForTimeout(250);
    expect(await page.evaluate(() => scrollY)).toBe(0);
});
test('gallery wheel pulses select one adjacent item and release both boundaries', async ({ page }, info) => {
    // Chromium mobile emulation divides synthetic wheel deltas by device scale.
    const wheelScale = info.project.use.isMobile ? await page.evaluate(() => devicePixelRatio) : 1;
    const wheel = (delta: number) => page.mouse.wheel(0, delta * wheelScale);
    await visit(page);
    await expect(page.locator('[data-home-loader]')).toBeHidden();
    await position(page, 'gallery', .4);
    const counter = page.locator('[data-gallery-position]');
    await expect(counter).toHaveText('1 / 3');
    const galleryHeight = await page.locator('[data-scene="gallery"]').evaluate(node => node.getBoundingClientRect().height - (Number.parseFloat((node as HTMLElement).style.getPropertyValue('--gallery-entry-distance')) || 0));
    expect(galleryHeight).toBeCloseTo(page.viewportSize()!.height * 4.6, 1);
    for (const [delta, label] of [[120, '2 / 3'], [240, '3 / 3'], [-100, '2 / 3'], [-120, '1 / 3']] as const) {
        await wheel(delta);
        await expect(counter).toHaveText(label);
    }
    const first = await page.evaluate(() => scrollY);
    await wheel(-120);
    await expect.poll(() => page.evaluate(() => scrollY)).toBeLessThan(first);
    await position(page, 'gallery', .54);
    await expect(counter).toHaveText('3 / 3');
    const last = await page.evaluate(() => scrollY);
    await wheel(120);
    await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(last);
    await position(page, 'gallery', .4);
    await expect(counter).toHaveText('1 / 3');
    await page.locator('input[value="articles"]').check();
    await page.mouse.move(page.viewportSize()!.width / 2, page.viewportSize()!.height / 2);
    await page.locator('#articles h3 a').first().focus();
    await wheel(120);
    await expect(counter).toHaveText('2 / 2');
    await expect(page.locator('input[value="articles"]')).toBeChecked();
    await expect.poll(() => page.locator('.gallery-card').evaluateAll(nodes => nodes.flatMap(n => n.getAnimations()).length)).toBe(0);
});
test('wheel reversal returns from the gallery through the film boundary to the top', async ({ page }, info) => {
    test.skip(info.project.name === 'tablet-834', 'Desktop and portrait native wheel boundary');
    await visit(page);
    await expect(page.locator('[data-home-loader]')).toBeHidden();
    await position(page, 'gallery', .3);
    await expect(page.locator('[data-film-status]')).toHaveText('Robotic arm · 192 / 192');
    const pulses = Math.ceil(await page.evaluate(() => scrollY) / 120) + 20;
    for (let i = 0; i < pulses; i++) await page.mouse.wheel(0, -120);
    await expect.poll(() => page.evaluate(() => scrollY)).toBe(0);
    await expect(page.locator('[data-film-status]')).toHaveText('Robotic arm · 001 / 192');
    await expect(page.locator('.hero-writing-text').first()).toHaveCSS('opacity', '1');
    await expect(page.locator('[data-scene-wipe]')).toBeHidden();
    // Also exercise a single-pixel position immediately above the start.
    const boundary = await page.locator('[data-scene="hero"]').evaluate(node => node.getBoundingClientRect().top + scrollY + 1);
    await page.evaluate(y => scrollTo(0, Math.ceil(y)), boundary);
    await page.mouse.wheel(0, -120);
    await expect.poll(() => page.evaluate(() => scrollY)).toBeLessThan(boundary);
});
test('second statement resolves from word blobs and exits through individual slits', async ({ page }, info) => {
    await visit(page);
    await expect(page.locator('[data-home-loader]')).toBeHidden();
    await expect(page.locator('.statement-word-text')).toHaveText(['Curious', 'by', 'nature', 'Engineering', 'by', 'choice']);
    const reading = () => page.locator('.statement-word').evaluateAll(nodes => nodes.map(node => {
        const filter=(node as HTMLElement).style.filter;
        const id=filter.match(/#([\w-]+)/)?.[1];
        return {
            filter,
            opacity:(node as HTMLElement).style.opacity,
            blur:id?Number(document.getElementById(id)?.querySelector('feGaussianBlur')?.getAttribute('stdDeviation')):0,
            transform:(node.querySelector('.statement-word-text') as HTMLElement).style.transform,
            hidden:(node.querySelector('.statement-word-text') as HTMLElement).style.opacity
        };
    }));
    await writingPosition(page, 'statements', -1);
    await expect.poll(async () => (await reading()).every(word => word.filter.startsWith('url(') && word.opacity === '0.3' && word.blur > 0)).toBe(true);
    await writingPosition(page, 'statements', 0);
    await expect(page.locator('.statement-face')).toBeVisible();
    await writingPosition(page, 'statements', 420 * .18);
    await expect.poll(async () => { const words=await reading();return words[0]!.blur<words[5]!.blur; }).toBe(true);
    const first = await reading();
    await page.screenshot({path:info.outputPath('second-writing-entry.png')});
    await page.waitForTimeout(250);
    expect(await reading()).toEqual(first);
    await writingPosition(page, 'statements', 421);
    await expect.poll(async () => (await reading()).every(word => word.filter === '' && word.opacity === '' && word.transform === '')).toBe(true);
    await writingPosition(page, 'statements', 420 * .18);
    await expect.poll(reading).toEqual(first);
    await writingPosition(page, 'statements', 1140 + 420 * .64);
    await expect.poll(async () => { const words=await reading();return words[0]!.hidden==='0'&&words[5]!.hidden!== '0'; }).toBe(true);
    const exit = await reading();
    await expect(page.locator('.statement-word-slot').first()).toHaveCSS('overflow','hidden');
    await expect(page.locator('.statement-copy')).toHaveCSS('transform','none');
    await page.screenshot({path:info.outputPath('second-writing-exit.png')});
    await page.waitForTimeout(250);
    expect(await reading()).toEqual(exit);
    await writingPosition(page, 'statements', 1561);
    await expect(page.locator('.statement-bottom')).toHaveCount(0);
    await expect.poll(async () => (await reading()).every(word => word.hidden === '0' && word.transform === 'translateY(-100%)')).toBe(true);
    await writingPosition(page, 'statements', 1140 + 420 * .64);
    await expect.poll(reading).toEqual(exit);
    await page.emulateMedia({reducedMotion:'reduce'});
    await expect.poll(async () => (await reading()).every(word => word.filter===''&&word.opacity===''&&word.hidden===''&&word.transform==='')).toBe(true);
});
test('first writing enters and exits through per-row slits from left to right', async ({ page }, info) => {
    await visit(page);
    await expect(page.locator('[data-home-loader]')).toBeHidden();
    await expect(page.locator('.hero-writing-text')).toHaveText(['Clarity is', 'engineered']);
    await expect(page.locator('.hero-writing-text').first()).toHaveCSS('text-transform', 'none');
    await expect(page.locator('.hero-writing-text').last()).toHaveCSS('text-transform', 'uppercase');
    for (const selector of ['.hero-writing-text', '.hero-writing-visual']) {
        await expect(page.locator(selector).last()).toHaveCSS('font-weight', '650');
        await expect(page.locator(selector).last()).toHaveCSS('font-style', 'normal');
        await expect(page.locator(selector).last()).toHaveCSS('font-variation-settings', '"wdth" 85, "wght" 650');
    }
    await expect(page.locator('.hero-writing-visual').last()).toHaveCSS('text-transform', 'uppercase');
    const reading = () => page.locator('.hero-writing-row').evaluateAll(nodes => nodes.map(node => ({
        opacity: (node.querySelector('.hero-writing-text') as HTMLElement).style.opacity,
        hidden: (node.querySelector('.hero-writing-visual') as HTMLElement).hidden,
        transforms: [...node.querySelectorAll<HTMLElement>('.hero-writing-visual>span')].map(glyph => glyph.style.transform)
    })));
    await position(page, 'hero', 0);
    await expect.poll(async () => (await reading()).every(row => row.hidden && row.opacity === '')).toBe(true);
    await page.screenshot({ path: info.outputPath('writing-open.png') });
    await writingPosition(page, 'hero', 420 * .35);
    await expect.poll(async () => (await reading()).every(row => !row.hidden)).toBe(true);
    const partial = await reading();
    const vertical = (value: string) => Number(value.match(/,\s*(-?[\d.]+)px/)![1]);
    expect(vertical(partial[0]!.transforms[0]!)).toBeLessThan(vertical(partial[0]!.transforms.at(-1)!));
    expect(vertical(partial[0]!.transforms[0]!)).toBeLessThan(vertical(partial[1]!.transforms[0]!));
    await page.screenshot({ path: info.outputPath('writing-pulled.png') });
    expect(await reading()).toEqual(partial);
    await writingPosition(page, 'hero', 420 * .8);
    await page.screenshot({ path: info.outputPath('writing-closing.png') });
    await writingPosition(page, 'hero', 420 * .35);
    await expect.poll(reading).toEqual(partial);
    await writingPosition(page, 'hero', 421);
    await expect.poll(async () => (await reading()).every(row => row.hidden && row.opacity === '0')).toBe(true);
    await position(page, 'hero', 0);
    await expect.poll(async () => (await reading()).every(row => row.hidden && row.opacity === '')).toBe(true);
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await position(page, 'hero', .25);
    await expect.poll(async () => (await reading()).every(row => row.hidden && row.opacity === '')).toBe(true);
});
test('all canonical scene states reverse without one-shot progression', async ({ page }, testInfo) => {
    await visit(page);
    for (const name of ['statements', 'capabilities', 'mark', 'gallery']) {
        await position(page, name, .35);
        await page.waitForTimeout(150);
        const face = page.locator(`[data-scene="${name}"] .scene-face`);
        const first = await face.getAttribute('style');
        await position(page, name, .7);
        await position(page, name, .35);
        await page.waitForTimeout(150);
        expect(await face.getAttribute('style')).toBe(first);
        await page.screenshot({ path: testInfo.outputPath(`${name}-reversible.png`) });
    }
});
test('gallery departure removes hidden keyboard targets and transfers focus visibly', async ({ page }) => {
    await visit(page);
    await position(page, 'gallery', .3);
    await expect.poll(() => page.locator('.gallery-face').evaluate(node => node.inert)).toBe(false);
    await page.locator('input[value="projects"]').focus();
    await expect(page.locator('input[value="projects"]')).toBeFocused();
    const wordmarkClip = (await page.locator('.home-sitename').boundingBox())!;
    const wordmark = await page.screenshot({ clip:wordmarkClip });
    await position(page, 'gallery', .945);
    await expect.poll(() => page.locator('.gallery-face').evaluate(node => node.inert)).toBe(true);
    expect(difference(wordmark, await page.screenshot({ clip:wordmarkClip })).changed).toBeLessThan(30);
    const departure = () => page.locator('.gallery-space').evaluate(node => {
        const style = getComputedStyle(node), matrix = new DOMMatrixReadOnly(style.transform);
        return { x:matrix.m41, scaleX:matrix.m11, scaleY:matrix.m22, opacity:style.opacity, clip:style.clipPath };
    });
    const outgoing = await departure();
    expect(outgoing.x).toBe(0);
    await expect(page.locator('[data-scene="gallery"]')).toHaveAttribute('data-gallery-empty', 'true');
    expect(outgoing.scaleX).toBe(1);
    expect(outgoing.scaleY).toBe(1);
    expect(outgoing.opacity).toBe('1');
    await expect(page.locator('.location-links nav .footer-rise').first()).toHaveCSS('opacity', '0');
    await position(page, 'location', .075);
    await expect.poll(async () => {
        const rises = await page.locator('.location-links nav .footer-rise').evaluateAll(nodes => nodes.map(node => new DOMMatrixReadOnly(getComputedStyle(node).transform).m42));
        return rises[0]! < rises[1]! && rises[1]! < rises[2]!;
    }).toBe(true);
    await expect(page.locator('.location-system')).toHaveCSS('opacity', '0');
    await position(page, 'location', .55);
    await expect(page.locator('.location-system')).toHaveCSS('opacity', '1');
    await expect(page.locator('.location-links nav a').first()).toHaveJSProperty('inert', false);
    await position(page, 'gallery', .945);
    await expect.poll(departure).toEqual(outgoing);
    const menuToggle = page.locator('[data-menu-toggle]');
    await expect(await menuToggle.isVisible() ? menuToggle : page.locator('.home-nav nav a').first()).toBeFocused();
    await position(page, 'gallery', .3);
    await expect.poll(() => page.locator('.gallery-face').evaluate(node => node.inert)).toBe(false);
    await page.locator('input[value="articles"]').check();
});
test('reduced motion uses readable static sections on initial and live preference changes', async ({ page }) => {
    await visit(page);
    await position(page, 'gallery', .945);
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-reduced', 'true');
    for (const scene of ['hero','statements','capabilities','mark','gallery','location']) {
        const face = page.locator(`[data-scene="${scene}"] .scene-face`);
        await expect.poll(() => face.evaluate(node => ({ position:getComputedStyle(node).position,clip:getComputedStyle(node).clipPath,inert:node.inert }))).toEqual({ position:'relative',clip:'none',inert:false });
    }
    await page.locator('input[value="articles"]').check();
    for (const link of await page.locator('#writing h3 a').all()) await expect(link).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(await page.evaluate(() => innerWidth + 1));
    await page.reload({waitUntil:'networkidle'});
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready','true');
    await expect.poll(() => page.locator('.statement-face').evaluate(node => getComputedStyle(node).position)).toBe('relative');
    await page.emulateMedia({ reducedMotion: 'no-preference' });
    await position(page, 'gallery', .3);
    await expect.poll(() => page.locator('.gallery-face').evaluate(node => getComputedStyle(node).position)).toBe('fixed');
    await expect.poll(() => page.locator('.gallery-face').evaluate(node => node.inert)).toBe(false);
});
test('gallery cover hides the live navigator trail and a quick reversal reveals it until it fades', async ({ page }, info) => {
    await page.clock.install();
    await visit(page);
    const canvas = page.locator('[data-film-canvas]');
    const coveredByGallery = () => canvas.evaluate(node => {
        const environment = node.closest('.home-environment');
        const cover = document.querySelector('[data-scene-wipe]')!;
        return !!environment && Number(getComputedStyle(environment).zIndex) < Number(getComputedStyle(cover).zIndex);
    });
    // The final film pose is the same on both sides of the cover. This lets
    // pixel differences identify the trail, rather than a changed film frame.
    await position(page, 'mark', .979);
    await expect(page.locator('[data-film-status]')).toHaveText('Robotic arm · 192 / 192');
    await page.clock.pauseAt(await page.evaluate(() => Date.now() + 50));
    await expect(page.locator('[data-scene-wipe]')).toBeHidden();
    // Empty left margin: gallery tilt, labels and the pointer cannot affect it.
    const clip = { x: 0, y: 400, width: 16, height: 120 };
    const clean = await page.screenshot({ clip, scale: 'css' });
    await position(page, 'gallery', .25);
    await page.evaluate(() => dispatchEvent(new Event('scroll')));
    await page.clock.runFor(64);
    const covered = await page.screenshot({ clip, scale: 'css' });
    await page.mouse.move(30, 300);
    await page.mouse.move(30, 600, { steps: 6 });
    await page.mouse.move(100, 630);
    await page.clock.runFor(32);
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-gpu-sheet', 'true');
    await expect(canvas).toHaveCSS('opacity', '1');
    expect(await coveredByGallery()).toBe(true);
    expect(difference(covered, await page.screenshot({ clip, scale: 'css' })).changed).toBe(0);
    await page.screenshot({ path: info.outputPath('navigator-hidden-under-gallery.png') });
    await position(page, 'mark', .979);
    await page.evaluate(() => dispatchEvent(new Event('scroll')));
    await page.clock.runFor(64);
    await expect(page.locator('[data-scene-wipe]')).toBeHidden();
    expect(difference(clean, await page.screenshot({ clip, scale: 'css' })).changed).toBeGreaterThan(100);
    await page.screenshot({ path: info.outputPath('navigator-revealed-on-reversal.png') });
    await page.clock.fastForward(5000);
    await expect(canvas).toHaveCSS('opacity', '0');
    expect(difference(clean, await page.screenshot({ clip, scale: 'css' })).mean).toBeLessThan(.1);
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.clock.runFor(64);
    const quiet = await page.screenshot({ clip, scale: 'css' });
    await page.mouse.move(30, 300);
    await page.mouse.move(30, 600, { steps: 6 });
    await page.clock.runFor(64);
    expect(difference(quiet, await page.screenshot({ clip, scale: 'css' })).changed).toBe(0);
});
test('failed incoming frames retain the outgoing film and recover through explicit retry', async ({ page }) => {
    let fail = true;
    await page.route('**/tree/frame_*.webp*', route => fail ? route.fulfill({ status: 503, body: 'temporarily unavailable' }) : route.continue());
    await page.route('**/tree.mp4*', route => fail ? route.fulfill({ status: 503, body: 'temporarily unavailable' }) : route.continue());
    await visit(page);
    await position(page, 'hero', .3);
    await selectFilm(page);
    await expect(page.locator('[data-film-status]')).toContainText('Film frame unavailable');
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'robotic-arm');
    fail = false;
    await selectFilm(page);
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'tree', { timeout: 10000 });
    await expect(page.locator('[data-film-status]')).toHaveText(/Tree · \d{3} \/ 192/);
    await expect(page.locator('[data-film-status]')).not.toContainText('unavailable');
});
test('compact identity activation reaches the authored M and resets accessibly', async ({ page }, info) => {
    await visit(page);
    const button = page.getByRole('button', { name: 'Inspect 0xmillennium identity' });
    if (info.project.name.startsWith('mobile')) await button.tap();
    else { await button.focus(); await page.keyboard.press('Enter'); }
    const mark = page.locator('.home-brand .m-mark');
    await expect.poll(() => mark.evaluate(node => Number(getComputedStyle(node).opacity)), { timeout: 12000 }).toBe(1);
    await page.keyboard.press('Escape');
    await expect.poll(() => mark.evaluate(node => Number(getComputedStyle(node).opacity))).toBe(0);
});
test('environment hold cancels, resumes and switches once; touch scrolling stays native', async ({ page }, info) => {
    await visit(page);
    await position(page, 'hero', .3);
    const viewport = page.viewportSize()!;
    const point = { x: viewport.width * .8, y: viewport.height * .72 };
    const film = page.locator('[data-home-v2]');
    const countdown = page.locator('[data-hold-countdown]');
    if (info.project.name.startsWith('mobile')) {
        const session = await page.context().newCDPSession(page);
        await session.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [point] });
        await expect(countdown).toBeVisible();
        const before = await page.evaluate(() => scrollY);
        for (let i = 1; i <= 8; i++) await session.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: point.x, y: point.y - i * 15 }] });
        await session.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
        await expect(countdown).toBeHidden();
        await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(before);
        await expect(film).toHaveAttribute('data-film', 'robotic-arm');
        await session.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [point] });
        await expect(film).toHaveAttribute('data-film', 'tree', { timeout: 10000 });
        await page.waitForTimeout(700);
        await expect(film).toHaveAttribute('data-film', 'tree');
        await session.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
        await session.detach();
    } else {
        await page.mouse.move(point.x, point.y);
        await page.mouse.down();
        await expect(countdown).toBeVisible();
        await page.waitForTimeout(350);
        await page.mouse.up();
        await expect(countdown).toBeHidden();
        await expect(film).toHaveAttribute('data-film', 'robotic-arm');
        await page.mouse.down();
        await expect(film).toHaveAttribute('data-film', 'tree', { timeout: 10000 });
        await page.waitForTimeout(700);
        await expect(film).toHaveAttribute('data-film', 'tree');
        await page.mouse.up();
    }
});
test('logo entrance retraces each piece when scrolling backward and stops with the scroll', async ({ page }, info) => {
    await visit(page);
    const slots = page.locator('[data-identity-slot]');
    const state = () => slots.evaluateAll(nodes => nodes.map(slot => ({
        y: new DOMMatrixReadOnly(getComputedStyle(slot.firstElementChild!).transform).m42,
        clipped: slot.hasAttribute('clip-path')
    })));
    const at = async (p: number) => {
        await page.locator('[data-scene="mark"]').evaluate((node, p) => {
            const gap = Number.parseFloat(document.querySelector<HTMLElement>('[data-home-v2]')!.style.getPropertyValue('--writing-gap'));
            const y = node.getBoundingClientRect().top + scrollY - gap + gap * p;
            scrollTo(0, p === 0 ? Math.floor(y) : Math.ceil(y));
        }, p);
        await page.waitForTimeout(50);
    };
    await at(0);
    const closed = await state();
    expect(closed.every(piece => piece.clipped && piece.y > 0)).toBe(true);
    await at(.45);
    await expect.poll(async () => (await state()).some((piece, i) => piece.y > 0 && piece.y < closed[i]!.y)).toBe(true);
    const partial = await state();
    const relative = partial.map((piece, i) => piece.y / closed[i]!.y);
    expect(Math.max(...relative) - Math.min(...relative)).toBeGreaterThan(.1);
    await page.waitForTimeout(250);
    expect(await state()).toEqual(partial);
    expect(await page.locator('[data-physical-identity] svg').evaluate(node => node.getAnimations({ subtree: true }).some(animation => animation.playState === 'running'))).toBe(false);
    await page.screenshot({ path: info.outputPath('logo-entry-partial.png') });
    await at(1);
    await expect.poll(async () => (await state()).every(piece => piece.y === 0 && !piece.clipped)).toBe(true);
    await at(.45);
    await expect.poll(state).toEqual(partial);
    await page.screenshot({ path: info.outputPath('logo-entry-reversed.png') });
    await at(.2);
    const earlier = await state();
    expect(earlier.every((piece, i) => piece.y >= partial[i]!.y)).toBe(true);
    expect(earlier.some((piece, i) => piece.y > partial[i]!.y)).toBe(true);
    await at(.45);
    await expect.poll(state).toEqual(partial);
    await at(0);
    await expect.poll(state).toEqual(closed);
    await at(.45);
    await expect.poll(state).toEqual(partial);
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await expect.poll(async () => (await state()).every(piece => piece.y === 0 && !piece.clipped)).toBe(true);
    expect(await page.locator('[data-physical-identity] svg').evaluate(node => node.getAnimations({ subtree: true }).length)).toBe(0);
    await page.emulateMedia({ reducedMotion: 'no-preference' });
    await at(.45);
    await expect.poll(state).toEqual(partial);
});
test('physical identity responds to pointer contact and restores on scene exit', async ({ page }, info) => {
    test.skip(info.project.name.startsWith('mobile'), 'Approved Experiment 01 deliberately keeps touch contact static and native scrolling available.');
    await visit(page);
    await position(page, 'mark', .5);
    const fragments = page.locator('[data-physical-identity] polygon');
    await expect(fragments).toHaveCount(8);
    const transforms = () => fragments.evaluateAll(nodes => nodes.map(node => node.getAttribute('transform')));
    const before = await transforms();
    const box = (await fragments.nth(6).boundingBox())!;
    await page.mouse.move(box.x - 35, box.y + box.height / 2);
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps: 5 });
    await expect.poll(transforms).not.toEqual(before);
    await expect(page.locator('[data-hold-countdown]')).toBeHidden();
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await expect.poll(transforms).toEqual(before);
    await page.emulateMedia({ reducedMotion: 'no-preference' });
    await position(page, 'gallery', .3);
    await expect.poll(transforms).toEqual(before);
});
test('later writing uses upward row exits while the film stays at its original scale', async ({ page }, info) => {
    test.skip(info.project.name === 'tablet-834', 'Desktop and portrait exercise the two compositions.');
    await page.addInitScript(() => history.replaceState({ homepageV2: { film: 'tree' } }, ''));
    await visit(page);
    const third = page.locator('.capabilities li>span'), fourth = page.locator('.brand-statement>span>span');
    const y = (lines: typeof third, index = 0) => lines.nth(index).evaluate(node => new DOMMatrixReadOnly(getComputedStyle(node).transform).m42);
    await writingPosition(page, 'capabilities', 420 * .1);
    await expect.poll(() => y(third)).toBeGreaterThan(0);
    expect(await y(third, 3)).toBeGreaterThan(await y(third));
    await writingPosition(page, 'capabilities', 780);
    await expect.poll(() => y(third, 3)).toBe(0);
    await presented(page);
    const thirdFrame = await page.locator('[data-film-status]').textContent();
    expect(thirdFrame).not.toContain('192 / 192');
    const bounds = await page.locator('[data-film-canvas]').boundingBox();
    const clip = { x: page.viewportSize()!.width - 80, y: 130, width: 60, height: 60 };
    const filmBefore = await page.screenshot({ clip });
    await writingPosition(page, 'capabilities', 1140 + 420 * .6);
    await expect.poll(() => y(third)).toBeLessThan(0);
    await writingPosition(page, 'mark', 420 * .2);
    await expect.poll(() => y(fourth)).toBeGreaterThan(0);
    await position(page, 'mark', .5);
    await expect.poll(() => y(fourth, 1)).toBe(0);
    await expect.poll(() => page.locator('[data-identity-slot]').evaluateAll(nodes => nodes.filter(node => node.hasAttribute('clip-path')).length)).toBe(0);
    expect(await page.locator('[data-film-canvas]').boundingBox()).toEqual(bounds);
    expect(difference(filmBefore, await page.screenshot({ clip })).changed).toBeGreaterThan(0);
    expect(await page.locator('[data-film-status]').textContent()).not.toBe(thirdFrame);
    await page.screenshot({ path: info.outputPath('fourth-writing.png') });
    await writingPosition(page, 'mark', 1140 + 420 * .8);
    await expect.poll(() => y(fourth)).toBeLessThan(0);
    await expect.poll(() => page.locator('[data-identity-slot] > g').first().evaluate(node => new DOMMatrixReadOnly(getComputedStyle(node).transform).m42)).toBeGreaterThan(0);
    await page.screenshot({ path: info.outputPath('fourth-writing-exit.png') });
    await position(page, 'mark', .5);
    await expect.poll(() => y(fourth)).toBe(0);
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await expect.poll(() => y(third)).toBe(0);
    await expect.poll(() => fourth.first().evaluate(node => getComputedStyle(node).opacity)).toBe('1');
    await expect(page.locator('[data-scene-wipe]')).toBeHidden();
    await expect.poll(() => page.locator('[data-identity-slot]').evaluateAll(nodes => nodes.filter(node => node.hasAttribute('clip-path')).length)).toBe(0);
});
test('gallery arrives on staggered dark columns above the retained film', async ({ page }, info) => {
    test.skip(info.project.name === 'tablet-834', 'Desktop and portrait exercise twelve and six columns.');
    await visit(page);
    await position(page, 'mark', .5);
    await expect(page.locator('[data-scene-wipe]')).toBeHidden();
    const coverStart = await page.locator('[data-scene="mark"]').evaluate(node => {
        const rect = node.getBoundingClientRect();
        return Math.ceil(rect.top + scrollY + rect.height * .98);
    });
    await page.evaluate(start => scrollTo(0, start + innerHeight * .1), coverStart);
    const bars = page.locator('[data-scene-wipe] i:visible');
    await expect(bars).toHaveCount(info.project.name.startsWith('mobile') ? 6 : 12);
    const translations = () => bars.evaluateAll(nodes => nodes.map(node => new DOMMatrixReadOnly(getComputedStyle(node).transform).m42));
    await expect.poll(async () => (await translations())[0]).toBeLessThan(page.viewportSize()!.height);
    const steps = await translations();
    expect(steps[0]).toBeLessThan(steps.at(-1)!);
    expect(await page.locator('[data-film-canvas]').evaluate(node => !!node.closest('.home-environment'))).toBe(true);
    await page.screenshot({ path: info.outputPath('gallery-curtain.png') });
    await page.evaluate(start => scrollTo(0, start + innerHeight * .3), coverStart);
    await expect.poll(async () => (await translations())[0]).toBe(0);
    expect((await translations()).at(-1)).toBeGreaterThan(0);
    await expect(page.locator('[data-gallery-position]')).toHaveText('0 / 3');
    await expect.poll(() => page.locator('.gallery-face').evaluate(node => Number(getComputedStyle(node).opacity))).toBeCloseTo(2 / 3, 1);
    const middle = await translations();
    await page.evaluate(start => scrollTo(0, start + innerHeight * .6), coverStart);
    await expect.poll(translations).toEqual(Array(info.project.name.startsWith('mobile') ? 6 : 12).fill(0));
    await expect(page.locator('[data-gallery-position]')).toHaveText('0 / 3');
    expect(await page.locator('[data-film-canvas]').evaluate(node => !!node.closest('.home-environment'))).toBe(true);
    await page.evaluate(start => scrollTo(0, start + innerHeight * .3), coverStart);
    await expect.poll(translations).toEqual(middle);
    await position(page, 'gallery', .3);
    await expect.poll(() => translations()).toEqual(Array(info.project.name.startsWith('mobile') ? 6 : 12).fill(0));
    expect(await page.locator('[data-film-canvas]').evaluate(node => !!node.closest('.home-environment'))).toBe(true);
    await expect(page.locator('[data-film-fallback]')).toBeHidden();
    await position(page, 'mark', .5);
    expect(await page.locator('[data-film-canvas]').evaluate(node => !!node.closest('.home-environment'))).toBe(true);
    await expect(page.locator('[data-scene-wipe]')).toBeHidden();
});
test('missing critical hero media releases the loader to readable content without claiming readiness', async ({ page }) => {
    await page.route('**/assets/cloudy-turquoise-*.webp', route => route.abort());
    await page.goto('/');
    await expect(page.locator('[data-home-loader]')).toBeHidden({ timeout: 15000 });
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-media-failure', 'true');
    await expect(page.locator('[data-home-v2]')).not.toHaveAttribute('data-ready', 'true');
    await position(page, 'gallery', .2);
    await expect(page.locator('.home-project h3 a').first()).toBeVisible();
});
test('gallery category, navigation and history restore film and scene', async ({ page }) => {
    await visit(page);
    await selectFilm(page);
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'tree');
    await position(page, 'gallery', .5);
    await page.locator('input[value="articles"]').check();
    await page.waitForTimeout(350);
    const y = await page.evaluate(() => scrollY);
    await page.locator('#articles a[href="/en/articles/fixture-writing-2"]').focus();
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/\/en\/articles\/fixture-writing-2$/);
    await expect(page.locator('.navigation-cover')).toHaveCount(0);
    await page.goBack({ waitUntil: 'networkidle' });
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-ready', 'true');
    await expect(page.locator('[data-home-v2]')).toHaveAttribute('data-film', 'tree');
    await expect(page.locator('input[value="articles"]')).toBeChecked();
    await expect.poll(() => page.evaluate(() => scrollY)).toBe(y);
    await expect(page.locator('[data-home-loader]')).toBeHidden();
    await page.goForward({ waitUntil: 'networkidle' });
    await expect(page.locator('.navigation-cover')).toHaveCount(0);
});
test('ordinary navigation paints styled source and destination covers before history bypass', async ({ page }, info) => {
    await visit(page);
    await expect(page.locator('[data-home-loader]')).toBeHidden();
    await position(page, 'gallery', .3);
    await expect.poll(() => page.locator('.gallery-face').evaluate(node => node.inert)).toBe(false);
    const destination = await page.locator('#work h3 a').first().getAttribute('href');
    await page.evaluate(() => {
        const state = window as Window & { sourceRaf: typeof requestAnimationFrame; sourceCallbacks: FrameRequestCallback[] };
        state.sourceRaf = requestAnimationFrame;
        state.sourceCallbacks = [];
        window.requestAnimationFrame = callback => { state.sourceCallbacks.push(callback); return 0; };
    });
    await page.locator('#work h3 a').first().click({ noWaitAfter: true });
    const cover = page.locator('.navigation-cover');
    await expect(cover).toBeVisible();
    await expect.poll(() => cover.evaluate(node => ({ position:getComputedStyle(node).position, width:node.getBoundingClientRect().width,height:node.getBoundingClientRect().height }))).toEqual({position:'fixed', ...{ width:page.viewportSize()!.width,height:page.viewportSize()!.height }});
    await page.screenshot({path:info.outputPath('styled-source-cover.png')});
    await page.evaluate(() => {
        const state = window as Window & { sourceRaf: typeof requestAnimationFrame; sourceCallbacks: FrameRequestCallback[] };
        window.requestAnimationFrame = state.sourceRaf;
        state.sourceCallbacks.forEach(callback => requestAnimationFrame(callback));
    });
    await page.waitForURL(`**${destination}`, {waitUntil:'domcontentloaded'});
    await expect(page.locator('.navigation-cover')).toHaveCSS('position','fixed');
    await expect(page.locator('.navigation-cover')).toHaveCount(0);
    await page.goBack({waitUntil:'networkidle'});
    await expect(page.locator('[data-home-loader]')).toBeHidden();
    await expect(page.locator('.navigation-cover')).toHaveCount(0);
    await page.goForward({waitUntil:'networkidle'});
    await expect(page.locator('.navigation-cover')).toHaveCount(0);
});
test('globe retains real geography and exposes keyboard controls without triggering film hold', async ({ page }) => {
    await visit(page);
    await position(page, 'location', .6);
    await expect(page.locator('.location-system')).toHaveAttribute('data-ready', 'true');
    await page.locator('#globe').focus();
    await page.keyboard.press('Home');
    await expect(page.locator('#location-panel')).toHaveAttribute('data-open', 'true');
    await page.keyboard.press('ArrowUp');
    await page.keyboard.press('Space');
    await page.locator('#globe').focus();
    await page.keyboard.press('ArrowRight');
    await expect(page.locator('[data-hold-countdown]')).toBeHidden();
});
test('reduced motion and blocked WebGL preserve semantic content and controls', async ({ browser }, testInfo) => {
    for (const mode of ['reduced', 'webgl']) {
        const context = await browser.newContext({ viewport: testInfo.project.use.viewport, reducedMotion: mode === 'reduced' ? 'reduce' : 'no-preference' });
        const page = await context.newPage();
        if (mode === 'webgl')
            await page.addInitScript(() => { const original = HTMLCanvasElement.prototype.getContext; HTMLCanvasElement.prototype.getContext = function (type: string, ...args: unknown[]) { if (type.startsWith('webgl'))
                return null; return original.apply(this, [type, ...args] as Parameters<typeof original>); } as typeof original; });
        await visit(page);
        await position(page, 'gallery', .2);
        await expect(page.locator('.home-project h3 a').first()).toBeVisible();
        await page.locator('input[value="articles"]').check();
        await expect(page.locator('#writing')).toBeVisible();
        await page.screenshot({ path: testInfo.outputPath(`${mode}.png`) });
        await context.close();
    }
});
test('runtime evidence measures actual task, layout and frame intervals', async ({ page }, testInfo) => {
    await visit(page);
    const session = await page.context().newCDPSession(page);
    await session.send('Performance.enable');
    const before = await session.send('Performance.getMetrics');
    const intervals = await page.evaluate(async () => { const node = document.querySelector('[data-scene="hero"]')!; const height = node.getBoundingClientRect().height; const values: number[] = []; let previous = performance.now(); for (let i = 0; i < 90; i++) {
        await new Promise<void>(resolve => requestAnimationFrame(now => { values.push(now - previous); previous = now; scrollTo(0, height * (.15 + Math.sin(i / 90 * Math.PI) * .55)); resolve(); }));
    } return values; });
    await presented(page);
    const after = await session.send('Performance.getMetrics');
    const m = (r: typeof before) => Object.fromEntries(r.metrics.map(m => [m.name, m.value]));
    const start = m(before), end = m(after), sorted = intervals.slice(1).sort((a, b) => a - b);
    const evidence = { p50Ms: sorted[Math.floor(sorted.length * .5)], p95Ms: sorted[Math.floor(sorted.length * .95)], over50Ms: sorted.filter(n => n > 50).length, taskSeconds: end.TaskDuration! - start.TaskDuration!, layouts: end.LayoutCount! - start.LayoutCount!, heapBefore: start.JSHeapUsedSize, heapAfter: end.JSHeapUsedSize, limitation: 'Headless local browser; RAF cadence is not compositor FPS. JS heap excludes decoder/GPU allocations.' };
    await testInfo.attach('runtime-evidence', { body: JSON.stringify(evidence, null, 2), contentType: 'application/json' });
    expect(Number.isFinite(evidence.taskSeconds)).toBe(true);
    await session.detach();
});
