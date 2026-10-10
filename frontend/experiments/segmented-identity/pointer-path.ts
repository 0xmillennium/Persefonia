import type { PointerSample } from "./core";
import { POINTER_STROKE } from "./pointer-stroke";

export const POINTER_PATH = Object.freeze({ capacity: POINTER_STROKE.capacity });

// Keep corners and observation time until the next live tick. Draining the whole
// batch prevents old observations from trailing a fast pointer across frames.
export class PointerPath {
  private samples: PointerSample[] = [];
  dropped = 0;
  get pending(): boolean { return this.samples.length > 0; }
  get size(): number { return this.samples.length; }
  clear(): void { this.samples = []; }
  push(sample: PointerSample): void {
    if (![sample.x, sample.y].every(Number.isFinite)
      || sample.elapsedSeconds !== undefined && (!Number.isFinite(sample.elapsedSeconds) || sample.elapsedSeconds <= 0))
      throw new Error("Invalid pointer sample");
    if (sample.active === false) this.clear();
    const next = { ...sample }, a = this.samples.at(-2), b = this.samples.at(-1);
    if (a && b && a.active !== false && b.active !== false && next.active !== false) {
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
      // An inactive marker is a discontinuity, not a point on the new path.
      // Preserve both it and the first fresh observation during simplification.
      let index = this.samples[0]?.active === false ? 2 : 1, loss = Infinity;
      for (let i = index; i < this.samples.length - 1; i++) {
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
  take(): readonly PointerSample[] | undefined {
    if (!this.pending) return undefined;
    const batch = this.samples; this.samples = []; return batch;
  }
}
