import { ISTANBUL, LocationProximity, projectLocation } from "./location";
import type { Camera, Orientation } from "./geometry";

export class LocationPresentation {
  private marker: HTMLElement;
  private panel: HTMLElement;
  private proximity = new LocationProximity();
  private panelWidth = 0;
  private panelHeight = 0;
  pinned = false;
  projection = { x: 0, y: 0, facing: -1 };

  constructor(stage: HTMLElement) {
    this.marker = stage.querySelector<HTMLElement>("#marker")!;
    this.panel = stage.querySelector<HTMLElement>("#location-panel")!;
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
  clear(): void {
    this.pinned = false; this.proximity.clear(); this.marker.hidden = true;
    this.panel.dataset.open = "false"; this.panel.setAttribute("aria-hidden", "true");
  }
}
