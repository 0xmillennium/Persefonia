import type { PointerSample } from "./core";
import { PointerPath } from "./pointer-path";

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
  private readonly reduced = matchMedia("(prefers-reduced-motion: reduce)");
  private readonly abort = new AbortController();
  get pending(): boolean { return this.path.pending; }
  get droppedSamples(): number { return this.path.dropped; }
  constructor(private readonly svg: SVGSVGElement, enabled: () => boolean, private readonly wake: () => void = () => {}) {
    svg.addEventListener("pointermove", e => {
      this.lastPointer = { x: e.clientX, y: e.clientY };
      if (e.pointerType === "touch") this.lastTouch = this.lastPointer;
      if (!acceptsHover(e) || this.reduced.matches || !enabled()) return;
      this.lastTouch = undefined;
      const coalesced = e.getCoalescedEvents?.() ?? [];
      this.observe(coalesced.length ? coalesced : [e]);
    }, { signal: this.abort.signal });
    for (const type of ["pointerdown", "pointerup"] as const) svg.addEventListener(type, e => {
      if (e.pointerType === "touch") {
        this.deactivate(); this.lastTouch = { x: e.clientX, y: e.clientY };
      }
    }, { signal: this.abort.signal });
    svg.addEventListener("mousemove", e => {
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
      svg.addEventListener(event, () => this.deactivate(), { signal: this.abort.signal });
    this.reduced.addEventListener("change", () => this.deactivate(), { signal: this.abort.signal });
    window.addEventListener("blur", () => this.deactivate(), { signal: this.abort.signal });
  }
  private observe(events: readonly MouseEvent[]): void {
    const matrix = this.svg.getScreenCTM();
    if (!matrix) return;
    const inverse = matrix.inverse();
    for (const event of events) {
      const point = new DOMPoint(event.clientX, event.clientY).matrixTransform(inverse);
      this.path.push({ x: point.x, y: point.y, active: true, elapsedSeconds: this.previousTimestamp === undefined
        ? undefined : Math.max(.001, (event.timeStamp - this.previousTimestamp) / 1000) });
      this.previousTimestamp = event.timeStamp;
    }
    this.wake();
  }
  deactivate(): void {
    this.previousTimestamp = undefined; this.lastPointer = undefined;
    this.path.push({ x: -100, y: -100, active: false }); this.wake();
  }
  take(): readonly PointerSample[] | undefined { return this.path.take(); }
  dispose(): void { this.path.clear(); this.abort.abort(); }
}
