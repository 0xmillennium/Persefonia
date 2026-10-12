import { GlobeOrientation } from "./orientation";

export interface PointerSample { id: number; x: number; y: number; time: number; touch: boolean }
interface VelocitySample { time: number; yaw: number; inferred?: boolean }
export class GlobeGesture {
  phase: "idle" | "pressed" | "dragging" = "idle";
  owner: number | null = null;
  private first: PointerSample | null = null;
  private last: PointerSample | null = null;
  private history: VelocitySample[] = [];
  private lastMovement = -Infinity;
  private yaw = 0;
  private radius = 1;

  constructor(private orientation: GlobeOrientation) {}
  press(sample: PointerSample, radius: number): boolean {
    if (this.owner !== null) return false;
    this.owner = sample.id;
    this.first = this.last = sample;
    this.radius = Math.max(radius, 1);
    this.yaw = 0;
    this.history = [{ time: sample.time, yaw: 0 }];
    this.lastMovement = -Infinity;
    this.phase = "pressed";
    this.orientation.press();
    return true;
  }
  move(sample: PointerSample): "ignored" | "pressed" | "dragging" | "scroll" {
    if (sample.id !== this.owner || !this.first || !this.last) return "ignored";
    const totalX = sample.x - this.first.x, totalY = sample.y - this.first.y;
    if (this.phase === "pressed") {
      if (Math.hypot(totalX, totalY) < 5) return "pressed";
      if (sample.touch && Math.abs(totalY) > Math.abs(totalX)) { this.cancel(); return "scroll"; }
      this.phase = "dragging";
    }
    const dx = sample.x - this.last.x, dy = sample.y - this.last.y;
    const yaw = dx * Math.PI / (this.radius * 1.1);
    // Sparse events after an initial hold must not average in all held time.
    // Bound that unobserved interval to the trailing window; subsequent actual
    // events and stationary time at release determine the measured velocity.
    if (sample.time - this.last.time > 100) {
      this.history = [{ time: sample.time - 100, yaw: this.yaw, inferred: true }];
    }
    this.orientation.drag(yaw, sample.touch ? 0 : dy * 0.8 / this.radius);
    this.yaw += yaw;
    if (Math.abs(dx) > 0.01) this.lastMovement = sample.time;
    this.history.push({ time: sample.time, yaw: this.yaw });
    while (this.history.length > 2 && this.history[1]!.time < sample.time - 100) this.history.shift();
    this.last = sample;
    return "dragging";
  }
  release(id: number, time: number): { handled: boolean; tap: boolean } {
    if (id !== this.owner) return { handled: false, tap: false };
    const tap = this.phase === "pressed";
    const cutoff = time - 100;
    const measured = this.history.filter(sample => !sample.inferred && sample.time >= cutoff);
    let first = measured.length >= 2 ? measured[0] : this.history[0];
    if (first && first.time < cutoff) {
      const index = this.history.findIndex(sample => sample.time >= cutoff);
      if (index > 0) {
        const a = this.history[index - 1]!, b = this.history[index]!;
        first = { time: cutoff, yaw: a.yaw + (b.yaw - a.yaw) * (cutoff - a.time) / (b.time - a.time) };
      } else first = undefined;
    }
    const elapsed = first ? time - first.time : 0;
    const velocity = !tap && time - this.lastMovement <= 100 && elapsed > 0 ?
      (this.yaw - first!.yaw) * 1000 / elapsed : 0;
    this.reset();
    this.orientation.release(velocity);
    return { handled: true, tap };
  }
  cancel(): void { this.reset(); this.orientation.interrupt(); }
  private reset(): void {
    this.owner = null; this.first = this.last = null; this.history = [];
    this.lastMovement = -Infinity; this.phase = "idle";
  }
}
