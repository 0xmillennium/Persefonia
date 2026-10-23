import type { MediaReadiness } from '../contracts/readiness';
import { videoReadiness } from './startup';
import { posterVersion } from 'virtual:home-media';
import { SeekFilm } from './seek-film';
import { FILM_READINESS_TIMEOUT, type FilmTier } from './delivery';
import type { Film } from './backgrounds';

export type PosterPose = 'opening' | 'final';
export interface FilmPresentation {
  kind: 'video' | 'poster';
  key: `${Film}:${number | PosterPose}`;
  source: HTMLVideoElement | HTMLImageElement;
}
interface Poster {
  film: Film;
  tier: FilmTier;
  pose: PosterPose;
  image: HTMLImageElement;
  ready: boolean;
  failed: boolean;
  timedOut: boolean;
  disposed: boolean;
  deadline: number;
}

/** Native seeking with a bounded pair of static poses, independent of input/rendering. */
export class ScrollFilms {
  private video: SeekFilm;
  private posters = new Map<string, Poster>();
  private reduced = false;
  private suspended = false;
  private disposed = false;
  private attempt = 0;
  constructor(
    private parent: HTMLElement,
    private tier: FilmTier,
    private changed: () => void,
  ) {
    this.video = new SeekFilm(parent, tier, changed);
  }
  setReduced(value: boolean) {
    this.reduced = value;
    this.video.suspend(value || this.suspended);
  }
  setTier(tier: FilmTier) {
    this.tier = tier;
    this.video.setTier(tier);
  }
  request(film: Film, index: number, pose: PosterPose) {
    if (this.disposed || this.suspended) return;
    if (!this.reduced) this.video.request(film, index);
    if (this.reduced || index === 191 || this.video.failed(film) || !this.video.hasVisual(film))
      this.preparePoster(film, pose);
  }
  private watch(poster: Poster) {
    if (poster.ready || poster.failed || poster.timedOut || poster.deadline || this.suspended)
      return;
    poster.deadline = window.setTimeout(() => {
      poster.deadline = 0;
      if (poster.disposed || this.disposed) return;
      poster.timedOut = true;
      this.changed();
    }, FILM_READINESS_TIMEOUT);
  }
  private preparePoster(film: Film, pose: PosterPose) {
    const key = `${film}:${this.tier}:${pose}`;
    if (this.posters.has(key)) return;
    const image = new Image();
    image.className = 'film-presentation';
    image.dataset.filmPoster = pose;
    image.alt = '';
    image.hidden = true;
    image.decoding = 'async';
    const poster: Poster = {
      film,
      tier: this.tier,
      pose,
      image,
      ready: false,
      failed: false,
      timedOut: false,
      disposed: false,
      deadline: 0,
    };
    this.posters.set(key, poster);
    this.parent.prepend(image);
    image.src = `/assets/home-posters/${posterVersion}/${this.tier}/${film}/${pose}.webp${this.attempt ? `?retry=${this.attempt}` : ''}`;
    this.watch(poster);
    void image
      .decode()
      .then(() => {
        if (this.disposed || poster.disposed || poster.failed) return;
        clearTimeout(poster.deadline);
        poster.deadline = 0;
        poster.ready = true;
        poster.timedOut = false;
        this.changed();
      })
      .catch(() => {
        if (this.disposed || poster.disposed) return;
        clearTimeout(poster.deadline);
        poster.deadline = 0;
        poster.failed = true;
        this.changed();
      });
  }
  presentation(film: Film, index: number, pose: PosterPose): FilmPresentation | undefined {
    if (!this.reduced) {
      const video = this.video.get(film, index);
      if (video) return { kind: 'video', key: `${film}:${index}`, source: video };
      if (!this.video.failed(film) && this.video.hasVisual(film)) return;
    }
    const ready = [...this.posters.values()].filter(
      (p) =>
        p.film === film &&
        p.ready &&
        !p.disposed &&
        // Retain an already displayed tier during resize, but never promote a late,
        // obsolete higher tier over the current responsive request.
        (p.tier <= this.tier || !p.image.hidden),
    );
    const poster =
      ready.find((p) => p.tier === this.tier && p.pose === pose) ??
      ready.find((p) => p.pose === pose) ??
      ready[0];
    if (poster) return { kind: 'poster', key: `${film}:${poster.pose}`, source: poster.image };
  }
  readiness(film: Film, index: number, pose: PosterPose): MediaReadiness {
    const presentation = this.presentation(film, index, pose);
    if (presentation?.kind === 'video')
      return videoReadiness(presentation.source as HTMLVideoElement, true, true, true);
    if (presentation?.kind === 'poster' && (this.reduced || this.video.failed(film)))
      return { state: this.reduced ? 'static' : 'fallback', progress: this.reduced ? 1 : 0 };
    return { state: this.unavailable(film, pose) ? 'unavailable' : 'loading', progress: 0 };
  }
  hasVisual(film: Film, index: number, pose: PosterPose) {
    return (
      !!this.presentation(film, index, pose) ||
      (!this.reduced && !this.video.failed(film) && this.video.hasVisual(film))
    );
  }
  failed(film: Film) {
    return !this.reduced && this.video.failed(film);
  }
  frameInterval(film: Film) {
    return this.video.frameInterval(film);
  }
  unavailable(film: Film, pose: PosterPose) {
    const poster = this.posters.get(`${film}:${this.tier}:${pose}`);
    return (
      (this.reduced || this.video.failed(film)) && !!poster && (poster.failed || poster.timedOut)
    );
  }
  get hasError() {
    return this.video.hasError || [...this.posters.values()].some((p) => p.failed || p.timedOut);
  }
  retry() {
    this.attempt++;
    this.video.retry();
    for (const [key, poster] of this.posters)
      if (poster.failed || poster.timedOut) {
        this.release(poster);
        this.posters.delete(key);
      }
  }
  show(
    film: Film | null,
    index: number,
    pose: PosterPose,
    bounds: readonly number[],
    opacity: number,
  ) {
    const presentation = film ? this.presentation(film, index, pose) : undefined;
    this.video.show(
      film && presentation?.kind !== 'poster' && !this.reduced && !this.video.failed(film)
        ? film
        : null,
      bounds,
      opacity,
    );
    for (const poster of this.posters.values()) {
      const image = poster.image;
      image.hidden = image !== presentation?.source;
      if (image.hidden) continue;
      image.style.left = `${bounds[0]}px`;
      image.style.top = `${bounds[1]}px`;
      image.style.width = `${bounds[2]! - bounds[0]!}px`;
      image.style.height = `${bounds[3]! - bounds[1]!}px`;
      image.style.opacity = String(opacity);
    }
  }
  suspend(value: boolean) {
    this.suspended = value;
    this.video.suspend(value || this.reduced);
    for (const poster of this.posters.values()) {
      if (value) {
        clearTimeout(poster.deadline);
        poster.deadline = 0;
      } else this.watch(poster);
    }
  }
  private release(poster: Poster) {
    poster.disposed = true;
    clearTimeout(poster.deadline);
    poster.image.removeAttribute('src');
    poster.image.remove();
  }
  dispose() {
    this.disposed = true;
    this.video.dispose();
    for (const poster of this.posters.values()) this.release(poster);
    this.posters.clear();
  }
}
