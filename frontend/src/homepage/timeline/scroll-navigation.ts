import { WheelGlide } from './wheel-glide';
import { wheelPath, wheelDestination } from './wheel-path';

export interface ScrollPath {
  readingEnd: number;
  cue: number;
  first: number;
  step: number;
  count: number;
  footer: number;
  bottom: number;
  filmStep: number;
}
/** Own wheel arbitration and cancellation; geometry and content remain page-owned. */
export function scrollNavigation(options: {
  signal: AbortSignal;
  reduced: MediaQueryList;
  path(): ScrollPath;
  tunerWheel(event: WheelEvent): boolean;
  beforeWheel(): void;
  cancelRestore(): void;
  changed(): void;
}) {
  const events = { signal: options.signal };
  const glide = new WheelGlide();
  let glideExpected = scrollY;
  window.addEventListener(
    'wheel',
    (event) => {
      options.cancelRestore();
      if (options.tunerWheel(event)) {
        glide.cancel();
        return;
      }
      if (
        document.documentElement.dataset.menuOpen === 'true' ||
        options.reduced.matches ||
        event.defaultPrevented ||
        !event.cancelable ||
        event.ctrlKey ||
        event.metaKey ||
        Math.abs(event.deltaX) >= Math.abs(event.deltaY) ||
        (event.deltaMode === WheelEvent.DOM_DELTA_PIXEL && Math.abs(event.deltaY) < 40) ||
        (event.target as Element).closest(
          'input:not([type="radio"]),textarea,select,[contenteditable="true"]',
        )
      ) {
        glide.cancel();
        return;
      }
      options.beforeWheel();
      const path = options.path();
      const { readingEnd, footer, first, step, count } = path;
      const stops = wheelPath(
        readingEnd,
        [path.cue, ...Array.from({ length: count ? count + 6 : 0 }, (_, i) => first + i * step)],
        footer,
        path.bottom,
        path.filmStep,
      );
      if (glide.active && Math.abs(scrollY - glideExpected) > 1) glide.cancel();
      const origin = glide.active ? glide.target : scrollY;
      const top = wheelDestination(stops, origin, Math.sign(event.deltaY));
      event.preventDefault();
      if (top === origin) return;
      const entrance = first;
      const lastVisible = first + (count + 5) * step;
      const crossing =
        (Math.min(scrollY, top) < entrance - 0.5 && Math.max(scrollY, top) > readingEnd + 0.5) ||
        (Math.min(scrollY, top) < footer - 0.5 && Math.max(scrollY, top) > lastVisible + 0.5);
      glide.cancel();
      glideExpected = scrollY;
      glide.retarget(
        scrollY,
        top - scrollY,
        0,
        stops[stops.length - 1]!,
        performance.now(),
        crossing ? 700 : 180,
      );
      options.changed();
    },
    { ...events, passive: false },
  );
  window.addEventListener(
    'keydown',
    (event) => {
      if (['ArrowUp', 'ArrowDown', 'PageUp', 'PageDown', 'Home', 'End', ' '].includes(event.key)) {
        glide.cancel();
        options.cancelRestore();
      }
    },
    events,
  );
  window.addEventListener(
    'pointerdown',
    () => {
      glide.cancel();
      options.cancelRestore();
    },
    { ...events, passive: true, capture: true },
  );
  window.addEventListener('blur', () => glide.cancel(), events);
  return {
    cancel() {
      glide.cancel();
    },
    update(now: number) {
      if (glide.active && Math.abs(scrollY - glideExpected) > 1) glide.cancel();
      if (!glide.active) return;
      scrollTo({ top: glide.sample(now), behavior: 'instant' });
      glideExpected = scrollY;
      if (glide.active) options.changed();
    },
    move(top: number, transition: boolean) {
      glide.cancel();
      if (transition) {
        glideExpected = scrollY;
        glide.retarget(
          scrollY,
          top - scrollY,
          Math.min(scrollY, top),
          Math.max(scrollY, top),
          performance.now(),
          700,
        );
      } else window.scrollTo({ top, behavior: 'instant' });
      options.changed();
    },
  };
}
