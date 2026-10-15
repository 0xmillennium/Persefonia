import { clamp } from './scenes';

export class WheelGlide {
    active = false;
    target = 0;
    private from = 0;
    private started = 0;
    private direction = 0;

    retarget(current: number, distance: number, min: number, max: number, now: number) {
        const direction = Math.sign(distance);
        const origin = this.active && direction === this.direction ? this.target : current;
        const target = Math.max(min, Math.min(max, origin + distance));
        if (this.active && target === this.target) return;
        this.from = current;
        this.target = target;
        this.started = now;
        this.direction = direction;
        this.active = target !== current;
    }

    sample(now: number) {
        const progress = clamp((now - this.started) / 180);
        if (progress === 1) this.active = false;
        return this.from + (this.target - this.from) * (1 - (1 - progress) ** 3);
    }

    cancel() { this.active = false; }
}
