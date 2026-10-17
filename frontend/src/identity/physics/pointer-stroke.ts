import type { PointerSample } from './model';
import { add, sub, mul, length } from './math';
import type { Vec } from './math';

export const POINTER_STROKE = Object.freeze({ capacity: 128, spacing: 2 });
export type StrokeLeg = { start: Vec; end: Vec; delta: Vec; elapsedSeconds: number };

// One observation batch belongs to one live tick, never a future replay queue.
// Arc-length subdivision retains every corner/reversal and each leg's observed
// speed, while the fixed physics clock remains independent of event frequency.
export class PointerStroke {
  readonly points: readonly Vec[];
  readonly distance: number;
  private readonly segments: { end: Vec; delta: Vec; distance: number; elapsedSeconds: number }[];
  private index = 0;
  private used = 0;
  private travelled = 0;
  constructor(
    origin: Vec,
    samples: readonly PointerSample[],
    width: number,
    height: number,
    defaultInterval: number,
  ) {
    this.points = [
      origin,
      ...samples.map((p) => ({
        x: Math.max(0, Math.min(width, p.x)),
        y: Math.max(0, Math.min(height, p.y)),
      })),
    ];
    this.segments = samples.map((p, i) => {
      const end = this.points[i + 1]!,
        delta = sub(end, this.points[i]!);
      return {
        end,
        delta,
        distance: length(delta),
        elapsedSeconds: p.elapsedSeconds ?? defaultInterval,
      };
    });
    this.distance = this.segments.reduce((sum, s) => sum + s.distance, 0);
  }
  take(distance: number, origin: Vec): StrokeLeg[] {
    const legs: StrokeLeg[] = [];
    while (this.index < this.segments.length) {
      const s = this.segments[this.index]!,
        remaining = s.distance - this.used;
      const amount = Math.min(remaining, Math.max(0, distance - this.travelled));
      if (amount === 0 && remaining > 0) break;
      this.used += amount;
      this.travelled += amount;
      const complete = this.used >= s.distance;
      const end = complete ? s.end : add(sub(s.end, s.delta), mul(s.delta, this.used / s.distance));
      legs.push({ start: origin, end, delta: s.delta, elapsedSeconds: s.elapsedSeconds });
      origin = end;
      if (complete) {
        this.index++;
        this.used = 0;
      } else break;
    }
    return legs;
  }
}
