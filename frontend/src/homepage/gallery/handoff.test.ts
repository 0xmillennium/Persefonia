import { expect, it } from 'vitest';
import { GalleryHandoff } from './handoff';

it('restores saved travel only on history restoration and returns to Projects from either boundary', () => {
  const saved = { pathCount: 12, tuningShift: 300 };
  expect(new GalleryHandoff(3, saved, false).travel.pathCount).toBe(3);
  const handoff = new GalleryHandoff(3, saved, true);
  expect(handoff.travel.pathCount).toBe(12);
  handoff.travel.measure(1200, 7000, 900);
  const gallery = {
    category: 'notes',
    count: 12,
    active: (_value: boolean) => {},
    select(category: string) {
      this.category = category;
      this.count = 3;
    },
  };
  const oldEnd = handoff.travel.end(gallery.count);
  expect(handoff.update(8000, 1000, 8000, gallery)).toBe(false);
  expect(gallery.category).toBe('projects');
  expect(handoff.travel.end(3)).toBeCloseTo(oldEnd);
  handoff.update(5000, 1000, 8000, gallery);
  gallery.category = 'articles';
  gallery.count = 8;
  handoff.travel.centerAt(5000);
  expect(handoff.update(1000, 1000, 8000, gallery)).toBe(true);
  expect(gallery.category).toBe('projects');
  expect(handoff.travel.tuningShift).toBe(0);
});
