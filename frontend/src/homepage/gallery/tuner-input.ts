import { ReleaseVelocity } from './kinetics';
import { collectionStop, nearestCollectionStop } from './tuner-stops';
import type { tunerView } from './tuner-view';
interface TunerInputState {
  position: number;
  committed: number;
  moving: boolean;
}
interface TunerInputActions {
  stop(): void;
  restore(animate: boolean): void;
  preview(position: number): void;
  choose(index: number): void;
  release(speed: number): void;
}

/** Input arbitration and pointer capture, independent of the tuner animation clock. */
export function tunerInput(
  directory: HTMLElement,
  visual: ReturnType<typeof tunerView>,
  reduced: MediaQueryList,
  signal: AbortSignal,
  state: () => TunerInputState,
  actions: TunerInputActions,
) {
  const { button, list, radios } = visual;
  const options = { signal };
  const wrapped = (index: number) => collectionStop(index, radios.length);
  const open = (value: boolean, focus = false) =>
    visual.open(value, focus ? wrapped(state().committed) : undefined);
  let wheelTime = -Infinity,
    wheelOwner = false,
    wheelSum = 0,
    wheelDirection = 0,
    lastTurn = -Infinity;
  let drag: {
    id: number;
    x: number;
    y: number;
    index: number;
    moved: boolean;
    touch: boolean;
    caught: boolean;
  } | null = null;
  let suppressClick = false;
  const velocity = new ReleaseVelocity();
  function finish(cancel = false, time = performance.now()) {
    if (!drag) return;
    const gesture = drag;
    drag = null;
    delete directory.dataset.tuning;
    if (button.hasPointerCapture(gesture.id)) button.releasePointerCapture(gesture.id);
    if (cancel || !gesture.moved) {
      actions.restore(true);
      // A touch that catches a running turn may not emit a compatibility click.
      if (!cancel && gesture.touch && gesture.caught) {
        suppressClick = true;
        open(true);
      }
    } else if (reduced.matches) actions.choose(Math.round(state().position));
    else actions.release(velocity.value(time));
  }

  function cancel() {
    finish(true);
    actions.restore(false);
  }
  button.addEventListener(
    'click',
    (event) => {
      if (suppressClick && event.detail > 0) {
        suppressClick = false;
        event.preventDefault();
        return;
      }
      suppressClick = false;
      if (state().moving) cancel();
      open(list.hidden === true, event.detail === 0);
    },
    options,
  );
  directory.addEventListener(
    'keydown',
    (event) => {
      const direction = ['ArrowRight', 'ArrowDown'].includes(event.key)
        ? 1
        : ['ArrowLeft', 'ArrowUp'].includes(event.key)
          ? -1
          : 0;
      if (direction || event.key === 'Home' || event.key === 'End') {
        event.preventDefault();
        event.stopPropagation();
        if (state().moving || drag) cancel();
        actions.choose(
          direction
            ? state().committed + direction
            : nearestCollectionStop(
                state().committed,
                event.key === 'Home' ? 0 : radios.length - 1,
                radios.length,
              ),
        );
        button.focus({ preventScroll: true });
      }
      if (event.key === 'Escape') {
        event.preventDefault();
        cancel();
        open(false);
        button.focus({ preventScroll: true });
      }
    },
    options,
  );
  list.addEventListener(
    'change',
    () => {
      open(false);
      button.focus({ preventScroll: true });
    },
    options,
  );
  list.addEventListener(
    'click',
    (event) => {
      if (
        event.target instanceof HTMLInputElement &&
        event.target.value === radios[wrapped(state().committed)]!.value
      ) {
        open(false);
        button.focus({ preventScroll: true });
      }
    },
    options,
  );
  directory.addEventListener(
    'focusout',
    (event) => {
      if (!directory.contains(event.relatedTarget as Node | null)) {
        cancel();
        open(false);
      }
    },
    options,
  );
  document.addEventListener(
    'pointerdown',
    (event) => {
      if (!directory.contains(event.target as Node)) {
        cancel();
        open(false);
      }
    },
    options,
  );
  button.addEventListener(
    'pointerdown',
    (event) => {
      if (!event.isPrimary || event.button !== 0) return;
      suppressClick = false;
      const caught = state().moving;
      actions.stop();
      velocity.reset(state().position, event.timeStamp);
      drag = {
        id: event.pointerId,
        x: event.clientX,
        y: event.clientY,
        index: state().position,
        moved: false,
        touch: event.pointerType === 'touch',
        caught,
      };
    },
    options,
  );
  button.addEventListener(
    'pointermove',
    (event) => {
      if (!drag || drag.id !== event.pointerId) return;
      const dx = event.clientX - drag.x,
        dy = event.clientY - drag.y;
      if (!drag.moved) {
        if (Math.abs(dy) > 7 && Math.abs(dy) >= Math.abs(dx)) {
          finish(true);
          return;
        }
        if (Math.abs(dx) < 7) return;
        drag.moved = suppressClick = true;
        directory.dataset.tuning = 'true';
        open(false);
        button.setPointerCapture(event.pointerId);
      }
      event.preventDefault();
      actions.preview(drag.index - dx / (button.clientWidth / 3));
      velocity.add(state().position, event.timeStamp);
    },
    options,
  );
  window.addEventListener(
    'pointerup',
    (event) => {
      if (event.pointerId === drag?.id) finish(false, event.timeStamp);
    },
    options,
  );
  window.addEventListener(
    'pointercancel',
    (event) => {
      if (event.pointerId === drag?.id) finish(true);
    },
    options,
  );
  button.addEventListener(
    'lostpointercapture',
    (event) => {
      if (event.pointerId === drag?.id) finish(true);
    },
    options,
  );
  window.addEventListener(
    'blur',
    () => {
      cancel();
      open(false);
      wheelTime = -Infinity;
    },
    options,
  );
  window.addEventListener(
    'scroll',
    () => {
      if (state().moving) cancel();
    },
    { ...options, passive: true },
  );
  window.addEventListener(
    'pagehide',
    () => {
      cancel();
      open(false);
    },
    options,
  );
  document.addEventListener(
    'visibilitychange',
    () => {
      if (document.hidden) {
        cancel();
        open(false);
        wheelTime = -Infinity;
      }
    },
    options,
  );
  reduced.addEventListener(
    'change',
    () => {
      cancel();
    },
    options,
  );
  return {
    finish,
    cancel,
    open,
    wheel(event: WheelEvent) {
      if (event.ctrlKey || event.metaKey || !event.cancelable || event.defaultPrevented)
        return false;
      const now = performance.now();
      if (now - wheelTime > 180) {
        wheelOwner =
          directory.contains(event.target as Node) &&
          !!directory.closest('[data-visible="true"]') &&
          !directory.closest('[inert]');
        wheelSum = wheelDirection = 0;
      }
      wheelTime = now;
      if (!wheelOwner) return false;
      event.preventDefault();
      if (state().moving || drag) cancel();
      const delta = Math.abs(event.deltaX) > Math.abs(event.deltaY) ? event.deltaX : event.deltaY;
      const direction = Math.sign(delta);
      if (!direction) return true;
      if (direction !== wheelDirection) wheelSum = 0;
      wheelDirection = direction;
      const normalized =
        delta * (event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? innerHeight : 1);
      if (Math.abs(normalized) >= 40) {
        actions.choose(state().committed + direction);
        wheelSum = 0;
        lastTurn = now;
      } else {
        wheelSum += normalized;
        if (Math.abs(wheelSum) >= 60 && now - lastTurn >= 160) {
          actions.choose(state().committed + direction);
          wheelSum = 0;
          lastTurn = now;
        }
      }
      return true;
    },
  };
}
