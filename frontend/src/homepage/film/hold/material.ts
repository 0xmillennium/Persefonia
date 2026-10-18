import { clamp } from '../model';
import type { Hold } from './state';
import { radiusFor } from '../rendering/shaders';
const smooth = (a: number, b: number, x: number) => {
  const t = clamp((x - a) / (b - a));
  return t * t * (3 - 2 * t);
};
function terrain(x: number, y: number): number {
  // A gesture's fixed local contour; evaluating it never transports a cropped field.
  return (
    Math.sin(x * 0.018 + Math.sin(y * 0.021)) * 0.55 +
    Math.sin(y * 0.029 - x * 0.011) * 0.3 +
    Math.sin(x * 0.047 + y * 0.033) * 0.15
  );
}
/** Persistent local contour, sampled into the GPU lattice at its current pose.
 * Re-press retains its profile; translation never interpolates previous distances. */
export class HoldMaterial {
  readonly width: number;
  readonly height: number;
  readonly pixels: Uint8Array;
  readonly distances: Float32Array;
  private radialDistances: Float32Array;
  private profile = new Float32Array(256);
  private revealTerrain: Float32Array;
  private structure: Float32Array;
  private initialized = false;
  private gestureGeneration = -1;
  private revealing = false;
  private lastPhase = '';
  private lastExpansion = -1;
  private lastX = NaN;
  private lastY = NaN;
  private lastReveal = -1;
  private lastRevealX = NaN;
  private lastRevealY = NaN;
  coverage = 0;
  area = 0;
  revision = 0;
  generations = 0;
  constructor(
    readonly stageWidth: number,
    readonly stageHeight: number,
    resolution = 192,
  ) {
    this.width = resolution;
    this.height = Math.min(256, Math.max(48, Math.round((resolution * stageHeight) / stageWidth)));
    const n = this.width * this.height;
    this.distances = new Float32Array(n);
    this.radialDistances = new Float32Array(n);
    this.revealTerrain = new Float32Array(n);
    this.structure = new Float32Array(n);
    this.pixels = new Uint8Array(n * 4);
    this.clear();
  }
  step(hold: Hold): void {
    if (!hold.animating) {
      if (this.initialized) this.clear();
      return;
    }
    const fresh = !this.initialized || this.gestureGeneration !== hold.generation;
    if (
      !fresh &&
      this.lastPhase === hold.phase &&
      this.lastExpansion === hold.expansion &&
      this.lastX === hold.center.x &&
      this.lastY === hold.center.y &&
      this.lastReveal === hold.revealProgress &&
      this.lastRevealX === hold.revealCenter.x &&
      this.lastRevealY === hold.revealCenter.y
    )
      return;
    const moved = fresh || this.lastX !== hold.center.x || this.lastY !== hold.center.y;
    this.lastPhase = hold.phase;
    this.lastExpansion = hold.expansion;
    this.lastX = hold.center.x;
    this.lastY = hold.center.y;
    this.lastReveal = hold.revealProgress;
    this.lastRevealX = hold.revealCenter.x;
    this.lastRevealY = hold.revealCenter.y;
    const w = this.stageWidth,
      h = this.stageHeight,
      diagonal = Math.hypot(w, h);
    if (fresh) {
      this.generations++;
      this.initialized = true;
      this.gestureGeneration = hold.generation;
      this.revealing = false;
      for (let i = 0; i < this.profile.length; i++) {
        const angle = (i * Math.PI * 2) / this.profile.length;
        this.profile[i] = terrain(
          hold.center.x + Math.cos(angle) * 180,
          hold.center.y + Math.sin(angle) * 180,
        );
      }
    }
    if (moved) {
      for (let y = 0; y < this.height; y++)
        for (let x = 0; x < this.width; x++) {
          const i = y * this.width + x;
          const dx = ((x + 0.5) / this.width) * w - hold.center.x;
          const dy = ((y + 0.5) / this.height) * h - hold.center.y;
          const angle = ((Math.atan2(dy, dx) / (Math.PI * 2) + 1) % 1) * this.profile.length;
          const index = Math.floor(angle),
            fraction = angle - index;
          this.structure[i] =
            this.profile[index]! * (1 - fraction) +
            this.profile[(index + 1) % this.profile.length]! * fraction;
          this.radialDistances[i] = Math.hypot(dx, dy);
        }
    }
    if (hold.phase !== 'REVEALING') {
      this.revealing = false;
      const radius = Math.max(0, radiusFor(Math.pow(hold.expansion, 1.6), hold.center, w, h));
      const amplitude = Math.min(radius * 0.16, diagonal * 0.045);
      for (let i = 0; i < this.distances.length; i++)
        this.distances[i] = this.radialDistances[i]! - radius - this.structure[i]! * amplitude;
    } else if (!this.revealing) {
      this.revealing = true;
      const center = hold.revealCenter;
      for (let y = 0; y < this.height; y++)
        for (let x = 0; x < this.width; x++) {
          const px = ((x + 0.5) / this.width) * w,
            py = ((y + 0.5) / this.height) * h,
            d = Math.hypot(px - center.x, py - center.y);
          // Freeze this aperture's spatial topology and origin at reveal initiation.
          this.revealTerrain[y * this.width + x] =
            d + this.structure[y * this.width + x]! * Math.min(d * 0.2, diagonal * 0.045);
        }
    }
    const softness = Math.max(3, (w / this.width) * 0.7),
      radius =
        hold.phase === 'REVEALING' ? radiusFor(hold.revealProgress, hold.revealCenter, w, h) : -1;
    let covered = 0,
      area = 0;
    for (let i = 0; i < this.distances.length; i++) {
      const mask = hold.expansion <= 0 ? 0 : 1 - smooth(-softness, softness, this.distances[i]!);
      const d = this.revealTerrain[i]! - radius,
        reveal = radius < 0 ? 0 : 1 - smooth(-softness, softness, d);
      const rim = radius < 0 ? 0 : Math.exp(-Math.abs(d) / (softness * 1.8));
      const growthRim =
        hold.phase === 'REVEALING' || hold.expansion <= 0
          ? 0
          : Math.exp(-Math.abs(this.distances[i]!) / (softness * 1.8)) *
            (0.85 + this.structure[i]! * 0.15);
      const j = i * 4;
      this.pixels[j] = Math.round(mask * 255);
      this.pixels[j + 1] = Math.round(reveal * 255);
      this.pixels[j + 2] = Math.round(rim * 255);
      this.pixels[j + 3] = Math.round(clamp(growthRim) * 255);
      if (mask > 0.995) covered++;
      if (mask > 0.5) area++;
    }
    this.coverage = covered / this.distances.length;
    this.area = area;
    this.revision++;
  }
  clear(): void {
    this.pixels.fill(0);
    this.distances.fill(1e6);
    this.initialized = false;
    this.revealing = false;
    this.lastPhase = '';
    this.coverage = 0;
    this.area = 0;
    this.revision++;
  }
  get bytes(): number {
    return (
      this.distances.byteLength +
      this.radialDistances.byteLength +
      this.profile.byteLength +
      this.revealTerrain.byteLength +
      this.structure.byteLength +
      this.pixels.byteLength
    );
  }
}
