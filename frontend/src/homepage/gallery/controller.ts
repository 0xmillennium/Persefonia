import { galleryView } from './view';
import { galleryInput } from './input';
import { collectionTuner } from './tuner';
import { galleryIndex } from '../contracts/gallery';
import { galleryRelease, gallerySettle } from './motion';
import { galleryProgress } from '../contracts/gallery';
import { KineticMotion } from './kinetics';

export interface GalleryOptions {
  root: HTMLElement;
  position: (progress: number) => void;
  changed?: () => void;
  leave?: (direction: number) => void;
  tune?: () => void;
  restoreCategory?: boolean;
}

export function mountGallery({
  root,
  position,
  changed = () => {},
  leave = () => {},
  tune = () => {},
  restoreCategory = false,
}: GalleryOptions) {
  const abort = new AbortController(),
    options = { signal: abort.signal };
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const view = galleryView(root, abort.signal);
  const { groups, radios, directory, space } = view;
  const savedCategory = restoreCategory ? history.state?.homepageV2?.category : null;
  let category = groups.some((group) => group.node.dataset.category === savedCategory)
      ? (savedCategory as string)
      : 'projects',
    progress = 0;
  let departing = 0;
  let tuner: ReturnType<typeof collectionTuner> | undefined;
  let selectedIndex = 0,
    targetIndex = 0,
    frame = 0,
    previous = 0,
    active = false;
  let tiltX = 0,
    tiltY = 0,
    targetX = 0,
    targetY = 0;
  let motion: KineticMotion | null = null;
  let ownedY = 0;
  const current = () => groups.find((group) => group.node.dataset.category === category)!;
  const stop = () => {
    cancelAnimationFrame(frame);
    frame = previous = 0;
    motion = null;
    delete root.dataset.galleryMotion;
  };
  function measureCards() {
    view.measureCards(current());
  }
  function render() {
    view.render(
      current(),
      selectedIndex,
      targetIndex,
      tiltX,
      tiltY,
      reduced.matches,
      !motion && !input.press?.dragging,
    );
  }
  function tick(now: number) {
    frame = 0;
    if (!active || reduced.matches || document.hidden) {
      previous = 0;
      return;
    }
    if (motion && Math.abs(scrollY - ownedY) > 1) {
      finish(false);
      previous = 0;
      changed();
      return;
    }
    const seconds = previous ? Math.min(0.05, (now - previous) / 1000) : 1 / 60;
    previous = now;
    if (motion) {
      const phase = motion.phase(now);
      moveTo(galleryProgress(motion.sample(now), current().cards.length), phase === 'resting');
      ownedY = scrollY;
      root.dataset.galleryMotion = phase;
      if (phase === 'resting') {
        motion = null;
        delete root.dataset.galleryMotion;
      }
    } else
      selectedIndex = input.press
        ? input.press.dragging
          ? targetIndex
          : input.press.index
        : gallerySettle(selectedIndex, targetIndex, seconds);
    tiltX = gallerySettle(tiltX, targetX, seconds);
    tiltY = gallerySettle(tiltY, targetY, seconds);
    render();
    if (
      motion ||
      (!input.press && selectedIndex !== targetIndex) ||
      tiltX !== targetX ||
      tiltY !== targetY
    )
      wake();
    else {
      previous = 0;
      if (departing) {
        const direction = departing;
        departing = 0;
        leave(direction);
      }
    }
  }
  function wake() {
    if (active && !reduced.matches && !document.hidden && !frame)
      frame = requestAnimationFrame(tick);
  }
  function update(p: number) {
    if (motion && Math.abs(scrollY - ownedY) > 1) {
      motion = null;
      delete root.dataset.galleryMotion;
    }
    progress = p;
    targetIndex = galleryIndex(progress, current().cards.length);
    if (!active) departing = 0;
    if (departing && (departing < 0 ? targetIndex > -4 : targetIndex < current().cards.length + 3))
      departing = 0;
    if (!active || reduced.matches || input.press?.dragging || motion) {
      selectedIndex = targetIndex;
      render();
    } else wake();
  }
  const finish = (settle: boolean, momentum = true, time = performance.now()) =>
    input.finish(settle, momentum, time);
  function moveTo(p: number, allowLeave = false) {
    departing = allowLeave ? (p === 0 ? -1 : p === 1 ? 1 : 0) : 0;
    update(p);
    position(p);
    changed();
  }
  function select(value: string, animate = false, turnDirection?: number, center = false) {
    if (value === category || !groups.some((group) => group.node.dataset.category === value))
      return;
    const oldGroup = current();
    const oldIndex = groups.indexOf(oldGroup);
    finish(false);
    category = value;
    const group = current();
    const transition = view.select(group, oldGroup);
    const index = reduced.matches || center ? 0 : -3;
    selectedIndex = targetIndex = index;
    departing = 0;
    measureCards();
    progress = galleryProgress(index, group.cards.length);
    render();
    if (center && active && !reduced.matches) tune();
    tuner?.update(category, active && animate);
    if (animate && active && !reduced.matches && group.group) {
      const direction = turnDirection ?? (groups.indexOf(group) > oldIndex ? 1 : -1);
      transition(direction);
    }
    changed();
  }
  const input = galleryInput(
    root,
    space,
    abort.signal,
    () => ({
      active,
      reduced: reduced.matches,
      coasting: !!motion,
      index: selectedIndex,
      target: targetIndex,
      spacing: view.spacing,
      cards: current().cards,
    }),
    {
      cancelMotion() {
        motion = null;
        delete root.dataset.galleryMotion;
      },
      begin() {
        targetIndex = selectedIndex;
      },
      freeze() {
        targetIndex = selectedIndex;
        stop();
      },
      release(speed) {
        motion = new KineticMotion(
          galleryRelease(selectedIndex, speed, current().cards.length),
          performance.now(),
        );
        ownedY = scrollY;
        root.dataset.galleryMotion = motion.phase(performance.now());
        wake();
      },
      focus(index) {
        selectedIndex = index;
      },
      render,
      moveTo,
    },
  );
  tuner = collectionTuner(directory, (value, direction) => select(value, true, direction, true));
  radios.forEach((radio) =>
    radio.addEventListener('change', () => tuner!.select(radio.value), options),
  );
  window.addEventListener(
    'blur',
    () => {
      finish(false);
      stop();
    },
    options,
  );
  window.addEventListener(
    'resize',
    () => {
      const oldSpacing = view.spacing;
      measureCards();
      if (oldSpacing !== view.spacing) finish(false);
      render();
    },
    options,
  );
  window.addEventListener(
    'pagehide',
    () => {
      finish(false);
      stop();
    },
    options,
  );
  document.addEventListener(
    'visibilitychange',
    () => {
      if (document.hidden) {
        finish(false);
        stop();
      } else wake();
    },
    options,
  );
  reduced.addEventListener(
    'change',
    () => {
      finish(false);
      stop();
      view.cancelTransitions(current());
      selectedIndex = targetIndex;
      tiltX = tiltY = targetX = targetY = 0;
      render();
    },
    options,
  );
  const initial = category;
  category = '';
  select(initial);
  const directoryObserver = new ResizeObserver(() => {
    const oldSpacing = view.spacing;
    measureCards();
    if (oldSpacing !== view.spacing) finish(false);
    render();
  });
  directoryObserver.observe(directory);
  directoryObserver.observe(space);
  return {
    update,
    active(value: boolean) {
      if (active === value) return;
      active = value;
      tuner?.active(value);
      if (!active) {
        departing = 0;
        finish(false);
        stop();
        selectedIndex = targetIndex = galleryIndex(progress, current().cards.length);
        render();
      } else wake();
    },
    orient(x: number, y: number) {
      targetX = x * 3;
      targetY = -y * 1.5;
      wake();
    },
    select,
    wheel() {
      departing = 0;
      finish(false);
    },
    tunerWheel(event: WheelEvent) {
      const owned = tuner!.wheel(event);
      if (owned) finish(false);
      return owned;
    },
    get category() {
      return category;
    },
    get count() {
      return current().cards.length;
    },
    get empty() {
      return root.dataset.galleryEmpty === 'true';
    },
    dispose() {
      finish(false);
      stop();
      abort.abort();
      directoryObserver.disconnect();
      tuner?.dispose();
      view.dispose();
    },
  };
}
