import type { MediaReadiness } from '../contracts/readiness';

export const STARTUP_DEADLINE = 12000;
export type StartupPhase = 'loading' | 'ready' | 'static' | 'fallback' | 'unavailable';
export type ImagePreparation = 'pending' | 'ready' | 'failed';

/** Progress describes completed preparation, never elapsed time or a claimed byte count. */
export function startupState(
  font: boolean,
  image: ImagePreparation,
  media: MediaReadiness,
  expired = false,
) {
  const complete = font && image === 'ready';
  const failed = font && image === 'failed';
  const phase: StartupPhase =
    complete && media.state === 'video'
      ? 'ready'
      : complete && media.state === 'static'
        ? 'static'
        : complete && media.state === 'fallback'
          ? 'fallback'
          : failed && media.state !== 'loading'
            ? media.state === 'unavailable'
              ? 'unavailable'
              : 'fallback'
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
            (font ? 10 : 0) +
              (image === 'ready' ? 15 : 0) +
              Math.max(0, Math.min(1, media.progress)) * 75,
          ),
        );
  return { phase, progress };
}
