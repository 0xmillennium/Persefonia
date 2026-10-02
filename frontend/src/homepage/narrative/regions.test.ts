import { expect, test } from "vitest";
import { PROVISIONAL_REGION_INTERVALS } from "./regions";

test("provisional intervals follow the exact mental-world order and endpoints", () => {
  expect(PROVISIONAL_REGION_INTERVALS.map(({ id, end }) => [id, end])).toEqual([
    ["threshold", 0.125],
    ["work", 0.25],
    ["writing", 0.375],
    ["research", 0.5],
    ["architecture", 0.625],
    ["return", 0.75],
    ["person", 0.875],
    ["final", 1]
  ]);
});

test("regions are unique and cover zero through one without gaps or overlaps", () => {
  const intervals = PROVISIONAL_REGION_INTERVALS;
  expect(intervals[0]?.start).toBe(0);
  expect(intervals.at(-1)?.end).toBe(1);
  expect(new Set(intervals.map(({ id }) => id)).size).toBe(intervals.length);

  for (const [index, interval] of intervals.entries()) {
    expect(interval.start).toBeLessThan(interval.end);
    if (index > 0) {
      expect(interval.start).toBe(intervals[index - 1]?.end);
    }
  }
});
