import { clamp } from './scenes';

/** Invert the scroll plateaus so dragging follows the pointer continuously. */
export function galleryProgress(index: number, count: number) {
    if (count < 2) return 0;
    const bounded = Math.max(0, Math.min(count - 1, index));
    const whole = Math.floor(bounded), fraction = bounded - whole;
    return (whole + (fraction === 0 ? 0 : .62 + fraction * .34)) / (count - 1);
}

export function galleryRelease(index: number, velocity: number, count: number) {
    return Math.max(0, Math.min(Math.max(0, count - 1), Math.round(index + Math.max(-6, Math.min(6, velocity)) * .18)));
}

export function gallerySettle(current: number, target: number, seconds: number) {
    const next = current + (target - current) * (1 - Math.exp(-18 * Math.max(0, seconds)));
    return Math.abs(target - next) < .001 ? target : next;
}

export function galleryDepth(distance: number) {
    return { blur: Math.min(2.4, Math.max(0, distance - .2) * 1.1), opacity: 1 - clamp((distance - .25) / 3) * .65 };
}
