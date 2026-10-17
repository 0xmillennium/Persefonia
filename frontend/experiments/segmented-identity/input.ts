import type { PointerSample } from "./core";
import { PointerPath } from "./pointer-path";
import type { Vec } from "./math";

type HoverObservation = Pick<PointerEvent, "pointerType" | "isPrimary" | "isTrusted" | "buttons" | "pressure" | "width" | "height">;
export function acceptsHover(event: HoverObservation): boolean {
  // Firefox's desktop input bridge can leave the device type unidentified.
  // Accept only its trusted, primary, zero-pressure point-sized hover. Known
  // touch devices and unidentified contact events remain static.
  return event.isPrimary && (event.pointerType === "mouse" || event.pointerType === "pen"
    || event.pointerType === "" && event.isTrusted && event.buttons === 0 && event.pressure === 0
      && event.width <= 1 && event.height <= 1);
}

export class InputAdapter {
  private readonly path = new PointerPath();
  private lastPointer: { x: number; y: number } | undefined;
  private lastTouch: { x: number; y: number } | undefined;
  private previousTimestamp: number | undefined;
  private contact: { id: number; x: number; y: number; dragging: boolean } | undefined;
  private releaseFrame = 0;
  private readonly reduced = matchMedia("(prefers-reduced-motion: reduce)");
  private readonly abort = new AbortController();
  get pending(): boolean { return this.path.pending; }
  get droppedSamples(): number { return this.path.dropped; }
  constructor(private readonly svg: SVGSVGElement, enabled: () => boolean, private readonly wake: () => void = () => {},
    private readonly viewport?: { origin: () => Vec; touch?: boolean }) {
    const target = viewport ? window : svg;
    target.addEventListener("pointermove", (event: Event) => {
      const e = event as PointerEvent;
      this.lastPointer = { x: e.clientX, y: e.clientY };
      if (e.pointerType === "touch") {
        this.lastTouch = this.lastPointer;
        const contact = this.contact;
        if (!contact || e.pointerId !== contact.id) return;
        if (this.reduced.matches || !enabled()) { this.deactivate(); return; }
        if (!contact.dragging) {
          const dx = e.clientX - contact.x, dy = e.clientY - contact.y;
          if (Math.hypot(dx, dy) < 6) return;
          if (Math.abs(dy) >= Math.abs(dx)) { this.deactivate(); return; }
          contact.dragging = true;
          this.svg.setPointerCapture(e.pointerId);
        }
        e.preventDefault();
        const coalesced = e.getCoalescedEvents?.() ?? [];
        this.observe(coalesced.length ? coalesced : [e]);
        return;
      }
      if (!acceptsHover(e) || this.reduced.matches || !enabled()) return;
      if (this.contact || this.releaseFrame) this.deactivate();
      this.lastTouch = undefined;
      const coalesced = e.getCoalescedEvents?.() ?? [];
      this.observe(coalesced.length ? coalesced : [e]);
    }, { signal: this.abort.signal });
    target.addEventListener("pointerdown", (event: Event) => {
      const e = event as PointerEvent;
      if (e.pointerType === "touch") {
        this.deactivate(); this.lastTouch = { x: e.clientX, y: e.clientY };
        if (this.viewport?.touch && e.isPrimary && enabled() && !this.reduced.matches
          && e.target instanceof Element && this.svg.contains(e.target) && e.target.closest('[data-fragment]')) {
          this.contact = { id: e.pointerId, x: e.clientX, y: e.clientY, dragging: false };
          this.observe([e]);
        }
      }
    }, { signal: this.abort.signal });
    target.addEventListener("pointerup", (event: Event) => {
      const e = event as PointerEvent;
      if (e.pointerType !== "touch") return;
      this.lastTouch = { x: e.clientX, y: e.clientY };
      if (e.pointerId !== this.contact?.id) return;
      this.contact = undefined;
      if (this.svg.hasPointerCapture(e.pointerId)) this.svg.releasePointerCapture(e.pointerId);
      // Let the last contact sample reach the physics tick before clearing it.
      this.releaseFrame = requestAnimationFrame(() => { this.releaseFrame = 0; this.deactivate(); });
    }, { signal: this.abort.signal });
    this.svg.addEventListener("lostpointercapture", event => {
      if (event.target === this.svg && event.pointerId === this.contact?.id) this.deactivate();
    }, { signal: this.abort.signal });
    target.addEventListener("mousemove", (event: Event) => {
      const e = event as MouseEvent;
      // Some desktop bridges expose only compatibility mouse events after a
      // touch. Do not duplicate pointer observations or replay synthetic touch
      // mouse events. Known touch coordinates allow for integer rounding.
      const matches = (point: { x: number; y: number } | undefined) => point
        && Math.abs(e.clientX - point.x) <= 1 && Math.abs(e.clientY - point.y) <= 1;
      const source = e as MouseEvent & { sourceCapabilities?: { firesTouchEvents: boolean }; mozInputSource?: number };
      if (!e.isTrusted || e.buttons !== 0 || this.reduced.matches || !enabled()
        || source.sourceCapabilities?.firesTouchEvents || source.mozInputSource === 5
        || matches(this.lastPointer) || matches(this.lastTouch)) return;
      this.lastTouch = undefined;
      this.observe([e]);
    }, { signal: this.abort.signal });
    for (const event of ["pointerleave", "pointercancel", "mouseleave"])
      (viewport ? document.documentElement : svg).addEventListener(event, () => this.deactivate(), { signal: this.abort.signal });
    this.reduced.addEventListener("change", () => this.deactivate(), { signal: this.abort.signal });
    window.addEventListener("blur", () => this.deactivate(), { signal: this.abort.signal });
  }
  private observe(events: readonly MouseEvent[]): void {
    const matrix = this.svg.getScreenCTM();
    if (!matrix) return;
    const inverse = matrix.inverse();
    const origin = this.viewport?.origin() ?? { x: 0, y: 0 };
    for (const event of events) {
      const point = new DOMPoint(event.clientX, event.clientY).matrixTransform(inverse);
      this.path.push({ x: point.x-origin.x, y: point.y-origin.y, active: true, elapsedSeconds: this.previousTimestamp === undefined
        ? undefined : Math.max(.001, (event.timeStamp - this.previousTimestamp) / 1000) });
      this.previousTimestamp = event.timeStamp;
    }
    this.wake();
  }
  deactivate(): void {
    cancelAnimationFrame(this.releaseFrame); this.releaseFrame = 0;
    const contact = this.contact; this.contact = undefined;
    if (contact && this.svg.hasPointerCapture(contact.id)) this.svg.releasePointerCapture(contact.id);
    this.previousTimestamp = undefined; this.lastPointer = undefined;
    this.path.push({ x: -100, y: -100, active: false }); this.wake();
  }
  take(): readonly PointerSample[] | undefined { return this.path.take(); }
  dispose(): void { this.deactivate(); this.path.clear(); this.abort.abort(); }
}
