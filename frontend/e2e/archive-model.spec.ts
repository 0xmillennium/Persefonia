import { expect, test } from "@playwright/test";
import { desktopArchiveStates, interpolateSheetProminence, mobileArchiveStates, tabletArchiveStates } from "../src/archive-model";

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
