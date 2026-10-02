import { beforeEach, expect, test, vi } from "vitest";
import { bootstrapHomepageRuntime } from "./bootstrap";
import { mediaFixture, pointerEvent } from "./browser-test-support";
import { renderSpatialPrototype } from "./spatial-prototype";
import { deriveNarrativeState } from "../narrative/derive-narrative-state";
import { deriveSpatialSceneModel } from "../spatial/scene-model";

const pixi = vi.hoisted(() => ({
  init: vi.fn(), destroy: vi.fn(), resize: vi.fn(), rendererDestroy: vi.fn(),
  canvas: { setAttribute: vi.fn(), style: { pointerEvents: "" }, remove: vi.fn(), parentElement: null as unknown }
}));
vi.mock("pixi.js", () => ({
  Application: class {
    init = pixi.init;
    destroy = pixi.destroy;
    renderer = { resize: pixi.resize, destroy: pixi.rendererDestroy };
    canvas = pixi.canvas;
  }
}));
vi.mock("pixi.js/unsafe-eval", () => ({}));
vi.mock("./spatial-prototype", () => ({ renderSpatialPrototype: vi.fn() }));

function fixture(attributes: Record<string, string> = {}) {
  const frames = new Map<number, FrameRequestCallback>();
  let frameId = 0;
  const media = mediaFixture();
  const document = Object.assign(new EventTarget(), { documentElement: { scrollHeight: 2000 } });
  const view = Object.assign(new EventTarget(), {
    innerWidth: 656, innerHeight: 1000, devicePixelRatio: 2, scrollY: 0,
    document, matchMedia: media.matchMedia,
    requestAnimationFrame: vi.fn((callback: FrameRequestCallback) => {
      frames.set(++frameId, callback);
      return frameId;
    }),
    cancelAnimationFrame: vi.fn((id: number) => frames.delete(id))
  });
  const mount = {
    ownerDocument: { defaultView: view },
    getAttribute: (name: string) => attributes[name] ?? null,
    setAttribute: vi.fn((name: string, value: string) => { attributes[name] = value; }),
    removeAttribute: vi.fn((name: string) => { delete attributes[name]; }),
    append: vi.fn(() => { pixi.canvas.parentElement = mount; })
  } as unknown as HTMLElement;
  const flush = () => {
    const pending = [...frames.values()];
    frames.clear();
    pending.forEach(callback => callback(0));
  };
  const transition = (type: string, persisted: boolean) => view.dispatchEvent(Object.assign(new Event(type), { persisted }));
  return { view, mount, frames, flush, transition, attributes, media };
}
const sceneAt = (progress: number) => deriveSpatialSceneModel(deriveNarrativeState(progress));
const frameAt = (progress: number, width: number, height: number) => expect.objectContaining({
  narrative: deriveNarrativeState(progress), spatial: sceneAt(progress), surface: { width, height }
});
const latestFrame = () => vi.mocked(renderSpatialPrototype).mock.calls.at(-1)![1];

beforeEach(() => { vi.resetAllMocks(); pixi.canvas.parentElement = null; pixi.canvas.style.pointerEvents = ""; });

test("a homepage without a runtime mount keeps its semantic page untouched", async () => {
  expect(await bootstrapHomepageRuntime(null)).toBeUndefined();
  expect(pixi.init).not.toHaveBeenCalled();
});

test.each<Record<string, string>>([{}, { "data-homepage-prototype-mode": "foo" }])("absent and unknown modes safely render fixed origin", async attributes => {
  const { mount } = fixture(attributes);
  const dispose = await bootstrapHomepageRuntime(mount);
  expect(renderSpatialPrototype).toHaveBeenCalledWith(expect.anything(), frameAt(0, 640, 358));
  expect(pixi.canvas.setAttribute).toHaveBeenCalledWith("aria-hidden", "true");
  expect(pixi.canvas.setAttribute.mock.calls).toEqual([["aria-hidden", "true"]]);
  expect(pixi.canvas.style.pointerEvents).toBe("none");
  dispose?.(); dispose?.();
  expect(pixi.destroy).toHaveBeenCalledTimes(1);
  expect(pixi.canvas.remove).toHaveBeenCalledTimes(1);
});

test("fixed progress stays authoritative through native scroll and resize", async () => {
  const f = fixture({ "data-homepage-prototype-progress": "0.58" });
  const dispose = await bootstrapHomepageRuntime(f.mount);
  f.view.scrollY = 800;
  f.view.dispatchEvent(new Event("scroll"));
  expect(f.frames.size).toBe(0);
  f.view.innerWidth = 390;
  f.view.dispatchEvent(new Event("resize"));
  f.flush();
  expect(renderSpatialPrototype).toHaveBeenLastCalledWith(expect.anything(), frameAt(.58, 374, 209));
  dispose?.();
});

test("native mode first renders current nonzero scroll and overrides invalid fixed input", async () => {
  const f = fixture({ "data-homepage-prototype-mode": "native", "data-homepage-prototype-progress": "NaN" });
  f.view.scrollY = 600;
  const dispose = await bootstrapHomepageRuntime(f.mount);
  expect(renderSpatialPrototype).toHaveBeenCalledTimes(1);
  expect(renderSpatialPrototype).toHaveBeenCalledWith(expect.anything(), frameAt(.6, 640, 358));
  expect(f.attributes["data-homepage-runtime-progress"]).toBe("0.6");
  dispose?.();
});

test("scroll notifications coalesce into the latest current scene, including backscroll and resize", async () => {
  const f = fixture({ "data-homepage-prototype-mode": "native", "data-homepage-prototype-progress": "0.10" });
  const dispose = await bootstrapHomepageRuntime(f.mount);
  f.view.scrollY = 300;
  f.view.dispatchEvent(new Event("scroll"));
  f.view.scrollY = 800;
  f.view.dispatchEvent(new Event("scroll"));
  expect(f.view.requestAnimationFrame).toHaveBeenCalledTimes(1);
  expect(renderSpatialPrototype).toHaveBeenCalledTimes(1);
  f.flush();
  expect(renderSpatialPrototype).toHaveBeenLastCalledWith(expect.anything(), frameAt(.8, 640, 358));
  const later = vi.mocked(renderSpatialPrototype).mock.calls.at(-1)![1].spatial;
  f.view.scrollY = 300;
  f.view.dispatchEvent(new Event("scroll"));
  f.flush();
  const earlier = vi.mocked(renderSpatialPrototype).mock.calls.at(-1)![1].spatial;
  expect(earlier).toEqual(sceneAt(.3));
  expect(earlier.ariadne.points.length).toBeLessThan(later.ariadne.points.length);
  expect(earlier.navigator.position).not.toEqual(later.navigator.position);
  expect(f.attributes["data-homepage-runtime-active-region"]).toBe("writing");
  f.view.innerWidth = 390;
  f.view.innerHeight = 500;
  f.view.document.documentElement.scrollHeight = 2500;
  f.view.dispatchEvent(new Event("resize"));
  f.flush();
  expect(pixi.resize).toHaveBeenLastCalledWith(374, 209);
  expect(renderSpatialPrototype).toHaveBeenLastCalledWith(expect.anything(), frameAt(.15, 374, 209));
  dispose?.();
});

test("duplicate bootstrap and persisted restore retain one application and one subscription set", async () => {
  const f = fixture({ "data-homepage-prototype-mode": "native" });
  const add = vi.spyOn(f.view, "addEventListener");
  const remove = vi.spyOn(f.view, "removeEventListener");
  const boot = bootstrapHomepageRuntime(f.mount);
  expect(bootstrapHomepageRuntime(f.mount)).toBe(boot);
  const dispose = await boot;
  expect(await bootstrapHomepageRuntime(f.mount)).toBe(dispose);
  f.view.dispatchEvent(new Event("scroll"));
  f.transition("pagehide", true);
  expect(f.frames.size).toBe(0);
  expect(pixi.destroy).not.toHaveBeenCalled();
  f.view.scrollY = 600;
  f.view.innerWidth = 390;
  f.view.dispatchEvent(new Event("scroll"));
  expect(f.frames.size).toBe(0);
  f.transition("pageshow", true);
  f.transition("pageshow", true);
  expect(renderSpatialPrototype).toHaveBeenCalledTimes(2);
  expect(renderSpatialPrototype).toHaveBeenLastCalledWith(expect.anything(), frameAt(.6, 374, 209));
  expect(pixi.init).toHaveBeenCalledTimes(1);
  expect(f.mount.append).toHaveBeenCalledTimes(1);
  for (const type of ["scroll", "resize", "pagehide", "pageshow"]) {
    expect(add.mock.calls.filter(([event]) => event === type).length -
      remove.mock.calls.filter(([event]) => event === type).length).toBe(1);
  }
  f.view.scrollY = 700;
  f.view.dispatchEvent(new Event("scroll"));
  f.flush();
  expect(renderSpatialPrototype).toHaveBeenLastCalledWith(expect.anything(), frameAt(.7, 374, 209));
  f.view.dispatchEvent(new Event("scroll"));
  f.transition("pagehide", false);
  dispose?.();
  expect(f.frames.size).toBe(0);
  expect(pixi.destroy).toHaveBeenCalledTimes(1);
  expect(pixi.canvas.remove).toHaveBeenCalledTimes(1);
  for (const type of ["scroll", "resize", "pagehide", "pageshow"]) {
    expect(add.mock.calls.filter(([event]) => event === type).length).toBe(
      remove.mock.calls.filter(([event]) => event === type).length);
  }
  expect(f.attributes["data-homepage-runtime-ready"]).toBeUndefined();
});

test("failure after Pixi initialization releases artifacts and subscriptions without scheduling work", async () => {
  const f = fixture({ "data-homepage-prototype-mode": "native" });
  const add = vi.spyOn(f.view, "addEventListener");
  const remove = vi.spyOn(f.view, "removeEventListener");
  vi.mocked(renderSpatialPrototype).mockImplementationOnce(() => { throw new Error("render failed"); });
  const error = vi.spyOn(console, "error").mockImplementation(() => {});
  try {
    expect(await bootstrapHomepageRuntime(f.mount)).toBeUndefined();
    expect(pixi.init).toHaveBeenCalledTimes(1);
    expect(pixi.destroy).toHaveBeenCalledWith(true, { children: true });
    expect(pixi.destroy).toHaveBeenCalledTimes(1);
    expect(pixi.canvas.remove).toHaveBeenCalledTimes(1);
    expect(f.mount.append).toHaveBeenCalledTimes(1);
    expect(f.frames.size).toBe(0);
    expect(f.view.requestAnimationFrame).not.toHaveBeenCalled();
    for (const [type, listener] of add.mock.calls) expect(remove).toHaveBeenCalledWith(type, listener);
    f.view.dispatchEvent(new Event("scroll"));
    f.transition("pageshow", true);
    expect(renderSpatialPrototype).toHaveBeenCalledTimes(1);
    expect(f.attributes["data-homepage-runtime-ready"]).toBeUndefined();
  } finally { error.mockRestore(); }
});

test("initialization failure cleans a partially acquired renderer and leaves the mount untouched", async () => {
  const f = fixture();
  pixi.init.mockRejectedValueOnce(new Error("WebGL unavailable"));
  const error = vi.spyOn(console, "error").mockImplementation(() => {});
  try {
    expect(await bootstrapHomepageRuntime(f.mount)).toBeUndefined();
    expect(f.mount.append).not.toHaveBeenCalled();
    expect(pixi.destroy).not.toHaveBeenCalled();
    expect(pixi.rendererDestroy).toHaveBeenCalledWith(true);
  } finally { error.mockRestore(); }
});

test.each(["NaN", "", "Infinity"])("invalid fixed progress %s fails before renderer initialization", async value => {
  const f = fixture({ "data-homepage-prototype-progress": value });
  const error = vi.spyOn(console, "error").mockImplementation(() => {});
  try {
    expect(await bootstrapHomepageRuntime(f.mount)).toBeUndefined();
    expect(pixi.init).not.toHaveBeenCalled();
    expect(f.mount.append).not.toHaveBeenCalled();
    expect(error).toHaveBeenCalledWith("Homepage candidate runtime could not initialize", expect.any(RangeError));
  } finally { error.mockRestore(); }
});

test.each([true, false])("navigation during asynchronous Pixi initialization respects persisted=%s ownership", async persisted => {
  const f = fixture({ "data-homepage-prototype-mode": "native" });
  let finishInit: (() => void) | undefined;
  pixi.init.mockImplementationOnce(() => new Promise<void>(resolve => { finishInit = resolve; }));
  const boot = bootstrapHomepageRuntime(f.mount);
  await vi.waitFor(() => expect(finishInit).toBeTypeOf("function"));
  f.transition("pagehide", persisted);
  finishInit!();
  const dispose = await boot;
  expect(renderSpatialPrototype).not.toHaveBeenCalled();
  if (persisted) {
    expect(pixi.destroy).not.toHaveBeenCalled();
    f.view.scrollY = 600;
    f.transition("pageshow", true);
    expect(renderSpatialPrototype).toHaveBeenCalledWith(expect.anything(), frameAt(.6, 640, 358));
    dispose?.();
  } else {
    expect(dispose).toBeUndefined();
  }
  expect(pixi.destroy).toHaveBeenCalledTimes(1);
  expect(f.frames.size).toBe(0);
});

test("a live render failure cancels work, removes the canvas, and stops subsequent updates", async () => {
  const f = fixture({ "data-homepage-prototype-mode": "native" });
  const addDocument = vi.spyOn(f.view.document, "addEventListener");
  const removeDocument = vi.spyOn(f.view.document, "removeEventListener");
  const dispose = await bootstrapHomepageRuntime(f.mount);
  vi.mocked(renderSpatialPrototype).mockImplementationOnce(() => { throw new Error("refresh failed"); });
  const error = vi.spyOn(console, "error").mockImplementation(() => {});
  try {
    f.view.dispatchEvent(new Event("scroll"));
    f.flush();
    f.view.dispatchEvent(new Event("scroll"));
    expect(f.frames.size).toBe(0);
    expect(pixi.destroy).toHaveBeenCalledTimes(1);
    expect(pixi.canvas.remove).toHaveBeenCalledTimes(1);
    expect(f.attributes["data-homepage-runtime-ready"]).toBeUndefined();
    for (const [type, listener] of addDocument.mock.calls) expect(removeDocument).toHaveBeenCalledWith(type, listener);
    for (const query of f.media.queries.keys()) f.media.change(query, true);
    f.view.document.dispatchEvent(pointerEvent("pointermove"));
    f.view.dispatchEvent(new Event("blur"));
    expect(f.frames.size).toBe(0);
    expect(Object.keys(f.attributes).filter(name => name.startsWith("data-homepage-runtime-"))).toEqual([]);
    dispose?.();
    expect(pixi.destroy).toHaveBeenCalledTimes(1);
  } finally { error.mockRestore(); }
});

test("mixed scroll, pointer, media and resize burst renders one frame with every latest current input", async () => {
  const f = fixture({ "data-homepage-prototype-mode": "native" });
  const dispose = await bootstrapHomepageRuntime(f.mount);
  f.view.scrollY = 400;
  f.view.dispatchEvent(new Event("scroll"));
  f.view.document.dispatchEvent(pointerEvent("pointermove", { clientX: 100 }));
  f.view.document.dispatchEvent(pointerEvent("pointermove", { clientX: 300, clientY: 200 }));
  f.media.change("(prefers-reduced-motion: reduce)", true);
  f.media.change("(hover: hover)", true);
  f.media.change("(pointer: coarse)", true);
  f.view.innerWidth = 1000;
  f.view.innerHeight = 500;
  f.view.devicePixelRatio = 3;
  f.view.scrollY = 600;
  f.view.dispatchEvent(new Event("resize"));
  expect(f.frames.size).toBe(1);
  expect(f.view.requestAnimationFrame).toHaveBeenCalledTimes(1);
  expect(renderSpatialPrototype).toHaveBeenCalledTimes(1);
  f.flush();
  expect(renderSpatialPrototype).toHaveBeenCalledTimes(2);
  expect(latestFrame()).toEqual({
    narrative: deriveNarrativeState(.4), spatial: sceneAt(.4), surface: { width: 640, height: 358 },
    attention: { active: true, modality: "pointer", normalizedX: .3, normalizedY: .4 },
    viewport: { width: 1000, height: 500, aspectRatio: 2, devicePixelRatio: 3, orientation: "landscape" },
    capabilities: { reducedMotion: true, hoverCapability: "hover", pointerPrecision: "coarse" },
    presentation: { composition: "wide", motion: "reduced" }
  });
  f.media.change("(prefers-reduced-motion: reduce)", false);
  f.media.change("(hover: hover)", false);
  f.media.change("(pointer: coarse)", false);
  f.media.change("(pointer: fine)", true);
  f.flush();
  expect(latestFrame().capabilities).toEqual({ reducedMotion: false, hoverCapability: "none", pointerPrecision: "fine" });
  expect(latestFrame().narrative).toEqual(deriveNarrativeState(.4));
  expect(latestFrame().spatial).toEqual(sceneAt(.4));
  dispose?.();
});

test("fixed progress survives pointer, touch, composition and motion changes; resize renormalizes active raw attention", async () => {
  const f = fixture({ "data-homepage-prototype-progress": "0.580" });
  f.view.innerWidth = 1000;
  const dispose = await bootstrapHomepageRuntime(f.mount);
  const baseline = latestFrame();
  const unchangedJourney = () => {
    expect(latestFrame().narrative).toEqual(baseline.narrative);
    expect(latestFrame().spatial).toEqual(baseline.spatial);
  };
  f.view.document.dispatchEvent(pointerEvent("pointermove", { clientX: 300, clientY: 250 }));
  f.flush();
  unchangedJourney();
  f.view.innerWidth = 600;
  f.view.innerHeight = 500;
  f.view.dispatchEvent(new Event("resize"));
  f.flush();
  unchangedJourney();
  expect(latestFrame().attention).toEqual({ active: true, modality: "pointer", normalizedX: .5, normalizedY: .5 });
  expect(latestFrame().presentation).toEqual({ composition: "compact", motion: "full" });
  f.media.change("(prefers-reduced-motion: reduce)", true);
  f.flush();
  unchangedJourney();
  expect(latestFrame().presentation).toEqual({ composition: "compact", motion: "reduced" });
  const touch = pointerEvent("pointerdown", { pointerType: "touch", pointerId: 5, clientX: 150 });
  f.view.document.dispatchEvent(touch);
  f.flush();
  unchangedJourney();
  expect(touch.defaultPrevented).toBe(false);
  expect(latestFrame().attention.modality).toBe("touch");
  f.view.document.dispatchEvent(pointerEvent("pointerup", { pointerType: "touch", pointerId: 5 }));
  f.flush();
  unchangedJourney();
  expect(latestFrame().attention).toEqual({ active: false, modality: "none" });
  expect(f.attributes["data-homepage-runtime-attention-x"]).toBeUndefined();
  expect(f.attributes["data-homepage-runtime-attention-y"]).toBeUndefined();
  dispose?.();
  expect(Object.keys(f.attributes).filter(name => name.startsWith("data-homepage-runtime-"))).toEqual([]);
});

test("BFCache resets attention, suspends all input subscriptions, and resumes current capabilities changed while suspended exactly once", async () => {
  const f = fixture({ "data-homepage-prototype-mode": "native" });
  const addView = vi.spyOn(f.view, "addEventListener");
  const removeView = vi.spyOn(f.view, "removeEventListener");
  const addDocument = vi.spyOn(f.view.document, "addEventListener");
  const removeDocument = vi.spyOn(f.view.document, "removeEventListener");
  const dispose = await bootstrapHomepageRuntime(f.mount);
  const mediaListeners = [...f.media.queries.values()].map(query => ({
    add: vi.spyOn(query, "addEventListener"), remove: vi.spyOn(query, "removeEventListener")
  }));
  f.view.document.dispatchEvent(pointerEvent("pointermove"));
  f.flush();
  expect(latestFrame().attention.active).toBe(true);
  for (let cycle = 0; cycle < 2; cycle++) {
    f.view.document.dispatchEvent(pointerEvent("pointermove"));
    f.transition("pagehide", true);
    f.transition("pagehide", true);
    expect(f.frames.size).toBe(0);
    expect(f.attributes["data-homepage-runtime-attention-active"]).toBe("false");
    expect(f.attributes["data-homepage-runtime-attention-x"]).toBeUndefined();
    expect(f.attributes["data-homepage-runtime-attention-y"]).toBeUndefined();
    for (const { add, remove } of mediaListeners) expect(remove.mock.calls.length - add.mock.calls.length).toBe(1);
    for (const type of ["scroll", "resize", "blur"]) {
      expect(addView.mock.calls.filter(([event]) => event === type).length).toBe(
        removeView.mock.calls.filter(([event]) => event === type).length);
    }
    expect(addDocument.mock.calls.length).toBe(removeDocument.mock.calls.length);
    f.view.innerWidth = 800;
    f.view.innerHeight = 500;
    f.view.devicePixelRatio = 4;
    f.view.scrollY = 600;
    f.media.change("(prefers-reduced-motion: reduce)", true);
    f.media.change("(pointer: coarse)", true);
    f.view.document.dispatchEvent(pointerEvent("pointermove"));
    f.view.dispatchEvent(new Event("scroll"));
    f.view.dispatchEvent(new Event("resize"));
    expect(f.frames.size).toBe(0);
    f.transition("pageshow", true);
    f.transition("pageshow", true);
    expect(latestFrame()).toEqual(expect.objectContaining({
      narrative: deriveNarrativeState(.4), spatial: sceneAt(.4),
      attention: { active: false, modality: "none" },
      viewport: { width: 800, height: 500, devicePixelRatio: 4, aspectRatio: 1.6, orientation: "landscape" },
      capabilities: { reducedMotion: true, hoverCapability: "none", pointerPrecision: "coarse" },
      presentation: { composition: "wide", motion: "reduced" }
    }));
    for (const type of ["scroll", "resize", "blur", "pagehide", "pageshow"]) {
      expect(addView.mock.calls.filter(([event]) => event === type).length -
        removeView.mock.calls.filter(([event]) => event === type).length).toBe(1);
    }
    for (const type of ["pointerdown", "pointermove", "pointerup", "pointercancel", "pointerout"]) {
      expect(addDocument.mock.calls.filter(([event]) => event === type).length -
        removeDocument.mock.calls.filter(([event]) => event === type).length).toBe(1);
    }
    for (const { add, remove } of mediaListeners) expect(add.mock.calls.length).toBe(remove.mock.calls.length);
    expect(pixi.init).toHaveBeenCalledTimes(1);
    expect(f.mount.append).toHaveBeenCalledTimes(1);
  }
  dispose?.();
  expect(addView.mock.calls.length).toBe(removeView.mock.calls.length);
  expect(addDocument.mock.calls.length).toBe(removeDocument.mock.calls.length);
  for (const { add, remove } of mediaListeners) expect(remove.mock.calls.length - add.mock.calls.length).toBe(1);
  for (const query of f.media.queries.keys()) f.media.change(query, false);
  f.view.document.dispatchEvent(pointerEvent("pointermove"));
  expect(f.frames.size).toBe(0);
  expect(Object.keys(f.attributes).filter(name => name.startsWith("data-homepage-runtime-"))).toEqual([]);
});

test("a failed restored frame permanently cleans diagnostics and cannot reinstall input subscriptions", async () => {
  const f = fixture({ "data-homepage-prototype-mode": "native" });
  const dispose = await bootstrapHomepageRuntime(f.mount);
  f.view.document.dispatchEvent(pointerEvent("pointermove"));
  f.flush();
  f.transition("pagehide", true);
  vi.mocked(renderSpatialPrototype).mockImplementationOnce(() => { throw new Error("restore failed"); });
  const error = vi.spyOn(console, "error").mockImplementation(() => {});
  try {
    f.transition("pageshow", true);
    f.transition("pageshow", true);
    for (const query of f.media.queries.keys()) f.media.change(query, true);
    f.view.document.dispatchEvent(pointerEvent("pointermove"));
    f.view.dispatchEvent(new Event("resize"));
    f.view.dispatchEvent(new Event("scroll"));
    expect(f.frames.size).toBe(0);
    expect(renderSpatialPrototype).toHaveBeenCalledTimes(3);
    expect(pixi.destroy).toHaveBeenCalledTimes(1);
    expect(Object.keys(f.attributes).filter(name => name.startsWith("data-homepage-runtime-"))).toEqual([]);
    dispose?.();
    expect(pixi.destroy).toHaveBeenCalledTimes(1);
  } finally { error.mockRestore(); }
});

test("native measurement includes the document geometry produced by sizing an in-flow prototype", async () => {
  const f = fixture({ "data-homepage-prototype-mode": "native" });
  f.view.scrollY = 600;
  pixi.resize.mockImplementation((_width: number, height: number) => {
    f.view.document.documentElement.scrollHeight = 2000 + height;
  });
  const dispose = await bootstrapHomepageRuntime(f.mount);
  expect(renderSpatialPrototype).toHaveBeenCalledTimes(1);
  expect(renderSpatialPrototype).toHaveBeenCalledWith(expect.anything(), frameAt(600 / 1358, 640, 358));
  f.view.innerWidth = 390;
  f.view.innerHeight = 500;
  f.view.dispatchEvent(new Event("resize"));
  f.flush();
  expect(renderSpatialPrototype).toHaveBeenLastCalledWith(expect.anything(), frameAt(600 / 1709, 374, 209));
  dispose?.();
});
