import { describe, expect, it } from 'vitest';
import { WritingEntrance } from './entrance';

describe('bounded writing entrance ownership', () => {
  it('finishes every row after input stops without changing its exit', () => {
    const entrance = new WritingEntrance(1000);
    expect(entrance.sample(0, 0, 0)).toBe(0);
    expect(entrance.sample(0.1, 0, 10)).toBeCloseTo(0.1);
    expect(entrance.sample(0.1, 0, 510)).toBeCloseTo(0.6);
    expect(entrance.running).toBe(true);
    expect(entrance.sample(0.1, 0, 1010)).toBe(1);
    expect(entrance.state).toBe('presented');
    expect(entrance.running).toBe(false);
  });
  it('does not charge time spent before a scene to its entrance', () => {
    const entrance = new WritingEntrance(1000);
    entrance.sample(0, 0, 0);
    expect(entrance.sample(0.1, 0, 50000)).toBeCloseTo(0.1);
  });
  it('gives departure priority over an interrupted entrance and reverses the exit', () => {
    const entrance = new WritingEntrance(1000);
    entrance.sample(0.05, 0, 0);
    expect(entrance.sample(1, 0.6, 100)).toBe(1);
    expect(entrance.state).toBe('exiting');
    expect(entrance.sample(1, 0.2, 500)).toBe(1);
    expect(entrance.state).toBe('reversing');
    expect(entrance.sample(0.2, 0, 700)).toBe(1);
    expect(entrance.state).toBe('presented');
    expect(entrance.sample(0, 0, 900)).toBe(0);
    expect(entrance.state).toBe('unentered');
    expect(entrance.sample(0.1, 0, 1000)).toBeCloseTo(0.1);
  });
  it('continues an in-scene entrance on reverse and cancels at the leading boundary', () => {
    const entrance = new WritingEntrance(1000);
    entrance.sample(0.3, 0, 0);
    expect(entrance.sample(0.1, 0, 200)).toBeCloseTo(0.5);
    expect(entrance.sample(0.1, 0, 1000)).toBe(1);
    expect(entrance.sample(0, 0, 1100)).toBe(0);
  });
  it('suspends hidden time and resumes without jumping or replaying', () => {
    const entrance = new WritingEntrance(1000);
    entrance.sample(0.1, 0, 0);
    entrance.sample(0.1, 0, 200);
    entrance.suspend();
    expect(entrance.state).toBe('suspended');
    expect(entrance.sample(0.1, 0, 50000)).toBeCloseTo(0.3);
    expect(entrance.sample(0.1, 0, 50200)).toBeCloseTo(0.5);
  });
  it('reconciles fast jumps, reduced motion and disposal without pending work', () => {
    const entrance = new WritingEntrance(1000);
    expect(entrance.sample(1, 1, 0)).toBe(1);
    expect(entrance.sample(0, 0, 100)).toBe(0);
    expect(entrance.sample(0.1, 0, 200, true)).toBe(1);
    expect(entrance.running).toBe(false);
    entrance.dispose();
    expect(entrance.sample(0.1, 0, 300)).toBe(1);
    expect(entrance.state).toBe('disposed');
    expect(entrance.running).toBe(false);
  });
});

describe('scene-coupled reverse exits for slit-only owners', () => {
  it('anchors a reverse exit to the arrived pose without snapping or charging hidden time', () => {
    const entrance = new WritingEntrance(1000, true);
    entrance.sample(0.2, 0, 0);
    entrance.sample(0.2, 0, 1000);
    expect(entrance.sample(0.15, 0, 1100)).toBeCloseTo(0.75);
    expect(entrance.state).toBe('reversing');
    expect(entrance.running).toBe(false);
    expect(entrance.sample(0.15, 0, 5000)).toBeCloseTo(0.75);
    entrance.suspend();
    expect(entrance.sample(0.15, 0, 10000)).toBeCloseTo(0.75);
    expect(entrance.sample(0.1, 0, 10100)).toBeCloseTo(0.5);
    expect(entrance.sample(0, 0, 10200)).toBe(0);
  });
  it('re-enters from a partially reversed slit and completes when input stops', () => {
    const entrance = new WritingEntrance(1000, true);
    entrance.sample(0.2, 0, 0);
    entrance.sample(0.2, 0, 1000);
    entrance.sample(0.1, 0, 1100);
    expect(entrance.sample(0.12, 0, 1200)).toBeCloseTo(0.5);
    expect(entrance.state).toBe('entering');
    expect(entrance.sample(0.12, 0, 2200)).toBe(1);
    expect(entrance.state).toBe('presented');
  });
  it('gives the authored forward exit priority over a reverse entrance window', () => {
    const entrance = new WritingEntrance(1000, true);
    entrance.sample(0.2, 0, 0);
    entrance.sample(0.2, 0, 1000);
    entrance.sample(0.1, 0, 1100);
    expect(entrance.sample(1, 0.6, 1200)).toBe(1);
    expect(entrance.state).toBe('exiting');
    expect(entrance.sample(1, 0.2, 1300)).toBe(1);
    expect(entrance.state).toBe('reversing');
  });
});
