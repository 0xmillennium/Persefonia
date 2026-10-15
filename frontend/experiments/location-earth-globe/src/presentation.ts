import { ISTANBUL, LocationProximity, projectLocation } from "./location";
import type { Camera, Orientation } from "./geometry";
import type { GlobeEntrance } from "./entrance";

export class LocationPresentation {
  private marker: HTMLElement;
  private panel: HTMLElement;
  private core: HTMLElement;
  private traces: HTMLElement[];
  private pulse: HTMLElement;
  private echoRing: HTMLElement;
  private signal: HTMLElement;
  private echoes: HTMLElement[];
  private lock: HTMLElement;
  private flare: HTMLElement;
  private entranceSettled = false;
  private proximity = new LocationProximity();
  private panelWidth = 0;
  private panelHeight = 0;
  pinned = false;
  projection: ReturnType<typeof projectLocation> = { x: 0, y: 0, facing: -1, world: [0, 0, -1] };

  constructor(stage: HTMLElement) {
    this.marker = stage.querySelector<HTMLElement>("#marker")!;
    this.panel = stage.querySelector<HTMLElement>("#location-panel")!;
    this.core = this.marker.querySelector<HTMLElement>(".marker-core")!;
    this.traces = Array.from(this.marker.querySelectorAll<HTMLElement>(".marker-trace"));
    this.pulse = this.marker.querySelector<HTMLElement>(".marker-pulse")!;
    this.echoRing = this.marker.querySelector<HTMLElement>(".marker-echo-ring")!;
    this.signal = this.marker.querySelector<HTMLElement>(".marker-signal")!;
    this.echoes = Array.from(this.marker.querySelectorAll<HTMLElement>(".marker-echo"));
    this.lock = this.marker.querySelector<HTMLElement>(".marker-lock")!;
    this.flare = this.marker.querySelector<HTMLElement>(".marker-flare")!;
    this.measure();
  }
  measure(): void {
    this.panelWidth = this.panel.offsetWidth;
    this.panelHeight = this.panel.offsetHeight;
  }
  update(orientation: Orientation, camera: Camera, pointer: { x: number; y: number } | null,
    hit: boolean, pressed: boolean): void {
    this.projection = projectLocation(ISTANBUL, orientation, camera);
    const { x, y, facing } = this.projection;
    const distance = pointer ? Math.hypot(pointer.x - x, pointer.y - y) : Infinity;
    const open = this.proximity.update(facing, distance, hit, pressed, this.pinned);
    if (!this.proximity.visible) this.pinned = false;
    this.marker.hidden = !this.proximity.visible;
    this.marker.style.transform = `translate(${x}px, ${y}px)`;
    const left = Math.max(8, Math.min(camera.width - this.panelWidth - 8, x - this.panelWidth / 2));
    const above = y > this.panelHeight + 24;
    const top = Math.max(8, Math.min(camera.height - this.panelHeight - 8, above ? y - this.panelHeight - 18 : y + 18));
    this.panel.style.transform = `translate(${left}px, ${top}px)`;
    this.panel.style.setProperty("--anchor", `${Math.max(12, Math.min(this.panelWidth - 12, x - left))}px`);
    this.panel.dataset.side = above ? "above" : "below";
    this.panel.dataset.open = String(open);
    this.panel.setAttribute("aria-hidden", String(!open));
  }
  get open(): boolean { return this.proximity.open; }
  get visible(): boolean { return this.proximity.visible; }
  entrance(entrance: GlobeEntrance, camera: Camera): void {
    if (this.entranceSettled) return;
    this.marker.style.opacity = String(entrance.reveal);
    this.signal.style.transform = `scale(${camera.radius / 338})`;
    const time = entrance.elapsed, scan = entrance.phase === "scan" && time > 0.12;
    const frame = Math.floor(time * 14), gather = entrance.progress(0.2, 1.35);
    const fade = 1 - entrance.progress(1.35, 0.45);
    this.core.style.transform = scan ? `translateX(${Math.sin(frame * 2.1) * 3 * fade}px) scale(${0.65 + gather * 0.35})` : "";
    this.core.style.opacity = scan ? "0.85" : "1";
    for (let i = 0; i < this.traces.length; i++) {
      const trace = this.traces[i]!;
      const seed = frame * 1.9 + i * 2.3;
      trace.style.opacity = scan ? String((0.22 + (Math.sin(seed) + 1) * 0.15) * fade) : "0";
      trace.style.transform = `translate(${Math.sin(seed * 1.3) * 36 * (1 - gather)}px, ${(i - 3.5) * 9 * (1 - gather) + Math.sin(seed) * 3 * fade}px) scaleX(${(0.2 + (Math.cos(seed) + 1) * 0.28) * (1 - gather * 0.55)})`;
    }
    for (let i = 0; i < this.echoes.length; i++) {
      const offset = (i ? -1 : 1) * (1 - gather) * 27;
      this.echoes[i]!.style.transform = `translate(${offset}px, ${Math.sin(frame + i) * fade * 3}px)`;
      this.echoes[i]!.style.opacity = scan ? String(0.35 * fade) : "0";
    }
    const acquired = entrance.progress(0.7, 1.1), release = entrance.progress(1.75, 0.35);
    this.lock.style.opacity = time > 0.3 && time < 2.1 ? String(Math.min(1, entrance.progress(0.3, 0.25)) * (1 - release) * 0.7) : "0";
    this.lock.style.transform = `rotate(${(1 - acquired) * 38}deg) scale(${1.3 - acquired * 0.78})`;
    const pulse = entrance.progress(1.8, 1.0), echo = entrance.progress(2.0, 1.2);
    this.pulse.style.opacity = time >= 1.8 ? String((1 - pulse) ** 2 * 0.8) : "0";
    this.pulse.style.transform = `scale(${0.18 + (1 - (1 - pulse) ** 3) * 0.92})`;
    this.echoRing.style.opacity = time > 2 ? String((1 - echo) ** 2 * 0.5) : "0";
    this.echoRing.style.transform = `scale(${0.22 + (1 - (1 - echo) ** 3) * 1.15})`;
    this.flare.style.opacity = entrance.moving ? String(entrance.progress(0.25, 0.4) * (1 - entrance.progress(1.8, 0.8)) * 0.75) : "0";
    this.flare.style.transform = `scale(${0.45 + acquired * 0.4})`;
    this.entranceSettled = !entrance.moving;
  }
  resetEntrance(): void {
    this.entranceSettled = false;
    this.clear();
    this.marker.style.opacity = "0";
  }
  clear(): void {
    this.pinned = false; this.proximity.clear(); this.marker.hidden = true;
    this.panel.dataset.open = "false"; this.panel.setAttribute("aria-hidden", "true");
  }
}
