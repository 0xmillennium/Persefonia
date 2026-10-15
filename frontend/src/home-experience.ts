import { mountFilm } from './homepage/film';
import { physicalIdentity } from './homepage/identity';
import { compactIdentity } from './homepage/compact-identity';
import { mountGallery } from './homepage/gallery';
import { mountFooter } from './homepage/footer';
import { siteName, letterReels } from './homepage/site-name';
import { pointerMarker } from './homepage/pointer-marker';
import { openingState, regionProgress, windowProgress, filmRange, writingPacing, writingBeat, footerPhase, CURTAIN_START, HOMEPAGE_SCROLL_SPEED, GALLERY_HANDOFF } from './homepage/scenes';
import { navigationCover } from './homepage/navigation';
import { exitStaircase } from './homepage/staircase';
import { heroWindow, windowClip } from './homepage/hero-window';
import { WheelGlide } from './homepage/wheel-glide';
import { firstWriting } from './homepage/first-writing';
import { secondWriting } from './homepage/second-writing';
import { laterWriting, galleryCurtain } from './homepage/later-writing';
/** Native scroll owns choreography. Every region is measured independently. */
export function enhanceHomepage(): () => void {
    const cleanup: Array<() => void> = [];
    try {
        return mountHomepage(cleanup);
    }
    catch (error) {
        for (const release of cleanup.reverse())
            try {
                release();
            }
            catch { /* Continue releasing independent resources. */ }
        throw error;
    }
}
function mountHomepage(cleanup: Array<() => void>): () => void {
    const found = document.querySelector<HTMLElement>('[data-home-v2]');
    if (!found)
        return () => { };
    const root: HTMLElement = found;
    const lifetime = new AbortController(), options = { signal: lifetime.signal };
    const pacing = writingPacing();
    const distances = { '--first-writing-distance': pacing.hero, '--statement-writing-distance': pacing.statement, '--writing-distance': pacing.body, '--writing-gap': pacing.gap };
    const clearDistances = () => { for (const property of Object.keys(distances)) root.style.removeProperty(property); };
    cleanup.push(() => { lifetime.abort(); clearDistances(); delete root.dataset.enhanced; delete root.dataset.film; delete root.dataset.filmBackdrop; root.querySelectorAll<HTMLElement>('.scene-face').forEach(face => face.inert = false); });
    const reduced = matchMedia('(prefers-reduced-motion: reduce)');
    const glide = new WheelGlide();
    let glideExpected = scrollY;
    const observer = new ResizeObserver(() => measure());
    cleanup.push(() => observer.disconnect());
    const regions = [...root.querySelectorAll<HTMLElement>('[data-scene]')].map(node => ({ node, name: node.dataset.scene!, face: node.querySelector<HTMLElement>('.scene-face')!, top: 0, height: 0 }));
    const environment = root.querySelector<HTMLElement>('.home-environment')!;
    const canvas = root.querySelector<HTMLCanvasElement>('[data-film-canvas]')!;
    const poster = root.querySelector<HTMLElement>('.home-cinema__image')!;
    const image = root.querySelector<HTMLImageElement>('[data-hero-image]')!;
    const loading = document.querySelector<HTMLElement>('[data-home-loader]')!;
    const loadingProgress = loading.querySelector<HTMLOutputElement>('[data-loading-progress]')!;
    const physical = physicalIdentity(root.querySelector<SVGSVGElement>('[data-physical-identity] svg')!);
    cleanup.push(() => physical.dispose());
    const compact = compactIdentity(document.querySelector<HTMLElement>('[data-compact-identity]')!);
    cleanup.push(() => compact.dispose());
    const name = siteName(document.querySelector<HTMLElement>('[data-site-name]')!);
    const letterEffects = [name];
    cleanup.push(() => name.dispose());
    let film: ReturnType<typeof mountFilm>;
    const marker = pointerMarker(target => film?.canHold(target) ?? false);
    cleanup.push(() => marker.dispose());
    for (const label of document.querySelectorAll<HTMLElement>('[data-nav-label]')) {
        const letters = letterReels(label);
        letterEffects.push(letters);
        cleanup.push(() => letters.dispose());
        const link = label.closest<HTMLAnchorElement>('a')!;
        link.addEventListener('pointerenter', event => { if (event.pointerType !== 'touch') link.dataset.previewActive = 'true'; }, options);
        link.addEventListener('pointerleave', () => { delete link.dataset.previewActive; }, options);
        cleanup.push(() => { delete link.dataset.previewActive; });
    }
    const writing = firstWriting(root);
    cleanup.push(() => writing.dispose());
    const perspective = secondWriting(root);
    cleanup.push(() => perspective.dispose());
    const later = laterWriting(root);
    cleanup.push(() => later.dispose());
    const footer = mountFooter(root);
    cleanup.push(() => footer.dispose());
    void document.fonts.ready.then(() => {
        if (lifetime.signal.aborted) return;
        for (const line of root.querySelectorAll<HTMLElement>('.capabilities li>span, .location-links a>.footer-rise')) {
            const letters = letterReels(line, true);
            letterEffects.push(letters);
            cleanup.push(() => letters.dispose());
        }
    });
    const wipe = root.querySelector<HTMLElement>('[data-scene-wipe]')!;
    const curtainBars = [...wipe.querySelectorAll<HTMLElement>('i')];
    let curtainCount = curtainBars.length;
    let curtainHeight = innerHeight, galleryEntryDistance = 0;
    let loadingIdentity: ReturnType<typeof compactIdentity> | null = null, readyCount = 0, disposed = false, raf = 0, suspended = false;
    cleanup.push(() => { disposed = true; cancelAnimationFrame(raf); loading.hidden = true; loadingIdentity?.dispose(); });
    let globe: {
        dispose(): void;
        active(value: boolean): void;
    } | null = null, globeStarting = false;
    const filmRegion = regions[0]!;
    const statementRegion = regions.find(r => r.name === 'statements')!;
    const markRegion = regions.find(r => r.name === 'mark')!;
    const galleryRegion = regions.find(r => r.name === 'gallery')!;
    const galleryPosition = (p: number) => galleryRegion.top + galleryEntryDistance + p * (galleryRegion.height - galleryEntryDistance);
    const gallery = mountGallery(galleryRegion.node, p => window.scrollTo({ top: galleryPosition(.12 + p * .7), behavior: 'instant' }));
    cleanup.push(() => gallery.dispose());
    let lastSave = 0;
    function save(force = false) { if (!force && performance.now() - lastSave < 250)
        return; lastSave = performance.now(); try {
        history.replaceState({ ...history.state, homepageV2: { film: film?.film, category: gallery.category } }, '');
    }
    catch { /* Browsers can deny state writes; native scroll restoration still applies. */ } }
    function measure() {
        glide.cancel();
        galleryEntryDistance = 0;
        galleryRegion.node.style.setProperty('--gallery-entry-distance', '0px');
        const count = Math.max(...[...root.querySelectorAll('.gallery-group')].map(g => g.children.length), 1);
        galleryRegion.node.style.setProperty('--gallery-distance', String((3 + count * 1.4) / HOMEPAGE_SCROLL_SPEED));
        clearDistances();
        const readRegions = () => { for (const region of regions) {
            region.top = region.node.getBoundingClientRect().top + scrollY;
            region.height = region.node.getBoundingClientRect().height;
        } };
        readRegions();
        curtainHeight = innerHeight;
        if (!reduced.matches) {
            for (const [property, distance] of Object.entries(distances)) root.style.setProperty(property, `${distance}px`);
            readRegions();
            const playback = filmRange(filmRegion.top, filmRegion.height, markRegion.top, markRegion.height);
            galleryEntryDistance = Math.max(0, Math.ceil(playback.end) + curtainHeight * GALLERY_HANDOFF.solidEnd - galleryRegion.top - galleryRegion.height * .12);
            galleryRegion.node.style.setProperty('--gallery-entry-distance', `${galleryEntryDistance}px`);
            readRegions();
        }
        film?.resize();
        writing.measure();
        perspective.measure();
        curtainCount = curtainBars.filter(bar => getComputedStyle(bar).display !== 'none').length;
        schedule();
    }
    function ready() { if (disposed)
        return; readyCount++; loadingProgress.value = `${readyCount} / 3`; if (readyCount === 3) {
        root.dataset.ready = 'true';
        measure();
        void exitLoader();
    } }
    async function exitLoader() {
        if (loading.hidden)
            return;
        await document.fonts.ready;
        const bars = [...loading.querySelectorAll<HTMLElement>('.home-staircase i')];
        loading.querySelectorAll<HTMLElement>('.loader-aperture,[data-loading-identity],output,p').forEach(node => node.hidden = true);
        await exitStaircase(bars, reduced.matches, false, loading.querySelector<HTMLElement>('.loader-sheet')!);
        loading.hidden = true;
        loadingIdentity?.dispose();
        loadingIdentity = null;
    }
    const historyVisit = (performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming | undefined)?.type === 'back_forward';
    if (!historyVisit) {
        loading.hidden = false;
        loadingIdentity = compactIdentity(loading.querySelector<HTMLElement>('[data-loading-identity]')!, true);
    }
    root.dataset.enhanced = 'true';
    film = mountFilm(root, ready, schedule);
    cleanup.push(() => film.dispose());
    void document.fonts.ready.then(ready);
    void image.decode().then(ready).catch(() => { root.dataset.mediaFailure = 'true'; loadingProgress.value = 'Hero image unavailable'; });
    const deadline = window.setTimeout(() => { if (readyCount < 3) {
        root.dataset.mediaFailure = 'true';
        loading.hidden = true;
        loadingIdentity?.dispose();
        loadingIdentity = null;
    } }, 12000);
    cleanup.push(() => clearTimeout(deadline));
    const navigation = navigationCover(document.body, options.signal);
    cleanup.push(navigation);
    function schedule() { if (!raf && !disposed && !suspended)
        raf = requestAnimationFrame(update); }
    function update(now: number) {
        raf = 0;
        if (disposed || suspended)
            return;
        if (glide.active && Math.abs(scrollY - glideExpected) > 1) glide.cancel();
        if (glide.active) {
            scrollTo({ top: glide.sample(now), behavior: 'instant' });
            glideExpected = scrollY;
            if (glide.active) schedule();
        }
        root.dataset.film = film.film;
        marker.refresh();
        const y = scrollY, hero = regionProgress(y, filmRegion.top, filmRegion.height);
        const firstBeat = writingBeat(y, filmRegion.top, true);
        writing.update(firstBeat.exit, reduced.matches);
        if (reduced.matches) {
            perspective.update(1, 0, true);
            later.third(1, 0, true);
            later.fourth(1, 0, 1, true);
            footer.update(1, true);
            delete root.dataset.filmBackdrop;
            canvas.dataset.foreground = 'false';
            if (canvas.parentElement !== environment) environment.append(canvas);
            poster.removeAttribute('style');
            if (film.hasFrame) poster.style.clipPath = windowClip(innerWidth, innerHeight, heroWindow(innerWidth, innerHeight, 0));
            film.update(0, 1, true, false, heroWindow(innerWidth, innerHeight, 0));
            root.querySelector<HTMLElement>('[data-scene-wipe]')!.hidden = true;
            for (const region of regions) {
                region.face.dataset.visible = 'true';
                region.face.inert = false;
                for (const name of ['clip-path', 'transform', 'z-index', '--gallery-exit', '--gallery-entry-opacity']) region.face.style.removeProperty(name);
                region.face.querySelectorAll<HTMLElement>(':scope>h2,.gallery-instrument,.gallery-more,.gallery-controls').forEach(node => node.style.removeProperty('clip-path'));
                region.face.querySelectorAll<HTMLElement>('.home-overline,.hero-scroll').forEach(node => node.style.removeProperty('visibility'));
            }
            gallery.active(false);
            gallery.update(0);
            physical.active(false);
            const location = regions.find(r => r.name === 'location')!;
            if (y + innerHeight >= location.top - innerHeight && !globeStarting) { globeStarting = true; void startGlobe(); }
            globe?.active(y + innerHeight >= location.top && y < location.top + location.height);
            save();
            return;
        }
        const opening = openingState(hero);
        const expansion = opening.expansion;
        // The photo is a foreground sheet. Its cutout exposes a stationary film.
        poster.style.clipPath = film.hasFrame ? windowClip(innerWidth, innerHeight, heroWindow(innerWidth, innerHeight, expansion)) : '';
        const galleryP = regionProgress(y, galleryRegion.top + galleryEntryDistance, galleryRegion.height - galleryEntryDistance);
        const playback = filmRange(filmRegion.top, filmRegion.height, markRegion.top, markRegion.height);
        const coverPhase = CURTAIN_START + (y - Math.ceil(playback.end)) / Math.max(1, curtainHeight);
        const wipeBands = galleryCurtain(coverPhase, curtainCount);
        const curtainSolid = windowProgress(coverPhase, CURTAIN_START + GALLERY_HANDOFF.solidStart, CURTAIN_START + GALLERY_HANDOFF.solidEnd);
        const locationRegion = regions.find(r => r.name === 'location')!;
        const locationPhase = footerPhase(y, locationRegion.top, locationRegion.height, innerHeight);
        const locationLead = innerHeight * .1;
        const galleryExit = windowProgress(galleryP, .82, 1);
        const globeActive = footer.update(locationPhase, false);
        const pastEnvironment = y >= galleryRegion.top;
        root.dataset.filmBackdrop = expansion === 1 && curtainSolid < .5 ? film.film : '';
        const capabilityRegion = regions.find(r => r.name === 'capabilities')!;
        const foreground = curtainSolid === 1;
        film.update(regionProgress(y, playback.start, playback.end - playback.start), 1, true, !pastEnvironment, heroWindow(innerWidth, innerHeight, expansion), foreground);
        const secondBeat = writingBeat(y, statementRegion.top);
        const thirdBeat = writingBeat(y, capabilityRegion.top);
        const fourthBeat = writingBeat(y, markRegion.top);
        perspective.update(secondBeat.entry, secondBeat.exit, false);
        later.third(thirdBeat.entry, thirdBeat.exit, false);
        later.fourth(fourthBeat.entry, fourthBeat.exit, windowProgress(y, markRegion.top - pacing.gap, markRegion.top), false);
        for (const region of regions) {
            const p = regionProgress(y, region.top, region.height);
            const lead = region.name === 'gallery' ? markRegion.height * .12 : region.name === 'location' ? locationLead : region.name === 'mark' ? pacing.gap : 0;
            const visible = y >= region.top - lead && y < region.top + region.height;
            region.face.dataset.visible = String(visible);
            const unavailable = !visible || region.name === 'gallery' && (wipeBands.at(-1)! < 1 || galleryExit >= .5);
            if (unavailable && region.face.contains(document.activeElement)) document.querySelector<HTMLElement>('.home-nav nav a')?.focus({ preventScroll: true });
            region.face.inert = unavailable;
            if (region.name === 'gallery') gallery.active(!unavailable);
            if (!visible)
                continue;
            region.face.style.setProperty('--local-progress', String(p));
            if (region.name === 'hero') {
                region.face.querySelectorAll<HTMLElement>('.home-overline,.hero-scroll').forEach(node => node.style.visibility = firstBeat.exit > .12 ? 'hidden' : '');
            }
            if (region.name === 'gallery') {
                region.face.style.setProperty('--gallery-entry-opacity', String(windowProgress(coverPhase, CURTAIN_START + GALLERY_HANDOFF.revealStart, CURTAIN_START + GALLERY_HANDOFF.revealEnd)));
                gallery.update(windowProgress(galleryP, .12, .82));
                region.face.style.setProperty('--gallery-exit', String(galleryExit));
                if (wipeBands.at(-1)! < 1) {
                    const frontier = wipeBands.flatMap((p, i) => [`${i * 100 / curtainCount}% ${(1 - p) * 100}%`, `${(i + 1) * 100 / curtainCount}% ${(1 - p) * 100}%`]);
                    region.face.style.clipPath = `polygon(0 100%,${frontier.join(',')},100% 100%)`;
                    region.face.style.zIndex = '11';
                } else {
                    region.face.style.clipPath = 'none';
                    region.face.style.zIndex = '11';
                }
                const exit = windowProgress(galleryP, .83, .9);
                region.face.querySelectorAll<HTMLElement>(':scope>h2,.gallery-instrument,.gallery-more,.gallery-controls').forEach(node => node.style.clipPath = `inset(0 0 ${exit * 100}% 0)`);
            }
            if (region.name === 'location')
                region.face.style.transform = `translateY(${-Math.max(0, y - (region.top + region.height - innerHeight))}px)`;
        }
        physical.active(later.settled && y >= markRegion.top && y < markRegion.top + markRegion.height && !document.hidden);
        wipe.hidden = coverPhase <= CURTAIN_START;
        wipe.style.setProperty('--curtain-solid', String(curtainSolid));
        wipeBands.forEach((p, i) => { (wipe.children[i] as HTMLElement).style.transform = `translateY(${(1 - p) * 100}%)`; });
        if (y >= locationRegion.top - locationLead - innerHeight && !globeStarting) {
            globeStarting = true;
            void startGlobe();
        }
        globe?.active(globeActive && y >= locationRegion.top && y < locationRegion.top + locationRegion.height);
        save();
    }
    async function startGlobe() {
        const stage = root.querySelector<HTMLElement>('.location-system')!;
        try {
            const [{ GlobeExperiment }, { default: maskUrl }] = await Promise.all([import('../experiments/location-earth-globe/src/experiment'), import('../experiments/location-earth-globe/assets/land-mask.bin?url')]);
            const response = await fetch(maskUrl, { signal: options.signal });
            if (!response.ok)
                throw new Error('Geography unavailable');
            const mask = new Uint8Array(await response.arrayBuffer());
            if (disposed)
                return;
            globe = new GlobeExperiment(stage, mask, false, stage);
            const region = regions.find(r => r.name === 'location')!;
            globe.active(reduced.matches ? scrollY + innerHeight >= region.top && scrollY < region.top + region.height
                : footerPhase(scrollY, region.top, region.height, innerHeight) > .6 && scrollY < region.top + region.height);
        }
        catch (error) {
            if (!disposed)
                stage.dataset.ready = 'false';
        }
    }
    window.addEventListener('scroll', schedule, { ...options, passive: true });
    // Discrete wheel pulses step film frames or one gallery item.
    // Fine trackpad deltas, native touch, keys and scene boundaries stay native.
    window.addEventListener('wheel', event => {
        if (reduced.matches || event.defaultPrevented || !event.cancelable || event.ctrlKey || event.metaKey
            || Math.abs(event.deltaX) >= Math.abs(event.deltaY)
            || (event.deltaMode === WheelEvent.DOM_DELTA_PIXEL && Math.abs(event.deltaY) < 40)
            || (event.target as Element).closest('input,textarea,select,[contenteditable="true"]')) { glide.cancel(); return; }
        const playback = filmRange(filmRegion.top, filmRegion.height, markRegion.top, markRegion.height);
        const galleryStart = galleryPosition(.12);
        const galleryEnd = galleryPosition(.82);
        const curtainEnd = Math.ceil(playback.end) + curtainHeight * GALLERY_HANDOFF.solidEnd;
        if (scrollY >= galleryStart - .5 && scrollY <= galleryEnd + .5 && scrollY >= curtainEnd
            && gallery.wheel(windowProgress(scrollY, galleryStart, galleryEnd), Math.sign(event.deltaY))) {
            glide.cancel();
            event.preventDefault();
            return;
        }

        if (scrollY < playback.start || scrollY > playback.end + .5
            || scrollY <= playback.start + .5 && event.deltaY < 0
            || scrollY >= playback.end - .5 && event.deltaY > 0) { glide.cancel(); return; }
        if (glide.active && (glide.target <= playback.start + .5 && event.deltaY < 0
            || glide.target >= playback.end - .5 && event.deltaY > 0)) {
            const boundary = event.deltaY < 0 ? playback.start : playback.end;
            glide.cancel();
            scrollTo({ top: boundary, behavior: 'instant' });
            return;
        }
        event.preventDefault();
        glideExpected = scrollY;
        glide.retarget(scrollY, Math.sign(event.deltaY) * playback.step * HOMEPAGE_SCROLL_SPEED, playback.start, playback.end, performance.now());
        schedule();
    }, { ...options, passive: false });
    window.addEventListener('keydown', event => {
        if (['ArrowUp', 'ArrowDown', 'PageUp', 'PageDown', 'Home', 'End', ' '].includes(event.key)) glide.cancel();
    }, options);
    window.addEventListener('pointerdown', () => glide.cancel(), { ...options, passive: true, capture: true });
    window.addEventListener('blur', () => glide.cancel(), options);
    window.addEventListener('resize', measure, { ...options, passive: true });
    window.addEventListener('pointermove', e => { if (scrollY >= galleryRegion.top && scrollY < galleryRegion.top + galleryRegion.height && e.pointerType !== 'touch' && !reduced.matches)
        gallery.orient(e.clientX / innerWidth * 2 - 1, e.clientY / innerHeight * 2 - 1); }, options);
    document.addEventListener('click', e => {
        const anchor = (e.target as Element).closest<HTMLAnchorElement>('a[href^="#"]');
        if (!anchor)
            return;
        if (anchor.hash === '#work' || anchor.hash === '#writing') {
            gallery.select(anchor.hash === '#writing' ? 'writings' : 'projects');
            e.preventDefault();
            window.scrollTo({ top: galleryPosition(.13), behavior: 'instant' });
            root.querySelector<HTMLElement>(anchor.hash)?.focus({ preventScroll: true });
        }
    }, options);
    reduced.addEventListener('change', () => { root.dataset.reduced = String(reduced.matches); measure(); }, options);
    document.addEventListener('visibilitychange', () => { suspended = document.hidden; if (suspended) {
        glide.cancel();
        film.suspend();
        physical.active(false);
    }
    else {
        film.resume();
        schedule();
    } }, options);
    window.addEventListener('pagehide', e => { glide.cancel(); save(true); suspended = true; film.suspend(); physical.active(false); if (!e.persisted)
        dispose(); }, options);
    window.addEventListener('pageshow', e => { if (!e.persisted)
        return; suspended = false; film.resume(); loading.hidden = true; measure(); }, options);
    observer.observe(root);
    root.dataset.reduced = String(reduced.matches);
    measure();
    function dispose() { if (disposed)
        return; disposed = true; lifetime.abort(); marker.dispose(); for (const letters of letterEffects) letters.dispose(); clearDistances(); clearTimeout(deadline); cancelAnimationFrame(raf); observer.disconnect(); film.dispose(); physical.active(false); physical.dispose(); footer.dispose(); later.dispose(); perspective.dispose(); compact.dispose(); loadingIdentity?.dispose(); gallery.dispose(); writing.dispose(); globe?.dispose(); navigation(); delete root.dataset.enhanced; delete root.dataset.filmBackdrop; loading.hidden = true; }
    return dispose;
}
