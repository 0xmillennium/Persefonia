import { expect, test } from "vitest";
import { normalizeNarrativeProgress } from "./progress";

test("finite progress is clamped to the mental-world traversal", () => {
  expect(normalizeNarrativeProgress(-0.5)).toBe(0);
  expect(normalizeNarrativeProgress(0)).toBe(0);
  expect(normalizeNarrativeProgress(0.437)).toBe(0.437);
  expect(normalizeNarrativeProgress(1)).toBe(1);
  expect(normalizeNarrativeProgress(1.5)).toBe(1);
});

test.each([NaN, Infinity, -Infinity])("non-finite progress %s is rejected", progress => {
  expect(() => normalizeNarrativeProgress(progress)).toThrow(RangeError);
});
