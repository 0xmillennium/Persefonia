import { frameAt, type Film } from './model';

export const BACKGROUNDS = [
  { id: 'robotic-arm', playback: 'scroll' },
  { id: 'tree', playback: 'scroll' },
  { id: 'waterfall', playback: 'loop' },
] as const;
export type Background = (typeof BACKGROUNDS)[number]['id'];
export type BackgroundKey = `${Background}:${number | 'opening' | 'final'}`;
export function isBackground(value: unknown): value is Background {
  return BACKGROUNDS.some((scene) => scene.id === value);
}
export function isScrollFilm(value: Background): value is Film {
  return value !== 'waterfall';
}
export function nextBackground(value: Background): Background {
  return BACKGROUNDS[
    (BACKGROUNDS.findIndex((scene) => scene.id === value) + 1) % BACKGROUNDS.length
  ]!.id;
}

/** Selection and page position do not own a looping video's playback clock. */
export class BackgroundTransport {
  film: Background = 'robotic-arm';
  desired = 0;
  presented: BackgroundKey | null = null;
  setProgress(progress: number) {
    this.desired = frameAt(progress);
  }
  present(key: BackgroundKey) {
    if (
      !key.startsWith(`${this.film}:`) ||
      (isScrollFilm(this.film) &&
        !key.endsWith(':opening') &&
        !key.endsWith(':final') &&
        key !== `${this.film}:${this.desired}`)
    )
      return false;
    this.presented = key;
    return true;
  }
  switchFilm() {
    this.film = nextBackground(this.film);
  }
  restore(film: Background) {
    this.film = film;
    this.presented = null;
  }
}
