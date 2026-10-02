import { expect, test, vi } from "vitest";
import { deriveDocumentScrollProgress, readDocumentScrollProgress, subscribeDocumentScroll } from "./scroll";

test.each([
  [0, 2000, 1000, 0], [500, 2000, 1000, .5], [1000, 2000, 1000, 1],
  [-100, 2000, 1000, 0], [1500, 2000, 1000, 1], [100, 1000, 1000, 0],
  [100, 500, 1000, 0], [500, 2000, 500, 1 / 3],
  [NaN, 2000, 1000, 0], [Infinity, 2000, 1000, 0], [500, Infinity, 1000, 0]
])("normalizes scroll %s with document height %s and viewport %s to %s", (scrollY, scrollHeight, viewportHeight, expected) => {
  expect(deriveDocumentScrollProgress({ scrollY, scrollHeight, viewportHeight })).toBe(expected);
});

test("reads current metrics and subscribes passively with disposable ownership", () => {
  const view = Object.assign(new EventTarget(), {
    scrollY: 300, innerHeight: 1000, document: { documentElement: { scrollHeight: 2000 } }
  }) as unknown as Window;
  const add = vi.spyOn(view, "addEventListener");
  const notify = vi.fn();
  const dispose = subscribeDocumentScroll(view, notify);
  expect(add).toHaveBeenCalledWith("scroll", notify, { passive: true });
  view.dispatchEvent(new Event("scroll"));
  expect(notify).toHaveBeenCalledTimes(1);
  expect(readDocumentScrollProgress(view)).toBe(.3);
  dispose();
  dispose();
  view.dispatchEvent(new Event("scroll"));
  expect(notify).toHaveBeenCalledTimes(1);
});
