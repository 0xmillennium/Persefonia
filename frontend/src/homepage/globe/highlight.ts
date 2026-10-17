import type { Vec3 } from './geometry';

/** Short interaction feedback; a settled or reduced-motion globe does not tick. */
export class GlobeHighlight {
  point: Vec3 = [0, 0, 1];
  strength = 0;
  moving = false;

  step(point: Vec3 | null, seconds: number, reduced: boolean): void {
    if (point) this.point = point;
    const target = point ? 1 : 0;
    const elapsed = seconds > 0 ? Math.min(seconds, 0.05) : 1 / 60;
    this.strength = reduced
      ? target
      : target + (this.strength - target) * Math.exp(-elapsed / (point ? 0.07 : 0.16));
    this.moving = Math.abs(this.strength - target) > 0.001;
    if (!this.moving) this.strength = target;
  }
}
