import type { Film } from './backgrounds';
import { videoVersion } from 'virtual:home-media';
import { FILM_READINESS_TIMEOUT, type FilmTier } from './delivery';
interface Entry {
  film: Film;
  video: HTMLVideoElement;
  tier: FilmTier;
  desired: number;
  index: number;
  pending: number;
  pendingSince: number;
  interval: number;
  failed: boolean;
  recoverable: boolean;
  retries: number;
  recovery: number;
  attempt: number;
  deadline: number;
  abort: AbortController;
}

/** One retained presentation and at most one preparing replacement per film. */
export class SeekFilm {
  private entries = new Map<Film, Entry>();
  private replacements = new Map<Film, Entry>();
  private disposed = false;
  private suspended = false;
  constructor(
    private parent: HTMLElement,
    private tier: FilmTier,
    private changed: () => void,
  ) {}
  private source(film: Film, entry: Entry) {
    return `/assets/home-videos/${videoVersion}/${entry.tier}/${film}.mp4${entry.attempt ? `?retry=${entry.attempt}` : ''}`;
  }
  private create(film: Film, index: number): Entry {
    const video = document.createElement('video');
    video.className = 'film-presentation';
    video.muted = video.playsInline = true;
    video.preload = 'auto';
    video.hidden = true;
    video.setAttribute('aria-hidden', 'true');
    const entry: Entry = {
      film,
      video,
      tier: this.tier,
      desired: index,
      index: -1,
      pending: -1,
      pendingSince: 0,
      interval: 1000 / 30,
      failed: false,
      recoverable: false,
      retries: 0,
      recovery: 0,
      attempt: 0,
      deadline: 0,
      abort: new AbortController(),
    };
    const options = { signal: entry.abort.signal };
    const settled = () => {
      if (
        this.disposed ||
        (entry.failed && !entry.recoverable) ||
        video.seeking ||
        video.readyState < 2
      )
        return;
      this.seek(entry, entry.desired);
      this.promote(film);
      this.changed();
    };
    video.addEventListener('loadeddata', settled, options);
    video.addEventListener('seeked', settled, options);
    video.addEventListener('error', () => this.fail(entry), options);
    video.addEventListener('loadedmetadata', () => this.seek(entry, entry.desired), options);
    video.addEventListener(
      'progress',
      () => {
        this.changed();
        if (this.suspended || entry.index === entry.desired) return;
        if (entry.failed) {
          if (entry.recoverable) {
            this.clearRecovery(entry);
            this.recover(entry);
          }
          return;
        }
        this.clearDeadline(entry);
        this.watch(entry);
      },
      options,
    );
    video.src = this.source(film, entry);
    this.parent.prepend(video);
    this.watch(entry);
    return entry;
  }
  setTier(tier: FilmTier) {
    if (tier === this.tier || this.disposed) return;
    this.tier = tier;
    for (const entry of this.replacements.values()) this.release(entry);
    this.replacements.clear();
  }
  request(film: Film, index: number) {
    if (this.disposed) return;
    let entry = this.entries.get(film);
    if (!entry) {
      entry = this.create(film, index);
      this.entries.set(film, entry);
    }
    this.seek(entry, index);
    if (entry.tier !== this.tier) {
      let replacement = this.replacements.get(film);
      if (!replacement) {
        replacement = this.create(film, index);
        this.replacements.set(film, replacement);
      }
      this.seek(replacement, index);
      this.promote(film);
    }
  }
  private seek(entry: Entry, index: number) {
    entry.desired = index;
    if (entry.failed && !entry.recoverable) return;
    this.reconcile(entry);
    if (
      entry.video.readyState >= 1 &&
      !entry.video.seeking &&
      entry.pending !== index &&
      entry.index !== index
    ) {
      entry.pending = index;
      entry.pendingSince = performance.now();
      entry.video.currentTime = (index + 0.25) / 30;
    }
    if (entry.index !== index || entry.video.seeking) this.watch(entry);
  }
  private reconcile(entry: Entry) {
    const video = entry.video;
    if (
      this.disposed ||
      (entry.failed && !entry.recoverable) ||
      video.error ||
      video.seeking ||
      video.readyState < 2
    )
      return;
    // Browser state can settle before its queued seeked event is delivered.
    entry.index = Math.min(191, Math.floor(video.currentTime * 30 + 0.0001));
    if (entry.pending === entry.index) {
      const elapsed = Math.max(1000 / 60, Math.min(100, performance.now() - entry.pendingSince));
      entry.interval = entry.interval * 0.8 + elapsed * 0.2;
    }
    entry.pending = -1;
    this.clearDeadline(entry);
    if (entry.index === entry.desired && entry.recoverable) {
      entry.failed = false;
      entry.recoverable = false;
      this.clearRecovery(entry);
    }
  }
  private promote(film: Film) {
    const entry = this.replacements.get(film);
    if (!entry) return;
    this.reconcile(entry);
    if (
      entry.failed ||
      entry.video.error ||
      entry.video.seeking ||
      entry.video.readyState < 2 ||
      entry.index !== entry.desired
    )
      return;
    const previous = this.entries.get(film);
    this.entries.set(film, entry);
    this.replacements.delete(film);
    if (previous) this.release(previous);
  }
  private watch(entry: Entry) {
    if (entry.deadline || entry.failed || this.disposed || this.suspended) return;
    entry.deadline = window.setTimeout(() => this.fail(entry, true), FILM_READINESS_TIMEOUT);
  }
  private clearDeadline(entry: Entry) {
    clearTimeout(entry.deadline);
    entry.deadline = 0;
  }
  private fail(entry: Entry, timedOut = false) {
    this.clearDeadline(entry);
    if (this.disposed || entry.abort.signal.aborted) return;
    if (timedOut) {
      this.reconcile(entry);
      if (
        !entry.video.error &&
        !entry.video.seeking &&
        entry.video.readyState >= 2 &&
        entry.index === entry.desired
      ) {
        this.changed();
        return;
      }
    }
    entry.failed = true;
    entry.recoverable = timedOut;
    this.recover(entry);
    this.changed();
  }
  private recover(entry: Entry) {
    // Give an idle request another readiness interval before retrying once.
    // Progress or healthy late readiness preserves the original download.
    if (
      this.suspended ||
      entry.recovery ||
      entry.retries >= 1 ||
      (!entry.recoverable && entry.video.error?.code !== 2)
    )
      return;
    entry.recovery = window.setTimeout(
      () => {
        entry.recovery = 0;
        entry.retries++;
        this.restart(entry);
        this.changed();
      },
      entry.recoverable ? FILM_READINESS_TIMEOUT : 250,
    );
  }
  private clearRecovery(entry: Entry) {
    clearTimeout(entry.recovery);
    entry.recovery = 0;
  }
  private restart(entry: Entry) {
    this.clearDeadline(entry);
    this.clearRecovery(entry);
    entry.failed = entry.recoverable = false;
    entry.index = entry.pending = -1;
    entry.attempt++;
    entry.video.src = this.source(entry.film, entry);
    entry.video.load();
    this.watch(entry);
  }
  get(film: Film, index: number) {
    this.promote(film);
    const entry = this.entries.get(film);
    if (entry) this.reconcile(entry);
    return entry &&
      !entry.failed &&
      !entry.video.error &&
      entry.video.readyState >= 2 &&
      !entry.video.seeking &&
      entry.index === index
      ? entry.video
      : undefined;
  }
  failed(film: Film) {
    return this.entries.get(film)?.failed ?? false;
  }
  hasVisual(film: Film) {
    return (this.entries.get(film)?.index ?? -1) >= 0;
  }
  frameInterval(film: Film) {
    return this.entries.get(film)?.interval ?? 1000 / 30;
  }
  get hasError() {
    return [...this.entries.values(), ...this.replacements.values()].some((entry) => entry.failed);
  }
  retry() {
    for (const entry of [...this.entries.values(), ...this.replacements.values()]) {
      if (!entry.failed) continue;
      entry.retries = 0;
      this.restart(entry);
    }
  }
  suspend(value: boolean) {
    this.suspended = value;
    for (const entry of [...this.entries.values(), ...this.replacements.values()]) {
      if (value) {
        this.clearDeadline(entry);
        this.clearRecovery(entry);
      } else {
        if (entry.failed) this.recover(entry);
        if (entry.index !== entry.desired || entry.video.seeking) this.watch(entry);
      }
    }
  }
  show(film: Film | null, bounds: readonly number[], opacity: number) {
    for (const [name, { video }] of this.entries) {
      video.hidden = film !== name;
      if (video.hidden) continue;
      video.style.left = `${bounds[0]}px`;
      video.style.top = `${bounds[1]}px`;
      video.style.width = `${bounds[2]! - bounds[0]!}px`;
      video.style.height = `${bounds[3]! - bounds[1]!}px`;
      video.style.opacity = String(opacity);
    }
  }
  private release(entry: Entry) {
    this.clearDeadline(entry);
    this.clearRecovery(entry);
    entry.abort.abort();
    entry.video.removeAttribute('src');
    entry.video.load();
    entry.video.remove();
  }
  dispose() {
    this.disposed = true;
    for (const entry of [...this.entries.values(), ...this.replacements.values()])
      this.release(entry);
    this.entries.clear();
    this.replacements.clear();
  }
}
