import { expect, it } from 'vitest';
import { KINETICS, KineticMotion, ReleaseVelocity, releasePlan } from './kinetics';

it('uses recent logical velocity, forgets a paused release and follows reversals', () => {
  const velocity = new ReleaseVelocity();
  velocity.reset(0, 0);
  velocity.add(20, 1000);
  velocity.add(20.5, 1050);
  velocity.add(21, 1100);
  expect(velocity.value(1100)).toBe(10);
  expect(velocity.value(1200)).toBe(0);
  velocity.add(20.5, 1150);
  expect(velocity.value(1150)).toBe(-10);
  velocity.reset(3, 2000);
  expect(velocity.value(2000)).toBe(0);
});

it('projects bounded whole stops in either direction and gives the gallery a shorter glide', () => {
  for (const from of [-13.7, -0.2, 0, 4.4, 20])
    for (const profile of [KINETICS.tuner, KINETICS.gallery])
      for (const velocity of [-100, -10, 0, 10, 100]) {
        const plan = releasePlan(from, velocity, profile);
        expect(Number.isInteger(plan.to)).toBe(true);
        expect(Math.abs(plan.to - from)).toBeLessThanOrEqual(profile.travel);
        expect(plan.duration).toBeLessThanOrEqual(profile.maxDuration);
      }
  const tuner = releasePlan(0, 20, KINETICS.tuner);
  const gallery = releasePlan(0, 20, KINETICS.gallery);
  expect(tuner.to).toBe(5);
  expect(gallery.to).toBe(3);
  expect(tuner.duration).toBeGreaterThan(gallery.duration);
  expect(releasePlan(0.7, 0, KINETICS.tuner).to).toBe(1);
  expect(releasePlan(0.2, 1, KINETICS.tuner).to).toBe(0);
});

it('has a monotonic, exact landing at every refresh rate with no overshoot or background integration', () => {
  const plan = releasePlan(0.2, 12, KINETICS.tuner);
  for (const hz of [30, 60, 120]) {
    const motion = new KineticMotion(plan, 100);
    let previous = plan.from;
    for (let time = 100; time < 100 + plan.duration; time += 1000 / hz) {
      const value = motion.sample(time);
      expect(value).toBeGreaterThanOrEqual(previous);
      expect(value).toBeLessThanOrEqual(plan.to);
      previous = value;
    }
    expect(motion.sample(100 + plan.duration)).toBe(plan.to);
    expect(motion.phase(100 + plan.duration)).toBe('resting');
    expect(motion.sample(10000)).toBe(plan.to);
  }
  const reverse = new KineticMotion(releasePlan(3, -12, KINETICS.tuner), 0);
  expect(reverse.sample(100)).toBeLessThan(3);
  expect(reverse.phase(0)).toBe('coasting');
  expect(reverse.phase(reverse.plan.duration * 0.9)).toBe('settling');
});

it('starts an interrupted turn exactly where the previous one was caught', () => {
  const first = new KineticMotion(releasePlan(0, 24, KINETICS.tuner), 0);
  const caught = first.sample(100);
  const second = new KineticMotion(releasePlan(caught, -12, KINETICS.tuner), 100);
  expect(second.sample(100)).toBe(caught);
  expect(second.sample(200)).toBeLessThan(caught);
});
