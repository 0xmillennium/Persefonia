import { expect, test, vi } from "vitest";
import { createCapabilitySource } from "./capabilities";
import { mediaFixture } from "./browser-test-support";

test("acquires only presentation media queries and reads their current matches with fine-pointer priority", () => {
  const media = mediaFixture();
  const source = createCapabilitySource(media as unknown as Window);
  expect(media.matchMedia.mock.calls.flat()).toEqual([
    "(prefers-reduced-motion: reduce)", "(hover: hover)", "(pointer: fine)", "(pointer: coarse)"
  ]);
  expect(source.read()).toEqual({ reducedMotion: false, hoverCapability: "none", pointerPrecision: "none" });
  media.change("(pointer: coarse)", true);
  expect(source.read().pointerPrecision).toBe("coarse");
  media.change("(pointer: fine)", true);
  media.change("(hover: hover)", true);
  media.change("(prefers-reduced-motion: reduce)", true);
  expect(source.read()).toEqual({ reducedMotion: true, hoverCapability: "hover", pointerPrecision: "fine" });
});

test("live changes notify one subscription set, disposal silences events, and reads do not depend on event history", () => {
  const media = mediaFixture();
  const source = createCapabilitySource(media as unknown as Window);
  const notify = vi.fn();
  const dispose = source.subscribe(notify);
  expect(source.subscribe(notify)).toBe(dispose);
  for (const query of media.queries.keys()) {
    media.change(query, true);
    media.change(query, false);
  }
  expect(notify).toHaveBeenCalledTimes(8);
  expect(source.read()).toEqual({ reducedMotion: false, hoverCapability: "none", pointerPrecision: "none" });
  dispose(); dispose();
  media.change("(prefers-reduced-motion: reduce)", true);
  expect(notify).toHaveBeenCalledTimes(8);
  const resumed = source.subscribe(notify);
  expect(source.read().reducedMotion).toBe(true);
  dispose();
  media.change("(hover: hover)", true);
  expect(notify).toHaveBeenCalledTimes(9);
  resumed();
  for (const query of media.queries.keys()) media.change(query, false);
  expect(notify).toHaveBeenCalledTimes(9);
});
