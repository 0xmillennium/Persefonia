import type { MediaReadiness } from '../contracts/readiness';

/** A short decoded lead supports the first movement without downloading the entire film. */
export function videoReadiness(
  video: HTMLVideoElement,
  decoded: boolean,
  playing = true,
  pausedSeek = false,
): MediaReadiness {
  const target = Math.min(0.25, Math.max(0, video.duration - video.currentTime));
  let ahead = 0;
  for (let i = 0; i < (video.buffered?.length ?? 0); i++) {
    if (
      video.buffered.start(i) <= video.currentTime + 0.01 &&
      video.buffered.end(i) >= video.currentTime
    )
      ahead = Math.max(ahead, video.buffered.end(i) - video.currentTime);
  }
  // A completed seek can precede Firefox's TimeRanges update; HAVE_ENOUGH_DATA
  // already guarantees data through the end, independently of that snapshot.
  const buffered =
    pausedSeek && video.readyState >= 4 ? 1 : target > 0 ? Math.min(1, ahead / target) : 0;
  const ready = decoded && playing && buffered === 1;
  return {
    state: ready ? 'video' : 'loading',
    progress: ready
      ? 1
      : Math.min(0.95, (video.readyState >= 1 ? 0.1 : 0) + buffered * 0.7 + (decoded ? 0.15 : 0)),
  };
}
