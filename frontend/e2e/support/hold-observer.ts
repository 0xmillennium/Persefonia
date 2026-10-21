export type HoldMask = {
  context: number;
  texture: number;
  sequence: number;
  action: number;
  clock: number;
  width: number;
  height: number;
  pixels: Uint8Array;
};
export type HoldObservation = {
  installed: boolean;
  context: number;
  contexts: number;
  sequence: number;
  action: number;
  mask?: HoldMask;
  backend?: { vendor: string; renderer: string; version: string };
  lastPress?: { x: number; y: number; action: number; sequence: number; clock: number };
};
declare global {
  interface Window {
    __holdObserver: HoldObservation;
  }
}

/** Serialized by Playwright before application modules. Retains only the latest target mask. */
export function installHoldObserver() {
  const state: HoldObservation = {
    installed: true,
    context: 0,
    contexts: 0,
    sequence: 0,
    action: 0,
  };
  window.__holdObserver = state;
  if (typeof WebGL2RenderingContext === 'undefined') return;
  window.addEventListener(
    'pointerdown',
    (event) => {
      if (event.target instanceof Element && event.target.closest('[data-home-v2]'))
        state.lastPress = {
          x: event.clientX,
          y: event.clientY,
          action: state.action,
          sequence: state.sequence,
          clock: performance.now(),
        };
    },
    true,
  );
  const contexts = new WeakMap<WebGL2RenderingContext, number>();
  const textures = new WeakMap<WebGLTexture, number>();
  const samplers = new WeakMap<WebGLUniformLocation, WebGLProgram>();
  const programs = new WeakMap<WebGL2RenderingContext, WebGLProgram>();
  const intended = new WeakMap<WebGL2RenderingContext, WebGLTexture>();
  const admitted = new WeakSet<WebGL2RenderingContext>();
  let nextTexture = 0;
  const getContext = HTMLCanvasElement.prototype.getContext;
  HTMLCanvasElement.prototype.getContext = function (this: HTMLCanvasElement, ...args: unknown[]) {
    const result = Reflect.apply(getContext, this, args);
    if (args[0] === 'webgl2' && this.matches('[data-film-canvas]') && result) {
      const gl = result as WebGL2RenderingContext;
      if (!contexts.has(gl)) {
        contexts.set(gl, ++state.contexts);
        state.backend = {
          vendor: String(gl.getParameter(gl.VENDOR)),
          renderer: String(gl.getParameter(gl.RENDERER)),
          version: String(gl.getParameter(gl.VERSION)),
        };
      }
      if (state.context !== contexts.get(gl)) {
        state.context = contexts.get(gl)!;
        state.mask = undefined;
      }
    }
    return result;
  } as typeof getContext;
  const prototype = WebGL2RenderingContext.prototype;
  const location = prototype.getUniformLocation;
  prototype.getUniformLocation = function (program, name) {
    const result = location.call(this, program, name);
    if (name === 'holdField' && result) samplers.set(result, program);
    return result;
  };
  const uniform = prototype.uniform1i;
  prototype.uniform1i = function (location, value) {
    uniform.call(this, location, value);
    if (location && samplers.has(location) && value === 3) {
      const active = this.getParameter(this.ACTIVE_TEXTURE) as number;
      this.activeTexture(this.TEXTURE3);
      const texture = this.getParameter(this.TEXTURE_BINDING_2D) as WebGLTexture | null;
      this.activeTexture(active);
      if (texture) {
        intended.set(this, texture);
        programs.set(this, samplers.get(location)!);
        admitted.add(this);
      }
    }
  };
  for (const name of ['texImage2D', 'texSubImage2D'] as const) {
    const original = prototype[name];
    (prototype[name] as unknown) = function (this: WebGL2RenderingContext, ...args: unknown[]) {
      const result = Reflect.apply(original, this, args);
      const pixels = args.at(-1);
      const context = contexts.get(this);
      if (
        context !== state.context ||
        !context ||
        !admitted.has(this) ||
        this.isContextLost() ||
        this.getParameter(this.CURRENT_PROGRAM) !== programs.get(this) ||
        this.canvas !== document.querySelector('[data-film-canvas]') ||
        this.getParameter(this.ACTIVE_TEXTURE) !== this.TEXTURE3 ||
        args[0] !== this.TEXTURE_2D ||
        args[1] !== 0 ||
        !(pixels instanceof Uint8Array)
      )
        return result;
      const offset = name === 'texImage2D' ? 3 : 4;
      const width = args[offset] as number,
        height = args[offset + 1] as number;
      const texture = this.getParameter(this.TEXTURE_BINDING_2D) as WebGLTexture | null;
      if (
        width !== 192 ||
        height < 48 ||
        height > 256 ||
        pixels.length !== width * height * 4 ||
        !texture ||
        texture !== intended.get(this)
      )
        return result;
      if (!textures.has(texture)) textures.set(texture, ++nextTexture);
      // One immutable snapshot, at most 192*256*4 bytes; no upload history or source images.
      state.mask = {
        context,
        texture: textures.get(texture)!,
        sequence: ++state.sequence,
        action: state.action,
        clock: performance.now(),
        width,
        height,
        pixels: pixels.slice(),
      };
      return result;
    };
  }
}

export function requireFreshMask(state: HoldObservation, after: number, action: number): HoldMask {
  if (!state.installed) throw new Error('Hold upload hook was not installed');
  if (!state.context)
    throw new Error('Required film WebGL2 context unavailable; fallback is not GPU qualification');
  const mask = state.mask;
  if (
    !mask ||
    mask.context !== state.context ||
    mask.sequence <= after ||
    mask.action !== action ||
    !Number.isFinite(mask.clock) ||
    mask.texture <= 0 ||
    mask.width !== 192 ||
    mask.height < 48 ||
    mask.height > 256 ||
    mask.pixels.length !== mask.width * mask.height * 4
  )
    throw new Error(
      `No fresh hold upload for action ${action} after ${after}; context=${state.context}, sequence=${state.sequence}`,
    );
  return mask;
}
