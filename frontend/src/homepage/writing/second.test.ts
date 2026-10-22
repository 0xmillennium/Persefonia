import { describe, expect, it } from 'vitest';
import { secondWordState } from './second';

describe('second-writing semantic phrase motion', () => {
  it('establishes readable complete phrase groups even near the start of entry', () => {
    for (const progress of [0, 0.18, 0.43, 0.75, 1]) {
      const states = Array.from({ length: 6 }, (_, i) => secondWordState(progress, 0, i, 6, 60));
      for (const state of states) {
        expect(state.blur).toBeLessThanOrEqual(0.65);
        expect(state.opacity).toBeGreaterThanOrEqual(0.7);
      }
      expect(states[0]).toEqual(states[1]);
      expect(states[1]).toEqual(states[2]);
      expect(states[3]).toEqual(states[4]);
      expect(states[4]).toEqual(states[5]);
    }
  });
  it('retains a readable establishment and reaches fully sharp lettering', () => {
    expect(secondWordState(0, 0, 0, 6, 60)).toEqual({
      blur: 0.6,
      opacity: 0.72,
      y: -0,
      visible: true,
    });
    for (let i = 0; i < 6; i++)
      expect(secondWordState(1, 0, i, 6, 60)).toEqual({
        blur: 0,
        opacity: 1,
        y: -0,
        visible: true,
      });
  });
  it('emphasizes the earlier phrase while retaining every later word', () => {
    const early = secondWordState(0.43, 0, 0, 6, 60);
    const late = secondWordState(0.43, 0, 5, 6, 60);
    expect(early.opacity).toBeGreaterThan(late.opacity);
    expect(early.blur).toBeLessThan(late.blur);
    expect(late.opacity).toBeGreaterThan(0.7);
  });
  it('fades whole phrases on exit without clipping individual words', () => {
    for (let i = 0; i < 6; i++) {
      expect(secondWordState(1, 0.5, i, 6, 60)).toEqual({
        blur: 0,
        opacity: 0.875,
        y: -1.25,
        visible: true,
      });
      expect(secondWordState(1, 1, i, 6, 60).visible).toBe(false);
    }
  });
  it('is reversible, clamps endpoints and handles a single word', () => {
    const progresses = [0, 0.2, 0.65, 1];
    const forward = progresses.map((p) => secondWordState(p, p, 0, 1, 28));
    expect([...progresses].reverse().map((p) => secondWordState(p, p, 0, 1, 28))).toEqual(
      [...forward].reverse(),
    );
    expect(secondWordState(-1, -1, 0, 1, 28)).toEqual(forward[0]);
    expect(secondWordState(2, 2, 0, 1, 28)).toEqual(forward[3]);
  });
});
