import type { Orientation } from './geometry';

export const MOTION = Object.freeze({
  baseSpeed: 0.075,
  pitchLimit: 0.85,
  decayRate: 1.6,
  maxReleaseSpeed: 7,
  flingThreshold: 0.2,
  initialPitch: 0.3,
  initialSpin: -0.5,
});
export const wrapAngle = (value: number): number =>
  ((((value + Math.PI) % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2)) - Math.PI;
export const clampPitch = (value: number): number =>
  Math.max(-MOTION.pitchLimit, Math.min(MOTION.pitchLimit, value));

export class GlobeOrientation implements Orientation {
  pitch: number = MOTION.initialPitch;
  spin: number = MOTION.initialSpin;
  excessSpeed = 0;
  pressed = false;
  paused = false;
  reduced = false;

  get baseSpeed(): number {
    return this.paused || this.reduced ? 0 : MOTION.baseSpeed;
  }
  get angularSpeed(): number {
    return this.pressed ? 0 : this.baseSpeed + this.excessSpeed;
  }
  get moving(): boolean {
    return !this.pressed && (this.baseSpeed !== 0 || Math.abs(this.excessSpeed) > 0.0001);
  }

  reset(): void {
    this.pitch = MOTION.initialPitch;
    this.spin = MOTION.initialSpin;
    this.interrupt();
  }
  press(): void {
    this.pressed = true;
    this.excessSpeed = 0;
  }
  drag(yaw: number, pitch: number): void {
    this.spin = wrapAngle(this.spin + yaw);
    this.pitch = clampPitch(this.pitch + pitch);
  }
  release(velocity = 0): void {
    this.pressed = false;
    const speed = Math.max(-MOTION.maxReleaseSpeed, Math.min(MOTION.maxReleaseSpeed, velocity));
    this.excessSpeed =
      !this.paused && !this.reduced && Math.abs(speed) >= MOTION.flingThreshold
        ? speed - this.baseSpeed
        : 0;
  }
  interrupt(): void {
    this.pressed = false;
    this.excessSpeed = 0;
  }
  step(seconds: number): void {
    if (this.pressed) return;
    const dt = Number.isFinite(seconds) ? Math.max(0, seconds) : 0;
    const decay = Math.exp(-MOTION.decayRate * dt);
    this.spin = wrapAngle(
      this.spin + this.baseSpeed * dt + (this.excessSpeed * (1 - decay)) / MOTION.decayRate,
    );
    this.excessSpeed *= decay;
    if (Math.abs(this.excessSpeed) < 0.0001) this.excessSpeed = 0;
  }
}
