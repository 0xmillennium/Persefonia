import type { Modality, Point } from './hold/state';
import { PIGMENT } from './rendering/pigment';
export interface MotionSample extends Point {
  vx: number;
  vy: number;
  speed: number;
  distance: number;
}
export function motionSplat(sample: MotionSample): { radius: number; dose: number } {
  const radius =
    PIGMENT.slowRadius -
    (PIGMENT.slowRadius - PIGMENT.fastRadius) * Math.max(0, Math.min(1, sample.speed / 1800));
  // Conserve the submitted line dose even when a long batch uses all 32 splats.
  // The GPU still bounds local pigment concentration independently.
  return { radius, dose: (sample.distance / radius) * 0.7 };
}
/** Keep ordered segments. Only exactly forward-collinear neighbors may merge.
 * Overload ages out the oldest segment instead of inventing a longer final tangent. */
export function queueMotion(queue: MotionSample[], sample: MotionSample, capacity = 32): boolean {
  let dropped = false;
  if (queue.length >= capacity) {
    let merged = false;
    for (let i = 0; i < queue.length - 1; i++) {
      const a = queue[i]!,
        b = queue[i + 1]!,
        dot = (a.vx * b.vx + a.vy * b.vy) / (a.speed * b.speed);
      if (dot < 1 - 1e-6) continue;
      const startX = b.x - (b.vx / b.speed) * b.distance,
        startY = b.y - (b.vy / b.speed) * b.distance;
      if (Math.hypot(startX - a.x, startY - a.y) > 0.001) continue;
      const distance = a.distance + b.distance,
        speed = distance / (a.distance / a.speed + b.distance / b.speed);
      queue.splice(i, 2, {
        ...b,
        distance,
        speed,
        vx: (b.vx / b.speed) * speed,
        vy: (b.vy / b.speed) * speed,
      });
      merged = true;
      break;
    }
    if (!merged) {
      queue.shift();
      dropped = true;
    }
  }
  queue.push(sample);
  return dropped;
}
/** Arc-length midpoints stay on the stored polyline, including its reversals.
 * Dose distance belongs to each equal path interval, never the packet count. */
export function resampleMotion(samples: readonly MotionSample[], maximum = 32): MotionSample[] {
  const total = samples.reduce((sum, s) => sum + s.distance, 0);
  if (total <= 0) return [];
  const count = Math.min(maximum, Math.max(1, Math.ceil(total / 8))),
    distance = total / count,
    result: MotionSample[] = [];
  let segment = 0,
    traveled = 0;
  for (let n = 0; n < count; n++) {
    const midpoint = (n + 0.5) * distance;
    while (segment < samples.length - 1 && traveled + samples[segment]!.distance < midpoint)
      traveled += samples[segment++]!.distance;
    const sample = samples[segment]!,
      back = Math.min(sample.distance, Math.max(0, traveled + sample.distance - midpoint));
    result.push({
      ...sample,
      x: sample.x - (sample.vx / sample.speed) * back,
      y: sample.y - (sample.vy / sample.speed) * back,
      distance,
    });
  }
  return result;
}
/** Event timestamps define speed. Rounded clocks must not erase path geometry;
 * equal-time movement reuses the last valid interval as a bounded estimate. */
export class PointerObserver {
  private last: (Point & { time: number }) | null = null;
  private latestTime: number | null = null;
  private interval = 1 / 60;
  modality: Modality = 'mouse';
  observe(x: number, y: number, time: number): MotionSample | null {
    if (!Number.isFinite(x + y + time)) return null;
    const old = this.last;
    if (this.latestTime !== null && time < this.latestTime) return null;
    const gap = this.latestTime === null ? Infinity : time - this.latestTime;
    this.latestTime = time;
    if (!old || gap > 750) {
      this.last = { x, y, time };
      return null;
    }
    const distance = Math.hypot(x - old.x, y - old.y);
    if (distance < 0.1) return null;
    this.last = { x, y, time };
    const elapsed = (time - old.time) / 1000;
    const dt = elapsed > 0 ? Math.max(elapsed, 0.001) : this.interval;
    if (elapsed > 0) this.interval = dt;
    const speed = Math.min(distance / dt, 4500);
    return {
      x,
      y,
      vx: ((x - old.x) / distance) * speed,
      vy: ((y - old.y) / distance) * speed,
      speed,
      distance,
    };
  }
  reset(): void {
    this.last = null;
    this.latestTime = null;
    this.interval = 1 / 60;
  }
}
export function eligible(
  button: number,
  primary: boolean,
  excluded: boolean,
  point: Point,
  width: number,
  height: number,
): boolean {
  return (
    button === 0 &&
    primary &&
    !excluded &&
    point.x >= 0 &&
    point.y >= 0 &&
    point.x <= width &&
    point.y <= height
  );
}
