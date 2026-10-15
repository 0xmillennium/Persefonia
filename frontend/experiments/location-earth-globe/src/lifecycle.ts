// One RAF owner; static/pressed/reduced scenes sleep until invalidated.
export class GlobeLifecycle {
  private raf = 0;
  private previous = 0;
  private visible = false;
  private available = true;
  private sceneActive = true;
  private pageActive = true;
  private abort = new AbortController();
  private observer: IntersectionObserver;
  disposed = false;

  constructor(element: HTMLElement, private draw: (seconds: number) => boolean,
    private interrupted: () => void) {
    const options = { signal: this.abort.signal };
    document.addEventListener("visibilitychange", () => this.reconcile(), options);
    window.addEventListener("pagehide", () => { this.pageActive = false; this.reconcile(); }, options);
    window.addEventListener("pageshow", () => { this.pageActive = true; this.reconcile(); }, options);
    this.observer = new IntersectionObserver(entries => {
      this.visible = entries[0]?.isIntersecting ?? false;
      this.reconcile();
    });
    this.observer.observe(element);
  }
  get active(): boolean { return !this.disposed && this.visible && this.available && this.sceneActive && this.pageActive && !document.hidden; }
  get scheduled(): boolean { return this.raf !== 0; }
  request(): void {
    if (!this.active || this.raf) return;
    this.raf = requestAnimationFrame(this.tick);
  }
  private tick = (time: number): void => {
    this.raf = 0;
    if (!this.active) return;
    const seconds = this.previous ? Math.max(0, (time - this.previous) / 1000) : 0;
    const moving = this.draw(seconds);
    this.previous = moving ? time : 0;
    if (moving) this.request();
  };
  private reconcile(): void {
    this.previous = 0;
    if (this.active) this.request();
    else { this.stop(); this.interrupted(); }
  }
  setAvailable(value: boolean): void { this.available = value; this.reconcile(); }
  setSceneActive(value: boolean): void { if(value===this.sceneActive)return;this.sceneActive=value;this.reconcile(); }
  private stop(): void { cancelAnimationFrame(this.raf); this.raf = 0; this.previous = 0; }
  dispose(): void {
    if (this.disposed) return;
    this.disposed = true;
    this.stop(); this.abort.abort(); this.observer.disconnect();
  }
}
