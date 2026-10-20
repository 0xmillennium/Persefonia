import { compactIdentity } from '../identity/compact/controller';
import { exitStaircase } from '../shared/motion/staircase';
import type { MediaReadiness } from './contracts/readiness';
import {
  STARTUP_DEADLINE,
  startupState,
  type StartupPhase,
  type ImagePreparation,
} from './loading/state';
import { loadingView } from './loading/view';

/** Coordinates usable startup, cinematic readiness and bounded graceful degradation. */
export function homepageReadiness(
  root: HTMLElement,
  loader: HTMLElement,
  historyVisit: boolean,
  reduced: MediaQueryList,
  onReady: () => void,
) {
  const abort = new AbortController();
  const view = loadingView(loader, reduced);
  const touch = matchMedia('(pointer: coarse)');
  let disposed = false,
    font = false,
    expired = false,
    released = false;
  let image: ImagePreparation = 'pending';
  let phase: StartupPhase = 'loading';
  let media: MediaReadiness = { state: 'loading', progress: 0 };
  let identity: ReturnType<typeof compactIdentity> | null = null;
  if (!historyVisit) {
    loader.hidden = false;
    identity = compactIdentity(loader.querySelector<HTMLElement>('[data-loading-identity]')!, true);
  }
  const hide = () => {
    loader.hidden = true;
    identity?.dispose();
    identity = null;
  };
  async function exit() {
    if (loader.hidden) return;
    // Clear the clipped content before the cover reveals the homepage.
    await view.exit();
    if (disposed) return;
    await exitStaircase(
      [...loader.querySelectorAll<HTMLElement>('.home-staircase i')],
      reduced.matches,
      false,
      loader.querySelector<HTMLElement>('.loader-sheet')!,
    );
    if (!disposed) hide();
  }
  const reconcile = () => {
    if (disposed || (released && phase !== 'fallback' && phase !== 'unavailable')) return;
    const next = startupState(font, image, media, expired);
    if (released && next.phase !== 'ready' && next.phase !== 'static') return;
    root.dataset.startupState = next.phase;
    if (!released) view.progress(next.progress);
    if (next.phase === 'loading') return;
    phase = next.phase;
    root.dataset.mediaFailure = String(phase === 'fallback' || phase === 'unavailable');
    if (phase !== 'unavailable' && image === 'ready') root.dataset.ready = 'true';
    view.phase(phase);
    clearTimeout(deadline);
    clearTimeout(hint);
    if (!released) {
      released = true;
      onReady();
      void exit();
    }
  };
  const deadline = window.setTimeout(() => {
    expired = true;
    reconcile();
  }, STARTUP_DEADLINE);
  const hint = window.setTimeout(() => {
    if (!disposed && !released) view.hint(touch.matches);
  }, 3000);
  touch.addEventListener(
    'change',
    () => {
      if (!disposed && !released && performance.now() - started >= 3000) view.hint(touch.matches);
    },
    { signal: abort.signal },
  );
  const started = performance.now();
  root.dataset.startupState = 'loading';
  return {
    media(value: MediaReadiness) {
      if (disposed) return;
      media = value;
      reconcile();
    },
    get complete() {
      return released;
    },
    track(hero: HTMLImageElement) {
      // Flush layout so used faces are requested, then settle those requests.
      // FontFaceSet.ready can wait for unrelated media/document loading in WebKit.
      root.getBoundingClientRect();
      const requestedFonts = [...document.fonts].filter((face) => face.status !== 'unloaded');
      void Promise.allSettled(requestedFonts.map((face) => face.loaded)).then(() => {
        if (disposed) return;
        font = true;
        reconcile();
      });
      void hero
        .decode()
        .then(() => {
          if (disposed) return;
          image = 'ready';
          reconcile();
        })
        .catch(() => {
          if (disposed) return;
          image = 'failed';
          reconcile();
        });
    },
    hide,
    dispose() {
      if (disposed) return;
      disposed = true;
      abort.abort();
      clearTimeout(deadline);
      clearTimeout(hint);
      view.dispose();
      hide();
      for (const name of ['ready', 'mediaFailure', 'startupState']) delete root.dataset[name];
    },
  };
}
