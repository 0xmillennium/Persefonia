import type { PointerSample } from "./core";
import type { Vec } from "./math";

export const POINTER_PATH = Object.freeze({ capacity: 128, travelPerTick: 32 });

// Keep stroke corners and observation time. Very long strokes are replayed
// over fixed ticks, rather than increasing the duration or cost of one tick.
export class PointerPath {
  private samples: PointerSample[] = [];
  private remaining: number | undefined;
  dropped = 0;
  get pending(): boolean { return this.samples.length > 0; }
  get size(): number { return this.samples.length; }
  clear(): void { this.samples = []; this.remaining = undefined; }
  push(sample: PointerSample): void {
    if (![sample.x, sample.y].every(Number.isFinite)
      || sample.elapsedSeconds !== undefined && (!Number.isFinite(sample.elapsedSeconds) || sample.elapsedSeconds <= 0))
      throw new Error("Invalid pointer sample");
    if (sample.active === false) this.clear();
    const next = { ...sample }, a = this.samples.at(-2), b = this.samples.at(-1);
    if (a && b && next.active !== false) {
      const ab = { x: b.x - a.x, y: b.y - a.y }, bc = { x: next.x - b.x, y: next.y - b.y };
      // Exact collinear samples carry no additional path geometry. Merge their
      // durations without flattening corners or reversals at high input rates.
      if (ab.x * bc.x + ab.y * bc.y >= 0 && Math.abs(ab.x * bc.y - ab.y * bc.x) < 1e-9) {
        next.elapsedSeconds = next.elapsedSeconds === undefined && b.elapsedSeconds === undefined ? undefined
          : (next.elapsedSeconds ?? 0) + (b.elapsedSeconds ?? 0);
        this.samples.pop();
      }
    }
    this.samples.push(next);
    while (this.samples.length > POINTER_PATH.capacity) {
      let index = 1, loss = Infinity;
      for (let i = 1; i < this.samples.length - 1; i++) {
        const a = this.samples[i - 1]!, b = this.samples[i]!, c = this.samples[i + 1]!;
        const distance = Math.hypot(a.x - b.x, a.y - b.y) + Math.hypot(b.x - c.x, b.y - c.y)
          - Math.hypot(a.x - c.x, a.y - c.y);
        if (distance < loss) { loss = distance; index = i; }
      }
      const removed = this.samples.splice(index, 1)[0]!, next = this.samples[index]!;
      next.elapsedSeconds = next.elapsedSeconds === undefined && removed.elapsedSeconds === undefined ? undefined
        : (next.elapsedSeconds ?? 0) + (removed.elapsedSeconds ?? 0);
      this.dropped++;
    }
  }
  take(origin?: Vec, active = false): PointerSample | undefined {
    const next = this.samples[0];
    if (!next) return undefined;
    if (next.active !== false && active && origin) {
      const distance = Math.hypot(next.x - origin.x, next.y - origin.y);
      this.remaining ??= distance;
      if (distance > POINTER_PATH.travelPerTick) {
        const fraction = POINTER_PATH.travelPerTick / distance;
        const timeFraction = Math.min(1, POINTER_PATH.travelPerTick / Math.max(1e-12, this.remaining));
        const elapsedSeconds = next.elapsedSeconds === undefined ? undefined : next.elapsedSeconds * timeFraction;
        if (elapsedSeconds !== undefined) next.elapsedSeconds! -= elapsedSeconds;
        this.remaining -= POINTER_PATH.travelPerTick;
        if (this.remaining <= 0) { this.samples.shift(); this.remaining = undefined; }
        // If a wall constrains the navigator, consume the attempted stroke
        // rather than retrying an unreachable endpoint forever.
        return { ...next, x: origin.x + (next.x - origin.x) * fraction,
          y: origin.y + (next.y - origin.y) * fraction, elapsedSeconds };
      }
    }
    this.remaining = undefined;
    return this.samples.shift();
  }
}
