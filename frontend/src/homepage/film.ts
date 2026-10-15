import { FilmTransport, FrameCache, TraversalEstimator, keyFor, otherFilm, FILMS, type Film } from '../../experiments/scroll-film-navigator/src/transport';
import { Hold, type Modality } from '../../experiments/scroll-film-navigator/src/hold';
import { HoldClock } from '../../experiments/scroll-film-navigator/src/hold-clock';
import { HoldMaterial } from '../../experiments/scroll-film-navigator/src/hold-material';
import { LiquidField } from '../../experiments/scroll-film-navigator/src/field';
import { Renderer } from '../../experiments/scroll-film-navigator/src/renderer';
import { PointerObserver, eligible } from '../../experiments/scroll-film-navigator/src/input';
import { loadBitmap } from '../../experiments/scroll-film-navigator/src/loader';
import { mediaVersion } from 'virtual:home-media';
import { filmCoverBox, type WindowBox } from './hero-window';
/** Approved transport and material; native scene coordinator is the only scroll owner. */
export function mountFilm(root: HTMLElement, ready: () => void, selectionChanged: () => void) {
    const canvas = root.querySelector<HTMLCanvasElement>('[data-film-canvas]')!;
    const status = root.querySelector<HTMLElement>('[data-film-status]')!;
    const countdown = root.querySelector<HTMLElement>('[data-hold-countdown]')!;
    const fallback = root.querySelector<HTMLImageElement>('[data-film-fallback]')!;
    const sheetImage = root.querySelector<HTMLImageElement>('[data-hero-image]')!;
    const abort = new AbortController(), options = { signal: abort.signal };
    const transport = new FilmTransport(), hold = new Hold(), clock = new HoldClock();
    const pointer = new PointerObserver(), traversal = new TraversalEstimator();
    const reduced = matchMedia('(prefers-reduced-motion: reduce)');
    const tier = innerWidth <= 700 ? 960 : 1600;
    const saved = history.state?.homepageV2?.film;
    if (FILMS.includes(saved))
        transport.restore(saved);
    let width = innerWidth, height = innerHeight, renderer: Renderer | null = null;
    let material = new HoldMaterial(width, height), field = new LiquidField(width, height);
    let raf = 0, previous = 0, gesture: number | null = null, disposed = false, suspended = document.hidden;
    let pausedAt = performance.now();
    let opacity = 0, filmVisible = true, holdEnabled = true, first = true, failed = false;
    let foreground = false, underlayKey = '';
    let needsDraw = true;
    let imageGeneration = 0;
    let sheetGeneration = 0, sheetSource = '';
    let aperture: WindowBox = [0, 0, width, height, 0];
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
    const url = (film: Film, index: number) => `/assets/home-films/${mediaVersion}/${tier}/${film}/frame_${String(index + 1).padStart(5, '0')}.webp`;
    const cache = new FrameCache<ImageBitmap>((film, index, signal, attempt) => loadBitmap(`${url(film, index)}${attempt ? '?retry=' + attempt : ''}`, signal), 20, 2, () => { request(); wake(); });
    const request = () => {
        // A history return can start beyond the film region. Prepare and
        // submit its restored target once so readiness and reverse re-entry
        // work; later off-scene cache work stays suspended.
        if (disposed || suspended || !filmVisible && !first)
            return;
        const targets = [{ film: transport.film, index: transport.desired }];
        if (hold.preparing)
            targets.push({ film: otherFilm(transport.film), index: transport.desired });
        cache.request(targets, renderer?.residentKeys ?? [], traversal.hint);
    };
    const initialize = () => {
        needsDraw = true;
        renderer?.dispose();
        renderer = null;
        sheetSource = '';
        delete root.dataset.gpuSheet;
        try {
            renderer = new Renderer(canvas, false, true);
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
        fluid.step(elapsed);
        material.step(hold);
        const activeKey = keyFor(transport.film, transport.desired), nextKey = keyFor(otherFilm(transport.film), transport.desired);
        const active = cache.get(activeKey), next = hold.preparing ? cache.get(nextKey) : undefined;
        const paired = !!active && !!next;
        try {
            if (renderer) {
                if (active && (hold.phase !== 'REVEALING' || paired))
                    renderer.upload(0, activeKey, active);
                if (paired)
                    renderer.upload(1, nextKey, next!);
            }
            if (hold.step(clock.frame(now), paired, material.coverage, reduced.matches || !renderer)) {
                transport.switchFilm();
                renderer?.swap();
                selectionChanged();
                request();
            }
            material.step(hold);
            const key = keyFor(transport.film, transport.desired);
            const draw = needsDraw || fluidChanged || holdChanged || fluid.active || hold.animating || renderer?.uploads !== uploads;
            const submitted = draw && renderer?.draw({ field: fluid, material, hold, time: now / 1000, reveal: hold.phase === 'REVEALING', reduced: reduced.matches, current: transport.film, next: otherFilm(transport.film), aperture: renderer.hasSheet ? aperture : undefined, filmBounds: filmCoverBox(width, height), imageOpacity: filmVisible && !foreground ? opacity : 0 });
            if (submitted) needsDraw = false;
            if (submitted && renderer?.residentKeys[0] === key) {
                transport.present(key);
                canvas.dataset.foreground = String(foreground);
                const parent = foreground ? root : root.querySelector<HTMLElement>('.home-environment')!;
                if (canvas.parentElement !== parent) parent.append(canvas);
                fallback.hidden = !foreground;
                // Once the curtain is opaque, retain the same film frame below it
                // while the existing GPU surface paints only the foreground trail.
                if (foreground && underlayKey !== key) {
                    underlayKey = key;
                    fallback.src = url(transport.film, transport.desired);
                }
                if (renderer?.hasSheet) root.dataset.gpuSheet = 'true';
                if (first) {
                    first = false;
                    ready();
                }
            }
            else if (!renderer && active && transport.presented !== key) {
                // A real decoded frame gates image-only fallback readiness.
                const generation = ++imageGeneration;
                fallback.src = url(transport.film, transport.desired);
                void fallback.decode().then(() => { if (disposed || generation !== imageGeneration)
                    return; transport.present(key); fallback.hidden = false; wake(); if (first) {
                    first = false;
                    ready();
                } }).catch(() => { });
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
        const displayed = transport.presented?.split(':');
        const frameLabel = displayed ? `${displayed[0] === 'tree' ? 'Tree' : 'Robotic arm'} · ${String(Number(displayed[1]) + 1).padStart(3, '0')} / 192` : 'Preparing first film frame';
        const message = cache.error ? 'Film frame unavailable. Last valid visual retained.' : hold.failure ?? frameLabel;
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
        if (cache.error) cache.retry();
        if (hold.press({ x: e.clientX, y: e.clientY }, e.pointerType as Modality)) {
            gesture = e.pointerId;
            // Touch retains native scrolling; capture only mouse/pen.
            if (e.pointerType !== 'touch')
                root.setPointerCapture(e.pointerId);
            request();
            wake();
        }
    }, options);
    window.addEventListener('pointermove', e => {
        if (disposed || suspended)
            return;
        if (e.pointerId === gesture) {
            clock.beforeEvent(hold, performance.now());
            hold.move({ x: e.clientX, y: e.clientY });
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
            || event.ctrlKey || event.metaKey || event.altKey || !holdEnabled || suspended || disposed) return;
        event.preventDefault();
        clock.beforeEvent(hold, performance.now());
        if (cache.error) cache.retry();
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
        update(progress: number, alpha: number, visible: boolean, allowHold: boolean, box: WindowBox, overlay = false) {
            if (opacity !== alpha || filmVisible !== visible || foreground !== overlay || box.some((value, index) => value !== aperture[index])) needsDraw = true;
            transport.setProgress(progress);
            traversal.observe(transport.desired, performance.now());
            opacity = alpha;
            filmVisible = visible;
            holdEnabled = allowHold;
            foreground = overlay;
            aperture = box;
            const bounds = filmCoverBox(width, height);
            fallback.style.left = `${bounds[0]}px`;
            fallback.style.top = `${bounds[1]}px`;
            fallback.style.width = `${bounds[2] - bounds[0]}px`;
            fallback.style.height = `${bounds[3] - bounds[1]}px`;
            fallback.style.opacity = String(visible ? alpha : 0);
            if (!allowHold && gesture !== null)
                release();
            request();
            wake();
        },
        resize() { width = innerWidth; height = innerHeight; release(); hold.reset(); clock.reset(); material = new HoldMaterial(width, height); renderer?.resize(width, height); needsDraw = true; wake(); },
        suspend() { release(); pausedAt = performance.now(); suspended = true; cancelAnimationFrame(raf); raf = 0; previous = 0; cache.suspend(); hold.reset(); pointer.reset(); clock.reset(); },
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
        dispose() { release(); disposed = true; abort.abort(); cancelAnimationFrame(raf); cache.dispose(); renderer?.dispose(); canvas.hidden = true; field.clear(); imageGeneration++; sheetGeneration++; delete root.dataset.gpuSheet; }
    };
}
