import type { Hold } from './state';

/** A pointer event splits the frame interval: time before release still belongs
 * to growth, and time before re-press belongs to cancellation. */
export class HoldClock {
  private time: number | null = null;
  frame(now: number): number {
    if (!Number.isFinite(now)) return 0;
    const elapsed = this.time === null ? 0 : Math.max(0, now - this.time) / 1000;
    this.time = Math.max(this.time ?? now, now);
    return elapsed;
  }
  beforeEvent(hold: Hold, now: number): void {
    if (hold.phase === 'GROWING' || hold.phase === 'CANCELING') hold.advance(this.frame(now));
    else if (hold.phase === 'IDLE' || hold.phase === 'SUCCESS_WAIT_RELEASE') this.frame(now);
    // Readiness and reveal remain owned by the rendered matching frame pair.
  }
  reset(): void {
    this.time = null;
  }
}
