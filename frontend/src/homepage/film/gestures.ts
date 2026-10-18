import { Hold, type Modality } from './hold/state';
import { HoldClock } from './hold/clock';
import { PointerObserver, eligible } from './input';
import type { MotionSample } from './input';
import type { Background } from './backgrounds';

export interface FilmGestureState {
  enabled: boolean;
  suspended: boolean;
  disposed: boolean;
  first: boolean;
  reduced: boolean;
  visible: boolean;
  covered: boolean;
  width: number;
  height: number;
  film: Background;
  prefetchedFrom: Background | null;
}
/** Input owns capture, touch arbitration and dwell; media/render policy stays with the film. */
export function filmGestures(
  root: HTMLElement,
  hold: Hold,
  clock: HoldClock,
  state: () => FilmGestureState,
  callbacks: {
    invalidate(): void;
    request(): void;
    changed(): void;
    retry(modality: 'pointer' | 'keyboard'): void;
    prefetch(from: Background): void;
    motion(sample: MotionSample): void;
  },
) {
  const abort = new AbortController(),
    options = { signal: abort.signal };
  const pointer = new PointerObserver();
  let gesture: number | null = null,
    touchStartedAt = 0,
    touchIdentifier: number | null = null,
    prefetchTimer = 0;
  const touchSettled = (time: number) => hold.modality === 'touch' && time - touchStartedAt >= 200;
  const release = () => {
    callbacks.invalidate();
    clock.beforeEvent(hold, performance.now());
    hold.release();
    const id = gesture;
    gesture = null;
    if (id !== null && root.hasPointerCapture(id)) root.releasePointerCapture(id);
    callbacks.changed();
  };
  root.addEventListener(
    'pointerdown',
    (e) => {
      if (!state().enabled || state().suspended || state().disposed || gesture !== null) return;
      const excluded = !!(e.target as Element).closest('a,button,input,summary,[data-input-owner]');
      if (
        !eligible(
          e.button,
          e.isPrimary,
          excluded,
          { x: e.clientX, y: e.clientY },
          state().width,
          state().height,
        )
      )
        return;
      clock.beforeEvent(hold, performance.now());
      callbacks.retry('pointer');
      if (hold.press({ x: e.clientX, y: e.clientY }, e.pointerType as Modality)) {
        gesture = e.pointerId;
        if (e.pointerType === 'touch') touchStartedAt = e.timeStamp;
        // Capture keeps ownership stable when the visual layers change;
        // it does not prevent a quick native pan from canceling the pointer.
        root.setPointerCapture(e.pointerId);
        callbacks.request();
        callbacks.changed();
      }
    },
    options,
  );
  window.addEventListener(
    'touchstart',
    (e) => {
      if (gesture === null || hold.modality !== 'touch') return;
      if (e.touches.length !== 1) {
        release();
        return;
      }
      touchIdentifier = e.touches[0]!.identifier;
    },
    { ...options, passive: true },
  );
  root.addEventListener(
    'touchmove',
    (e) => {
      if (gesture === null || hold.modality !== 'touch') return;
      if (e.touches.length !== 1) {
        release();
        return;
      }
      // This must be a non-passive TouchEvent listener: preventing a pointer
      // event (or changing touch-action after contact) cannot stop native pan.
      if (touchSettled(e.timeStamp) && e.cancelable) e.preventDefault();
    },
    { ...options, passive: false },
  );
  for (const type of ['touchend', 'touchcancel'] as const)
    window.addEventListener(
      type,
      (e) => {
        if (![...e.changedTouches].some((touch) => touch.identifier === touchIdentifier)) return;
        touchIdentifier = null;
        if (gesture !== null && hold.modality === 'touch') release();
      },
      { ...options, passive: true },
    );
  root.addEventListener(
    'contextmenu',
    (e) => {
      if (
        !state().enabled ||
        state().suspended ||
        state().first ||
        state().reduced ||
        (e.target as Element).closest('a,button,input,summary,[data-input-owner]')
      )
        return;
      if (touchIdentifier !== null || (e as PointerEvent).pointerType === 'touch')
        e.preventDefault();
    },
    options,
  );
  window.addEventListener(
    'pointermove',
    (e) => {
      if (
        state().disposed ||
        state().suspended ||
        document.documentElement.dataset.menuOpen === 'true'
      )
        return;
      if (e.pointerId === gesture) {
        clock.beforeEvent(hold, performance.now());
        if (!touchSettled(e.timeStamp)) hold.move({ x: e.clientX, y: e.clientY });
        if (!hold.pressed) release();
        callbacks.changed();
      }
      const owner = (e.target as Element).closest('[data-input-owner]');
      clearTimeout(prefetchTimer);
      if (
        !state().first &&
        !state().reduced &&
        state().enabled &&
        state().visible &&
        !state().covered &&
        e.pointerType !== 'touch' &&
        state().prefetchedFrom !== state().film &&
        !(e.target as Element).closest('a,button,input,summary,[data-input-owner]')
      ) {
        const from = state().film;
        prefetchTimer = window.setTimeout(() => {
          prefetchTimer = 0;
          if (
            state().disposed ||
            state().suspended ||
            !state().enabled ||
            state().covered ||
            from !== state().film
          )
            return;
          callbacks.prefetch(from);
        }, 300);
      }
      if (
        e.pointerType === 'touch' ||
        state().reduced ||
        (owner && !owner.hasAttribute('data-physical-identity'))
      ) {
        pointer.reset();
        return;
      }
      const samples = e.getCoalescedEvents?.();
      for (const sample of samples?.length ? samples : [e]) {
        const motion = pointer.observe(sample.clientX, sample.clientY, sample.timeStamp);
        if (motion) callbacks.motion(motion);
      }
      callbacks.changed();
    },
    options,
  );
  for (const type of ['pointerup', 'pointercancel'] as const)
    window.addEventListener(
      type,
      (e) => {
        if (e.pointerId === gesture) release();
      },
      options,
    );
  root.addEventListener(
    'lostpointercapture',
    (e) => {
      if (e.pointerId === gesture) release();
    },
    options,
  );
  window.addEventListener('blur', release, options);
  root.addEventListener(
    'keydown',
    (event) => {
      if (
        event.target !== root ||
        event.key.toLowerCase() !== 'f' ||
        event.repeat ||
        event.ctrlKey ||
        event.metaKey ||
        event.altKey ||
        (!state().enabled && !state().reduced) ||
        state().suspended ||
        state().disposed
      )
        return;
      event.preventDefault();
      clock.beforeEvent(hold, performance.now());
      callbacks.retry('keyboard');
      hold.select({ x: state().width * 0.65, y: state().height * 0.5 });
      callbacks.request();
      callbacks.changed();
    },
    options,
  );
  return {
    release,
    get active() {
      return gesture !== null;
    },
    reset() {
      clearTimeout(prefetchTimer);
      pointer.reset();
    },
    canHold(target: Element) {
      const value = state();
      return (
        !value.first &&
        value.enabled &&
        !value.suspended &&
        !value.disposed &&
        !value.reduced &&
        hold.phase === 'IDLE' &&
        root.contains(target) &&
        !target.closest('a,button,input,summary,[data-input-owner]')
      );
    },
    dispose() {
      release();
      abort.abort();
      clearTimeout(prefetchTimer);
      pointer.reset();
    },
  };
}
