import type { StartupPhase } from './state';

/** Preparation signals drive the bar; its animation never decides readiness. */
export function loadingView(loader: HTMLElement, reduced: MediaQueryList) {
  const progress = loader.querySelector<HTMLElement>('[data-loading-progress]')!;
  const fill = progress.querySelector<HTMLElement>('.loading-progress-fill')!;
  const status = loader.querySelector<HTMLElement>('[data-loading-status]')!;
  const hint = loader.querySelector<HTMLElement>('[data-loading-hint]')!;
  let value = 0,
    disposed = false;
  return {
    progress(next: number) {
      value = Math.max(value, Math.min(100, Math.floor(next)));
      progress.dataset.progress = String(value);
      progress.setAttribute('aria-valuenow', String(value));
      fill.style.transform = `scaleX(${value / 100})`;
    },
    hint(touch: boolean) {
      hint.textContent = touch
        ? 'Swipe to explore. Touch and hold the background to change the film.'
        : 'Scroll to explore. Hold the background to change the film.';
      if (!reduced.matches)
        hint.animate([{ transform: 'translateY(100%)' }, { transform: 'translateY(0)' }], {
          duration: 280,
          easing: 'cubic-bezier(.22,1,.36,1)',
        });
    },
    phase(phase: StartupPhase) {
      const text =
        phase === 'ready'
          ? 'Ready to explore'
          : phase === 'static'
            ? 'Static view ready'
            : phase === 'fallback'
              ? 'Opening with a still image. Video will appear when available.'
              : phase === 'unavailable'
                ? 'Opening readable content; some media is unavailable.'
                : 'Preparing homepage';
      status.textContent = text;
      if (phase === 'fallback' || phase === 'unavailable') {
        progress.dataset.mode = phase;
        hint.textContent = phase === 'fallback' ? 'Still-image mode' : 'Content available';
        progress.setAttribute('aria-valuetext', hint.textContent);
      } else progress.removeAttribute('aria-valuetext');
    },
    async exit() {
      if (!reduced.matches)
        await Promise.allSettled(fill.getAnimations().map((animation) => animation.finished));
      if (disposed || reduced.matches) return;
      await Promise.all(
        [...loader.querySelectorAll<HTMLElement>('[data-loader-rise],[data-loader-fade]')].map(
          (node, i) =>
            node
              .animate(
                node.hasAttribute('data-loader-fade')
                  ? [{ opacity: 1 }, { opacity: 0 }]
                  : [{ transform: 'translateY(0)' }, { transform: 'translateY(-110%)' }],
                {
                  duration: 220,
                  delay: i * 24,
                  easing: 'cubic-bezier(.22,1,.36,1)',
                  fill: 'both',
                },
              )
              .finished.catch(() => {}),
        ),
      );
    },
    dispose() {
      disposed = true;
      fill.style.removeProperty('transform');
      for (const animation of loader.getAnimations({ subtree: true })) animation.cancel();
      progress.setAttribute('aria-valuenow', '0');
      progress.removeAttribute('aria-valuetext');
      hint.textContent = '';
      delete progress.dataset.progress;
      delete progress.dataset.mode;
    },
  };
}
