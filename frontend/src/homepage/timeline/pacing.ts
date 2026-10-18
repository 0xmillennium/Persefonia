import { FRAME_COUNT } from '../film/model';

export const HOMEPAGE_SCROLL_SPEED = 4;
export const FILM_SCROLL_STEP = 120 / HOMEPAGE_SCROLL_SPEED;
export const CURTAIN_START = 0.98;
export const GALLERY_HANDOFF = { column: 0.3, stagger: 0.025, entryDistance: 1.0375, cue: 0.12 };
export const clamp = (value: number) => Math.max(0, Math.min(1, value));
export const windowProgress = (value: number, start: number, end: number) =>
  clamp((value - start) / (end - start));
export function regionProgress(y: number, top: number, height: number) {
  return clamp((y - top) / Math.max(1, height));
}
/** Opening and film playback respond together from the first scroll. */
export function openingState(progress: number) {
  return {
    expansion: windowProgress(progress, 0, 0.42),
  };
}
export function filmRange(
  heroTop: number,
  _heroHeight: number,
  markTop: number,
  markHeight: number,
) {
  const start = heroTop,
    end = markTop + markHeight * CURTAIN_START;
  return { start, end, step: (end - start) / (FRAME_COUNT - 1) };
}
/** Shared reading and transition budgets for the four authored writing scenes. */
export function writingPacing() {
  const transition = 14 * FILM_SCROLL_STEP,
    reading = 24 * FILM_SCROLL_STEP;
  const body = transition * 2 + reading;
  // First writing exits immediately; the later three retain their reading time.
  const distance = (gap: number) =>
    transition + gap + (body + gap) + body + gap + body * CURTAIN_START;
  const gap = ((FRAME_COUNT - 1) * FILM_SCROLL_STEP - distance(0)) / (distance(1) - distance(0));
  return { transition, reading, gap, hero: transition + gap, statement: body + gap, body };
}
export function writingBeat(y: number, top: number, initiallyOpen = false) {
  const { transition, reading } = writingPacing();
  const readingStart = top + (initiallyOpen ? 0 : transition);
  return {
    entry: initiallyOpen ? 1 : windowProgress(y, top, readingStart),
    exit: initiallyOpen
      ? windowProgress(y, top, top + transition)
      : windowProgress(y, readingStart + reading, readingStart + reading + transition),
  };
}
/** Plateaus with short reversible turns; never time-driven snapping. */
export function galleryIndex(progress: number, count: number) {
  if (count < 1) return 0;
  const position = clamp(progress) * (count + 7),
    index = Math.floor(position),
    fraction = position - index;
  return index + windowProgress(fraction, 0.62, 0.96) - 4;
}

/** Footer rows emerge after the gallery has cleared, followed by the globe. */
export function footerRowProgress(phase: number, index: number, detail = false) {
  const start = (detail ? 0.45 : 0) + index * (detail ? 0.05 : 0.08);
  return windowProgress(phase, start, start + (detail ? 0.35 : 0.45));
}

/** Native scroll positions round to pixels; keep the completed footer reachable. */
export function footerPhase(y: number, top: number, height: number, viewport: number) {
  return (y - Math.floor(top)) / Math.max(1, Math.floor(Math.min(viewport, height - viewport)));
}
