import { FRAME_COUNT } from '../film/model';
import { windowProgress } from '../contracts/progress';
import { CURTAIN_START } from '../contracts/choreography';

export const HOMEPAGE_SCROLL_SPEED = 4;
export const FILM_SCROLL_STEP = 120 / HOMEPAGE_SCROLL_SPEED;
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
/** Native scroll positions round to pixels; keep the completed footer reachable. */
export function footerPhase(y: number, top: number, height: number, viewport: number) {
  return (y - Math.floor(top)) / Math.max(1, Math.floor(Math.min(viewport, height - viewport)));
}

/** First touch card centers about one stable viewport after gallery entry. */
export function touchGalleryDistance(count: number): number {
  return 1 / (0.12 + 2.8 / (Math.max(1, count) + 7));
}
