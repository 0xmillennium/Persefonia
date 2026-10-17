import { mountFilm } from './homepage/film';
import { mobileNavigation } from './homepage/mobile-navigation';
import { physicalIdentity } from './homepage/identity';
import { compactIdentity } from './homepage/compact-identity';
import { mountGallery } from './homepage/gallery';
import { mountFooter } from './homepage/footer';
import { siteName, letterReels } from './homepage/site-name';
import { pointerMarker } from './homepage/pointer-marker';
import { openingState, regionProgress, windowProgress, filmRange, writingPacing, writingBeat, footerPhase, CURTAIN_START, HOMEPAGE_SCROLL_SPEED, GALLERY_HANDOFF } from './homepage/scenes';
import { navigationCover } from './homepage/navigation';
import { exitStaircase } from './homepage/staircase';
import { WheelGlide } from './homepage/wheel-glide';
import { wheelPath, wheelDestination } from './homepage/wheel-path';
import { galleryEntrance, galleryTravelProgress, galleryTravelPosition } from './homepage/gallery-motion';
import { firstWriting } from './homepage/first-writing';
import { secondWriting } from './homepage/second-writing';
import { galleryWriting } from './homepage/gallery-writing';
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
    const historyVisit = (performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming | undefined)?.type === 'back_forward';
    const storedPosition = () => {
        const state = history.state?.homepageV2;
        return Number.isFinite(state?.y) && state.y >= 0 && state.width === innerWidth && state.height === innerHeight ? state.y as number : null;
    };
    let restoreY = historyVisit ? storedPosition() : null, restoreFrame = 0;
    let pageShown = document.readyState === 'complete';
    const cancelRestore = () => { restoreY = null; cancelAnimationFrame(restoreFrame); restoreFrame = 0; };
    const pacing = writingPacing();
    const distances = { '--first-writing-distance': pacing.hero, '--statement-writing-distance': pacing.statement, '--writing-distance': pacing.body, '--writing-gap': pacing.gap };
    const clearDistances = () => { for (const property of Object.keys(distances)) root.style.removeProperty(property); };
    cleanup.push(() => { lifetime.abort(); clearDistances(); delete root.dataset.enhanced; delete root.dataset.film; delete root.dataset.filmBackdrop; root.querySelectorAll<HTMLElement>('.scene-face').forEach(face => face.inert = false); });
    const reduced = matchMedia('(prefers-reduced-motion: reduce)');
    const glide = new WheelGlide();
    let glideExpected = scrollY;
    const measuredSizes = new WeakMap<Element, string>();
    const sizeOf = (node: Element) => `${node.clientWidth}:${node.clientHeight}`;
    // Explicit gallery measurements already include their own layout writes.
    const observer = new ResizeObserver(entries => {
        if (entries.some(entry => measuredSizes.get(entry.target) !== sizeOf(entry.target))) measure();
    });
    cleanup.push(() => observer.disconnect());
    const regions = [...root.querySelectorAll<HTMLElement>('[data-scene]')].map(node => ({ node, name: node.dataset.scene!, face: node.querySelector<HTMLElement>('.scene-face')!, top: 0, height: 0 }));
    const environment = root.querySelector<HTMLElement>('.home-environment')!;
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
    for (const label of document.querySelectorAll<HTMLElement>('.home-nav [data-nav-label]')) {
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
    const galleryText = galleryWriting(galleryRegion.node);
    cleanup.push(() => galleryText.dispose());
    const gallerySpace = galleryRegion.node.querySelector<HTMLElement>('.gallery-space')!;
    let galleryClipLeft = 0, galleryClipTop = 0;
    cleanup.push(() => { gallerySpace.style.removeProperty('clip-path'); galleryRegion.node.style.removeProperty('--gallery-tail-distance'); });
    // Collection tuning rebases card travel, never the authored curtain or the current page position.
    const savedTravel = history.state?.homepageV2;
    const projectsCount = root.querySelectorAll('.gallery-category[data-category="projects"] .gallery-card').length;
    let pathCount = restoreY !== null && Number.isInteger(savedTravel?.pathCount) && savedTravel.pathCount > 0 ? savedTravel.pathCount as number : Math.max(1, projectsCount);
    let tuningShift = restoreY !== null && Number.isFinite(savedTravel?.tuningShift) ? savedTravel.tuningShift as number : 0;
    let gallerySide: -1 | 0 | 1 | null = null;
    let galleryBaseHeight = 1;
    const galleryPosition = (p: number) => galleryRegion.top + galleryEntryDistance + p * (galleryBaseHeight - galleryEntryDistance);
    const galleryFirst = () => galleryPosition(.12 + .7 / (pathCount + 7));
    const galleryStep = () => (galleryPosition(.82) - galleryPosition(.12)) / (pathCount + 7);
    const travelFirst = () => galleryFirst() + tuningShift * galleryStep();
    const travelEnd = () => travelFirst() + (gallery.count + 6) * galleryStep();
    const travelPosition = (p: number) => galleryTravelPosition(p, travelFirst(), galleryStep(), gallery.count);
    const galleryCue = () => Math.ceil(filmRange(filmRegion.top, filmRegion.height, markRegion.top, markRegion.height).end) + curtainHeight * GALLERY_HANDOFF.cue;
    const galleryBoundary = (direction: number) => direction < 0 ? markRegion.top + pacing.transition + pacing.reading
        : regions.find(region => region.name === 'location')!.top;
    function moveGallery(top: number, transition: boolean) {
        glide.cancel();
        if (transition) {
            glideExpected = scrollY;
            glide.retarget(scrollY, top - scrollY, Math.min(scrollY, top), Math.max(scrollY, top), performance.now(), 700);
        } else window.scrollTo({ top, behavior: 'instant' });
        schedule();
    }
    const gallery = mountGallery(galleryRegion.node, p => {
        if (p === 0 && scrollY < travelFirst() - galleryStep() - .5) {
            moveGallery(galleryBoundary(-1), true);
            return;
        }
        const top = travelPosition(p);
        moveGallery(top, Math.min(scrollY, top) < travelFirst() - galleryStep() - .5);
    }, schedule, direction => {
        moveGallery(galleryBoundary(direction), direction < 0);
    }, () => {
        glide.cancel();
        // Tuner selections start with card one centered at the current page position.
        tuningShift = (scrollY - galleryFirst()) / galleryStep() - 3;
        measure();
        save(true);
    }, restoreY !== null);
    cleanup.push(() => gallery.dispose());
    function defaultGallery(side: -1 | 1) {
        if (gallery.category === 'projects' && tuningShift === 0 && pathCount === Math.max(1, projectsCount)) return;
        const end = travelEnd();
        gallery.active(false);
        gallery.select('projects');
        pathCount = Math.max(1, projectsCount);
        // Below the gallery, preserve its measured end so the footer cannot jump.
        tuningShift = side > 0 ? Math.max(0, (end - galleryPosition(.82)) / galleryStep()) : 0;
        if (side < 0) measure(true);
    }
    let lastSave = 0;
    function save(force = false) { if (restoreY !== null || !force && performance.now() - lastSave < 250)
        return; lastSave = performance.now(); try {
        history.replaceState({ ...history.state, homepageV2: { film: film?.film, category: gallery.category, pathCount, tuningShift, y: scrollY, width: innerWidth, height: innerHeight } }, '');
    }
    catch { /* Browsers can deny state writes; native scroll restoration still applies. */ } }
    function measure(preserveGlide = false) {
        const measuredY = scrollY;
        if (!preserveGlide) glide.cancel();
        galleryEntryDistance = 0;
        galleryRegion.node.style.setProperty('--gallery-entry-distance', '0px');
        galleryRegion.node.style.setProperty('--gallery-tail-distance', '0px');
        const count = Math.max(...[...root.querySelectorAll('.gallery-group')].map(g => g.children.length), 1);
        galleryRegion.node.style.setProperty('--gallery-distance', String((3 + (count + 8) * 1.4) / HOMEPAGE_SCROLL_SPEED));
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
            galleryEntryDistance = Math.max(0, Math.ceil(playback.end) + curtainHeight * GALLERY_HANDOFF.entryDistance - galleryRegion.top - galleryRegion.height * .12);
            galleryRegion.node.style.setProperty('--gallery-entry-distance', `${galleryEntryDistance}px`);
            readRegions();
        }
        galleryBaseHeight = galleryRegion.height;
        if (!reduced.matches) {
            const tail = Math.max(0, travelEnd() - galleryPosition(.82));
            galleryRegion.node.style.setProperty('--gallery-tail-distance', `${tail}px`);
            readRegions();
            if (tail > 0 && scrollY < measuredY) scrollTo({ top: measuredY, behavior: 'instant' });
        }
        film?.resize();
        writing.measure();
        perspective.measure();
        const galleryBox = gallerySpace.getBoundingClientRect();
        galleryClipLeft = galleryBox.left;
        galleryClipTop = galleryBox.top;
        curtainCount = curtainBars.filter(bar => getComputedStyle(bar).display !== 'none').length;
        for (const node of [root, environment]) measuredSizes.set(node, sizeOf(node));
        schedule();
        restorePosition();
    }
    function restorePosition() {
        if (restoreY === null || !pageShown || readyCount < 3 || restoreFrame || disposed) return;
        restoreFrame = requestAnimationFrame(() => {
            restoreFrame = 0;
            if (disposed || restoreY === null) return;
            const y = restoreY;
            restoreY = null;
            measure();
            scrollTo({ top: y, behavior: 'instant' });
            schedule();
        });
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
    if (!historyVisit) {
        loading.hidden = false;
        loadingIdentity = compactIdentity(loading.querySelector<HTMLElement>('[data-loading-identity]')!, true);
    }
    root.dataset.enhanced = 'true';
    const mobileMenu = mobileNavigation();
    cleanup.push(mobileMenu);
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
        if (!reduced.matches && restoreY === null) {
            const side = scrollY <= Math.round(galleryCue()) ? -1 : scrollY >= Math.round(galleryBoundary(1)) ? 1 : 0;
            if (side && side !== gallerySide) defaultGallery(side);
            gallerySide = side;
        }
        root.dataset.film = film.film;
        marker.refresh();
        const y = scrollY, hero = regionProgress(y, filmRegion.top, filmRegion.height);
        const firstBeat = writingBeat(y, filmRegion.top, true);
        writing.update(firstBeat.exit, reduced.matches);
        if (reduced.matches) {
            gallerySpace.style.removeProperty('clip-path');
            galleryText.update(1, 0, true);
            perspective.update(1, 0, true);
            later.third(1, 0, true);
            later.fourth(1, 0, 1, true);
            footer.update(1, true);
            delete root.dataset.filmBackdrop;
            film.update(0, 1, true, false, 0, now);
            root.querySelector<HTMLElement>('[data-scene-wipe]')!.hidden = true;
            for (const region of regions) {
                region.face.dataset.visible = 'true';
                region.face.inert = false;
                for (const name of ['clip-path', 'transform', 'z-index', '--gallery-exit', '--gallery-entry-opacity']) region.face.style.removeProperty(name);
                region.face.querySelectorAll<HTMLElement>('.home-overline,.hero-scroll').forEach(node => node.style.removeProperty('visibility'));
            }
            gallery.active(false);
            physical.active(false);
            const location = regions.find(r => r.name === 'location')!;
            if (y + innerHeight >= location.top - innerHeight && !globeStarting) { globeStarting = true; void startGlobe(); }
            globe?.active(y + innerHeight >= location.top && y < location.top + location.height);
            save();
            return;
        }
        const opening = openingState(hero);
        const expansion = opening.expansion;
        const galleryP = y < travelEnd() ? .12 + .7 * galleryTravelProgress(y, travelFirst(), galleryStep(), gallery.count)
            : .82 + .18 * regionProgress(y, travelEnd(), galleryBoundary(1) - travelEnd());
        const playback = filmRange(filmRegion.top, filmRegion.height, markRegion.top, markRegion.height);
        const entrance = galleryEntrance(y, Math.ceil(playback.end), galleryCue(), galleryFirst(), galleryStep(), curtainCount);
        const coverPhase = entrance.phase;
        const wipeBands = galleryCurtain(coverPhase, curtainCount);
        const curtainSolid = entrance.darkness;
        const locationRegion = regions.find(r => r.name === 'location')!;
        const locationPhase = footerPhase(y, locationRegion.top, locationRegion.height, innerHeight);
        const locationLead = innerHeight * .1;
        const galleryExit = windowProgress(galleryP, .82, 1);
        galleryText.update(entrance.writing, windowProgress(galleryP, .83, .9), false);
        const globeActive = footer.update(locationPhase, false);
        const pastEnvironment = y >= galleryRegion.top;
        root.dataset.filmBackdrop = expansion === 1 && curtainSolid < .5 ? film.film : '';
        const capabilityRegion = regions.find(r => r.name === 'capabilities')!;
        film.update(regionProgress(y, playback.start, playback.end - playback.start), 1, true, !pastEnvironment, expansion, now, wipeBands.every(p => p === 1));
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
            const unavailable = !visible || region.name === 'gallery' && (y < Math.round(travelFirst()) && gallery.empty && (gallery.count > 0 || entrance.opacity === 0) || galleryExit >= .5);
            if (unavailable && region.face.contains(document.activeElement)) {
                const toggle = document.querySelector<HTMLElement>('[data-menu-toggle]');
                (toggle?.getClientRects().length ? toggle : document.querySelector<HTMLElement>('.home-nav nav a'))?.focus({ preventScroll: true });
            }
            region.face.inert = unavailable;
            if (region.name === 'gallery') {
                gallery.update(galleryTravelProgress(y, travelFirst(), galleryStep(), gallery.count));
                gallery.active(!unavailable);
            }
            if (!visible)
                continue;
            region.face.style.setProperty('--local-progress', String(p));
            if (region.name === 'hero') {
                region.face.querySelectorAll<HTMLElement>('.home-overline,.hero-scroll').forEach(node => node.style.visibility = firstBeat.exit > .12 ? 'hidden' : '');
            }
            if (region.name === 'gallery') {
                region.face.style.setProperty('--gallery-entry-opacity', String(entrance.opacity));
                region.face.style.setProperty('--gallery-exit', String(galleryExit));
                region.face.style.clipPath = 'none';
                if (wipeBands.at(-1)! < 1) {
                    const frontier = wipeBands.flatMap((p, i) => [`${i * innerWidth / curtainCount - galleryClipLeft}px ${(1 - p) * curtainHeight - galleryClipTop}px`, `${(i + 1) * innerWidth / curtainCount - galleryClipLeft}px ${(1 - p) * curtainHeight - galleryClipTop}px`]);
                    gallerySpace.style.clipPath = `polygon(0 100%,${frontier.join(',')},100% 100%)`;
                    region.face.style.zIndex = '11';
                } else {
                    gallerySpace.style.clipPath = 'none';
                    region.face.style.zIndex = '11';
                }
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
    // Both directions traverse the same measured stops, including section handoffs.
    // Fine trackpad deltas, native touch and keys retain native scrolling.
    window.addEventListener('wheel', event => {
        cancelRestore();
        if (gallery.tunerWheel(event)) { glide.cancel(); return; }
        if (document.documentElement.dataset.menuOpen === 'true' || reduced.matches || event.defaultPrevented || !event.cancelable || event.ctrlKey || event.metaKey
            || Math.abs(event.deltaX) >= Math.abs(event.deltaY)
            || (event.deltaMode === WheelEvent.DOM_DELTA_PIXEL && Math.abs(event.deltaY) < 40)
            || (event.target as Element).closest('input:not([type="radio"]),textarea,select,[contenteditable="true"]')) { glide.cancel(); return; }
        gallery.wheel();
        const playback = filmRange(filmRegion.top, filmRegion.height, markRegion.top, markRegion.height);
        const readingEnd = galleryBoundary(-1), footerTop = galleryBoundary(1);
        const stops = wheelPath(readingEnd, [galleryCue(), ...Array.from({ length: gallery.count ? gallery.count + 6 : 0 }, (_, i) =>
            travelFirst() + i * galleryStep())], footerTop,
            document.documentElement.scrollHeight - innerHeight, playback.step * HOMEPAGE_SCROLL_SPEED);
        if (glide.active && Math.abs(scrollY - glideExpected) > 1) glide.cancel();
        const origin = glide.active ? glide.target : scrollY;
        const top = wheelDestination(stops, origin, Math.sign(event.deltaY));
        event.preventDefault();
        if (top === origin) return;
        const entrance = travelFirst();
        const lastVisible = travelFirst() + (gallery.count + 5) * galleryStep();
        const crossing = Math.min(scrollY, top) < entrance - .5 && Math.max(scrollY, top) > readingEnd + .5
            || Math.min(scrollY, top) < footerTop - .5 && Math.max(scrollY, top) > lastVisible + .5;
        glide.cancel();
        glideExpected = scrollY;
        glide.retarget(scrollY, top - scrollY, 0, stops[stops.length - 1]!, performance.now(), crossing ? 700 : 180);
        schedule();
    }, { ...options, passive: false });
    window.addEventListener('keydown', event => {
        if (['ArrowUp', 'ArrowDown', 'PageUp', 'PageDown', 'Home', 'End', ' '].includes(event.key)) { glide.cancel(); cancelRestore(); }
    }, options);
    window.addEventListener('pointerdown', () => { glide.cancel(); cancelRestore(); }, { ...options, passive: true, capture: true });
    window.addEventListener('blur', () => glide.cancel(), options);
    window.addEventListener('resize', () => measure(), { ...options, passive: true });
    window.addEventListener('pointermove', e => { if (document.documentElement.dataset.menuOpen !== 'true' && scrollY >= galleryRegion.top && scrollY < galleryRegion.top + galleryRegion.height && e.pointerType !== 'touch' && !reduced.matches)
        gallery.orient(e.clientX / innerWidth * 2 - 1, e.clientY / innerHeight * 2 - 1); }, options);
    document.addEventListener('click', e => {
        const anchor = (e.target as Element).closest<HTMLAnchorElement>('a[href^="#"]');
        if (!anchor)
            return;
        if (anchor.hash === '#work' || anchor.hash === '#writing') {
            gallery.select(anchor.hash === '#writing' ? 'articles' : 'projects');
            tuningShift = 0;
            pathCount = Math.max(1, gallery.count);
            measure();
            e.preventDefault();
            glide.cancel();
            cancelRestore();
            window.scrollTo({ top: travelFirst(), behavior: 'instant' });
            // Make the destination available before transferring keyboard focus.
            cancelAnimationFrame(raf);
            update(performance.now());
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
    window.addEventListener('pageshow', e => {
        pageShown = true;
        if (e.persisted) { restoreY = storedPosition(); suspended = false; film.resume(); loading.hidden = true; measure(); }
        restorePosition();
    }, options);
    observer.observe(root);
    observer.observe(environment);
    root.dataset.reduced = String(reduced.matches);
    measure();
    cleanup.push(() => cancelAnimationFrame(restoreFrame));
    function dispose() { gallerySpace.style.removeProperty('clip-path'); galleryRegion.node.style.removeProperty('--gallery-tail-distance'); if (disposed)
        return; disposed = true; lifetime.abort(); mobileMenu(); marker.dispose(); for (const letters of letterEffects) letters.dispose(); clearDistances(); clearTimeout(deadline); cancelAnimationFrame(raf); cancelAnimationFrame(restoreFrame); observer.disconnect(); film.dispose(); physical.active(false); physical.dispose(); footer.dispose(); later.dispose(); perspective.dispose(); compact.dispose(); loadingIdentity?.dispose(); gallery.dispose(); writing.dispose(); globe?.dispose(); navigation(); delete root.dataset.enhanced; delete root.dataset.filmBackdrop; loading.hidden = true; }
    return dispose;
}
