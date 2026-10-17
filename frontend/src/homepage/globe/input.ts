import { sphereIntersection, type Camera } from './geometry';
import { GlobeGesture, type PointerSample } from './gesture';
import { GlobeOrientation } from './orientation';

export class GlobeInput {
  readonly gesture: GlobeGesture;
  hover: { x: number; y: number } | null = null;
  private abort = new AbortController();

  constructor(
    private canvas: HTMLCanvasElement,
    private camera: () => Camera,
    private orientation: GlobeOrientation,
    private changed: (reason?: 'cancel') => void,
    private tapped: (x: number, y: number) => void = () => {},
  ) {
    this.gesture = new GlobeGesture(orientation);
    const options = { signal: this.abort.signal };
    document.addEventListener(
      'keydown',
      (event) => {
        if (!['Shift', 'Control', 'Alt', 'Meta'].includes(event.key))
          delete canvas.dataset.focusInput;
      },
      options,
    );
    const sample = (e: PointerEvent): PointerSample => {
      const rect = canvas.getBoundingClientRect();
      return {
        id: e.pointerId,
        x: e.clientX - rect.left,
        y: e.clientY - rect.top,
        time: e.timeStamp,
        touch: e.pointerType === 'touch',
      };
    };
    canvas.addEventListener(
      'pointerdown',
      (e) => {
        if (e.button !== 0 || !e.isPrimary) return;
        canvas.dataset.focusInput = 'pointer';
        const s = sample(e);
        if (!sphereIntersection(s.x, s.y, this.camera())) return;
        if (!this.gesture.press(s, this.camera().radius)) return;
        this.hover = null;
        if (!s.touch) {
          // Keep keyboard continuation available without showing a pointer ring.
          e.preventDefault();
          canvas.focus({ preventScroll: true });
          canvas.setPointerCapture(s.id);
        }
        this.changed();
      },
      options,
    );
    canvas.addEventListener(
      'pointermove',
      (e) => {
        const s = sample(e);
        if (this.gesture.owner !== null) {
          const result = this.gesture.move(s);
          if (result === 'ignored') return;
          if (result === 'scroll') this.cancel();
          if (result === 'dragging' && !canvas.hasPointerCapture(s.id))
            canvas.setPointerCapture(s.id);
        } else if (e.pointerType !== 'touch') {
          this.hover = { x: s.x, y: s.y };
        }
        this.changed();
      },
      options,
    );
    canvas.addEventListener(
      'pointerup',
      (e) => {
        const s = sample(e),
          release = this.gesture.release(s.id, s.time);
        if (!release.handled) return;
        if (canvas.hasPointerCapture(s.id)) canvas.releasePointerCapture(s.id);
        this.hover = s.touch ? null : { x: s.x, y: s.y };
        if (s.touch && release.tap) this.tapped(s.x, s.y);
        this.changed();
      },
      options,
    );
    for (const type of ['pointercancel', 'lostpointercapture'] as const) {
      canvas.addEventListener(
        type,
        (e) => {
          if (e.pointerId === this.gesture.owner) this.cancel();
        },
        options,
      );
    }
    canvas.addEventListener(
      'pointerleave',
      () => {
        if (this.gesture.owner === null) {
          this.hover = null;
          this.changed();
        }
      },
      options,
    );
    canvas.addEventListener(
      'keydown',
      (e) => {
        const increments: Record<string, [number, number]> = {
          ArrowLeft: [-0.15, 0],
          ArrowRight: [0.15, 0],
          ArrowUp: [0, -0.12],
          ArrowDown: [0, 0.12],
        };
        if (e.key === 'Escape') {
          this.cancel();
          return;
        }
        const increment = increments[e.key];
        if (!increment) return;
        e.preventDefault();
        this.cancel();
        this.orientation.drag(...increment);
        this.changed();
      },
      options,
    );
    window.addEventListener('blur', () => this.cancel(), options);
  }

  cancel(): void {
    const id = this.gesture.owner;
    this.gesture.cancel();
    this.hover = null;
    if (id !== null && this.canvas.hasPointerCapture(id)) this.canvas.releasePointerCapture(id);
    this.changed('cancel');
  }
  dispose(): void {
    this.abort.abort();
    this.cancel();
    delete this.canvas.dataset.focusInput;
  }
}
