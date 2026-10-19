import type { MediaReadiness } from '../contracts/readiness';

export const STARTUP_DEADLINE = 12000;
export type StartupPhase = 'loading' | 'ready' | 'static' | 'fallback' | 'unavailable';

/** Progress describes completed preparation, never elapsed time or a claimed byte count. */
export function startupState(
  font: boolean,
  image: boolean,
  media: MediaReadiness,
  expired = false,
) {
  const complete = font && image;
  const phase: StartupPhase =
    complete && media.state === 'video'
      ? 'ready'
      : complete && media.state === 'static'
        ? 'static'
        : complete && media.state === 'fallback'
          ? 'fallback'
          : expired
            ? complete && media.state !== 'unavailable'
              ? 'fallback'
              : 'unavailable'
            : 'loading';
  const progress =
    phase === 'ready' || phase === 'static'
      ? 100
      : Math.min(
          99,
          Math.floor(
            (font ? 10 : 0) + (image ? 15 : 0) + Math.max(0, Math.min(1, media.progress)) * 75,
          ),
        );
  return { phase, progress };
}
