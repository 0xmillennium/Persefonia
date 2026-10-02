import { expect, test, vi } from "vitest";
import { createAttentionSource } from "./attention";
import { pointerEvent } from "./browser-test-support";

const inactive = { active: false, modality: "none" };
function fixture() {
  const document = new EventTarget();
  const view = Object.assign(new EventTarget(), { document }) as unknown as Window;
  const source = createAttentionSource(view);
  const notify = vi.fn();
  const dispose = source.subscribe(notify);
  const read = () => source.read({ width: 800, height: 400 });
  const send = (type: string, values: Partial<PointerEvent> = {}) => {
    const event = pointerEvent(type, values);
    document.dispatchEvent(event);
    expect(event.defaultPrevented).toBe(false);
  };
  return { document, view, source, notify, dispose, read, send };
}

test.each(["mouse", "pen"])("primary %s movement supplies pointer attention without canceling default interaction", pointerType => {
  const f = fixture();
  expect(f.read()).toEqual(inactive);
  f.send("pointermove", { pointerType, isPrimary: false });
  expect(f.read()).toEqual(inactive);
  f.send("pointermove", { pointerType });
  expect(f.read()).toEqual({ active: true, modality: "pointer", normalizedX: .25, normalizedY: .25 });
  // Current viewport authority is independent of the event-time dimensions.
  expect(f.source.read({ width: 400, height: 200 })).toEqual({ active: true, modality: "pointer", normalizedX: .5, normalizedY: .5 });
  f.send("pointermove", { pointerType, clientX: -100, clientY: 500 });
  expect(f.read()).toEqual({ active: true, modality: "pointer", normalizedX: 0, normalizedY: 1 });
  f.send("pointermove", { pointerType, clientX: NaN, clientY: Infinity });
  expect(f.read()).toEqual({ active: true, modality: "pointer", normalizedX: 0, normalizedY: 0 });
  f.dispose();
});

test.each(["pointerup", "pointercancel"])("primary touch owns temporary attention until %s and secondary contacts cannot steal or clear it", end => {
  const f = fixture();
  f.send("pointermove", { pointerType: "touch" });
  expect(f.read()).toEqual(inactive);
  f.send("pointerdown", { pointerType: "touch", pointerId: 2, isPrimary: false });
  expect(f.read()).toEqual(inactive);
  f.send("pointerdown", { pointerType: "touch" });
  f.send("pointerdown", { pointerType: "touch", pointerId: 2, isPrimary: false, clientX: 800 });
  f.send("pointermove", { pointerType: "touch", pointerId: 2, isPrimary: false, clientX: 800 });
  f.send(end, { pointerType: "touch", pointerId: 2, isPrimary: false });
  f.send("pointermove", { pointerId: 3, clientX: 800 });
  expect(f.read()).toEqual({ active: true, modality: "touch", normalizedX: .25, normalizedY: .25 });
  f.send("pointermove", { pointerType: "touch", clientX: 400, clientY: 200 });
  expect(f.read()).toEqual({ active: true, modality: "touch", normalizedX: .5, normalizedY: .5 });
  f.send(end, { pointerType: "touch" });
  expect(f.read()).toEqual(inactive);
  f.dispose();
});

test("document exit, cancellation, blur and explicit reset remove coordinates", () => {
  const f = fixture();
  f.send("pointermove");
  f.send("pointerout", { relatedTarget: new EventTarget() });
  expect(f.read().active).toBe(true);
  f.send("pointerout", { pointerType: "touch", pointerId: 2, isPrimary: false });
  expect(f.read().active).toBe(true);
  f.send("pointerup");
  expect(f.read().active).toBe(true);
  f.send("pointerout");
  expect(f.read()).toEqual(inactive);
  f.send("pointermove");
  f.send("pointercancel");
  expect(f.read()).toEqual(inactive);
  f.send("pointermove");
  f.view.dispatchEvent(new Event("blur"));
  expect(f.read()).toEqual(inactive);
  f.send("pointermove");
  f.source.reset();
  expect(f.read()).toEqual(inactive);
  f.dispose();
});

test("subscriptions are passive, idempotent, disposable and restart inactive", () => {
  const document = new EventTarget();
  const view = Object.assign(new EventTarget(), { document }) as unknown as Window;
  const add = vi.spyOn(document, "addEventListener");
  const remove = vi.spyOn(document, "removeEventListener");
  const source = createAttentionSource(view);
  const notify = vi.fn();
  const dispose = source.subscribe(notify);
  expect(source.subscribe(notify)).toBe(dispose);
  for (const [, , options] of add.mock.calls) expect(options).toEqual({ passive: true });
  document.dispatchEvent(pointerEvent("pointermove"));
  dispose(); dispose();
  document.dispatchEvent(pointerEvent("pointermove"));
  view.dispatchEvent(new Event("blur"));
  expect(notify).toHaveBeenCalledTimes(1);
  expect(source.read({ width: 800, height: 400 })).toEqual(inactive);
  for (const [type, listener] of add.mock.calls) expect(remove).toHaveBeenCalledWith(type, listener);
  const resumed = source.subscribe(notify);
  dispose();
  document.dispatchEvent(pointerEvent("pointermove"));
  expect(notify).toHaveBeenCalledTimes(2);
  resumed();
});
