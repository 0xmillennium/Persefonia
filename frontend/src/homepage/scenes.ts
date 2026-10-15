import { FRAME_COUNT } from '../../experiments/scroll-film-navigator/src/transport';

export const HOMEPAGE_SCROLL_SPEED = 4;
export const FILM_SCROLL_STEP = 120 / HOMEPAGE_SCROLL_SPEED;
export const CURTAIN_START = .98;
export const GALLERY_HANDOFF = { column: .3, stagger: .025, solidStart: .25, solidEnd: 1.0375, revealStart: .1, revealEnd: .4 };
export const clamp = (value: number) => Math.max(0, Math.min(1, value));
export const windowProgress = (value: number, start: number, end: number) => clamp((value - start) / (end - start));
export function regionProgress(y: number, top: number, height: number) { return clamp((y - top) / Math.max(1, height)); }
/** Opening keeps its own pacing; film playback spans the later measured scenes. */
export function openingState(progress: number) {
    return {
        expansion: windowProgress(progress, .12, .42)
    };
}
export function filmRange(heroTop: number, heroHeight: number, markTop: number, markHeight: number) {
    const start = heroTop + heroHeight * .42, end = markTop + markHeight * CURTAIN_START;
    return { start, end, step: (end - start) / (FRAME_COUNT - 1) };
}
/** Shared reading and transition budgets for the four authored writing scenes. */
export function writingPacing() {
    const transition = 14 * FILM_SCROLL_STEP, reading = 24 * FILM_SCROLL_STEP;
    const body = transition * 2 + reading;
    // First writing is already open on arrival; the later three have an entrance.
    const distance = (gap: number) => (reading + transition + gap) * .58
        + (body + gap) + body + gap + body * CURTAIN_START;
    const gap = ((FRAME_COUNT - 1) * FILM_SCROLL_STEP - distance(0)) / (distance(1) - distance(0));
    return { transition, reading, gap, hero: reading + transition + gap, statement: body + gap, body };
}
export function writingBeat(y: number, top: number, initiallyOpen = false) {
    const { transition, reading } = writingPacing();
    const readingStart = top + (initiallyOpen ? 0 : transition);
    return {
        entry: initiallyOpen ? 1 : windowProgress(y, top, readingStart),
        exit: windowProgress(y, readingStart + reading, readingStart + reading + transition)
    };
}
/** Plateaus with short reversible turns; never time-driven snapping. */
export function galleryIndex(progress: number, count: number) {
    if (count < 2)
        return 0;
    const position = clamp(progress) * (count - 1), index = Math.floor(position), fraction = position - index;
    return index + windowProgress(fraction, .62, .96);
}
export function staircase(progress: number, count = 8) { return Array.from({ length: count }, (_, i) => windowProgress(progress, i * .045, .68 + i * .045)); }

/** Footer rows emerge after the gallery has cleared, followed by the globe. */
export function footerRowProgress(phase: number, index: number, detail = false) {
    const start = (detail ? .45 : 0) + index * (detail ? .05 : .08);
    return windowProgress(phase, start, start + (detail ? .35 : .45));
}

/** Native scroll positions round to pixels; keep the completed footer reachable. */
export function footerPhase(y: number, top: number, height: number, viewport: number) {
    return (y - Math.floor(top)) / Math.max(1, Math.floor(Math.min(viewport, height - viewport)));
}

/** A discrete wheel pulse selects one adjacent item, then releases at either end. */
export function galleryWheelProgress(progress: number, count: number, direction: number) {
    if (count < 2 || direction === 0) return null;
    const next = Math.round(galleryIndex(progress, count)) + Math.sign(direction);
    return next < 0 || next >= count ? null : next / (count - 1);
}
