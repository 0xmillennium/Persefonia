import { describe, expect, it } from 'vitest';
import { WheelGlide } from './wheel-glide';

describe('film wheel glide', () => {
    it('traverses an authored section handoff over its full duration and reverses along the same path', () => {
        const forward = new WheelGlide(), backward = new WheelGlide();
        forward.retarget(5300, 1600, 5300, 6900, 0, 700);
        backward.retarget(6900, -1600, 5300, 6900, 0, 700);
        for (const time of [0, 100, 250, 350, 500, 600, 700])
            expect(forward.sample(time)).toBeCloseTo(backward.sample(700 - time), 8);
        expect(forward.sample(350)).toBe(6100);
        expect(forward.sample(700)).toBe(6900);
        expect(forward.active).toBe(false);
    });
    it('spreads source frames across the glide and settles exactly in both directions', () => {
        const glide = new WheelGlide();
        glide.retarget(600, 120, 0, 1000, 0);
        const positions = [0, 30, 60, 90, 120, 150, 180].map(time => glide.sample(time));
        expect(positions[0]).toBe(600);
        expect(positions.at(-1)).toBe(720);
        expect(positions.every((value, index) => index === 0 || value > positions[index - 1]!)).toBe(true);
        expect(positions[1]! - positions[0]!).toBeCloseTo(positions[6]! - positions[5]!);
        const thresholds = [0, 1, 2, 3].map(index => {
            const sample = new WheelGlide();
            sample.retarget(0, 4, 0, 4, 0);
            return Array.from({ length: 181 }, (_, time) => time).find(time => Math.round(sample.sample(time)) > index)!;
        });
        expect(thresholds[0]).toBeGreaterThan(30);
        expect(thresholds.at(-1)).toBeGreaterThan(135);
        expect(thresholds.at(-1)).toBeLessThan(150);
        expect(glide.active).toBe(false);
    });

    it('preserves accumulated wheel distance independently of render cadence', () => {
        const glide = new WheelGlide();
        glide.retarget(600, 120, 0, 2000, 0);
        const current = glide.sample(30);
        glide.retarget(current, 120, 0, 2000, 30);
        glide.retarget(current, 120, 0, 2000, 30);
        expect(glide.sample(210)).toBe(960);
    });

    it('reverses from the current position without following the old forward destination', () => {
        const glide = new WheelGlide();
        glide.retarget(600, 120, 0, 2000, 0);
        glide.retarget(600, 120, 0, 2000, 0);
        const current = glide.sample(40);
        glide.retarget(current, -120, 0, 2000, 40);
        expect(glide.sample(60)).toBeLessThan(current);
        expect(glide.sample(220)).toBeCloseTo(current - 120);
    });

    it('clamps both boundaries without extending an already pending stop', () => {
        for (const [current, distance, target] of [[50, -120, 0], [950, 120, 1000]]) {
            const glide = new WheelGlide();
            glide.retarget(current!, distance!, 0, 1000, 0);
            glide.retarget(glide.sample(80), distance!, 0, 1000, 80);
            expect(glide.sample(180)).toBe(target);
            expect(glide.active).toBe(false);
        }
    });

    it('cancels momentum so subsequent input starts from the native position', () => {
        const glide = new WheelGlide();
        glide.retarget(600, 120, 0, 2000, 0);
        glide.cancel();
        expect(glide.active).toBe(false);
        glide.retarget(900, 120, 0, 2000, 100);
        expect(glide.sample(280)).toBe(1020);
    });
});
