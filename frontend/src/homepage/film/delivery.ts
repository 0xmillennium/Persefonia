import { filmCoverBox } from './cutouts';

export const FILM_READINESS_TIMEOUT = 6000;
export type LoopTier = 1280 | 1920;
export type FilmTier = 960 | 1600;

/** Select from the actual fixed cover crop, with hysteresis around tier changes. */
export function filmTier(width: number, height: number, current?: FilmTier): FilmTier {
  const [left, , right] = filmCoverBox(width, height);
  const required = right - left;
  const threshold = current === 1600 ? 1000 : 1200;
  return required > threshold ? 1600 : 960;
}

export function loopTier(width: number, height: number, current?: LoopTier): LoopTier {
  if (width <= 700 && height > width) return 1280;
  const [left, , right] = filmCoverBox(width, height);
  return right - left > (current === 1920 ? 1320 : 1440) ? 1920 : 1280;
}
