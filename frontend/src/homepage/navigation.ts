import './navigation.css';
import identitySvg from './compact-identity.svg?raw';
import { compactIdentity } from './compact-identity';
import { exitStaircase } from './staircase';
const KEY = 'persefonia-navigation-cover';
const reduced = () => matchMedia('(prefers-reduced-motion: reduce)').matches;
function cover() {
    const sheet = document.createElement('div');
    sheet.className = 'navigation-cover';
    sheet.setAttribute('role', 'status');
    sheet.setAttribute('aria-label', 'Loading destination');
    const ink = document.createElement('div');
    ink.className = 'navigation-cover__ink';
    const brand = document.createElement('div');
    brand.className = 'compact-brand';
    const button = document.createElement('button');
    button.type = 'button';
    button.tabIndex = -1;
    button.setAttribute('aria-hidden', 'true');
    const plane = document.createElement('span');
    plane.className = 'identity-plane';
    // Trusted, repository-owned SVG, extracted unchanged from Experiment 04.
    const svg = new DOMParser().parseFromString(identitySvg, 'image/svg+xml').documentElement;
    plane.append(document.importNode(svg, true));
    button.append(plane);
    const shadow = document.createElement('span');
    shadow.className = 'floor-shadow';
    button.append(shadow);
    brand.append(button);
    ink.append(brand);
    const word = document.createElement('span');
    word.textContent = '0xmillennium';
    ink.append(word);
    const progress = document.createElement('output');
    progress.textContent = 'Preparing destination';
    ink.append(progress);
    for (let i = 0; i < 8; i++) {
        const band = document.createElement('i');
        sheet.append(band);
    }
    sheet.append(ink);
    document.body.append(sheet);
    let animation: ReturnType<typeof compactIdentity>;
    try {
        animation = compactIdentity(brand, true);
    }
    catch (error) {
        sheet.remove();
        throw error;
    }
    return { sheet, dispose() { animation.dispose(); sheet.remove(); } };
}
/** Native multi-page routing; marker is one-use and never replayed by history. */
export function restoreNavigationCover() {
    const type = (performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming | undefined)?.type;
    let marker: string | null = null;
    try {
        marker = sessionStorage.getItem(KEY);
        sessionStorage.removeItem(KEY);
    }
    catch { }
    if (!marker || type === 'back_forward')
        return;
    try {
        const data = JSON.parse(marker);
        if (data.url !== location.href || Date.now() - data.time > 60000)
            return;
    }
    catch {
        return;
    }
    const presentation = cover();
    const readiness = Promise.all([document.fonts.ready, ...[...document.images].filter(img => img.getBoundingClientRect().top < innerHeight).map(img => img.decode().catch(() => { }))]);
    const timer = window.setTimeout(presentation.dispose, 12000);
    void readiness.then(async () => { presentation.sheet.querySelector('output')!.textContent = 'Ready'; await exitStaircase([...presentation.sheet.children] as HTMLElement[], reduced(), true); presentation.dispose(); clearTimeout(timer); });
    window.addEventListener('pagehide', presentation.dispose, { once: true });
}
export function navigationCover(root: HTMLElement, signal: AbortSignal) {
    let presentation: ReturnType<typeof cover> | null = null;
    let timer = 0;
    const release = () => { clearTimeout(timer); presentation?.dispose(); presentation = null; };
    root.addEventListener('click', e => {
        if (e.defaultPrevented || !(e instanceof MouseEvent) || e.button !== 0 || e.metaKey || e.ctrlKey || e.altKey || e.shiftKey)
            return;
        const anchor = (e.target as Element).closest<HTMLAnchorElement>('a[href]');
        if (!anchor || anchor.target || anchor.hasAttribute('download'))
            return;
        const url = new URL(anchor.href);
        if (url.origin !== location.origin || url.pathname === location.pathname && url.search === location.search)
            return;
        try {
            sessionStorage.setItem(KEY, JSON.stringify({ url: url.href, time: Date.now() }));
        }
        catch {
            return;
        }
        try {
            release();
            presentation = cover();
            // A failed/stopped document request must not leave the source covered.
            timer = window.setTimeout(release, 12000);
        }
        catch (error) {
            sessionStorage.removeItem(KEY);
            console.error('Navigation presentation unavailable', error);
            return;
        }
        e.preventDefault();
        requestAnimationFrame(() => requestAnimationFrame(() => location.assign(url.href)));
    }, { signal });
    window.addEventListener('pageshow', e => { if (e.persisted) {
        release();
    } }, { signal });
    return release;
}
