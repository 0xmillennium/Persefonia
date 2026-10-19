import { describe, expect, it } from 'vitest';
import { galleryIndex } from '../contracts/gallery';
import { galleryDepth, galleryRelease, gallerySettle } from './motion';
import {
  galleryProgress,
  galleryEntrance,
  galleryTravelProgress,
  galleryTravelPosition,
} from '../contracts/gallery';
import { galleryCurtain } from '../writing/later';

describe('gallery direct manipulation', () => {
  it('grows the curtain through notch four and darkens from notch five through eight', () => {
    const filmEnd = 5730,
      cue = 5838.2,
      first = 6954.3,
      step = 289.8;
    const stops = [
      5341,
      Math.round(cue),
      ...Array.from({ length: 7 }, (_, i) => Math.round(first + step * i)),
    ];
    for (const columns of [6, 8, 12]) {
      const states = stops.map((y) => galleryEntrance(y, filmEnd, cue, first, step, columns));
      expect(galleryCurtain(states[0]!.phase, columns)).toEqual(Array(columns).fill(0));
      for (const notch of [1, 2, 3]) {
        const bands = galleryCurtain(states[notch]!.phase, columns);
        expect(bands[0]).toBeGreaterThan(0);
        expect(bands.at(-1)).toBeLessThan(1);
        expect(states[notch]!.darkness).toBe(0);
      }
      expect(states[1]!.opacity).toBe(0);
      expect(states[2]!.opacity).toBe(1);
      expect(galleryCurtain(states[4]!.phase, columns)).toEqual(Array(columns).fill(1));
      expect(states[4]!.darkness).toBe(0);
      expect(states.slice(0, 5).every((state) => state.writing === 0)).toBe(true);
      expect(states.slice(5).every((state) => state.writing === 1)).toBe(true);
      const halfway = galleryEntrance(
        (stops[4]! + stops[5]!) / 2,
        filmEnd,
        cue,
        first,
        step,
        columns,
      );
      expect(halfway.writing).toBeCloseTo(0.5);
      expect(galleryCurtain(halfway.phase, columns)).toEqual(Array(columns).fill(1));
      expect(states.slice(5).map((state) => state.darkness)).toEqual(expect.arrayContaining([1]));
      for (const notch of [5, 6, 7, 8])
        expect(states[notch]!.darkness).toBeCloseTo((notch - 4) / 4, 2);
    }
  });
  it('maps continuous drag positions through scroll plateaus without sticking or escaping either boundary', () => {
    for (const count of [1, 2, 3, 12]) {
      for (let n = -400; n <= (count + 3) * 100; n++) {
        const index = n / 100;
        expect(galleryIndex(galleryProgress(index, count), count)).toBeCloseTo(index, 8);
      }
      expect(galleryProgress(-5, count)).toBe(0);
      expect(galleryProgress(count + 5, count)).toBe(1);
    }
    expect(galleryProgress(1, 0)).toBe(0);
    expect(galleryProgress(0, 1)).toBe(0.5);
  });
  it('settles a resting release to the nearest card and projects a bounded fling in either direction', () => {
    expect(galleryRelease(0.7, 0, 5).to).toBe(1);
    expect(galleryRelease(0.3, 5, 5).to).toBe(1);
    expect(galleryRelease(1.7, -5, 5).to).toBe(1);
    expect(galleryRelease(-4, -100, 5).to).toBe(-4);
    expect(galleryRelease(8, 100, 5).to).toBe(8);
    expect(galleryRelease(0, 5, 0).to).toBe(0);
    expect(galleryRelease(0, 0, 1).to).toBe(0);
    expect(galleryRelease(0, -100, 5).to).toBe(0);
    expect(galleryRelease(4, 100, 5).to).toBe(4);
    expect(galleryRelease(-0.7, -100, 5).to).toBe(-1);
    expect(galleryRelease(1, 100, 10).to).toBe(4);
  });
  it('has frame-rate independent settling and reverses toward a new target without jumping', () => {
    const at30 = gallerySettle(0, 1, 1 / 30);
    expect(gallerySettle(gallerySettle(0, 1, 1 / 60), 1, 1 / 60)).toBeCloseTo(at30, 8);
    const reversed = gallerySettle(at30, 0, 1 / 60);
    expect(reversed).toBeGreaterThan(0);
    expect(reversed).toBeLessThan(at30);
    let value = 0;
    for (let i = 0; i < 30; i++) value = gallerySettle(value, 1, 1 / 60);
    expect(value).toBe(1);
  });
  it('rebases unequal collections at a stationary page position with three notches to center and reversible travel', () => {
    for (const count of [1, 2, 3, 12]) {
      for (const first of [6954.3, 10000, 25000]) {
        const step = 289.8;
        for (let index = -3; index <= count + 3; index++) {
          const y = first + (index + 3) * step;
          expect(galleryIndex(galleryTravelProgress(y, first, step, count), count)).toBeCloseTo(
            index,
            8,
          );
          const p = galleryProgress(index + 0.35, count);
          expect(
            galleryTravelProgress(galleryTravelPosition(p, first, step, count), first, step, count),
          ).toBeCloseTo(p, 8);
        }
        expect(
          galleryIndex(galleryTravelProgress(first + step * 3, first, step, count), count),
        ).toBe(0);
      }
    }
  });
  it('centers tuner selections at a stationary page anchor, including during entrance and late gallery travel', () => {
    for (const count of [1, 2, 3, 12])
      for (const y of [6500, 7824, 15000]) {
        const step = 280,
          first = y - 3 * step;
        expect(galleryIndex(galleryTravelProgress(y, first, step, count), count)).toBe(0);
        expect(galleryIndex(galleryTravelProgress(y + step, first, step, count), count)).toBe(1);
        expect(galleryIndex(galleryTravelProgress(y - step, first, step, count), count)).toBe(-1);
        expect(galleryTravelPosition(galleryProgress(0, count), first, step, count)).toBe(y);
      }
  });
  it('keeps the centered cover sharp and bounds depth blur and fading', () => {
    expect(galleryDepth(0)).toEqual({ blur: 0, opacity: 1 });
    expect(galleryDepth(1).blur).toBeGreaterThan(0);
    expect(galleryDepth(2).opacity).toBeLessThan(galleryDepth(1).opacity);
    expect(galleryDepth(100).blur).toBe(2.4);
    expect(galleryDepth(100).opacity).toBeCloseTo(0.35);
  });
});
