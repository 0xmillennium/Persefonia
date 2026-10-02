import { expect, test } from "vitest";
import { COMPACT_MAX_WIDTH_PX, deriveCompositionProfile, readViewportState } from "./viewport";

test.each([[800, 400, "landscape"], [400, 800, "portrait"], [400, 400, "square"]] as const)(
  "current %s × %s viewport derives %s orientation, aspect ratio and unmodified DPR", (width, height, orientation) => {
    const view = { innerWidth: width as number, innerHeight: height, devicePixelRatio: 3.5 };
    expect(readViewportState(view as Window)).toEqual({ width, height, aspectRatio: width / height, devicePixelRatio: 3.5, orientation });
    view.innerWidth = 1000;
    expect(readViewportState(view as Window).width).toBe(1000);
  }
);

test.each([NaN, Infinity, -1, 0])("unsafe browser value %s has a finite positive fallback", value => {
  expect(readViewportState({ innerWidth: value, innerHeight: value, devicePixelRatio: value } as Window))
    .toEqual({ width: 1, height: 1, aspectRatio: 1, devicePixelRatio: 1, orientation: "square" });
});

test.each([[671, "compact"], [672, "compact"], [673, "wide"], [834, "wide"], [1024, "wide"]] as const)(
  "width %s selects %s at the existing public boundary", (width, expected) => {
    expect(COMPACT_MAX_WIDTH_PX).toBe(672);
    expect(deriveCompositionProfile(readViewportState({ innerWidth: width, innerHeight: 900, devicePixelRatio: 2 } as Window))).toBe(expected);
  }
);
