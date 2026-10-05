import { describe, expect, it } from "vitest";
import { filmProgress } from "./home-film";

describe("pinned seasonal scene geometry", () => {
  it("finishes before its stage leaves the viewport", () => {
    expect(filmProgress(0, 2700, 0, 900)).toBe(0);
    expect(filmProgress(-900, 2700, 900, 900)).toBe(.5);
    expect(filmProgress(-1800, 2700, 1800, 900)).toBe(1);
    expect(filmProgress(-2200, 2700, 2200, 900)).toBe(1);
  });
  it("clamps entry, reverses, and does not depend on downstream CMS length", () => {
    expect(filmProgress(500, 2000, 0, 800)).toBe(0);
    expect(filmProgress(-600, 2000, 600, 800)).toBe(.5);
    expect(filmProgress(-300, 2000, 300, 800)).toBe(.25);
    expect(filmProgress(-300, 2000, 8300, 800)).toBe(.25);
  });
  it("uses the actual resized stage height and handles the static composition", () => {
    expect(filmProgress(-400, 1600, 400, 800)).toBe(.5);
    expect(filmProgress(-400, 2000, 400, 1000)).toBe(.4);
    expect(filmProgress(-400, 800, 400, 800)).toBe(0);
    expect(filmProgress(0, 800, 0, 0)).toBe(0);
  });
});
