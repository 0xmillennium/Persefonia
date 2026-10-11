import { fragment, vertex, radiusFor } from "./material";
import type { LiquidField } from "./field";
import type { Hold } from "./hold";
import type { FrameKey, Film } from "./transport";
export interface Scene { field: LiquidField; hold: Hold; time: number; reveal: boolean; reduced: boolean; current: Film; next: Film }
interface TimerExtension { TIME_ELAPSED_EXT: number; GPU_DISJOINT_EXT: number }
export class Renderer {
  readonly kind="webgl2";
  readonly gl: WebGL2RenderingContext;
  private program: WebGLProgram;
  private textures: WebGLTexture[] = [];
  private keys: (FrameKey|null)[] = [null,null];
  private textureBytes = [0,0];
  private uniforms = new Map<string,WebGLUniformLocation|null>();
  private timer: TimerExtension|null=null;
  private queries: WebGLQuery[]=[];
  readonly gpuSamples: number[]=[];
  uploads=0; draws=0;
  width=1; height=1;
  disposed=false;
  constructor(readonly canvas: HTMLCanvasElement) {
    const gl=canvas.getContext("webgl2",{alpha:false,antialias:false,preserveDrawingBuffer:false,powerPreference:"default"});
    if(!gl) throw new Error("WebGL 2 unavailable; full CPU compositor selected");
    this.gl=gl;
    const compile=(type:number,source:string) => {
      const shader=gl.createShader(type); if(!shader) throw new Error("Shader allocation failed");
      gl.shaderSource(shader,source); gl.compileShader(shader);
      if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS)) { const error=gl.getShaderInfoLog(shader); gl.deleteShader(shader); throw new Error(`Shader: ${error}`); }
      return shader;
    };
    const shaders: WebGLShader[]=[];
    let program:WebGLProgram|null=null;
    try {
      shaders.push(compile(gl.VERTEX_SHADER,vertex));shaders.push(compile(gl.FRAGMENT_SHADER,fragment));
      program=gl.createProgram();if(!program)throw new Error("Program allocation failed");
      gl.attachShader(program,shaders[0]!);gl.attachShader(program,shaders[1]!);gl.linkProgram(program);
    } finally {for(const shader of shaders)gl.deleteShader(shader);}
    if(!gl.getProgramParameter(program,gl.LINK_STATUS)) {const error=gl.getProgramInfoLog(program);gl.deleteProgram(program);throw new Error(`Program: ${error}`);}
    this.program=program; gl.useProgram(program);
    for(let i=0;i<3;i++) {
      const tex=gl.createTexture(); if(!tex) {this.dispose();throw new Error("Texture allocation failed");}
      this.textures.push(tex); gl.activeTexture(gl.TEXTURE0+i); gl.bindTexture(gl.TEXTURE_2D,tex);
      gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR); gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
      gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array([12,15,13,255]));
    }
    this.timer=gl.getExtension("EXT_disjoint_timer_query_webgl2") as TimerExtension|null;
    gl.uniform1i(this.uniform("filmA"),0); gl.uniform1i(this.uniform("filmB"),1); gl.uniform1i(this.uniform("field"),2);
  }
  private uniform(name: string): WebGLUniformLocation|null {
    if(!this.uniforms.has(name)) this.uniforms.set(name,this.gl.getUniformLocation(this.program,name));
    return this.uniforms.get(name)!;
  }
  resize(width: number,height: number): void {
    this.width=width;this.height=height;
    const scale=Math.min(devicePixelRatio,1.5,2560/Math.max(width,height));
    this.canvas.width=Math.round(width*scale);this.canvas.height=Math.round(height*scale);
    this.gl.viewport(0,0,this.canvas.width,this.canvas.height);
  }
  upload(slot: number,key: FrameKey,image: ImageBitmap): void {
    if(this.keys[slot]===key) return;
    const gl=this.gl;
    gl.activeTexture(gl.TEXTURE0+slot); gl.bindTexture(gl.TEXTURE_2D,this.textures[slot]!);
    gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,image);
    if(gl.getError()!==gl.NO_ERROR) throw new Error(`Film texture upload failed: ${key}`);
    this.keys[slot]=key; this.textureBytes[slot]=image.width*image.height*4; this.uploads++;
  }
  swap(): void { [this.textures[0],this.textures[1]]=[this.textures[1]!,this.textures[0]!]; [this.keys[0],this.keys[1]]=[this.keys[1]!,this.keys[0]!]; [this.textureBytes[0],this.textureBytes[1]]=[this.textureBytes[1]!,this.textureBytes[0]!]; }
  draw(scene: Scene): void {
    const gl=this.gl;if(this.disposed||gl.isContextLost())return;
    gl.useProgram(this.program);
    for(let i=0;i<3;i++) {gl.activeTexture(gl.TEXTURE0+i);gl.bindTexture(gl.TEXTURE_2D,this.textures[i]!);}
    gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,scene.field.width,scene.field.height,0,gl.RGBA,gl.UNSIGNED_BYTE,scene.field.pixels);
    gl.uniform2f(this.uniform("viewport"),this.width,this.height); gl.uniform2f(this.uniform("fieldSize"),scene.field.width,scene.field.height);
    gl.uniform2f(this.uniform("center"),scene.hold.center.x,scene.hold.center.y);
    gl.uniform1f(this.uniform("radius"),radiusFor(scene.hold.expansion,scene.hold.center,this.width,this.height));
    gl.uniform1f(this.uniform("time"),scene.time); gl.uniform1f(this.uniform("reveal"),scene.reveal?1:0);
    gl.uniform1f(this.uniform("grace"),scene.hold.phase==="GRACE"?0:1);
    gl.uniform1f(this.uniform("reduced"),scene.reduced?1:0);gl.uniform1f(this.uniform("fieldActive"),scene.field.active?1:0);gl.uniform1f(this.uniform("portrait"),this.width/this.height<.9?1:0);
    gl.uniform1f(this.uniform("framingA"),scene.current==="robotic-arm"?.56:.5);gl.uniform1f(this.uniform("framingB"),scene.next==="robotic-arm"?.56:.5);
    const query=this.timer&&this.queries.length<4?gl.createQuery():null;
    if(query) gl.beginQuery(this.timer!.TIME_ELAPSED_EXT,query);
    gl.drawArrays(gl.TRIANGLES,0,3);this.draws++;
    if(query) {gl.endQuery(this.timer!.TIME_ELAPSED_EXT);this.queries.push(query);}
    this.collectQueries();
  }
  private collectQueries(): void {
    const gl=this.gl;if(!this.timer)return;
    const disjoint=gl.getParameter(this.timer.GPU_DISJOINT_EXT) as boolean;
    while(this.queries.length) {
      const q=this.queries[0]!;
      if(!gl.getQueryParameter(q,gl.QUERY_RESULT_AVAILABLE))break;
      if(!disjoint) {this.gpuSamples.push((gl.getQueryParameter(q,gl.QUERY_RESULT) as number)/1e6);if(this.gpuSamples.length>2048)this.gpuSamples.shift();}
      gl.deleteQuery(q);this.queries.shift();
    }
  }
  get residentKeys(): FrameKey[] {return this.keys.filter((key):key is FrameKey=>key!==null);}
  get bytes(): number {return this.textureBytes[0]!+this.textureBytes[1]!+144*192*4+this.canvas.width*this.canvas.height*4;}
  get info(): object {const ext=this.gl.getExtension("WEBGL_debug_renderer_info");return {vendor:ext?this.gl.getParameter(ext.UNMASKED_VENDOR_WEBGL):this.gl.getParameter(this.gl.VENDOR),renderer:ext?this.gl.getParameter(ext.UNMASKED_RENDERER_WEBGL):this.gl.getParameter(this.gl.RENDERER),buffer:[this.canvas.width,this.canvas.height],gpuTimer:!!this.timer};}
  dispose(): void {if(this.disposed)return;this.disposed=true;for(const q of this.queries)this.gl.deleteQuery(q);this.queries=[];for(const tex of this.textures)this.gl.deleteTexture(tex);this.textures=[];this.gl.deleteProgram(this.program);this.keys=[null,null];this.textureBytes=[0,0];}
}
