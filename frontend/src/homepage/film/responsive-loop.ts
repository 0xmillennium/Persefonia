import { LoopFilm } from './loop-film';
import type { LoopTier } from './delivery';

/** Retain the playing film while one resolution replacement prepares. */
export class ResponsiveLoop {
  private current: LoopFilm;
  private candidate: LoopFilm | null = null;
  private disposed = false;
  private suspended = false;
  private lastSource: LoopFilm | null = null;
  private lastRevision = -1;
  private serial = 0;
  get revision() {
    if (this.current !== this.lastSource || this.current.revision !== this.lastRevision) {
      this.lastSource = this.current;
      this.lastRevision = this.current.revision;
      this.serial++;
    }
    return this.serial;
  }
  constructor(
    private parent: HTMLElement,
    private tier: LoopTier,
    private changed: () => void,
  ) {
    this.current = this.create(tier);
  }
  private create(tier: LoopTier) {
    const film = new LoopFilm(this.parent, tier, () => {
      if (this.disposed) return;
      if (film === this.candidate && film.videoReady) {
        this.current.dispose();
        this.current = film;
        this.candidate = null;
      }
      this.changed();
    });
    return film;
  }
  setTier(tier: LoopTier) {
    if (this.disposed || tier === this.tier) return;
    this.tier = tier;
    this.candidate?.dispose();
    this.candidate = null;
  }
  prepare() {
    if (this.disposed) return;
    // Before first use there is no presentation to preserve.
    if (!this.current.prepared && this.current.tier !== this.tier) {
      this.current.dispose();
      this.current = this.create(this.tier);
    }
    this.current.prepare();
    if (this.current.tier !== this.tier && !this.candidate) {
      this.candidate = this.create(this.tier);
      this.candidate.suspend(this.suspended);
      this.candidate.prepare(this.current.time);
    }
  }
  get videoReady() {
    return this.current.videoReady;
  }
  get image() {
    return this.current.image;
  }
  get posterUrl() {
    return this.current.posterUrl;
  }
  get failed() {
    return this.current.failed;
  }
  get blocked() {
    return this.current.blocked;
  }
  retry() {
    this.current.retry();
    this.candidate?.retry();
  }
  setPlayback(allowed: boolean, observe: boolean) {
    this.current.setPlayback(allowed, observe);
  }
  show(visible: boolean, bounds: readonly number[], opacity: number) {
    this.current.show(visible, bounds, opacity);
  }
  suspend(value: boolean) {
    this.suspended = value;
    this.current.suspend(value);
    this.candidate?.suspend(value);
  }
  dispose() {
    this.disposed = true;
    this.current.dispose();
    this.candidate?.dispose();
    this.candidate = null;
  }
}
