import { clamp } from '../contracts/progress';
import { KINETICS, releasePlan } from './kinetics';

export function galleryRelease(index: number, velocity: number, count: number) {
  if (count < 1) return releasePlan(0, 0, KINETICS.gallery, 0, 0);
  // Blank entrance/exit positions belong to deliberate dragging, not inertia.
  return index < 0 || index > count - 1
    ? releasePlan(index, 0, KINETICS.gallery, -4, count + 3)
    : releasePlan(index, velocity, KINETICS.gallery, 0, count - 1);
}

export function gallerySettle(current: number, target: number, seconds: number) {
  const next = current + (target - current) * (1 - Math.exp(-18 * Math.max(0, seconds)));
  return Math.abs(target - next) < 0.001 ? target : next;
}

export function galleryDepth(distance: number) {
  return {
    blur: Math.min(2.4, Math.max(0, distance - 0.2) * 1.1),
    opacity: 1 - clamp((distance - 0.25) / 3) * 0.65,
  };
}
