import { afterEach, expect, it, vi } from 'vitest';
import { installHoldObserver, requireFreshMask } from '../../e2e/support/hold-observer';

afterEach(() => vi.unstubAllGlobals());

it('requires the target context and a fresh action-bound upload, even within one material generation', () => {
  const state = { installed: true, context: 2, sequence: 7, action: 3, contexts: 2 };
  const sample = {
    context: 2,
    sequence: 7,
    action: 3,
    clock: 100,
    texture: 1,
    width: 192,
    height: 120,
    pixels: new Uint8Array(192 * 120 * 4),
  };
  expect(requireFreshMask({ ...state, mask: sample }, 6, 3)).toBe(sample);
  for (const change of [
    { context: 1 },
    { sequence: 6 },
    { action: 2 },
    { texture: 0 },
    { height: 0 },
  ])
    expect(() => requireFreshMask({ ...state, mask: { ...sample, ...change } }, 6, 3)).toThrow();
  expect(() => requireFreshMask(state, 6, 3)).toThrow('No fresh hold upload');
  expect(() => requireFreshMask({ ...state, context: 0 }, 6, 3)).toThrow('WebGL2');
  expect(() => requireFreshMask({ ...state, installed: false }, 6, 3)).toThrow('hook');
});

it('does not invent readiness when WebGL2 is unavailable', () => {
  vi.stubGlobal('window', {});
  vi.stubGlobal('WebGL2RenderingContext', undefined);
  installHoldObserver();
  expect(window.__holdObserver.installed).toBe(true);
  expect(() => requireFreshMask(window.__holdObserver, 0, 0)).toThrow('WebGL2');
});

it('waits for a real upload and rejects another texture, canvas and replaced context', () => {
  class Canvas {
    gl!: GL;
    target = true;
    matches() {
      return this.target;
    }
    getContext(_kind?: string) {
      return this.gl;
    }
  }
  class GL {
    ACTIVE_TEXTURE = 1;
    TEXTURE3 = 3;
    TEXTURE_2D = 4;
    TEXTURE_BINDING_2D = 5;
    VENDOR = 6;
    RENDERER = 7;
    VERSION = 8;
    CURRENT_PROGRAM = 9;
    active = 3;
    texture = {};
    program = {};
    isContextLost() {
      return false;
    }
    constructor(public canvas: Canvas) {}
    getParameter(key: number) {
      return key === this.ACTIVE_TEXTURE
        ? this.active
        : key === this.CURRENT_PROGRAM
          ? this.program
          : this.texture;
    }
    activeTexture(value: number) {
      this.active = value;
    }
    getUniformLocation(..._args: unknown[]) {
      return {};
    }
    uniform1i(..._args: unknown[]) {}
    texImage2D(..._args: unknown[]) {}
    texSubImage2D(..._args: unknown[]) {}
  }
  const canvas = new Canvas();
  canvas.gl = new GL(canvas);
  vi.stubGlobal('window', { addEventListener() {} });
  vi.stubGlobal('document', { querySelector: () => canvas });
  vi.stubGlobal('HTMLCanvasElement', Canvas);
  vi.stubGlobal('WebGL2RenderingContext', GL);
  installHoldObserver();
  const admit = (surface: Canvas) => {
    const gl = surface.getContext('webgl2');
    gl.uniform1i(gl.getUniformLocation(gl.program, 'holdField'), 3);
    return gl;
  };
  const upload = (gl: GL) =>
    gl.texImage2D(gl.TEXTURE_2D, 0, 0, 192, 120, 0, 0, 0, new Uint8Array(192 * 120 * 4));
  const gl = admit(canvas);
  const state = window.__holdObserver;
  expect(() => requireFreshMask(state, 0, 0)).toThrow('No fresh hold upload');
  upload(gl);
  expect(requireFreshMask(state, 0, 0).sequence).toBe(1);
  state.action++;
  expect(() => requireFreshMask(state, 1, 1)).toThrow('No fresh hold upload');
  const texture = gl.texture;
  gl.texture = {};
  upload(gl);
  gl.texture = texture;
  expect(state.sequence).toBe(1);
  const other = new Canvas();
  other.target = false;
  other.gl = new GL(other);
  upload(admit(other));
  expect(state.sequence).toBe(1);
  canvas.gl = new GL(canvas);
  const replacement = admit(canvas);
  expect(state.mask).toBeUndefined();
  upload(gl);
  expect(state.mask).toBeUndefined();
  upload(replacement);
  expect(requireFreshMask(state, 1, 1).context).toBe(2);
});
