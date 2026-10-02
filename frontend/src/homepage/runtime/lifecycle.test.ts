import { expect, test, vi } from "vitest";
import { subscribeHomepageLifecycle } from "./lifecycle";

const transition = (type: string, persisted: boolean): Event => Object.assign(new Event(type), { persisted });

test("persisted navigation suspends and restores once while permanent teardown removes lifecycle listeners", () => {
  const view = new EventTarget() as unknown as Window;
  const callbacks = { suspend: vi.fn(), resume: vi.fn(), destroy: vi.fn() };
  const remove = vi.spyOn(view, "removeEventListener");
  const lifecycle = subscribeHomepageLifecycle(view, callbacks);
  expect(lifecycle.state).toBe("running");
  view.dispatchEvent(transition("pageshow", false));
  expect(callbacks.resume).not.toHaveBeenCalled();
  view.dispatchEvent(transition("pagehide", true));
  view.dispatchEvent(transition("pagehide", true));
  expect(lifecycle.state).toBe("suspended");
  expect(callbacks.suspend).toHaveBeenCalledTimes(1);
  expect(callbacks.destroy).not.toHaveBeenCalled();
  view.dispatchEvent(transition("pageshow", true));
  view.dispatchEvent(transition("pageshow", true));
  expect(lifecycle.state).toBe("running");
  expect(callbacks.resume).toHaveBeenCalledTimes(1);
  view.dispatchEvent(transition("pagehide", false));
  lifecycle.destroy();
  lifecycle.resume();
  view.dispatchEvent(transition("pageshow", true));
  expect(lifecycle.state).toBe("destroyed");
  expect(callbacks.destroy).toHaveBeenCalledTimes(1);
  expect(callbacks.resume).toHaveBeenCalledTimes(1);
  expect(remove.mock.calls.map(([type]) => type)).toEqual(["pagehide", "pageshow"]);
});
