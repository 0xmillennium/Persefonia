import { compactIdentity } from '../identity/compact/controller';
import { exitStaircase } from '../shared/motion/staircase';

type Resource = 'font' | 'image' | 'film';

/** Owns readiness, the bounded loader deadline and its asynchronous animation. */
export function homepageReadiness(
  root: HTMLElement,
  loader: HTMLElement,
  historyVisit: boolean,
  reduced: MediaQueryList,
  onReady: () => void,
) {
  const progress = loader.querySelector<HTMLOutputElement>('[data-loading-progress]')!;
  const settled = new Set<Resource>();
  let disposed = false;
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
    await document.fonts.ready;
    if (disposed) return;
    loader
      .querySelectorAll<HTMLElement>('.loader-aperture,[data-loading-identity],output,p')
      .forEach((node) => (node.hidden = true));
    await exitStaircase(
      [...loader.querySelectorAll<HTMLElement>('.home-staircase i')],
      reduced.matches,
      false,
      loader.querySelector<HTMLElement>('.loader-sheet')!,
    );
    if (!disposed) hide();
  }
  const ready = (resource: Resource) => {
    if (disposed || settled.has(resource)) return;
    settled.add(resource);
    progress.value = `${settled.size} / 3`;
    if (settled.size === 3) {
      clearTimeout(deadline);
      root.dataset.ready = 'true';
      onReady();
      void exit();
    }
  };
  const deadline = window.setTimeout(() => {
    if (settled.size < 3) {
      root.dataset.mediaFailure = 'true';
      hide();
    }
  }, 12000);
  return {
    ready,
    get complete() {
      return settled.size === 3;
    },
    track(image: HTMLImageElement) {
      void document.fonts.ready.then(() => ready('font'));
      void image
        .decode()
        .then(() => ready('image'))
        .catch(() => {
          if (disposed) return;
          root.dataset.mediaFailure = 'true';
          progress.value = 'Hero image unavailable';
        });
    },
    hide,
    dispose() {
      if (disposed) return;
      disposed = true;
      clearTimeout(deadline);
      delete root.dataset.ready;
      delete root.dataset.mediaFailure;
      for (const animation of loader.getAnimations({ subtree: true })) animation.cancel();
      hide();
    },
  };
}
