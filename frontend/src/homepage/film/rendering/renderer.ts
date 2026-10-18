import { fragment, vertex } from './shaders';
import { GpuFluid } from './gpu-fluid';
import type { LiquidField } from './field';
import type { HoldMaterial } from '../hold/material';
import type { Hold } from '../hold/state';
import type { FrameKey, Film } from '../backgrounds';
type Aperture = readonly [number, number, number, number, number];
type Circle = readonly [number, number, number];
export interface Scene<Source extends string = Film> {
  field: LiquidField | GpuFluid;
  material: HoldMaterial;
  hold: Hold;
  time: number;
  reveal: boolean;
  reduced: boolean;
  current: Source;
  next: Source;
  aperture?: Aperture;
  circles?: readonly Circle[];
  nextAperture?: Aperture;
  extraAperture?: Aperture;
  nextExtraAperture?: Aperture;
  nextCircles?: readonly Circle[];
  dividers?: Circle;
  nextDividers?: Circle;
  capsules?: readonly Aperture[];
  nextCapsules?: readonly Aperture[];
  filmBounds?: readonly [number, number, number, number];
  nextFilmBounds?: readonly [number, number, number, number];
  imageOpacity?: number;
}
interface TimerExtension {
  TIME_ELAPSED_EXT: number;
  GPU_DISJOINT_EXT: number;
}
export class Renderer<Key extends string = FrameKey, Source extends string = Film> {
  readonly kind = 'webgl2';
  readonly gl: WebGL2RenderingContext;
  private program: WebGLProgram;
  private textures: WebGLTexture[] = [];
  private sources: (ImageBitmap | HTMLVideoElement | HTMLImageElement | null)[] = [null, null];
  private keys: (Key | null)[] = [null, null];
  private textureBytes = [0, 0];
  private sheetSize = [0, 0];
  private uniforms = new Map<string, WebGLUniformLocation | null>();
  private capsuleBoxes = new Float32Array(16);
  private capsuleRadii = new Float32Array(4);
  private circleData = new Float32Array(21);
  private timer: TimerExtension | null = null;
  private queries: WebGLQuery[] = [];
  readonly gpuSamples: number[] = [];
  uploads = 0;
  draws = 0;
  width = 1;
  height = 1;
  fluid: GpuFluid | null = null;
  private holdSize = [0, 0];
  private holdRevision = -1;
  private material: HoldMaterial | null = null;
  private frameQuery: WebGLQuery | null = null;
  private backendInfo: { vendor: unknown; renderer: unknown } | null = null;
  disposed = false;
  constructor(
    readonly canvas: HTMLCanvasElement,
    private captureIdentity = false,
    private transparent = false,
    diagnostics = false,
  ) {
    const gl = canvas.getContext('webgl2', {
      alpha: this.transparent,
      antialias: false,
      preserveDrawingBuffer: false,
      powerPreference: 'default',
    });
    if (!gl) throw new Error('WebGL 2 unavailable; basic fallback selected');
    this.gl = gl;
    const compile = (type: number, source: string) => {
      const shader = gl.createShader(type);
      if (!shader) throw new Error('Shader allocation failed');
      gl.shaderSource(shader, source);
      gl.compileShader(shader);
      if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
        const error = gl.getShaderInfoLog(shader);
        gl.deleteShader(shader);
        throw new Error(`Shader: ${error}`);
      }
      return shader;
    };
    const shaders: WebGLShader[] = [];
    let program: WebGLProgram | null = null;
    try {
      shaders.push(compile(gl.VERTEX_SHADER, vertex));
      shaders.push(compile(gl.FRAGMENT_SHADER, fragment));
      program = gl.createProgram();
      if (!program) throw new Error('Program allocation failed');
      gl.attachShader(program, shaders[0]!);
      gl.attachShader(program, shaders[1]!);
      gl.linkProgram(program);
    } finally {
      for (const shader of shaders) gl.deleteShader(shader);
    }
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
      const error = gl.getProgramInfoLog(program);
      gl.deleteProgram(program);
      throw new Error(`Program: ${error}`);
    }
    this.program = program;
    gl.useProgram(program);
    for (let i = 0; i < 5; i++) {
      const tex = gl.createTexture();
      if (!tex) {
        this.dispose();
        throw new Error('Texture allocation failed');
      }
      this.textures.push(tex);
      gl.activeTexture(gl.TEXTURE0 + i);
      gl.bindTexture(gl.TEXTURE_2D, tex);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      gl.texImage2D(
        gl.TEXTURE_2D,
        0,
        gl.RGBA,
        1,
        1,
        0,
        gl.RGBA,
        gl.UNSIGNED_BYTE,
        new Uint8Array([12, 15, 13, 255]),
      );
    }
    this.timer = diagnostics
      ? (gl.getExtension('EXT_disjoint_timer_query_webgl2') as TimerExtension | null)
      : null;
    gl.uniform1i(this.uniform('filmA'), 0);
    gl.uniform1i(this.uniform('filmB'), 1);
    gl.uniform1i(this.uniform('field'), 2);
    gl.uniform1i(this.uniform('holdField'), 3);
    gl.uniform1i(this.uniform('sheet'), 4);
  }
  private uniform(name: string): WebGLUniformLocation | null {
    if (!this.uniforms.has(name))
      this.uniforms.set(name, this.gl.getUniformLocation(this.program, name));
    return this.uniforms.get(name)!;
  }
  resize(width: number, height: number): void {
    this.fluid?.dispose();
    this.fluid = new GpuFluid(this.gl, width, height);
    this.width = width;
    this.height = height;
    const scale = Math.min(devicePixelRatio, 1.5, 2560 / Math.max(width, height));
    this.canvas.width = Math.round(width * scale);
    this.canvas.height = Math.round(height * scale);
    this.gl.viewport(0, 0, this.canvas.width, this.canvas.height);
  }
  upload(slot: number, key: Key, image: ImageBitmap | HTMLVideoElement | HTMLImageElement): void {
    if (this.keys[slot] === key && this.sources[slot] === image) return;
    const width =
      image instanceof HTMLVideoElement
        ? image.videoWidth
        : image instanceof HTMLImageElement
          ? image.naturalWidth
          : image.width;
    const height =
      image instanceof HTMLVideoElement
        ? image.videoHeight
        : image instanceof HTMLImageElement
          ? image.naturalHeight
          : image.height;
    const gl = this.gl;
    gl.activeTexture(gl.TEXTURE0 + slot);
    gl.bindTexture(gl.TEXTURE_2D, this.textures[slot]!);
    if (this.textureBytes[slot] === width * height * 4)
      gl.texSubImage2D(gl.TEXTURE_2D, 0, 0, 0, gl.RGBA, gl.UNSIGNED_BYTE, image);
    else {
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, image);
      if (gl.getError() !== gl.NO_ERROR) throw new Error(`Film texture allocation failed: ${key}`);
    }
    this.keys[slot] = key;
    this.sources[slot] = image;
    this.textureBytes[slot] = width * height * 4;
    this.uploads++;
  }
  swap(): void {
    [this.textures[0], this.textures[1]] = [this.textures[1]!, this.textures[0]!];
    [this.keys[0], this.keys[1]] = [this.keys[1]!, this.keys[0]!];
    [this.sources[0], this.sources[1]] = [this.sources[1]!, this.sources[0]!];
    [this.textureBytes[0], this.textureBytes[1]] = [this.textureBytes[1]!, this.textureBytes[0]!];
  }
  setSheet(image: ImageBitmap): void {
    const gl = this.gl;
    gl.activeTexture(gl.TEXTURE4);
    gl.bindTexture(gl.TEXTURE_2D, this.textures[4]!);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, image);
    if (gl.getError() !== gl.NO_ERROR) throw new Error('Hero sheet texture upload failed');
    this.sheetSize = [image.width, image.height];
  }
  get hasSheet(): boolean {
    return this.sheetSize[0]! > 0;
  }
  draw(scene: Scene<Source>): boolean {
    const gl = this.gl;
    if (this.disposed || gl.isContextLost()) return false;
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    gl.viewport(0, 0, this.canvas.width, this.canvas.height);
    gl.useProgram(this.program);
    for (let i = 0; i < 5; i++) {
      gl.activeTexture(gl.TEXTURE0 + i);
      gl.bindTexture(gl.TEXTURE_2D, this.textures[i]!);
    }
    gl.uniform2f(this.uniform('sheetSize'), this.sheetSize[0]!, this.sheetSize[1]!);
    gl.uniform2f(this.uniform('viewport'), this.width, this.height);
    const aperture = scene.aperture ?? [0, 0, this.width, this.height, 0];
    gl.uniform4f(this.uniform('aperture'), aperture[0], aperture[1], aperture[2], aperture[3]);
    gl.uniform1f(this.uniform('apertureRadius'), aperture[4]);
    const nextAperture = scene.nextAperture ?? aperture;
    gl.uniform4f(
      this.uniform('nextAperture'),
      nextAperture[0],
      nextAperture[1],
      nextAperture[2],
      nextAperture[3],
    );
    gl.uniform1f(this.uniform('nextApertureRadius'), nextAperture[4]);
    for (const [name, bars] of [
      ['dividersA', scene.dividers],
      ['dividersB', scene.nextDividers],
    ] as const)
      gl.uniform3f(this.uniform(name), bars?.[0] ?? 0, bars?.[1] ?? 0, bars?.[2] ?? 0);
    for (const [name, box] of [
      ['extraAperture', scene.extraAperture],
      ['nextExtraAperture', scene.nextExtraAperture],
    ] as const) {
      gl.uniform4f(this.uniform(name), box?.[0] ?? 0, box?.[1] ?? 0, box?.[2] ?? 0, box?.[3] ?? 0);
      gl.uniform1f(this.uniform(`${name}Radius`), box?.[4] ?? 0);
    }
    for (const [name, capsules] of [
      ['capsulesA', scene.capsules],
      ['capsulesB', scene.nextCapsules],
    ] as const) {
      const count = Math.min(4, capsules?.length ?? 0);
      gl.uniform1i(this.uniform(name === 'capsulesA' ? 'capsuleCountA' : 'capsuleCountB'), count);
      if (count) {
        for (let i = 0; i < count; i++) {
          const box = capsules![i]!;
          this.capsuleBoxes.set([box[0], box[1], box[2], box[3]], i * 4);
          this.capsuleRadii[i] = box[4];
        }
        gl.uniform4fv(this.uniform(`${name}[0]`), this.capsuleBoxes);
        gl.uniform1fv(
          this.uniform(name === 'capsulesA' ? 'capsuleRadiiA[0]' : 'capsuleRadiiB[0]'),
          this.capsuleRadii,
        );
      }
    }
    for (const [name, circles] of [
      ['circlesA', scene.circles],
      ['circlesB', scene.nextCircles ?? scene.circles],
    ] as const) {
      const count = Math.min(7, circles?.length ?? 0);
      gl.uniform1i(this.uniform(name === 'circlesA' ? 'circleCountA' : 'circleCountB'), count);
      if (count) {
        for (let i = 0; i < count; i++) this.circleData.set(circles![i]!, i * 3);
        gl.uniform3fv(this.uniform(`${name}[0]`), this.circleData);
      }
    }
    const filmBounds = scene.filmBounds ?? [0, 0, 0, 0];
    gl.uniform4f(
      this.uniform('filmBounds'),
      filmBounds[0],
      filmBounds[1],
      filmBounds[2],
      filmBounds[3],
    );
    const nextFilmBounds = scene.nextFilmBounds ?? filmBounds;
    gl.uniform4f(
      this.uniform('nextFilmBounds'),
      nextFilmBounds[0],
      nextFilmBounds[1],
      nextFilmBounds[2],
      nextFilmBounds[3],
    );
    gl.uniform1f(this.uniform('imageOpacity'), scene.imageOpacity ?? 1);
    gl.uniform2f(this.uniform('fieldSize'), scene.field.width, scene.field.height);
    gl.uniform2f(this.uniform('holdSize'), scene.material.width, scene.material.height);
    gl.activeTexture(gl.TEXTURE2);
    gl.bindTexture(gl.TEXTURE_2D, this.fluid!.texture);
    gl.activeTexture(gl.TEXTURE3);
    gl.bindTexture(gl.TEXTURE_2D, this.textures[3]!);
    if (this.material !== scene.material || this.holdRevision !== scene.material.revision) {
      if (this.holdSize[0] === scene.material.width && this.holdSize[1] === scene.material.height)
        gl.texSubImage2D(
          gl.TEXTURE_2D,
          0,
          0,
          0,
          scene.material.width,
          scene.material.height,
          gl.RGBA,
          gl.UNSIGNED_BYTE,
          scene.material.pixels,
        );
      else {
        gl.texImage2D(
          gl.TEXTURE_2D,
          0,
          gl.RGBA,
          scene.material.width,
          scene.material.height,
          0,
          gl.RGBA,
          gl.UNSIGNED_BYTE,
          scene.material.pixels,
        );
        this.holdSize = [scene.material.width, scene.material.height];
      }
      this.material = scene.material;
      this.holdRevision = scene.material.revision;
    }
    gl.uniform1f(this.uniform('revealing'), scene.hold.phase === 'REVEALING' ? 1 : 0);
    gl.uniform1f(this.uniform('holdActive'), scene.hold.animating ? 1 : 0);
    gl.uniform1f(this.uniform('reduced'), scene.reduced ? 1 : 0);
    gl.uniform1f(this.uniform('fieldActive'), scene.field.active ? 1 : 0);
    gl.uniform1f(this.uniform('portrait'), this.width / this.height < 0.9 ? 1 : 0);
    gl.uniform1f(this.uniform('framingA'), scene.current === 'robotic-arm' ? 0.56 : 0.5);
    gl.uniform1f(this.uniform('framingB'), scene.next === 'robotic-arm' ? 0.56 : 0.5);
    const identity = this.keys[0]?.split(':');
    gl.uniform1i(
      this.uniform('captureCode'),
      this.captureIdentity && identity
        ? 512 + (identity[0] === 'tree' ? 256 : 0) + Number(identity[1])
        : -1,
    );
    const query = this.frameQuery;
    this.frameQuery = null;
    gl.drawArrays(gl.TRIANGLES, 0, 3);
    this.draws++;
    if (query) {
      gl.endQuery(this.timer!.TIME_ELAPSED_EXT);
      this.queries.push(query);
    }
    this.collectQueries();
    return true;
  }
  beginFrame(): void {
    if (this.timer && !this.frameQuery && this.queries.length < 4 && !this.gl.isContextLost()) {
      const query = this.gl.createQuery();
      if (query) {
        this.frameQuery = query;
        this.gl.beginQuery(this.timer.TIME_ELAPSED_EXT, query);
      }
    }
  }
  private collectQueries(): void {
    const gl = this.gl;
    if (!this.timer || !this.queries.length) return;
    const disjoint = gl.getParameter(this.timer.GPU_DISJOINT_EXT) as boolean;
    while (this.queries.length) {
      const q = this.queries[0]!;
      if (!gl.getQueryParameter(q, gl.QUERY_RESULT_AVAILABLE)) break;
      if (!disjoint) {
        this.gpuSamples.push((gl.getQueryParameter(q, gl.QUERY_RESULT) as number) / 1e6);
        if (this.gpuSamples.length > 2048) this.gpuSamples.shift();
      }
      gl.deleteQuery(q);
      this.queries.shift();
    }
  }
  get residentKeys(): Key[] {
    return this.keys.filter((key): key is Key => key !== null);
  }
  get bytes(): number {
    return this.disposed
      ? 0
      : this.textureBytes[0]! +
          this.textureBytes[1]! +
          this.sheetSize[0]! * this.sheetSize[1]! * 4 +
          (this.fluid?.bytes ?? 0) +
          this.holdSize[0]! * this.holdSize[1]! * 4 +
          this.canvas.width * this.canvas.height * 4;
  }
  get textureCount(): number {
    return this.disposed ? 0 : this.textures.length + (this.fluid?.textures ?? 0);
  }
  get info(): object {
    // Backend identity is fixed for this renderer instance. Repeated diagnostic
    // inspection must not synchronously query the GPU during scroll/decode work.
    if (!this.backendInfo) {
      const ext = this.gl.getExtension('WEBGL_debug_renderer_info');
      this.backendInfo = {
        vendor: ext
          ? this.gl.getParameter(ext.UNMASKED_VENDOR_WEBGL)
          : this.gl.getParameter(this.gl.VENDOR),
        renderer: ext
          ? this.gl.getParameter(ext.UNMASKED_RENDERER_WEBGL)
          : this.gl.getParameter(this.gl.RENDERER),
      };
    }
    return {
      ...this.backendInfo,
      buffer: [this.canvas.width, this.canvas.height],
      gpuTimer: !!this.timer,
    };
  }
  dispose(): void {
    if (this.disposed) return;
    this.disposed = true;
    if (this.frameQuery) {
      if (!this.gl.isContextLost()) this.gl.endQuery(this.timer!.TIME_ELAPSED_EXT);
      this.gl.deleteQuery(this.frameQuery);
      this.frameQuery = null;
    }
    this.fluid?.dispose();
    this.fluid = null;
    for (const q of this.queries) this.gl.deleteQuery(q);
    this.queries = [];
    for (const tex of this.textures) this.gl.deleteTexture(tex);
    this.textures = [];
    this.sources = [];
    this.gl.deleteProgram(this.program);
    this.keys = [null, null];
    this.textureBytes = [0, 0];
  }
}
