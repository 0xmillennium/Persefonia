import { FrameCache, TraversalEstimator, type FrameKey } from '../../experiments/scroll-film-navigator/src/transport';
import { Hold, type Modality } from '../../experiments/scroll-film-navigator/src/hold';
import { HoldClock } from '../../experiments/scroll-film-navigator/src/hold-clock';
import { HoldMaterial } from '../../experiments/scroll-film-navigator/src/hold-material';
import { LiquidField } from '../../experiments/scroll-film-navigator/src/field';
import { Renderer } from '../../experiments/scroll-film-navigator/src/renderer';
import { PointerObserver, eligible } from '../../experiments/scroll-film-navigator/src/input';
import { loadBitmap } from '../../experiments/scroll-film-navigator/src/loader';
import { mediaVersion } from 'virtual:home-media';
import { applyHeroOpening, filmCoverBox, heroOpening, type HeroWritingBounds } from './hero-window';
import { SeekFilm } from './seek-film';
import { BackgroundTransport, isBackground, isScrollFilm, nextBackground, type Background, type BackgroundKey } from './film-scenes';
import { LoopFilm } from './loop-film';
/** Approved transport and material; native scene coordinator is the only scroll owner. */
export function mountFilm(root: HTMLElement, ready: () => void, selectionChanged: () => void) {
    const canvas = root.querySelector<HTMLCanvasElement>('[data-film-canvas]')!;
    const environment = root.querySelector<HTMLElement>('.home-environment')!;
    const status = root.querySelector<HTMLElement>('[data-film-status]')!;
    const countdown = root.querySelector<HTMLElement>('[data-hold-countdown]')!;
    let fallback = root.querySelector<HTMLImageElement>('[data-film-fallback]')!;
    const sheetImage = root.querySelector<HTMLImageElement>('[data-hero-image]')!;
    const sheet = root.querySelector<HTMLElement>('.home-cinema__image')!;
    const abort = new AbortController(), options = { signal: abort.signal };
    const transport = new BackgroundTransport(), hold = new Hold(), clock = new HoldClock();
    const pointer = new PointerObserver(), traversal = new TraversalEstimator();
    const reduced = matchMedia('(prefers-reduced-motion: reduce)');
    const tier = innerWidth <= 700 ? 960 : 1600;
    const saved = history.state?.homepageV2?.film;
    if (isBackground(saved))
        transport.restore(saved);
    // CSS excludes a reserved scrollbar gutter and uses the stable viewport
    // height. Native media, cutouts and the shader must share that exact box.
    const viewport = environment.getBoundingClientRect();
    const writing = root.querySelector<HTMLElement>('.hero-statement')!;
    const measureWriting = (): HeroWritingBounds => {
        const environmentBounds = environment.getBoundingClientRect();
        const origin = environmentBounds.top;
        const bounds = writing.getBoundingClientRect();
        return { top: bounds.top - origin, bottom: bounds.bottom - origin,
            right: Math.max(...[...writing.querySelectorAll('.hero-writing-text')].map(node => node.getBoundingClientRect().right)) - environmentBounds.left,
            ceiling: document.querySelector('.home-nav')!.getBoundingClientRect().bottom - origin + 16,
            floor: root.querySelector('.hero-scroll')!.getBoundingClientRect().top - origin - 16 };
    };
    let writingBounds = measureWriting();
    let width = viewport.width, height = viewport.height, renderer: Renderer<BackgroundKey, Background> | null = null;
    let material = new HoldMaterial(width, height), field = new LiquidField(width, height);
    let raf = 0, previous = 0, gesture: number | null = null, disposed = false, suspended = document.hidden;
    let touchStartedAt = 0, touchIdentifier: number | null = null;
    // An immediate swipe belongs to native scrolling. Once a finger rests,
    // retain the hold through adjustments instead of treating them as a swipe.
    const touchSettled = (time: number) => hold.modality === 'touch' && time - touchStartedAt >= 200;
    let pausedAt = performance.now();
    let opacity = 0, filmVisible = true, holdEnabled = true, first = true, failed = false;
    let covered = false;
    let needsDraw = true, surfacePainted = false;
    let imageGeneration = 0, pendingImageKey = '';
    let sheetGeneration = 0, sheetSource = '';
    let expansion = 0;
    let sheetOpening = '';
    const video = new SeekFilm(root.querySelector<HTMLElement>('.home-environment')!, tier, () => { request(); wake(); });
    const loop = new LoopFilm(root.querySelector<HTMLElement>('.home-environment')!, tier === 960 ? 1280 : 1920, () => wake());
    const presentationKey = (film: Background): BackgroundKey => `${film}:${film === 'waterfall' ? loop.revision : transport.desired}`;
    const imageFor = (film: Background) => isScrollFilm(film) ? video.get(film, transport.desired) ?? cache.get(`${film}:${transport.desired}`) : loop.image;
    const syncLoop = (effects = false) => {
        const selected = transport.film === 'waterfall';
        const menu = document.documentElement.dataset.menuOpen === 'true';
        loop.setPlayback(selected && filmVisible && !covered && !suspended && !menu && !reduced.matches, effects && selected);
    };
    const menuObserver = new MutationObserver(() => { syncLoop(); wake(); });
    menuObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-menu-open'] });
    async function loadSheet() {
        const target = renderer;
        if (!target || sheetSource === sheetImage.currentSrc) return;
        const generation = ++sheetGeneration;
        try {
            await sheetImage.decode();
            const source = sheetImage.currentSrc;
            const bitmap = await createImageBitmap(sheetImage);
            try {
                if (disposed || renderer !== target || generation !== sheetGeneration) return;
                target.setSheet(bitmap);
                sheetSource = source;
                needsDraw = true;
                wake();
            } finally { bitmap.close(); }
        } catch { /* The DOM photo and its static cutout remain the fallback. */ }
    }
    const url = (film: Background, index: number) => film === 'waterfall' ? loop.posterUrl : `/assets/home-films/${mediaVersion}/${tier}/${film}/frame_${String(index + 1).padStart(5, '0')}.webp`;
    const cache = new FrameCache<ImageBitmap>((film, index, signal, attempt) => loadBitmap(`${url(film, index)}${attempt ? '?retry=' + attempt : ''}`, signal), 20, 2, () => { request(); wake(); });
    const request = () => {
        // A history return can start beyond the film region. Prepare and
        // submit its restored target once so readiness and reverse re-entry
        // work; later off-scene cache work stays suspended.
        if (disposed || suspended || !filmVisible && !first)
            return;
        const scenes = hold.preparing ? [transport.film, nextBackground(transport.film)] : [transport.film];
        if (scenes.includes('waterfall')) loop.prepare();
        const targets = scenes.filter(isScrollFilm).map(film => ({ film, index: transport.desired }));
        for (const target of targets) video.request(target.film, target.index);
        const fallbackTargets = targets.filter(target => video.failed(target.film));
        const residents = renderer?.residentKeys.filter((key): key is FrameKey => !key.startsWith('waterfall:')) ?? [];
        if (fallbackTargets.length) cache.request(fallbackTargets, residents, { ...traversal.hint, lookahead: 6 });
        else cache.suspend();
    };
    const initialize = () => {
        needsDraw = true;
        surfacePainted = false;
        renderer?.dispose();
        renderer = null;
        sheetSource = '';
        delete root.dataset.gpuSheet;
        try {
            renderer = new Renderer<BackgroundKey, Background>(canvas, false, true);
            renderer.resize(width, height);
            canvas.hidden = false;
            failed = false;
        }
        catch {
            renderer?.dispose();
            renderer = null;
            canvas.hidden = true;
            failed = true;
        }
        request();
        void loadSheet();
        wake();
    };
    const wake = () => { if (!raf && !disposed && !suspended)
        raf = requestAnimationFrame(tick); };
    const tick = (now: number) => {
        raf = 0;
        if (disposed || suspended)
            return;
        const elapsed = previous ? (now - previous) / 1000 : 0;
        previous = now;
        const fluid = renderer?.fluid ?? field;
        const fluidChanged = fluid.active, holdChanged = hold.animating;
        const uploads = renderer?.uploads;
        syncLoop(!!renderer && !reduced.matches && (fluid.active || hold.animating));
        fluid.step(elapsed);
        material.step(hold);
        const activeKey = presentationKey(transport.film), nextKey = presentationKey(nextBackground(transport.film));
        const active = imageFor(transport.film);
        const next = hold.preparing ? imageFor(nextBackground(transport.film)) : undefined;
        const paired = !!active && !!next;
        try {
            if (renderer && (fluid.active || hold.animating || isScrollFilm(transport.film) && video.failed(transport.film))) {
                if (active && (hold.phase !== 'REVEALING' || paired)) renderer.upload(0, activeKey, active);
                if (paired) renderer.upload(1, nextKey, next!);
            }
            if (hold.step(clock.frame(now), paired, material.coverage, reduced.matches || !renderer)) {
                transport.switchFilm();
                renderer?.swap();
                syncLoop(!!renderer && !reduced.matches && (fluid.active || hold.animating));
                selectionChanged();
                request();
            }
            material.step(hold);
            const key = presentationKey(transport.film);
            const bounds = filmCoverBox(width, height, transport.film);
            const nativeAvailable = isScrollFilm(transport.film) ? !video.failed(transport.film) && video.hasVisual(transport.film) : !!loop.image;
            const native = nativeAvailable
                && (reduced.matches || !renderer || !fluid.active && !hold.animating);
            if (native) {
                video.show(isScrollFilm(transport.film) ? transport.film : null, bounds, filmVisible ? opacity : 0);
                loop.show(transport.film === 'waterfall', bounds, filmVisible ? opacity : 0);
                canvas.style.opacity = '0';
                surfacePainted = false;
                delete root.dataset.gpuSheet;
                fallback.hidden = true;
                needsDraw = true;
                if (imageFor(transport.film) && transport.present(key) && first) {
                    first = false;
                    ready();
                }
            } else {
                if (renderer?.residentKeys[0] === key) canvas.style.opacity = '';
                const draw = (surfacePainted || renderer?.residentKeys[0] === key)
                    && (needsDraw || fluidChanged || holdChanged || fluid.active || hold.animating || renderer?.uploads !== uploads);
                const opening = heroOpening(width, height, expansion, transport.film, writingBounds);
                const incoming = heroOpening(width, height, expansion, nextBackground(transport.film), writingBounds);
                const submitted = draw && renderer?.draw({ field: fluid, material, hold, time: now / 1000, reveal: hold.phase === 'REVEALING', reduced: reduced.matches, current: transport.film, next: nextBackground(transport.film), aperture: renderer.hasSheet ? opening.box : undefined, circles: renderer.hasSheet ? opening.circles : undefined, nextAperture: renderer.hasSheet ? incoming.box : undefined, nextCircles: renderer.hasSheet ? incoming.circles : undefined, extraAperture: renderer.hasSheet ? opening.extraBox : undefined, nextExtraAperture: renderer.hasSheet ? incoming.extraBox : undefined, dividers: renderer.hasSheet ? opening.dividers : undefined, nextDividers: renderer.hasSheet ? incoming.dividers : undefined, capsules: renderer.hasSheet ? opening.capsules : undefined, nextCapsules: renderer.hasSheet ? incoming.capsules : undefined, filmBounds: bounds, nextFilmBounds: filmCoverBox(width, height, nextBackground(transport.film)), imageOpacity: filmVisible ? opacity : 0 });
                if (submitted) { needsDraw = false; surfacePainted = true; }
                if (submitted && renderer?.residentKeys[0] === key) {
                    video.show(null, bounds, 0);
                    loop.show(false, bounds, 0);
                    canvas.hidden = false;
                    transport.present(key);
                    // The gallery curtain covers this entire environment. Keep
                    // fluid state alive underneath so a quick reversal reveals it.
                    fallback.hidden = true;
                    if (renderer.hasSheet) root.dataset.gpuSheet = 'true';
                    if (first) { first = false; ready(); }
                } else if (!renderer && active && transport.presented !== key && pendingImageKey !== key) {
                    const generation = ++imageGeneration;
                    pendingImageKey = key;
                    const candidate = fallback.cloneNode(false) as HTMLImageElement;
                    candidate.src = url(transport.film, transport.desired);
                    void candidate.decode().then(() => {
                        if (disposed || generation !== imageGeneration) return;
                        pendingImageKey = '';
                        if (!transport.present(key)) return;
                        candidate.style.cssText = fallback.style.cssText;
                        fallback.replaceWith(candidate);
                        fallback = candidate;
                        fallback.hidden = false;
                        video.show(null, bounds, 0);
                        loop.show(false, bounds, 0);
                        wake();
                        if (first) { first = false; ready(); }
                    }).catch(() => { if (generation === imageGeneration) pendingImageKey = ''; });
                }
            }
        }
        catch {
            renderer?.dispose();
            renderer = null;
            canvas.hidden = true;
            delete root.dataset.gpuSheet;
            hold.reset();
            failed = true;
            request();
            wake();
        }
        const openingKey = `${transport.film}:${width}:${height}:${expansion}:${JSON.stringify(writingBounds)}`;
        if (!first && sheetOpening !== openingKey) {
            applyHeroOpening(sheet, width, height, heroOpening(width, height, expansion, transport.film, writingBounds));
            sheetOpening = openingKey;
        }
        const displayed = transport.presented?.split(':');
        const frameLabel = transport.film === 'waterfall' ? 'Waterfall · loop' : displayed ? `${displayed[0] === 'tree' ? 'Tree' : 'Robotic arm'} · ${String(Number(displayed[1]) + 1).padStart(3, '0')} / 192` : 'Preparing first film frame';
        const message = transport.film === 'waterfall' && (loop.failed || loop.blocked) ? 'Waterfall · playback unavailable; still frame retained. Hold the background or select the film again to retry.' : cache.error ? 'Film frame unavailable. Last valid visual retained.' : hold.failure ?? frameLabel;
        if (status.textContent !== message) status.textContent = message;
        countdown.hidden = hold.countdown === null;
        if (hold.countdown !== null) {
            countdown.textContent = String(hold.countdown);
            countdown.style.transform = `translate(${hold.center.x}px,${hold.center.y}px)`;
        }
        if (fluid.active || hold.animating)
            wake();
        else
            previous = 0;
    };
    const release = () => {
        needsDraw = true;
        clock.beforeEvent(hold, performance.now());
        hold.release();
        const id = gesture;
        gesture = null;
        if (id !== null && root.hasPointerCapture(id))
            root.releasePointerCapture(id);
        wake();
    };
    root.addEventListener('pointerdown', e => {
        if (!holdEnabled || suspended || disposed || gesture !== null)
            return;
        const excluded = !!(e.target as Element).closest('a,button,input,summary,[data-input-owner]');
        if (!eligible(e.button, e.isPrimary, excluded, { x: e.clientX, y: e.clientY }, width, height))
            return;
        clock.beforeEvent(hold, performance.now());
        if (cache.error || video.hasError) { cache.retry(); video.retry(); }
        if (transport.film === 'waterfall') loop.retry();
        if (hold.press({ x: e.clientX, y: e.clientY }, e.pointerType as Modality)) {
            gesture = e.pointerId;
            if (e.pointerType === 'touch') touchStartedAt = e.timeStamp;
            // Capture keeps ownership stable when the visual layers change;
            // it does not prevent a quick native pan from canceling the pointer.
            root.setPointerCapture(e.pointerId);
            request();
            wake();
        }
    }, options);
    window.addEventListener('touchstart', e => {
        if (gesture === null || hold.modality !== 'touch') return;
        if (e.touches.length !== 1) { release(); return; }
        touchIdentifier = e.touches[0]!.identifier;
    }, { ...options, passive: true });
    root.addEventListener('touchmove', e => {
        if (gesture === null || hold.modality !== 'touch') return;
        if (e.touches.length !== 1) { release(); return; }
        // This must be a non-passive TouchEvent listener: preventing a pointer
        // event (or changing touch-action after contact) cannot stop native pan.
        if (touchSettled(e.timeStamp) && e.cancelable) e.preventDefault();
    }, { ...options, passive: false });
    for (const type of ['touchend', 'touchcancel'] as const)
        window.addEventListener(type, e => {
            if (![...e.changedTouches].some(touch => touch.identifier === touchIdentifier)) return;
            touchIdentifier = null;
            if (gesture !== null && hold.modality === 'touch') release();
        }, { ...options, passive: true });
    root.addEventListener('contextmenu', e => {
        if (!holdEnabled || suspended || first || reduced.matches
            || (e.target as Element).closest('a,button,input,summary,[data-input-owner]')) return;
        if (touchIdentifier !== null || (e as PointerEvent).pointerType === 'touch') e.preventDefault();
    }, options);
    window.addEventListener('pointermove', e => {
        if (disposed || suspended || document.documentElement.dataset.menuOpen === 'true')
            return;
        if (e.pointerId === gesture) {
            clock.beforeEvent(hold, performance.now());
            if (!touchSettled(e.timeStamp)) hold.move({ x: e.clientX, y: e.clientY });
            if (!hold.pressed)
                release();
            wake();
        }
        const owner = (e.target as Element).closest('[data-input-owner]');
        if (e.pointerType === 'touch' || reduced.matches || owner && !owner.hasAttribute('data-physical-identity')) {
            pointer.reset();
            return;
        }
        const samples = e.getCoalescedEvents?.();
        for (const sample of samples?.length ? samples : [e]) {
            const motion = pointer.observe(sample.clientX, sample.clientY, sample.timeStamp);
            if (motion)
                (renderer?.fluid ?? field).inject(motion);
        }
        wake();
    }, options);
    for (const type of ['pointerup', 'pointercancel'] as const)
        window.addEventListener(type, e => { if (e.pointerId === gesture)
            release(); }, options);
    root.addEventListener('lostpointercapture', e => { if (e.pointerId === gesture)
        release(); }, options);
    window.addEventListener('blur', release, options);
    root.addEventListener('keydown', event => {
        if (event.target !== root || event.key.toLowerCase() !== 'f' || event.repeat
            || event.ctrlKey || event.metaKey || event.altKey || !holdEnabled && !reduced.matches || suspended || disposed) return;
        event.preventDefault();
        clock.beforeEvent(hold, performance.now());
        if (cache.error || video.hasError) { cache.retry(); video.retry(); }
        if (nextBackground(transport.film) === 'waterfall') loop.retry();
        hold.select({ x: width * .65, y: height * .5 });
        request();
        wake();
    }, options);
    reduced.addEventListener('change', () => { release(); hold.reset(); (renderer?.fluid ?? field).clear(); needsDraw = true; wake(); }, options);
    canvas.addEventListener('webglcontextlost', e => { e.preventDefault(); release(); renderer?.dispose(); renderer = null; canvas.hidden = true; delete root.dataset.gpuSheet; hold.reset(); request(); wake(); }, options);
    canvas.addEventListener('webglcontextrestored', initialize, options);
    sheetImage.addEventListener('load', () => { void loadSheet(); }, options);
    initialize();
    return {
        update(progress: number, alpha: number, visible: boolean, allowHold: boolean, openingProgress: number, now = performance.now(), fullyCovered = false) {
            if (opacity !== alpha || filmVisible !== visible || expansion !== openingProgress) needsDraw = true;
            transport.setProgress(progress);
            traversal.observe(transport.desired, performance.now());
            opacity = alpha;
            filmVisible = visible;
            holdEnabled = allowHold;
            covered = fullyCovered;
            expansion = openingProgress;
            const bounds = filmCoverBox(width, height, transport.film);
            fallback.style.left = `${bounds[0]}px`;
            fallback.style.top = `${bounds[1]}px`;
            fallback.style.width = `${bounds[2] - bounds[0]}px`;
            fallback.style.height = `${bounds[3] - bounds[1]}px`;
            fallback.style.opacity = String(visible ? alpha : 0);
            if (!allowHold && gesture !== null)
                release();
            request();
            cancelAnimationFrame(raf);
            tick(now);
        },
        resize() {
            const bounds = environment.getBoundingClientRect();
            const nextWriting = measureWriting();
            if (JSON.stringify(writingBounds) !== JSON.stringify(nextWriting)) {
                writingBounds = nextWriting;
                needsDraw = true;
                wake();
            }
            if (width === bounds.width && height === bounds.height) return;
            width = bounds.width;
            height = bounds.height;
            release(); hold.reset(); clock.reset();
            material = new HoldMaterial(width, height);
            field = new LiquidField(width, height);
            renderer?.resize(width, height);
            needsDraw = true;
            wake();
        },
        suspend() { release(); pausedAt = performance.now(); suspended = true; syncLoop(); cancelAnimationFrame(raf); raf = 0; previous = 0; cache.suspend(); hold.reset(); pointer.reset(); clock.reset(); },
        resume() { if (disposed)
            return; suspended = false; needsDraw = true; (renderer?.fluid ?? field).step((performance.now() - pausedAt) / 1000); request(); wake(); },
        canHold(target: Element) {
            return !first && holdEnabled && !suspended && !disposed && !reduced.matches
                && hold.phase === 'IDLE' && root.contains(target)
                && !target.closest('a,button,input,summary,[data-input-owner]');
        },
        get film() { return transport.film; },
        get failed() { return failed; },
        get hasFrame() { return !first; },
        dispose() { release(); disposed = true; abort.abort(); menuObserver.disconnect(); cancelAnimationFrame(raf); video.dispose(); loop.dispose(); cache.dispose(); renderer?.dispose(); canvas.hidden = true; field.clear(); imageGeneration++; sheetGeneration++; delete root.dataset.gpuSheet; sheet.style.removeProperty('clip-path'); sheet.style.removeProperty('mask-image'); sheet.style.removeProperty('mask-composite'); }
    };
}
