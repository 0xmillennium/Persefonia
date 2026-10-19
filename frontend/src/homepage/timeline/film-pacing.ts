import { FRAME_COUNT } from '../film/model';

/** Pace the shared cinematic position without intercepting native scrolling. */
export class FilmPacing {
  private y: number | null = null;
  private target = 0;
  private time = 0;
  private deadline = 0;
  moving = false;

  reset() {
    this.y = null;
    this.moving = false;
  }

  sample(target: number, start: number, end: number, interval: number, now: number): number {
    const previous = this.y;
    const elapsed = Math.max(0, Math.min(50, now - this.time));
    this.time = now;
    if (
      previous === null ||
      (previous >= end && target >= end) ||
      (previous <= start && target <= start)
    ) {
      this.y = this.target = target;
      this.moving = false;
      return target;
    }
    const from = Math.max(start, Math.min(end, previous));
    const to = Math.max(start, Math.min(end, target));
    if (target !== this.target) {
      this.target = target;
      this.deadline = now + 240;
    }
    const gap = to - from;
    // One source frame per measured seek interval is the base budget. The
    // logarithmic allowance lets intentional fast exploration move faster;
    // the deadline bounds catch-up after input stops, including slow decoders.
    const budget = (end - start) / (FRAME_COUNT - 1) / Math.max(1000 / 60, interval);
    const rate = budget * (1 + Math.log1p(Math.abs(gap) / Math.max(1, budget * 80)));
    const catchup = Math.abs(gap) / Math.max(1, this.deadline - now);
    const distance = Math.min(Math.abs(gap), elapsed * Math.max(rate, catchup));
    this.y = Math.abs(gap) < 0.5 ? to : from + Math.sign(gap) * distance;
    this.moving = this.y !== to;
    return this.y;
  }
}
