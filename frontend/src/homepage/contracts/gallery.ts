import { clamp, windowProgress } from './progress';
import { CURTAIN_START, GALLERY_HANDOFF } from './choreography';

/** Plateaus with short reversible turns; never time-driven snapping. */
export function galleryIndex(progress: number, count: number) {
  if (count < 1) return 0;
  const position = clamp(progress) * (count + 7),
    index = Math.floor(position),
    fraction = position - index;
  return index + windowProgress(fraction, 0.62, 0.96) - 4;
}

export function galleryEntrance(
  y: number,
  filmEnd: number,
  cue: number,
  first: number,
  step: number,
  columns: number,
) {
  const cueY = Math.round(cue),
    firstY = Math.round(first),
    completeY = Math.round(first + step * 2);
  const middleY = Math.round(first + step);
  const growth =
    y <= cueY
      ? 0.06 * windowProgress(y, filmEnd, cueY)
      : y <= firstY
        ? 0.06 + 0.16 * windowProgress(y, cueY, firstY)
        : y <= middleY
          ? 0.22 + 0.28 * windowProgress(y, firstY, middleY)
          : 0.5 + 0.5 * windowProgress(y, middleY, completeY);
  return {
    phase:
      CURTAIN_START + growth * (GALLERY_HANDOFF.column + (columns - 1) * GALLERY_HANDOFF.stagger),
    darkness: windowProgress(y, completeY, Math.round(first + step * 6)),
    opacity: windowProgress(y, cueY, firstY),
    writing: windowProgress(y, completeY, Math.round(first + step * 3)),
  };
}

/** Invert the scroll plateaus so dragging follows the pointer continuously. */
export function galleryProgress(index: number, count: number) {
  if (count < 1) return 0;
  const bounded = Math.max(-4, Math.min(count + 3, index)) + 4;
  const whole = Math.floor(bounded),
    fraction = bounded - whole;
  return (whole + (fraction === 0 ? 0 : 0.62 + fraction * 0.34)) / (count + 7);
}

/** Rebase card travel at a tuner selection without changing the film/curtain timeline. */
export function galleryTravelProgress(y: number, first: number, step: number, count: number) {
  return clamp((y - first + step) / (step * (count + 7)));
}
export function galleryTravelPosition(
  progress: number,
  first: number,
  step: number,
  count: number,
) {
  return first - step + progress * step * (count + 7);
}
