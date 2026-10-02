import { beforeEach, expect, test, vi } from "vitest";
import { bootstrapHomepageRuntime } from "./bootstrap";
import { renderSpatialPrototype } from "./spatial-prototype";

const pixi = vi.hoisted(() => ({
  init: vi.fn(),
  destroy: vi.fn(),
  canvas: { setAttribute: vi.fn() },
  render: vi.fn()
}));

vi.mock("pixi.js", () => ({
  Application: class {
    init = pixi.init;
    destroy = pixi.destroy;
    render = pixi.render;
    canvas = pixi.canvas;
  }
}));
vi.mock("pixi.js/unsafe-eval", () => ({}));
vi.mock("./spatial-prototype", () => ({ renderSpatialPrototype: vi.fn() }));

beforeEach(() => {
  vi.clearAllMocks();
});

test("a homepage without a runtime mount keeps its semantic page untouched", async () => {
  const result = await bootstrapHomepageRuntime(null);
  expect(result).toBeUndefined();
  expect(pixi.init).not.toHaveBeenCalled();
});

test("a mounted candidate can initialize and be disposed", async () => {
  const append = vi.fn();
  const mount = { append, getAttribute: vi.fn().mockReturnValue(null), setAttribute: vi.fn(), removeAttribute: vi.fn() } as unknown as HTMLElement;

  const dispose = await bootstrapHomepageRuntime(mount);
  expect(pixi.init).toHaveBeenCalledWith({
    preference: ["webgl"], width: 640, height: 358, autoStart: false, backgroundAlpha: 0
  });
  expect(pixi.canvas.setAttribute).toHaveBeenCalledWith("aria-hidden", "true");
  expect(renderSpatialPrototype).toHaveBeenCalledWith(expect.anything(), 0, 640, 358);
  expect(append).toHaveBeenCalledWith(pixi.canvas);
  expect(dispose).toBeTypeOf("function");
  dispose?.();
  dispose?.();
  expect(pixi.destroy).toHaveBeenCalledWith(true, { children: true });
  expect(pixi.destroy).toHaveBeenCalledTimes(1);
});

test("explicit finite prototype progress reaches the spatial renderer", async () => {
  const mount = {
    append: vi.fn(), getAttribute: vi.fn().mockReturnValue("0.437"),
    setAttribute: vi.fn(), removeAttribute: vi.fn()
  } as unknown as HTMLElement;
  const dispose = await bootstrapHomepageRuntime(mount);
  expect(renderSpatialPrototype).toHaveBeenCalledWith(expect.anything(), .437, 640, 358);
  dispose?.();
});

test("a candidate renderer failure leaves the mount untouched", async () => {
  pixi.init.mockRejectedValueOnce(new Error("WebGL unavailable"));
  const append = vi.fn();
  const mount = { append, getAttribute: vi.fn().mockReturnValue(null), setAttribute: vi.fn(), removeAttribute: vi.fn() } as unknown as HTMLElement;
  const error = vi.spyOn(console, "error").mockImplementation(() => {});

  try {
    const result = await bootstrapHomepageRuntime(mount);
    expect(result).toBeUndefined();
    expect(append).not.toHaveBeenCalled();
    expect(pixi.destroy).not.toHaveBeenCalled();
    expect(error).toHaveBeenCalledWith("Homepage candidate runtime could not initialize", expect.any(Error));
  } finally {
    error.mockRestore();
  }
});

test("invalid prototype progress fails before renderer initialization", async () => {
  const append = vi.fn();
  const mount = { append, getAttribute: vi.fn().mockReturnValue("NaN") } as unknown as HTMLElement;
  const error = vi.spyOn(console, "error").mockImplementation(() => {});
  try {
    expect(await bootstrapHomepageRuntime(mount)).toBeUndefined();
    expect(pixi.init).not.toHaveBeenCalled();
    expect(append).not.toHaveBeenCalled();
    expect(error).toHaveBeenCalledWith("Homepage candidate runtime could not initialize", expect.any(RangeError));
  } finally {
    error.mockRestore();
  }
});
