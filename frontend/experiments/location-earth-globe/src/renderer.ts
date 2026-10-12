import { vertex, fragment } from "./material";
import { geographicPoint, orientationMatrix, type Camera, type Orientation, type Vec3 } from "./geometry";
import { surfaceSamples } from "./sampling.mjs";
import type { GlobeEntrance } from "./entrance";

export class GlobeRenderer {
  readonly gl: WebGL2RenderingContext;
  private program: WebGLProgram | null = null;
  private buffer: WebGLBuffer | null = null;
  private vao: WebGLVertexArrayObject | null = null;
  private uniforms = new Map<string, WebGLUniformLocation>();
  private count = 0;
  private ratio = 1;
  draws = 0;
  disposed = false;
  readonly bytes: number;

  constructor(readonly canvas: HTMLCanvasElement, mask: Uint8Array) {
    const gl = canvas.getContext("webgl2", { antialias: false, depth: false, alpha: true, powerPreference: "default" });
    if (!gl) throw new Error("WebGL 2 is unavailable");
    this.gl = gl;
    const shaders: WebGLShader[] = [];
    const samples = surfaceSamples();
    if (mask.length !== Math.ceil(samples.length / 8)) throw new Error("Invalid geographic mask");
    const data = new Float32Array(samples.length * 4);
    samples.forEach((point, i) => {
      data.set(geographicPoint(point.latitude, point.longitude), i * 4);
      data[i * 4 + 3] = (mask[i >> 3]! >> (i & 7)) & 1;
    });
    this.bytes = data.byteLength;
    try {
      this.program = gl.createProgram();
      if (!this.program) throw new Error("Program allocation failed");
      for (const [kind, source] of [[gl.VERTEX_SHADER, vertex], [gl.FRAGMENT_SHADER, fragment]] as const) {
        const shader = gl.createShader(kind);
        if (!shader) throw new Error("Shader allocation failed");
        shaders.push(shader);
        gl.shaderSource(shader, source);
        gl.compileShader(shader);
        gl.attachShader(this.program, shader);
      }
      gl.linkProgram(this.program);
      if (!gl.getProgramParameter(this.program, gl.LINK_STATUS)) {
        throw new Error(shaders.map(s => gl.getShaderInfoLog(s)).join("\n") || "Globe shader linking failed");
      }
      for (const name of ["orientation", "viewport", "focal", "pointSize", "hoverPoint", "hoverActive", "reveal", "entranceTime", "entrancePoint"]) {
        const location = gl.getUniformLocation(this.program, name);
        if (!location) throw new Error(`Missing uniform ${name}`);
        this.uniforms.set(name, location);
      }
      this.buffer = gl.createBuffer();
      this.vao = gl.createVertexArray();
      if (!this.buffer || !this.vao) throw new Error("Geometry allocation failed");
      gl.bindVertexArray(this.vao);
      gl.bindBuffer(gl.ARRAY_BUFFER, this.buffer);
      gl.bufferData(gl.ARRAY_BUFFER, data, gl.STATIC_DRAW);
      gl.enableVertexAttribArray(0);
      gl.vertexAttribPointer(0, 3, gl.FLOAT, false, 16, 0);
      gl.enableVertexAttribArray(1);
      gl.vertexAttribPointer(1, 1, gl.FLOAT, false, 16, 12);
      this.count = samples.length;
      gl.enable(gl.BLEND);
      gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
      gl.clearColor(0, 0, 0, 0);
    } catch (error) {
      this.dispose();
      throw error;
    } finally {
      for (const shader of shaders) gl.deleteShader(shader);
    }
  }

  resize(camera: Camera, dpr: number): void {
    // Bounded density and area, including when moving between monitors.
    this.ratio = Math.min(dpr, 2, 1400 / Math.max(camera.width, camera.height));
    const width = Math.max(1, Math.round(camera.width * this.ratio));
    const height = Math.max(1, Math.round(camera.height * this.ratio));
    if (this.canvas.width !== width || this.canvas.height !== height) {
      this.canvas.width = width;
      this.canvas.height = height;
    }
    this.gl.viewport(0, 0, width, height);
  }

  draw(camera: Camera, orientation: Orientation, hover: Vec3 | null, entrance: GlobeEntrance, location: Vec3): void {
    if (this.disposed) return;
    const gl = this.gl;
    gl.clear(gl.COLOR_BUFFER_BIT);
    gl.useProgram(this.program);
    gl.bindVertexArray(this.vao);
    gl.uniformMatrix3fv(this.uniforms.get("orientation")!, false, orientationMatrix(orientation));
    gl.uniform2f(this.uniforms.get("viewport")!, camera.width, camera.height);
    gl.uniform1f(this.uniforms.get("focal")!, camera.focal);
    gl.uniform1f(this.uniforms.get("pointSize")!, Math.max(1.2, camera.radius / 330 * 3.0) * this.ratio);
    gl.uniform3f(this.uniforms.get("hoverPoint")!, ...(hover ?? [0, 0, 1] as const));
    gl.uniform1f(this.uniforms.get("hoverActive")!, hover ? 1 : 0);
    gl.uniform1f(this.uniforms.get("reveal")!, entrance.reveal);
    gl.uniform1f(this.uniforms.get("entranceTime")!, entrance.elapsed);
    gl.uniform3f(this.uniforms.get("entrancePoint")!, ...location);
    gl.drawArrays(gl.POINTS, 0, this.count);
    this.draws++;
  }

  dispose(): void {
    if (this.disposed) return;
    this.disposed = true;
    this.gl.bindVertexArray(null);
    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, null);
    this.gl.useProgram(null);
    this.gl.deleteBuffer(this.buffer);
    this.gl.deleteVertexArray(this.vao);
    this.gl.deleteProgram(this.program);
    this.buffer = null;
    this.vao = null;
    this.program = null;
    this.uniforms.clear();
  }

  get resources(): { buffers: number; programs: number; vaos: number; bytes: number } {
    return { buffers: this.buffer ? 1 : 0, programs: this.program ? 1 : 0, vaos: this.vao ? 1 : 0,
      bytes: this.disposed ? 0 : this.bytes };
  }
}
