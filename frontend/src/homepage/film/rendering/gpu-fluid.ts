import { clamp } from '../model';
import { queueMotion, resampleMotion, motionSplat, type MotionSample } from '../input';
import { PIGMENT, pigmentSeed } from './pigment';
interface Target {
  texture: WebGLTexture;
  framebuffer: WebGLFramebuffer;
  width: number;
  height: number;
}
type Pair = [Target, Target];
const vertex = `#version 300 es
precision highp float;
out vec2 uv;
void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);uv=p;gl_Position=vec4(p*2.-1.,0,1);}`;
const header = `#version 300 es
precision highp float;
in vec2 uv;out vec4 result;
uniform sampler2D source,velocity,auxiliary,reverseField;
uniform vec2 size,viewport,point,force;
uniform float dt,decay,radius,dose;
uniform vec4 splats[32];uniform vec2 forces[32];uniform int splatCount;
vec4 linearSample(sampler2D t,vec2 p){vec2 s=vec2(textureSize(t,0)),q=clamp(p,vec2(0),vec2(1))*s-.5,i=floor(q),f=fract(q);return mix(mix(texture(t,(i+.5)/s),texture(t,(i+vec2(1.5,.5))/s),f.x),mix(texture(t,(i+vec2(.5,1.5))/s),texture(t,(i+1.5)/s),f.x),f.y);}
`;
const bodies: Record<string, string> = {
  advect: `vec2 v=linearSample(velocity,uv).xy;vec2 back=uv-dt*v/size;result=linearSample(source,back)*decay;`,
  correct: `vec2 back=uv-dt*linearSample(velocity,uv).xy/size,grid=vec2(textureSize(auxiliary,0)),cell=floor(back*grid-.5);vec4 a=texture(auxiliary,(cell+.5)/grid),b=texture(auxiliary,(cell+vec2(1.5,.5))/grid),c=texture(auxiliary,(cell+vec2(.5,1.5))/grid),d=texture(auxiliary,(cell+1.5)/grid);vec4 lo=min(min(a,b),min(c,d)),hi=max(max(a,b),max(c,d));vec4 corrected=texture(source,uv)+.5*(texture(auxiliary,uv)-texture(reverseField,uv));result=clamp(corrected,lo,hi)*decay;`,
  splat: `vec2 v=texture(source,uv).xy;for(int i=0;i<32;i++){if(i>=splatCount)break;vec2 offset=(uv-splats[i].xy)*viewport;float weight=exp(-dot(offset,offset)/(splats[i].z*splats[i].z));vec2 tangent=normalize(forces[i]+vec2(.00001));vec2 normal=vec2(-tangent.y,tangent.x);float across=dot(offset,normal)/splats[i].z;v+=(forces[i]+tangent*length(forces[i])*.65*across)*weight*splats[i].w;}result=vec4(v,0,0);`,
  pigment: `vec3 d=texture(source,uv).rgb;for(int i=0;i<32;i++){if(i>=splatCount)break;vec2 offset=(uv-splats[i].xy)*viewport;vec2 tangent=normalize(forces[i]+vec2(.00001)),normal=vec2(-tangent.y,tangent.x);float across=dot(offset,normal),along=dot(offset,tangent),r=splats[i].z,len=r+((${PIGMENT.slowRadius}.0-r)/${PIGMENT.slowRadius - PIGMENT.fastRadius}.0)*${PIGMENT.wakeLength}.0;float weight=exp(-along*along/(len*len)-across*across/(r*r));vec2 p=vec2(uv.x,1.-uv.y)*viewport;${pigmentSeed}d+=vec3(weight,weight*band,weight*vein)*splats[i].w;}result=vec4(min(d,vec3(3)),0);`,
  curl: `vec2 h=1./size;float c=.5*(texture(velocity,uv+vec2(h.x,0)).y-texture(velocity,uv-vec2(h.x,0)).y-texture(velocity,uv+vec2(0,h.y)).x+texture(velocity,uv-vec2(0,h.y)).x);result=vec4(c,0,0,0);`,
  confine: `vec2 h=1./size;float c=texture(auxiliary,uv).r;vec2 g=.5*vec2(abs(texture(auxiliary,uv+vec2(0,h.y)).r)-abs(texture(auxiliary,uv-vec2(0,h.y)).r),abs(texture(auxiliary,uv+vec2(h.x,0)).r)-abs(texture(auxiliary,uv-vec2(h.x,0)).r));g=g/(length(g)+.0001);vec2 v=texture(velocity,uv).xy+vec2(g.x,-g.y)*c*dt*24.;vec2 neighbors=(texture(velocity,uv+vec2(h.x,0)).xy+texture(velocity,uv-vec2(h.x,0)).xy+texture(velocity,uv+vec2(0,h.y)).xy+texture(velocity,uv-vec2(0,h.y)).xy)*.25;result=vec4(clamp(mix(v,neighbors,min(dt*.15,.08)),vec2(-240),vec2(240)),0,0);`,
  divergence: `vec2 h=1./size;vec2 l=texture(velocity,uv-vec2(h.x,0)).xy,r=texture(velocity,uv+vec2(h.x,0)).xy,b=texture(velocity,uv-vec2(0,h.y)).xy,t=texture(velocity,uv+vec2(0,h.y)).xy;result=vec4(.5*(r.x-l.x+t.y-b.y),0,0,0);`,
  pressure: `vec2 h=1./size;float p=texture(source,uv-vec2(h.x,0)).r+texture(source,uv+vec2(h.x,0)).r+texture(source,uv-vec2(0,h.y)).r+texture(source,uv+vec2(0,h.y)).r;result=vec4((p-texture(auxiliary,uv).r)*.25,0,0,0);`,
  project: `vec2 h=1./size;vec2 gradient=.5*vec2(texture(auxiliary,uv+vec2(h.x,0)).r-texture(auxiliary,uv-vec2(h.x,0)).r,texture(auxiliary,uv+vec2(0,h.y)).r-texture(auxiliary,uv-vec2(0,h.y)).r);vec2 v=texture(velocity,uv).xy-gradient;if(uv.x<h.x||uv.x>1.-h.x)v.x=0.;if(uv.y<h.y||uv.y>1.-h.y)v.y=0.;result=vec4(v,0,0);`,
};
/** GPU-owned velocity, pressure projection, vorticity and independently advected pigment.
 * No field readback or CPU pixel loop occurs in normal presentation. */
export class GpuFluid {
  readonly width: number;
  readonly height: number;
  readonly velocityWidth: number;
  readonly velocityHeight: number;
  private velocity: Pair;
  private dye: Pair;
  private dyeScratch: Pair;
  private pressure: Pair;
  private divergence: Target;
  private curl: Target;
  private programs = new Map<string, WebGLProgram>();
  private locations = new Map<WebGLProgram, Map<string, WebGLUniformLocation | null>>();
  private targets: Target[] = [];
  private samples: MotionSample[] = [];
  private splats = new Float32Array(128);
  private forces = new Float32Array(64);
  private splatCount = 0;
  private age = 5;
  private disposed = false;
  active = false;
  injections = 0;
  passes = 0;
  droppedSamples = 0;
  transportSecondsDiscarded = 0;
  energy = 0;
  peak = 0;
  constructor(
    readonly gl: WebGL2RenderingContext,
    readonly stageWidth: number,
    readonly stageHeight: number,
  ) {
    if (!gl.getExtension('EXT_color_buffer_float'))
      throw new Error('Floating-point framebuffer support unavailable');
    const long = Math.max(stageWidth, stageHeight);
    this.velocityWidth = Math.max(24, Math.round((stageWidth / long) * 192));
    this.velocityHeight = Math.max(24, Math.round((stageHeight / long) * 192));
    this.width = Math.max(48, Math.round((stageWidth / long) * 512));
    this.height = Math.max(48, Math.round((stageHeight / long) * 512));
    try {
      for (const [name, body] of Object.entries(bodies))
        this.programs.set(name, this.compile(header + `void main(){${body}}`));
      this.velocity = [
        this.target(this.velocityWidth, this.velocityHeight),
        this.target(this.velocityWidth, this.velocityHeight),
      ];
      this.dye = [
        this.target(this.width, this.height, true),
        this.target(this.width, this.height, true),
      ];
      this.dyeScratch = [
        this.target(this.width, this.height, true),
        this.target(this.width, this.height, true),
      ];
      this.pressure = [
        this.target(this.velocityWidth, this.velocityHeight),
        this.target(this.velocityWidth, this.velocityHeight),
      ];
      this.divergence = this.target(this.velocityWidth, this.velocityHeight);
      this.curl = this.target(this.velocityWidth, this.velocityHeight);
      this.clear();
    } catch (error) {
      this.dispose();
      throw error;
    }
  }
  private compile(fragment: string): WebGLProgram {
    const gl = this.gl,
      shaders: WebGLShader[] = [],
      p = gl.createProgram();
    if (!p) throw new Error('Fluid program allocation failed');
    try {
      for (const [kind, source] of [
        [gl.VERTEX_SHADER, vertex],
        [gl.FRAGMENT_SHADER, fragment],
      ] as const) {
        const s = gl.createShader(kind);
        if (!s) throw new Error('Fluid shader allocation failed');
        shaders.push(s);
        gl.shaderSource(s, source);
        gl.compileShader(s);
        if (!gl.getShaderParameter(s, gl.COMPILE_STATUS))
          throw new Error(gl.getShaderInfoLog(s) ?? 'Fluid compile failed');
        gl.attachShader(p, s);
      }
      gl.linkProgram(p);
      if (!gl.getProgramParameter(p, gl.LINK_STATUS))
        throw new Error(gl.getProgramInfoLog(p) ?? 'Fluid link failed');
      return p;
    } catch (error) {
      gl.deleteProgram(p);
      throw error;
    } finally {
      for (const s of shaders) gl.deleteShader(s);
    }
  }
  private target(width: number, height: number, linear = false): Target {
    const gl = this.gl,
      texture = gl.createTexture(),
      framebuffer = gl.createFramebuffer();
    if (!texture || !framebuffer) {
      if (texture) gl.deleteTexture(texture);
      if (framebuffer) gl.deleteFramebuffer(framebuffer);
      throw new Error('Fluid target allocation failed');
    }
    const target = { texture, framebuffer, width, height };
    this.targets.push(target);
    // RGBA16F is filterable in WebGL2. Pressure/velocity retain exact grid
    // reads; dye uses hardware interpolation for continuous optical slopes.
    gl.bindTexture(gl.TEXTURE_2D, texture);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, linear ? gl.LINEAR : gl.NEAREST);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, linear ? gl.LINEAR : gl.NEAREST);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA16F, width, height, 0, gl.RGBA, gl.HALF_FLOAT, null);
    gl.bindFramebuffer(gl.FRAMEBUFFER, framebuffer);
    gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, texture, 0);
    if (gl.checkFramebufferStatus(gl.FRAMEBUFFER) !== gl.FRAMEBUFFER_COMPLETE)
      throw new Error('Fluid framebuffer incomplete');
    return target;
  }
  private pass(
    name: string,
    destination: Target,
    source: Target,
    velocity = this.velocity[0],
    auxiliary = this.divergence,
    values: Record<string, number | [number, number]> = {},
  ): void {
    const gl = this.gl,
      p = this.programs.get(name)!;
    gl.useProgram(p);
    gl.bindFramebuffer(gl.FRAMEBUFFER, destination.framebuffer);
    gl.viewport(0, 0, destination.width, destination.height);
    let locations = this.locations.get(p);
    if (!locations) {
      locations = new Map();
      this.locations.set(p, locations);
    }
    const location = (name: string) => {
      if (!locations!.has(name)) locations!.set(name, gl.getUniformLocation(p, name));
      return locations!.get(name)!;
    };
    for (const [index, target] of [source, velocity, auxiliary].entries()) {
      gl.activeTexture(gl.TEXTURE0 + index);
      gl.bindTexture(gl.TEXTURE_2D, target.texture);
      gl.uniform1i(location(['source', 'velocity', 'auxiliary'][index]!), index);
    }
    if (name === 'correct') {
      gl.activeTexture(gl.TEXTURE3);
      gl.bindTexture(gl.TEXTURE_2D, this.dyeScratch[1].texture);
      gl.uniform1i(location('reverseField'), 3);
    }
    const parameters = {
      size: [this.velocityWidth, this.velocityHeight],
      viewport: [this.stageWidth, this.stageHeight],
      ...values,
    };
    for (const [name, value] of Object.entries(parameters)) {
      if (Array.isArray(value)) gl.uniform2f(location(name), value[0]!, value[1]!);
      else gl.uniform1f(location(name), value);
    }
    if (name === 'splat' || name === 'pigment') {
      gl.uniform4fv(location('splats[0]'), this.splats);
      gl.uniform2fv(location('forces[0]'), this.forces);
      gl.uniform1i(location('splatCount'), this.splatCount);
    }
    gl.drawArrays(gl.TRIANGLES, 0, 3);
    this.passes++;
  }
  inject(sample: MotionSample): void {
    if (
      this.disposed ||
      !Number.isFinite(
        sample.x + sample.y + sample.vx + sample.vy + sample.speed + sample.distance,
      ) ||
      sample.distance <= 0 ||
      sample.speed <= 0
    )
      return;
    if (!this.active) this.age = 0;
    this.active = true;
    this.injections++;
    if (queueMotion(this.samples, { ...sample })) this.droppedSamples++;
  }
  // Suspension invalidates unconsumed input, never the already rendered field.
  discardPending(): void {
    this.samples = [];
  }
  step(elapsed: number): void {
    if (!this.active || this.disposed) return;
    const aged = this.age + Math.max(0, elapsed),
      fresh = this.samples.length > 0;
    if (aged >= 4.2) {
      if (!fresh) {
        this.clear();
        return;
      }
      // Expire the old field without charging the preceding stall to fresh input.
      const pending = this.samples;
      this.clear();
      this.samples = pending;
      this.active = true;
    }
    this.age = fresh ? 0 : aged;
    const dt = clamp(elapsed, 0, 1 / 30),
      gl = this.gl;
    // One bounded transport step prevents catch-up spirals on constrained devices.
    // Excess transport time is deliberately discarded; wall time owns fade/expiry.
    this.transportSecondsDiscarded += Math.max(0, elapsed - dt);
    this.pass('advect', this.velocity[1], this.velocity[0], this.velocity[0], undefined, {
      dt,
      decay: Math.exp(-PIGMENT.drag * elapsed),
    });
    this.velocity.reverse();
    this.splatCount = 0;
    for (const sample of resampleMotion(this.samples)) {
      const { radius, dose } = motionSplat(sample),
        i = this.splatCount++;
      this.splats.set(
        [sample.x / this.stageWidth, 1 - sample.y / this.stageHeight, radius, dose],
        i * 4,
      );
      this.forces.set(
        [
          (sample.vx / this.stageWidth) * this.velocityWidth * PIGMENT.force,
          (-sample.vy / this.stageHeight) * this.velocityHeight * PIGMENT.force,
        ],
        i * 2,
      );
    }
    if (this.splatCount) {
      this.pass('splat', this.velocity[1], this.velocity[0]);
      this.velocity.reverse();
    }
    this.samples = [];
    this.pass('curl', this.curl, this.velocity[0]);
    this.pass('confine', this.velocity[1], this.velocity[0], this.velocity[0], this.curl, { dt });
    this.velocity.reverse();
    this.pass('divergence', this.divergence, this.velocity[0]);
    for (let n = 0; n < 12; n++) {
      this.pass('pressure', this.pressure[1], this.pressure[0], this.velocity[0], this.divergence);
      this.pressure.reverse();
    }
    this.pass('project', this.velocity[1], this.velocity[0], this.velocity[0], this.pressure[0]);
    this.velocity.reverse();
    // Bounded MacCormack correction preserves transported veins without creating new extrema.
    this.pass('advect', this.dyeScratch[0], this.dye[0], this.velocity[0], undefined, {
      dt,
      decay: 1,
    });
    this.pass('advect', this.dyeScratch[1], this.dyeScratch[0], this.velocity[0], undefined, {
      dt: -dt,
      decay: 1,
    });
    this.pass('correct', this.dye[1], this.dyeScratch[0], this.velocity[0], this.dye[0], {
      dt,
      decay: Math.exp(-PIGMENT.fade * elapsed),
    });
    this.dye.reverse();
    // Fresh dye arrives after aging/transporting the existing pigment.
    if (this.splatCount) {
      this.pass('pigment', this.dye[1], this.dye[0]);
      this.dye.reverse();
    }
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
  }
  clear(): void {
    this.samples = [];
    this.active = false;
    this.age = 5;
    this.energy = 0;
    this.peak = 0;
    if (this.disposed) return;
    const gl = this.gl;
    gl.clearColor(0, 0, 0, 0);
    for (const target of this.targets) {
      gl.bindFramebuffer(gl.FRAMEBUFFER, target.framebuffer);
      gl.clear(gl.COLOR_BUFFER_BIT);
    }
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
  }
  // Explicit inspection only: synchronizing GPU readback never runs inside the render loop.
  metrics(): {
    mass: number;
    area: number;
    varianceX: number;
    varianceY: number;
    components: number;
  } {
    if (this.disposed) return { mass: 0, area: 0, varianceX: 0, varianceY: 0, components: 0 };
    const gl = this.gl,
      data = new Float32Array(this.width * this.height * 4);
    gl.bindFramebuffer(gl.FRAMEBUFFER, this.dye[0].framebuffer);
    gl.readPixels(0, 0, this.width, this.height, gl.RGBA, gl.FLOAT, data);
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    let mass = 0,
      mx = 0,
      my = 0,
      area = 0,
      peak = 0,
      vx = 0,
      vy = 0,
      components = 0;
    const n = this.width * this.height,
      seen = new Uint8Array(n);
    for (let i = 0; i < n; i++) {
      const d = data[i * 4]!;
      mass += d;
      mx += (i % this.width) * d;
      my += Math.floor(i / this.width) * d;
      if (d > 0.1) area++;
      peak = Math.max(peak, d);
    }
    mx /= mass || 1;
    my /= mass || 1;
    for (let i = 0; i < n; i++) {
      const d = data[i * 4]!;
      vx += ((i % this.width) - mx) ** 2 * d;
      vy += (Math.floor(i / this.width) - my) ** 2 * d;
      if (d <= 0.1 || seen[i]) continue;
      components++;
      const stack = [i];
      seen[i] = 1;
      while (stack.length) {
        const j = stack.pop()!;
        for (const k of [j - 1, j + 1, j - this.width, j + this.width])
          if (
            k >= 0 &&
            k < n &&
            Math.abs((k % this.width) - (j % this.width)) <= 1 &&
            !seen[k] &&
            data[k * 4]! > 0.1
          ) {
            seen[k] = 1;
            stack.push(k);
          }
      }
    }
    this.energy = mass;
    this.peak = peak;
    return { mass, area, varianceX: vx / (mass || 1), varianceY: vy / (mass || 1), components };
  }
  get texture(): WebGLTexture {
    return this.dye[0].texture;
  }
  get bytes(): number {
    return this.disposed ? 0 : this.targets.reduce((n, t) => n + t.width * t.height * 8, 0);
  }
  get textures(): number {
    return this.targets.length;
  }
  get pendingSamples(): number {
    return this.samples.length;
  }
  dispose(): void {
    if (this.disposed) return;
    this.disposed = true;
    for (const t of this.targets) {
      this.gl.deleteTexture(t.texture);
      this.gl.deleteFramebuffer(t.framebuffer);
    }
    for (const p of this.programs.values()) this.gl.deleteProgram(p);
    this.programs.clear();
    this.locations.clear();
    this.targets = [];
    this.samples = [];
    this.active = false;
  }
}
