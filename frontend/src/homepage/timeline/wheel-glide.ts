import { clamp } from './pacing';

export class WheelGlide {
  active = false;
  target = 0;
  private from = 0;
  private started = 0;
  private direction = 0;
  private duration = 180;

  retarget(
    current: number,
    distance: number,
    min: number,
    max: number,
    now: number,
    duration = 180,
  ) {
    const direction = Math.sign(distance);
    const origin = this.active && direction === this.direction ? this.target : current;
    const target = Math.max(min, Math.min(max, origin + distance));
    if (this.active && target === this.target) return;
    this.from = current;
    this.target = target;
    this.started = now;
    this.direction = direction;
    this.duration = duration;
    this.active = target !== current;
  }

  sample(now: number) {
    const progress = clamp((now - this.started) / this.duration);
    if (progress === 1) this.active = false;
    return this.from + (this.target - this.from) * progress * progress * (3 - 2 * progress);
  }

  cancel() {
    this.active = false;
  }
}
