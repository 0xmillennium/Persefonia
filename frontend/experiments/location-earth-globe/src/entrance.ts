// Finite first-visible reveal. Time advances only through the scene's RAF owner.
export class GlobeEntrance {
  static readonly duration = 3.2;
  elapsed = 0;
  started = false;

  step(seconds: number, reduced: boolean): void {
    if (reduced) { this.finish(); return; }
    this.started = true;
    this.elapsed = Math.min(GlobeEntrance.duration, this.elapsed + Math.max(0, seconds));
  }
  finish(): void { this.elapsed = GlobeEntrance.duration; }
  get moving(): boolean { return this.elapsed < GlobeEntrance.duration; }
  progress(start: number, duration: number): number {
    return Math.max(0, Math.min(1, (this.elapsed - start) / duration));
  }
  get reveal(): number {
    const t = this.progress(0, 1.15);
    return t * t * (3 - 2 * t);
  }
  get phase(): "scan" | "pulse" | "settled" {
    return this.elapsed < 1.8 ? "scan" : this.moving ? "pulse" : "settled";
  }
}
