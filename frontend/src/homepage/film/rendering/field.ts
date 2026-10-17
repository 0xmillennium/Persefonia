import { clamp } from '../model';
import { motionSplat, queueMotion, resampleMotion, type MotionSample } from '../input';
import { PIGMENT, pigmentBands } from './pigment';
export const FIELD = {
  width: 192,
  maxHeight: 256,
  cutoff: 0.012,
  lifetime: 4.2,
  maxBytes: 2_500_000,
};
/** Compatibility implementation of the existing projected flow and transported
 * pigment. A half-resolution velocity grid keeps CPU work and storage bounded. */
export class LiquidField {
  readonly width: number;
  readonly height: number;
  readonly pixels: Uint8Array;
  readonly velocityWidth: number;
  readonly velocityHeight: number;
  private dye: Float32Array;
  private forward: Float32Array;
  private reverse: Float32Array;
  private vx: Float32Array;
  private vy: Float32Array;
  private nx: Float32Array;
  private ny: Float32Array;
  private pressure: Float32Array;
  private nextPressure: Float32Array;
  private curl: Float32Array;
  private divergence: Float32Array;
  private samples: MotionSample[] = [];
  private age = FIELD.lifetime;
  energy = 0;
  peak = 0;
  active = false;
  injections = 0;
  constructor(
    readonly stageWidth: number,
    readonly stageHeight: number,
    width = FIELD.width,
  ) {
    this.width = width;
    this.height = Math.min(
      FIELD.maxHeight,
      Math.max(48, Math.round((width * stageHeight) / stageWidth)),
    );
    this.velocityWidth = Math.ceil(width / 2);
    this.velocityHeight = Math.ceil(this.height / 2);
    const n = width * this.height,
      v = this.velocityWidth * this.velocityHeight;
    this.dye = new Float32Array(n * 3);
    this.forward = new Float32Array(n * 3);
    this.reverse = new Float32Array(n * 3);
    this.pixels = new Uint8Array(n * 4);
    this.vx = new Float32Array(v);
    this.vy = new Float32Array(v);
    this.nx = new Float32Array(v);
    this.ny = new Float32Array(v);
    this.pressure = new Float32Array(v);
    this.nextPressure = new Float32Array(v);
    this.curl = new Float32Array(v);
    this.divergence = new Float32Array(v);
  }
  inject(sample: MotionSample): void {
    if (
      !Number.isFinite(
        sample.x + sample.y + sample.vx + sample.vy + sample.speed + sample.distance,
      ) ||
      sample.speed <= 0 ||
      sample.distance <= 0
    )
      return;
    this.active = true;
    this.injections++;
    queueMotion(this.samples, { ...sample });
  }
  discardPending(): void {
    this.samples = [];
  }
  private sample(
    data: Float32Array,
    x: number,
    y: number,
    width: number,
    height: number,
    stride = 1,
    channel = 0,
  ): number {
    x = clamp(x, 0, width - 1);
    y = clamp(y, 0, height - 1);
    const ix = Math.floor(x),
      iy = Math.floor(y),
      fx = x - ix,
      fy = y - iy;
    const a = (iy * width + ix) * stride + channel,
      b = (iy * width + Math.min(ix + 1, width - 1)) * stride + channel;
    const c = (Math.min(iy + 1, height - 1) * width + ix) * stride + channel,
      d = (Math.min(iy + 1, height - 1) * width + Math.min(ix + 1, width - 1)) * stride + channel;
    return (
      (data[a]! * (1 - fx) + data[b]! * fx) * (1 - fy) + (data[c]! * (1 - fx) + data[d]! * fx) * fy
    );
  }
  private deposit(sample: MotionSample, velocity: boolean): void {
    const { radius, dose } = motionSplat(sample),
      length =
        radius +
        ((PIGMENT.slowRadius - radius) / (PIGMENT.slowRadius - PIGMENT.fastRadius)) *
          PIGMENT.wakeLength;
    const w = velocity ? this.velocityWidth : this.width,
      h = velocity ? this.velocityHeight : this.height;
    const dx = sample.vx / sample.speed,
      dy = sample.vy / sample.speed,
      extent = (velocity ? radius : length) * 2.6;
    const minX = Math.max(0, Math.floor(((sample.x - extent) / this.stageWidth) * w)),
      maxX = Math.min(w - 1, Math.ceil(((sample.x + extent) / this.stageWidth) * w));
    const minY = Math.max(0, Math.floor(((sample.y - extent) / this.stageHeight) * h)),
      maxY = Math.min(h - 1, Math.ceil(((sample.y + extent) / this.stageHeight) * h));
    const forceX = (sample.vx / this.stageWidth) * w * PIGMENT.force,
      forceY = (sample.vy / this.stageHeight) * h * PIGMENT.force;
    for (let y = minY; y <= maxY; y++)
      for (let x = minX; x <= maxX; x++) {
        const px = ((x + 0.5) / w) * this.stageWidth,
          py = ((y + 0.5) / h) * this.stageHeight,
          ox = px - sample.x,
          oy = py - sample.y;
        const across = -ox * dy + oy * dx,
          along = ox * dx + oy * dy;
        const weight =
            Math.exp(
              (-along * along) / (velocity ? radius : length) ** 2 -
                (across * across) / (radius * radius),
            ) * dose,
          i = y * w + x;
        if (velocity) {
          const shear = (Math.hypot(forceX, forceY) * 0.65 * across) / radius;
          this.vx[i] = this.vx[i]! + (forceX + dx * shear) * weight;
          this.vy[i] = this.vy[i]! + (forceY + dy * shear) * weight;
        } else {
          const [band, vein] = pigmentBands(px, py, across);
          this.dye[i * 3] = Math.min(3, this.dye[i * 3]! + weight);
          this.dye[i * 3 + 1] = Math.min(3, this.dye[i * 3 + 1]! + weight * band);
          this.dye[i * 3 + 2] = Math.min(3, this.dye[i * 3 + 2]! + weight * vein);
        }
      }
  }
  step(elapsed: number): void {
    if (!this.active) return;
    elapsed = Math.max(0, elapsed);
    const fresh = this.samples.length > 0,
      aged = this.age + elapsed;
    if (aged >= FIELD.lifetime) {
      if (!fresh) {
        this.clear();
        return;
      }
      const pending = this.samples;
      this.clear();
      this.samples = pending;
      this.active = true;
    }
    this.age = fresh ? 0 : aged;
    const dt = clamp(elapsed, 0, 1 / 30),
      drag = Math.exp(-PIGMENT.drag * elapsed),
      w = this.velocityWidth,
      h = this.velocityHeight;
    for (let y = 0; y < h; y++)
      for (let x = 0; x < w; x++) {
        const i = y * w + x,
          sx = x - this.vx[i]! * dt,
          sy = y - this.vy[i]! * dt;
        this.nx[i] = this.sample(this.vx, sx, sy, w, h) * drag;
        this.ny[i] = this.sample(this.vy, sx, sy, w, h) * drag;
      }
    [this.vx, this.nx] = [this.nx, this.vx];
    [this.vy, this.ny] = [this.ny, this.vy];
    const deposits = resampleMotion(this.samples);
    for (const s of deposits) this.deposit(s, true);
    this.samples = [];
    for (let y = 0; y < h; y++)
      for (let x = 0; x < w; x++) {
        const i = y * w + x,
          l = y * w + Math.max(0, x - 1),
          r = y * w + Math.min(w - 1, x + 1),
          t = Math.max(0, y - 1) * w + x,
          b = Math.min(h - 1, y + 1) * w + x;
        this.curl[i] = 0.5 * (this.vy[r]! - this.vy[l]! - this.vx[b]! + this.vx[t]!);
      }
    for (let y = 0; y < h; y++)
      for (let x = 0; x < w; x++) {
        const i = y * w + x,
          l = y * w + Math.max(0, x - 1),
          r = y * w + Math.min(w - 1, x + 1),
          t = Math.max(0, y - 1) * w + x,
          b = Math.min(h - 1, y + 1) * w + x;
        const gx = 0.5 * (Math.abs(this.curl[b]!) - Math.abs(this.curl[t]!)),
          gy = 0.5 * (Math.abs(this.curl[r]!) - Math.abs(this.curl[l]!)),
          scale = (this.curl[i]! * dt * 24) / (Math.hypot(gx, gy) + 0.0001),
          mix = Math.min(dt * 0.15, 0.08);
        this.nx[i] = clamp(
          (this.vx[i]! + gx * scale) * (1 - mix) +
            (this.vx[l]! + this.vx[r]! + this.vx[t]! + this.vx[b]!) * 0.25 * mix,
          -240,
          240,
        );
        this.ny[i] = clamp(
          (this.vy[i]! - gy * scale) * (1 - mix) +
            (this.vy[l]! + this.vy[r]! + this.vy[t]! + this.vy[b]!) * 0.25 * mix,
          -240,
          240,
        );
      }
    [this.vx, this.nx] = [this.nx, this.vx];
    [this.vy, this.ny] = [this.ny, this.vy];
    for (let y = 0; y < h; y++)
      for (let x = 0; x < w; x++) {
        const i = y * w + x,
          l = y * w + Math.max(0, x - 1),
          r = y * w + Math.min(w - 1, x + 1),
          t = Math.max(0, y - 1) * w + x,
          b = Math.min(h - 1, y + 1) * w + x;
        this.divergence[i] = 0.5 * (this.vx[r]! - this.vx[l]! + this.vy[b]! - this.vy[t]!);
      }
    for (let n = 0; n < 12; n++) {
      for (let y = 0; y < h; y++)
        for (let x = 0; x < w; x++) {
          const i = y * w + x;
          this.nextPressure[i] =
            (this.pressure[y * w + Math.max(0, x - 1)]! +
              this.pressure[y * w + Math.min(w - 1, x + 1)]! +
              this.pressure[Math.max(0, y - 1) * w + x]! +
              this.pressure[Math.min(h - 1, y + 1) * w + x]! -
              this.divergence[i]!) *
            0.25;
        }
      [this.pressure, this.nextPressure] = [this.nextPressure, this.pressure];
    }
    for (let y = 0; y < h; y++)
      for (let x = 0; x < w; x++) {
        const i = y * w + x;
        this.vx[i] =
          x === 0 || x === w - 1
            ? 0
            : this.vx[i]! - 0.5 * (this.pressure[y * w + x + 1]! - this.pressure[y * w + x - 1]!);
        this.vy[i] =
          y === 0 || y === h - 1
            ? 0
            : this.vy[i]! -
              0.5 * (this.pressure[(y + 1) * w + x]! - this.pressure[(y - 1) * w + x]!);
      }
    const dw = this.width,
      dh = this.height;
    for (const backwards of [false, true])
      for (let y = 0; y < dh; y++)
        for (let x = 0; x < dw; x++) {
          const sx = ((x + 0.5) / dw) * w - 0.5,
            sy = ((y + 0.5) / dh) * h - 0.5,
            sign = backwards ? -1 : 1;
          const bx = x - (sign * dt * this.sample(this.vx, sx, sy, w, h) * dw) / w,
            by = y - (sign * dt * this.sample(this.vy, sx, sy, w, h) * dh) / h;
          const px = clamp(bx, 0, dw - 1),
            py = clamp(by, 0, dh - 1),
            ix = Math.floor(px),
            iy = Math.floor(py),
            fx = px - ix,
            fy = py - iy;
          const a = (iy * dw + ix) * 3,
            b = (iy * dw + Math.min(ix + 1, dw - 1)) * 3,
            c = (Math.min(iy + 1, dh - 1) * dw + ix) * 3,
            d = (Math.min(iy + 1, dh - 1) * dw + Math.min(ix + 1, dw - 1)) * 3;
          const source = backwards ? this.forward : this.dye,
            target = backwards ? this.reverse : this.forward;
          for (let channel = 0; channel < 3; channel++)
            target[(y * dw + x) * 3 + channel] =
              (source[a + channel]! * (1 - fx) + source[b + channel]! * fx) * (1 - fy) +
              (source[c + channel]! * (1 - fx) + source[d + channel]! * fx) * fy;
        }
    const decay = Math.exp(-PIGMENT.fade * elapsed);
    for (let y = 0; y < dh; y++)
      for (let x = 0; x < dw; x++) {
        const sx = ((x + 0.5) / dw) * w - 0.5,
          sy = ((y + 0.5) / dh) * h - 0.5;
        const bx = clamp(x - (dt * this.sample(this.vx, sx, sy, w, h) * dw) / w, 0, dw - 1),
          by = clamp(y - (dt * this.sample(this.vy, sx, sy, w, h) * dh) / h, 0, dh - 1),
          ix = Math.floor(bx),
          iy = Math.floor(by);
        const a = (iy * dw + ix) * 3,
          b = (iy * dw + Math.min(ix + 1, dw - 1)) * 3,
          c = (Math.min(iy + 1, dh - 1) * dw + ix) * 3,
          d = (Math.min(iy + 1, dh - 1) * dw + Math.min(ix + 1, dw - 1)) * 3;
        for (let channel = 0; channel < 3; channel++) {
          const i = (y * dw + x) * 3 + channel,
            lo = Math.min(
              this.dye[a + channel]!,
              this.dye[b + channel]!,
              this.dye[c + channel]!,
              this.dye[d + channel]!,
            ),
            hi = Math.max(
              this.dye[a + channel]!,
              this.dye[b + channel]!,
              this.dye[c + channel]!,
              this.dye[d + channel]!,
            );
          this.reverse[i] =
            clamp(this.forward[i]! + 0.5 * (this.dye[i]! - this.reverse[i]!), lo, hi) * decay;
        }
      }
    [this.dye, this.reverse] = [this.reverse, this.dye];
    for (const s of deposits) this.deposit(s, false);
    let energy = 0,
      peak = 0;
    for (let i = 0; i < dw * dh; i++) {
      const density = this.dye[i * 3]!;
      energy += density;
      peak = Math.max(peak, density);
      for (let c = 0; c < 3; c++)
        this.pixels[i * 4 + c] = Math.round(clamp(this.dye[i * 3 + c]! / 3) * 255);
      this.pixels[i * 4 + 3] = 255;
    }
    this.energy = energy;
    this.peak = peak;
    this.active = peak > FIELD.cutoff;
    if (!this.active) this.clear();
  }
  get pigment(): Float32Array {
    return this.dye;
  }
  clear(): void {
    this.samples = [];
    for (const data of [
      this.dye,
      this.forward,
      this.reverse,
      this.vx,
      this.vy,
      this.nx,
      this.ny,
      this.pressure,
      this.nextPressure,
      this.curl,
      this.divergence,
    ])
      data.fill(0);
    this.pixels.fill(0);
    this.energy = 0;
    this.peak = 0;
    this.active = false;
    this.age = FIELD.lifetime;
  }
  metrics(): {
    mass: number;
    area: number;
    varianceX: number;
    varianceY: number;
    components: number;
  } {
    let mass = 0,
      mx = 0,
      my = 0,
      area = 0,
      varianceX = 0,
      varianceY = 0,
      components = 0;
    for (let y = 0; y < this.height; y++)
      for (let x = 0; x < this.width; x++) {
        const d = this.dye[(y * this.width + x) * 3]!;
        mass += d;
        mx += x * d;
        my += y * d;
        if (d > 0.1) area++;
      }
    mx /= mass || 1;
    my /= mass || 1;
    const seen = new Uint8Array(this.width * this.height),
      stack: number[] = [];
    for (let y = 0; y < this.height; y++)
      for (let x = 0; x < this.width; x++) {
        const i = y * this.width + x,
          d = this.dye[i * 3]!;
        varianceX += (x - mx) ** 2 * d;
        varianceY += (y - my) ** 2 * d;
        if (d <= 0.1 || seen[i]) continue;
        components++;
        stack.push(i);
        seen[i] = 1;
        while (stack.length) {
          const j = stack.pop()!;
          for (const k of [j - 1, j + 1, j - this.width, j + this.width])
            if (
              k >= 0 &&
              k < seen.length &&
              Math.abs((k % this.width) - (j % this.width)) <= 1 &&
              !seen[k] &&
              this.dye[k * 3]! > 0.1
            ) {
              seen[k] = 1;
              stack.push(k);
            }
        }
      }
    return {
      mass,
      area,
      varianceX: varianceX / (mass || 1),
      varianceY: varianceY / (mass || 1),
      components,
    };
  }
  get bytes(): number {
    return this.dye.byteLength * 3 + this.vx.byteLength * 8 + this.pixels.byteLength;
  }
}
