import { clamp, windowProgress, CURTAIN_START, GALLERY_HANDOFF } from './scenes';

export function galleryEntrance(y: number, filmEnd: number, cue: number, first: number, step: number, columns: number) {
    const cueY = Math.round(cue), firstY = Math.round(first), completeY = Math.round(first + step * 2);
    const middleY = Math.round(first + step);
    const growth = y <= cueY ? .06 * windowProgress(y, filmEnd, cueY)
        : y <= firstY ? .06 + .16 * windowProgress(y, cueY, firstY)
        : y <= middleY ? .22 + .28 * windowProgress(y, firstY, middleY)
        : .5 + .5 * windowProgress(y, middleY, completeY);
    return {
        phase: CURTAIN_START + growth * (GALLERY_HANDOFF.column + (columns - 1) * GALLERY_HANDOFF.stagger),
        darkness: windowProgress(y, completeY, Math.round(first + step * 6)),
        opacity: windowProgress(y, cueY, firstY),
        writing: windowProgress(y, completeY, Math.round(first + step * 3))
    };
}

/** Invert the scroll plateaus so dragging follows the pointer continuously. */
export function galleryProgress(index: number, count: number) {
    if (count < 1) return 0;
    const bounded = Math.max(-4, Math.min(count + 3, index)) + 4;
    const whole = Math.floor(bounded), fraction = bounded - whole;
    return (whole + (fraction === 0 ? 0 : .62 + fraction * .34)) / (count + 7);
}

export function galleryRelease(index: number, velocity: number, count: number) {
    if (count < 1) return 0;
    return Math.max(-4, Math.min(count + 3, Math.round(index + Math.max(-6, Math.min(6, velocity)) * .18)));
}

export function gallerySettle(current: number, target: number, seconds: number) {
    const next = current + (target - current) * (1 - Math.exp(-18 * Math.max(0, seconds)));
    return Math.abs(target - next) < .001 ? target : next;
}

export function galleryDepth(distance: number) {
    return { blur: Math.min(2.4, Math.max(0, distance - .2) * 1.1), opacity: 1 - clamp((distance - .25) / 3) * .65 };
}

/** Rebase card travel at a tuner selection without changing the film/curtain timeline. */
export function galleryTravelProgress(y: number, first: number, step: number, count: number) {
    return clamp((y - first + step) / (step * (count + 7)));
}
export function galleryTravelPosition(progress: number, first: number, step: number, count: number) {
    return first - step + progress * step * (count + 7);
}
