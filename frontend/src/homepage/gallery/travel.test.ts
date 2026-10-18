import { describe, expect, it } from 'vitest';
import { GalleryTravel } from './travel';
import { galleryIndex } from '../timeline/pacing';

describe('measured gallery travel', () => {
  it('centers a newly tuned collection at the current position without moving the curtain', () => {
    for (const count of [1, 3, 8, 12]) {
      const travel = new GalleryTravel(3);
      travel.measure(1200, 7000, 900);
      const authoredEntrance = travel.first();
      const position = 5200;
      travel.centerAt(position);
      const progress = 4 / (count + 7);
      expect(galleryIndex(progress, count)).toBe(0);
      expect(travel.travelPosition(progress, count)).toBeCloseTo(position, 8);
      expect(travel.first()).toBe(authoredEntrance);
      expect(travel.end(count)).toBeGreaterThan(position);
    }
  });

  it('restores Projects at either entry and preserves sufficient footer travel', () => {
    const travel = new GalleryTravel(3);
    travel.measure(1200, 7000, 900);
    const authoredEntrance = travel.first();
    travel.centerAt(7500);
    const previousEnd = travel.end(12);
    travel.resetDefault(3, 1, previousEnd);
    expect(travel.first()).toBe(authoredEntrance);
    expect(travel.end(3)).toBeCloseTo(previousEnd, 8);
    travel.resetDefault(3, -1, previousEnd);
    expect(travel.travelFirst()).toBe(authoredEntrance);
    travel.centerAt(7500);
    travel.resetEntry(8);
    expect(travel.tuningShift).toBe(0);
    expect(travel.pathCount).toBe(8);
    expect(travel.travelFirst()).toBe(travel.first());
  });
});
