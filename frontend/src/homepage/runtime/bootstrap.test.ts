import { beforeEach, expect, test, vi } from "vitest";
import { bootstrapHomepageRuntime } from "./bootstrap";
import { renderSpatialPrototype } from "./spatial-prototype";
import { deriveNarrativeState } from "../narrative/derive-narrative-state";
import { deriveSpatialSceneModel } from "../spatial/scene-model";

const pixi = vi.hoisted(() => ({
  init: vi.fn(), destroy: vi.fn(), resize: vi.fn(), rendererDestroy: vi.fn(),
  canvas: { setAttribute: vi.fn(), remove: vi.fn(), parentElement: null as unknown }
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
  const view = Object.assign(new EventTarget(), {
    innerWidth: 656, innerHeight: 1000, scrollY: 0,
    document: { documentElement: { scrollHeight: 2000 } },
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
  return { view, mount, frames, flush, transition, attributes };
}
const sceneAt = (progress: number) => deriveSpatialSceneModel(deriveNarrativeState(progress));

beforeEach(() => { vi.resetAllMocks(); pixi.canvas.parentElement = null; });

test("a homepage without a runtime mount keeps its semantic page untouched", async () => {
  expect(await bootstrapHomepageRuntime(null)).toBeUndefined();
  expect(pixi.init).not.toHaveBeenCalled();
});

test.each<Record<string, string>>([{}, { "data-homepage-prototype-mode": "foo" }])("absent and unknown modes safely render fixed origin", async attributes => {
  const { mount } = fixture(attributes);
  const dispose = await bootstrapHomepageRuntime(mount);
  expect(renderSpatialPrototype).toHaveBeenCalledWith(expect.anything(), sceneAt(0), 640, 358);
  expect(pixi.canvas.setAttribute).toHaveBeenCalledWith("aria-hidden", "true");
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
  expect(renderSpatialPrototype).toHaveBeenLastCalledWith(expect.anything(), sceneAt(.58), 374, 209);
  dispose?.();
});

test("native mode first renders current nonzero scroll and overrides invalid fixed input", async () => {
  const f = fixture({ "data-homepage-prototype-mode": "native", "data-homepage-prototype-progress": "NaN" });
  f.view.scrollY = 600;
  const dispose = await bootstrapHomepageRuntime(f.mount);
  expect(renderSpatialPrototype).toHaveBeenCalledTimes(1);
  expect(renderSpatialPrototype).toHaveBeenCalledWith(expect.anything(), sceneAt(.6), 640, 358);
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
  expect(renderSpatialPrototype).toHaveBeenLastCalledWith(expect.anything(), sceneAt(.8), 640, 358);
  const later = vi.mocked(renderSpatialPrototype).mock.calls.at(-1)![1];
  f.view.scrollY = 300;
  f.view.dispatchEvent(new Event("scroll"));
  f.flush();
  const earlier = vi.mocked(renderSpatialPrototype).mock.calls.at(-1)![1];
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
  expect(renderSpatialPrototype).toHaveBeenLastCalledWith(expect.anything(), sceneAt(.15), 374, 209);
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
  expect(renderSpatialPrototype).toHaveBeenLastCalledWith(expect.anything(), sceneAt(.6), 374, 209);
  expect(pixi.init).toHaveBeenCalledTimes(1);
  expect(f.mount.append).toHaveBeenCalledTimes(1);
  for (const type of ["scroll", "resize", "pagehide", "pageshow"]) {
    expect(add.mock.calls.filter(([event]) => event === type).length -
      remove.mock.calls.filter(([event]) => event === type).length).toBe(1);
  }
  f.view.scrollY = 700;
  f.view.dispatchEvent(new Event("scroll"));
  f.flush();
  expect(renderSpatialPrototype).toHaveBeenLastCalledWith(expect.anything(), sceneAt(.7), 374, 209);
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
    expect(renderSpatialPrototype).toHaveBeenCalledWith(expect.anything(), sceneAt(.6), 640, 358);
    dispose?.();
  } else {
    expect(dispose).toBeUndefined();
  }
  expect(pixi.destroy).toHaveBeenCalledTimes(1);
  expect(f.frames.size).toBe(0);
});

test("a live render failure cancels work, removes the canvas, and stops subsequent updates", async () => {
  const f = fixture({ "data-homepage-prototype-mode": "native" });
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
  expect(renderSpatialPrototype).toHaveBeenCalledWith(expect.anything(), sceneAt(600 / 1358), 640, 358);
  f.view.innerWidth = 390;
  f.view.innerHeight = 500;
  f.view.dispatchEvent(new Event("resize"));
  f.flush();
  expect(renderSpatialPrototype).toHaveBeenLastCalledWith(expect.anything(), sceneAt(600 / 1709), 374, 209);
  dispose?.();
});
