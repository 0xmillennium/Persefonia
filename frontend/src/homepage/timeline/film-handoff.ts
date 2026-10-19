/** Give the final film pose one painted frame before the gallery can cover it. */
export class FilmHandoff {
  private armed = false;
  private started: number | null = null;
  private painted: number | null = null;
  skip() {
    this.armed = false;
    this.started = this.painted = null;
  }
  sample(y: number, end: number, ready: boolean, now: number): number {
    if (y < end) {
      this.armed = true;
      this.started = this.painted = null;
      return y;
    }
    // History/direct navigation beyond the film must remain immediate. A broken
    // decoder must not trap the visual page either; static recovery stays live.
    if (!this.armed) return y;
    this.started ??= now;
    if (now - this.started >= 600 || (this.painted !== null && now > this.painted)) {
      this.skip();
      return y;
    }
    if (ready) this.painted ??= now;
    return end;
  }
}
