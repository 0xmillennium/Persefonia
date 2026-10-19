import { ReleaseVelocity } from './kinetics';
import { galleryProgress } from '../contracts/gallery';
interface GalleryInputState {
  active: boolean;
  reduced: boolean;
  coasting: boolean;
  index: number;
  target: number;
  spacing: number;
  cards: HTMLElement[];
}
interface GalleryInputActions {
  cancelMotion(): void;
  begin(): void;
  freeze(): void;
  release(speed: number): void;
  focus(index: number): void;
  render(): void;
  moveTo(progress: number): void;
}

/** Owns an individual gallery gesture; selection and momentum stay in the controller. */
export function galleryInput(
  root: HTMLElement,
  space: HTMLElement,
  signal: AbortSignal,
  state: () => GalleryInputState,
  actions: GalleryInputActions,
) {
  const options = { signal };
  let keyboardSelecting = false,
    suppressClick = false;
  let press: {
    id: number;
    x: number;
    y: number;
    index: number;
    spacing: number;
    dragging: boolean;
    caught: boolean;
  } | null = null;
  const velocity = new ReleaseVelocity();
  function finish(settle: boolean, momentum = true, time = performance.now()) {
    actions.cancelMotion();
    if (!press) return;
    const gesture = press;
    press = null;
    delete root.dataset.dragging;
    if (space.hasPointerCapture(gesture.id)) space.releasePointerCapture(gesture.id);
    if (!gesture.dragging && !gesture.caught) return;
    if (settle) actions.release(momentum && gesture.dragging ? velocity.value(time) : 0);
    else actions.freeze();
  }
  root.addEventListener(
    'keydown',
    () => {
      finish(false);
      keyboardSelecting = true;
    },
    options,
  );
  root.addEventListener(
    'focusin',
    (event) => {
      if (press) return;
      const card = (event.target as Element).closest<HTMLElement>('.gallery-card');
      if (!card) return;
      const cards = state().cards,
        index = cards.indexOf(card);
      if (index >= 0 && Math.abs(index - state().target) > 0.5) {
        finish(false);
        if (keyboardSelecting) actions.focus(index);
        actions.moveTo(galleryProgress(index, cards.length));
        if (keyboardSelecting) actions.render();
      }
    },
    options,
  );
  space.addEventListener(
    'dragstart',
    (event) => {
      event.preventDefault();
    },
    options,
  );
  root.addEventListener(
    'pointerdown',
    () => {
      suppressClick = false;
      keyboardSelecting = false;
    },
    { ...options, capture: true },
  );
  space.addEventListener(
    'pointerdown',
    (e) => {
      if (
        !state().active ||
        state().reduced ||
        !e.isPrimary ||
        e.button !== 0 ||
        state().cards.length < 1 ||
        press ||
        (e.target as Element).closest('button,input,select,textarea,[contenteditable="true"]')
      )
        return;
      const caught = state().coasting;
      finish(false);
      actions.begin();
      if (caught) suppressClick = true;
      velocity.reset(state().index, e.timeStamp);
      press = {
        id: e.pointerId,
        x: e.clientX,
        y: e.clientY,
        index: state().index,
        spacing: state().spacing,
        dragging: false,
        caught,
      };
    },
    options,
  );
  space.addEventListener(
    'pointermove',
    (e) => {
      if (!press || e.pointerId !== press.id) return;
      const dx = e.clientX - press.x,
        dy = e.clientY - press.y;
      if (!press.dragging) {
        if (Math.abs(dy) > 8 && Math.abs(dy) >= Math.abs(dx)) {
          finish(false);
          return;
        }
        if (Math.abs(dx) < 8) return;
        press.dragging = true;
        suppressClick = true;
        root.dataset.dragging = 'true';
        space.setPointerCapture(e.pointerId);
      }
      e.preventDefault();
      const count = state().cards.length;
      const index = Math.max(-4, Math.min(count + 3, press.index + dx / press.spacing));
      velocity.add(index, e.timeStamp);
      actions.moveTo(galleryProgress(index, count));
    },
    options,
  );
  window.addEventListener(
    'pointerup',
    (e) => {
      if (e.pointerId === press?.id) finish(true, true, e.timeStamp);
    },
    options,
  );
  window.addEventListener(
    'pointercancel',
    (e) => {
      if (e.pointerId === press?.id) finish(true, false);
    },
    options,
  );
  space.addEventListener(
    'lostpointercapture',
    (e) => {
      if (e.target === space && e.pointerId === press?.id) finish(true, false);
    },
    options,
  );
  root.addEventListener(
    'click',
    (e) => {
      if (suppressClick && e instanceof MouseEvent && e.detail > 0) {
        e.preventDefault();
        e.stopPropagation();
      }
      suppressClick = false;
    },
    { ...options, capture: true },
  );
  window.addEventListener(
    'pointerdown',
    (event) => {
      if (state().coasting && !space.contains(event.target as Node)) finish(false);
    },
    { ...options, capture: true, passive: true },
  );
  window.addEventListener(
    'keydown',
    () => {
      if (state().coasting) finish(false);
    },
    options,
  );
  return {
    finish,
    get press() {
      return press;
    },
  };
}
