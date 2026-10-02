import { expect, test } from "@playwright/test";
import { desktopArchiveStates, interpolateSheetProminence, mobileArchiveStates, tabletArchiveStates } from "../src/archive-model";
import { renderedArchivePositionMatches } from "./support/archive-position";

test("Archive rendered-position matching does not accept a preceding interpolation frame", ({}, testInfo) => {
  test.skip(testInfo.project.name !== "desktop-1440");
  for (const [requested, target, preceding] of [
    [.51, .5103790613718412, .5004512635379061],
    [4.5, 4.500262881177708, 4.489747634069401],
    [4.51, 4.509726603575184, 4.500262881177708]
  ]) {
    expect(preceding!).toBeGreaterThan(requested! - .03);
    expect(renderedArchivePositionMatches(preceding!, target!)).toBe(false);
    expect(renderedArchivePositionMatches(target!, target!)).toBe(true);
    expect(renderedArchivePositionMatches(target! - .0000005, target!)).toBe(true);
    expect(renderedArchivePositionMatches(target! + .0000005, target!)).toBe(true);
    expect(renderedArchivePositionMatches(target! + .01, target!)).toBe(false);
  }
});

test("Archive sheet material follows every chapter transition continuously", ({}, testInfo) => {
  test.skip(testInfo.project.name !== "desktop-1440");
  for (const states of [desktopArchiveStates, tabletArchiveStates, mobileArchiveStates]) {
    expect(states).toHaveLength(7);
    for (let chapter = 0; chapter < states.length - 1; chapter++) {
      const from = states[chapter]!;
      const to = states[chapter + 1]!;
      expect(from.sheetProminence).toHaveLength(4);
      for (let sheet = 0; sheet < 4; sheet++) {
        const start = from.sheetProminence[sheet]!;
        const end = to.sheetProminence[sheet]!;
        expect(start).toBeGreaterThanOrEqual(0);
        expect(end).toBeLessThanOrEqual(1);
        for (const progress of [0, .25, .49, .5, .51, .75, 1]) {
          const value = interpolateSheetProminence(from, to, sheet, progress);
          expect(value).toBeCloseTo(start + (end - start) * progress, 6);
        }
        expect(interpolateSheetProminence(from, to, sheet, 0)).toBe(start);
        expect(interpolateSheetProminence(from, to, sheet, 1)).toBe(end);
        expect(Math.abs(interpolateSheetProminence(from, to, sheet, .51) -
          interpolateSheetProminence(from, to, sheet, .49))).toBeLessThanOrEqual(Math.abs(end - start) * .021);
      }
    }
  }
  expect(desktopArchiveStates[3]!.sheetProminence).not.toEqual(desktopArchiveStates[4]!.sheetProminence);
});
