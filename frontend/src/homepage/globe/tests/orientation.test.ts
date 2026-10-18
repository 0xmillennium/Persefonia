import { describe, expect, it } from 'vitest';
import { GlobeOrientation, MOTION, wrapAngle } from '../orientation';
import { transformPoint } from '../geometry';

describe('intrinsic orientation and momentum', () => {
  it('suspends immediately on press, including an interrupted fling', () => {
    const o = new GlobeOrientation();
    o.release(5);
    o.step(0.02);
    o.press();
    const spin = o.spin;
    o.step(0.04);
    expect(o.spin).toBe(spin);
    expect(o.angularSpeed).toBe(0);
    expect(o.excessSpeed).toBe(0);
  });
  it('permits multiple unrestricted turns without discontinuity', () => {
    const o = new GlobeOrientation();
    o.drag(50 * Math.PI * 2 + 0.2, 0);
    expect(o.spin).toBeCloseTo(MOTION.initialSpin + 0.2);
    expect(wrapAngle(-150 * Math.PI * 2 - 0.2)).toBeCloseTo(-0.2);
  });
  it('bounds pitch and retains it under release and continued intrinsic spin', () => {
    const o = new GlobeOrientation();
    o.press();
    o.drag(0.4, 10);
    o.release(4);
    expect(o.pitch).toBe(MOTION.pitchLimit);
    const pole = transformPoint([0, 1, 0], o);
    for (let i = 0; i < 240; i++) o.step(1 / 60);
    expect(o.pitch).toBe(MOTION.pitchLimit);
    const after = transformPoint([0, 1, 0], o);
    after.forEach((v, i) => expect(v).toBeCloseTo(pole[i]!));
    o.drag(0, -10);
    expect(o.pitch).toBe(-MOTION.pitchLimit);
  });
  it('integrates signed release speed analytically independent of frame rate', () => {
    for (const release of [-6, 6]) {
      const results = [10, 12, 30, 60, 120].map((hz) => {
        const o = new GlobeOrientation();
        o.release(release);
        expect(o.angularSpeed).toBeCloseTo(release);
        for (let i = 0; i < hz; i++) o.step(1 / hz);
        return o;
      });
      results.forEach((o) => {
        expect(o.spin).toBeCloseTo(results[0]!.spin, 10);
        expect(o.angularSpeed).toBeCloseTo(
          MOTION.baseSpeed + (release - MOTION.baseSpeed) * Math.exp(-MOTION.decayRate),
          10,
        );
      });
      const o = results[0]!;
      for (let i = 0; i < 600; i++) o.step(1 / 60);
      expect(o.angularSpeed).toBeCloseTo(MOTION.baseSpeed, 4);
    }
  });
  it('slow release and interruption never manufacture a fling', () => {
    const o = new GlobeOrientation();
    o.release(0.05);
    expect(o.excessSpeed).toBe(0);
    o.release(5);
    o.interrupt();
    expect(o.excessSpeed).toBe(0);
    expect(o.pressed).toBe(false);
  });
  it('retains elapsed active time through irregular slow frames', () => {
    const o = new GlobeOrientation();
    o.release(4);
    for (const dt of [0.016, 0.084, 0.2, 0.1, 0.15, 0.05, 0.3, 0.1]) o.step(dt);
    expect(o.angularSpeed).toBeCloseTo(
      MOTION.baseSpeed + (4 - MOTION.baseSpeed) * Math.exp(-1.6),
      10,
    );
  });
  it('keeps reduced/paused scenes manual', () => {
    const o = new GlobeOrientation();
    for (const key of ['reduced', 'paused'] as const) {
      o[key] = true;
      o.release(6);
      const held = o.spin;
      o.step(0.05);
      expect(o.spin).toBe(held);
      expect(o.moving).toBe(false);
      o.drag(0.2, 0.1);
      expect(o.spin).not.toBe(held);
      o[key] = false;
    }
  });
});
