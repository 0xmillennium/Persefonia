import { cameraFor, sphereIntersection, type Vec3 } from "./geometry";
import { GlobeInput } from "./input";
import { GlobeLifecycle } from "./lifecycle";
import { GlobeOrientation } from "./orientation";
import { GlobeRenderer } from "./renderer";
import { LocationPresentation } from "./presentation";
import { ISTANBUL } from "./location";
import { GlobeEntrance } from "./entrance";

export class GlobeExperiment {
  private canvas: HTMLCanvasElement;
  private renderer: GlobeRenderer | null = null;
  private orientation = new GlobeOrientation();
  private entrance = new GlobeEntrance();
  private camera = cameraFor(1, 1);
  private input: GlobeInput;
  private lifecycle: GlobeLifecycle;
  private resize: ResizeObserver;
  private abort = new AbortController();
  private reduced = matchMedia("(prefers-reduced-motion: reduce)");
  private hover: Vec3 | null = null;
  private location: LocationPresentation;
  private failure: string | null = null;
  private submitted = 0;
  private lastResources = { buffers: 0, programs: 0, vaos: 0, bytes: 0 };
  disposed = false;

  constructor(private stage: HTMLElement, private mask: Uint8Array) {
    this.canvas = stage.querySelector<HTMLCanvasElement>("canvas")!;
    this.renderer = new GlobeRenderer(this.canvas, mask);
    this.location = new LocationPresentation(stage);
    this.input = new GlobeInput(this.canvas, () => this.camera, this.orientation, reason => {
      if (this.orientation.pressed) this.entrance.finish();
      if (reason === "cancel" || this.input.gesture.phase === "dragging") this.location.pinned = false;
      if (!this.input.hover || this.orientation.pressed) this.hover = null;
      this.invalidate();
    }, (x, y) => {
      if (this.location.visible && Math.hypot(x - this.location.projection.x, y - this.location.projection.y) < 32) {
        this.location.pinned = !this.location.pinned;
      }
    });
    this.lifecycle = new GlobeLifecycle(stage, seconds => this.draw(seconds), () => {
      this.input.cancel(); this.location.clear();
    });
    this.resize = new ResizeObserver(() => this.measure());
    this.resize.observe(stage);
    const options = { signal: this.abort.signal };
    this.canvas.addEventListener("webglcontextlost", e => {
      e.preventDefault();
      this.lifecycle.setAvailable(false);
      this.entrance.finish();
      this.renderer?.dispose();
      this.lastResources = this.renderer?.resources ?? this.lastResources;
      this.renderer = null;
      this.fail("Graphics were interrupted. Location information remains available.");
    }, options);
    this.canvas.addEventListener("webglcontextrestored", () => {
      if (this.disposed) return;
      try {
        this.renderer = new GlobeRenderer(this.canvas, this.mask);
        this.failure = null;
        this.measure();
        this.lifecycle.setAvailable(true);
        this.controlsAvailable(true);
      } catch (error) { this.fail(`Graphics could not be restored: ${String(error)}`); }
    }, options);
    window.addEventListener("resize", () => this.measure(), options);
    this.canvas.addEventListener("keydown", () => this.entrance.finish(), options);
    this.reduced.addEventListener("change", () => this.preferences(), options);
    const pause = document.querySelector<HTMLButtonElement>("#pause")!;
    pause.addEventListener("click", () => {
      this.entrance.finish();
      this.input.cancel(); this.orientation.paused = !this.orientation.paused;
      this.preferences();
    }, options);
    for (const [id, amount] of [["tilt-up", -0.12], ["tilt-down", 0.12]] as const) {
      document.querySelector<HTMLButtonElement>(`#${id}`)!.addEventListener("click", () => {
        this.entrance.finish();
        this.input.cancel(); this.orientation.drag(0, amount); this.invalidate();
      }, options);
    }
    document.querySelector<HTMLButtonElement>("#find-location")!.addEventListener("click", () => {
      this.entrance.finish();
      this.input.cancel();
      this.orientation.pitch = 0.3;
      this.orientation.spin = -ISTANBUL.longitude * Math.PI / 180;
      this.orientation.paused = true;
      this.preferences();
      this.location.pinned = true;
      this.invalidate();
    }, options);
    document.addEventListener("keydown", e => {
      if (e.key === "Escape") { this.entrance.finish(); this.location.clear(); this.invalidate(); }
    }, options);
    this.preferences();
    this.measure();
    this.controlsAvailable(true);
    // Opt-in lifecycle probes for this isolated experiment. state() and RAF
    // perform no WebGL metadata queries or pixel readback.
    if (import.meta.env.DEV || new URLSearchParams(location.search).has("inspect")) {
      const contextProbe = this.renderer.gl.getExtension("WEBGL_lose_context");
      window.__globeExperiment = {
        state: () => this.state(), dispose: () => this.dispose(),
        contextLoss: () => { if (!this.disposed) contextProbe?.loseContext(); },
        contextRestore: () => { if (!this.disposed) contextProbe?.restoreContext(); }
      };
    }
    if (import.meta.hot) import.meta.hot.dispose(() => this.dispose());
  }
  private measure(): void {
    if (this.disposed) return;
    this.camera = cameraFor(this.stage.clientWidth, this.stage.clientHeight);
    this.location.measure();
    this.renderer?.resize(this.camera, devicePixelRatio);
    this.invalidate();
  }
  private preferences(): void {
    this.input.cancel();
    this.orientation.reduced = this.reduced.matches;
    if (this.orientation.reduced) this.entrance.finish();
    const pause = document.querySelector<HTMLButtonElement>("#pause")!;
    pause.disabled = this.orientation.reduced;
    pause.setAttribute("aria-pressed", String(this.orientation.paused || this.orientation.reduced));
    pause.textContent = this.orientation.reduced ? "Rotation off (reduced motion)" :
      this.orientation.paused ? "Resume rotation" : "Pause rotation";
    this.invalidate();
  }
  private invalidate(): void { if (!this.disposed) this.lifecycle?.request(); }
  private controlsAvailable(available: boolean): void {
    for (const control of document.querySelectorAll<HTMLButtonElement>(".controls button")) control.disabled = !available;
    if (available) document.querySelector<HTMLButtonElement>("#pause")!.disabled = this.orientation.reduced;
  }
  private draw(seconds: number): boolean {
    if (!this.renderer || this.disposed) return false;
    this.entrance.step(seconds, this.orientation.reduced);
    this.orientation.step(seconds);
    const pointer = this.input.hover;
    this.hover = !this.orientation.pressed && pointer ? sphereIntersection(pointer.x, pointer.y, this.camera) : null;
    this.location.update(this.orientation, this.camera, pointer, this.hover !== null, this.orientation.pressed);
    if (this.location.open) this.entrance.finish();
    this.location.entrance(this.entrance, this.camera);
    this.renderer.draw(this.camera, this.orientation, this.hover, this.entrance, this.location.projection.world);
    this.submitted++;
    this.stage.dataset.ready = "true";
    this.stage.dataset.pressed = String(this.orientation.pressed);
    return this.orientation.moving || this.entrance.moving;
  }
  private fail(message: string): void {
    this.failure = message;
    this.location.clear();
    this.controlsAvailable(false);
    this.stage.dataset.ready = "false";
    this.stage.querySelector<HTMLElement>("#fallback")!.textContent = `Istanbul, Türkiye. ${message}`;
  }
  state() {
    return { orientation: { pitch: this.orientation.pitch, spin: this.orientation.spin,
      speed: this.orientation.angularSpeed, excess: this.orientation.excessSpeed, pressed: this.orientation.pressed },
      phase: this.input.gesture.phase, pointer: this.input.gesture.owner, hover: this.hover,
      location: { ...this.location.projection, visible: this.location.visible, open: this.location.open, pinned: this.location.pinned },
      camera: this.camera, reduced: this.orientation.reduced, paused: this.orientation.paused,
      entrance: { elapsed: this.entrance.elapsed, started: this.entrance.started, phase: this.entrance.phase, reveal: this.entrance.reveal },
      active: this.lifecycle.active, scheduled: this.lifecycle.scheduled, disposed: this.disposed,
      failure: this.failure, submitted: this.submitted, resources: this.renderer?.resources ?? this.lastResources };
  }
  dispose(): void {
    if (this.disposed) return;
    this.disposed = true;
    this.lifecycle.dispose(); this.input.dispose(); this.resize.disconnect(); this.abort.abort();
    this.renderer?.dispose();
    this.lastResources = this.renderer?.resources ?? this.lastResources;
    this.renderer = null; this.hover = null;
    this.location.clear();
    this.controlsAvailable(false);
    this.stage.dataset.ready = "false";
  }
}

declare global {
  interface Window {
    __globeExperiment?: {
      state(): ReturnType<GlobeExperiment["state"]>;
      dispose(): void; contextLoss(): void; contextRestore(): void;
    };
  }
}
