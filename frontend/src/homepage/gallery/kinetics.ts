const RECENT_MS = 100;

/** Positions are logical stops, independent of screen size and input device. */
export class ReleaseVelocity {
  private samples: Array<{ value: number; time: number }> = [];
  reset(value: number, time: number) {
    this.samples = [{ value, time }];
  }
  add(value: number, time: number) {
    const last = this.samples.at(-1);
    const before = this.samples.at(-2);
    if (last && before && (value - last.value) * (last.value - before.value) < 0)
      this.samples = [last];
    this.samples.push({ value, time });
    while (this.samples.length > 2 && this.samples[0]!.time < time - RECENT_MS)
      this.samples.shift();
  }
  value(now: number) {
    const first = this.samples[0],
      last = this.samples.at(-1);
    if (!first || !last || now - last.time >= RECENT_MS || last.time <= first.time) return 0;
    return (last.value - first.value) / Math.max(0.008, (now - first.time) / 1000);
  }
}

export const KINETICS = {
  tuner: { friction: 4, travel: 8, minDuration: 600, maxDuration: 1000 },
  gallery: { friction: 6.5, travel: 3, minDuration: 350, maxDuration: 650 },
} as const;
type Profile = (typeof KINETICS)[keyof typeof KINETICS];
export interface ReleasePlan {
  from: number;
  to: number;
  duration: number;
  coast: boolean;
}

export function releasePlan(
  from: number,
  velocity: number,
  profile: Profile,
  min = -Infinity,
  max = Infinity,
): ReleasePlan {
  const coast = Math.abs(velocity) >= 1.5;
  const projected = from + (coast ? velocity / profile.friction : 0);
  const lower = Math.max(min, Math.ceil(from - profile.travel));
  const upper = Math.min(max, Math.floor(from + profile.travel));
  const to = Math.max(lower, Math.min(upper, Math.round(projected)));
  const duration = coast
    ? Math.max(
        profile.minDuration,
        Math.min(profile.maxDuration, (Math.abs(to - from) * 4000) / Math.abs(velocity)),
      )
    : 180;
  return { from, to, duration: to === from ? 0 : duration, coast };
}

/** Absolute-time deceleration: interruption never depends on accumulated ticks. */
export class KineticMotion {
  constructor(
    readonly plan: ReleasePlan,
    private started: number,
  ) {}
  sample(now: number) {
    const p = this.progress(now);
    return p === 1
      ? this.plan.to
      : this.plan.from + (this.plan.to - this.plan.from) * (1 - (1 - p) ** 4);
  }
  private progress(now: number) {
    return this.plan.duration
      ? Math.max(0, Math.min(1, (now - this.started) / this.plan.duration))
      : 1;
  }
  phase(now: number): 'coasting' | 'settling' | 'resting' {
    const p = this.progress(now);
    return p === 1 ? 'resting' : this.plan.coast && p < 0.75 ? 'coasting' : 'settling';
  }
}
