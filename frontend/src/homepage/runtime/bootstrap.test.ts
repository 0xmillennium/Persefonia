import { beforeEach, expect, test, vi } from "vitest";
import { bootstrapHomepageRuntime } from "./bootstrap";

const pixi = vi.hoisted(() => ({
  init: vi.fn(),
  destroy: vi.fn(),
  canvas: { setAttribute: vi.fn() }
}));

vi.mock("pixi.js", () => ({
  Application: class {
    init = pixi.init;
    destroy = pixi.destroy;
    canvas = pixi.canvas;
  }
}));
vi.mock("pixi.js/unsafe-eval", () => ({}));

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
  const mount = { append } as unknown as HTMLElement;

  const dispose = await bootstrapHomepageRuntime(mount);
  expect(pixi.init).toHaveBeenCalledWith({
    preference: ["webgl"], width: 1, height: 1, autoStart: false, backgroundAlpha: 0
  });
  expect(pixi.canvas.setAttribute).toHaveBeenCalledWith("aria-hidden", "true");
  expect(append).toHaveBeenCalledWith(pixi.canvas);
  expect(dispose).toBeTypeOf("function");
  dispose?.();
  dispose?.();
  expect(pixi.destroy).toHaveBeenCalledWith(true);
  expect(pixi.destroy).toHaveBeenCalledTimes(1);
});

test("a candidate renderer failure leaves the mount untouched", async () => {
  pixi.init.mockRejectedValueOnce(new Error("WebGL unavailable"));
  const append = vi.fn();
  const mount = { append } as unknown as HTMLElement;
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
