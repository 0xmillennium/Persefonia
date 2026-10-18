import { collectionTuner } from './tuner';
import { galleryIndex } from '../timeline/pacing';
import { galleryDepth, galleryProgress, galleryRelease, gallerySettle } from './motion';

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
  const groups = [...root.querySelectorAll<HTMLElement>('.gallery-category')].map((node) => ({
    node,
    group: node.querySelector<HTMLElement>('.gallery-group'),
    cards: [...node.querySelectorAll<HTMLElement>('.gallery-card')],
  }));
  const radios = [...root.querySelectorAll<HTMLInputElement>('[name="gallery-category"]')];
  const output = root.querySelector<HTMLOutputElement>('[data-gallery-position]')!;
  const space = root.querySelector<HTMLElement>('.gallery-space')!;
  const abort = new AbortController(),
    options = { signal: abort.signal };
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const savedCategory = restoreCategory ? history.state?.homepageV2?.category : null;
  let category = groups.some((group) => group.node.dataset.category === savedCategory)
      ? (savedCategory as string)
      : 'projects',
    progress = 0;
  let keyboardSelecting = false,
    departing = 0;
  const directory = root.querySelector<HTMLElement>('.gallery-instrument')!;
  const transitions = new Map<HTMLElement, Animation>();
  let tuner: ReturnType<typeof collectionTuner> | undefined;
  let selectedIndex = 0,
    targetIndex = 0,
    frame = 0,
    previous = 0,
    active = false;
  let cardWidth = 1,
    spacing = 1;
  let tiltX = 0,
    tiltY = 0,
    targetX = 0,
    targetY = 0;
  let press: {
    id: number;
    x: number;
    y: number;
    index: number;
    last: number;
    time: number;
    velocity: number;
    spacing: number;
    dragging: boolean;
  } | null = null;
  let suppressClick = false;
  const current = () => groups.find((group) => group.node.dataset.category === category)!;
  const stop = () => {
    cancelAnimationFrame(frame);
    frame = previous = 0;
  };
  function measureCards() {
    const card = current().cards[0] ?? groups.find((group) => group.cards.length)?.cards[0];
    if (card) cardWidth = parseFloat(getComputedStyle(card).width) || 1;
    spacing = Math.min(cardWidth * (1.62 / 2.6), space.clientWidth / 6);
  }
  for (const image of root.querySelectorAll<HTMLImageElement>('.gallery-cover img')) {
    image.addEventListener(
      'error',
      () => {
        image.hidden = true;
      },
      options,
    );
    if (image.complete && image.naturalWidth === 0) image.hidden = true;
  }
  const rendered = new WeakMap<HTMLElement, string>();
  function render() {
    const group = current();
    const rotation = reduced.matches ? '' : `rotateY(${tiltX}deg) rotateX(${tiltY}deg)`;
    if (group.group && rendered.get(group.group) !== rotation) {
      group.group.style.setProperty('transform', rotation);
      rendered.set(group.group, rotation);
    }
    group.cards.forEach((card, i) => {
      const offset = selectedIndex - i,
        distance = Math.abs(offset);
      const state = `${offset}:${spacing}:${cardWidth}:${reduced.matches}`;
      if (rendered.get(card) === state) return;
      rendered.set(card, state);
      card.style.transform = reduced.matches
        ? ''
        : `translateX(${offset * spacing}px) translateZ(${-Math.min(distance, 2) * cardWidth * (0.85 / 2.6)}px) rotateY(${-Math.max(-1, Math.min(1, offset / 2)) * (180 / 3.4)}deg)`;
      const depth = galleryDepth(distance);
      if (reduced.matches) {
        card.style.removeProperty('--gallery-blur');
        card.style.removeProperty('--gallery-depth-opacity');
      } else {
        card.style.setProperty('--gallery-blur', `${depth.blur}px`);
        card.style.setProperty('--gallery-depth-opacity', String(depth.opacity));
      }
      card.dataset.front = String(distance < 0.5);
      card.style.visibility = distance > 3 && !reduced.matches ? 'hidden' : 'inherit';
    });
    const centered = targetIndex >= 0 && targetIndex <= group.cards.length - 1;
    const text = `${centered ? Math.round(targetIndex) + 1 : 0} / ${group.cards.length}`;
    if (output.value !== text) output.value = text;
    root.dataset.galleryIndex = String(selectedIndex);
    root.dataset.galleryEmpty = String(
      !group.cards.length ||
        (!reduced.matches && (selectedIndex < -3 || selectedIndex > group.cards.length + 2)),
    );
  }
  function tick(now: number) {
    frame = 0;
    if (!active || reduced.matches || document.hidden) {
      previous = 0;
      return;
    }
    const seconds = previous ? Math.min(0.05, (now - previous) / 1000) : 1 / 60;
    previous = now;
    selectedIndex = press?.dragging
      ? targetIndex
      : gallerySettle(selectedIndex, targetIndex, seconds);
    tiltX = gallerySettle(tiltX, targetX, seconds);
    tiltY = gallerySettle(tiltY, targetY, seconds);
    render();
    if (selectedIndex !== targetIndex || tiltX !== targetX || tiltY !== targetY) wake();
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
    progress = p;
    targetIndex = galleryIndex(progress, current().cards.length);
    if (!active) departing = 0;
    if (departing && (departing < 0 ? targetIndex > -4 : targetIndex < current().cards.length + 3))
      departing = 0;
    if (!active || reduced.matches || press?.dragging) {
      selectedIndex = targetIndex;
      render();
    } else wake();
  }
  function finish(settle: boolean, momentum = true) {
    if (!press) return;
    const gesture = press;
    press = null;
    delete root.dataset.dragging;
    if (space.hasPointerCapture(gesture.id)) space.releasePointerCapture(gesture.id);
    if (!gesture.dragging) return;
    if (settle) {
      const velocity = !momentum || performance.now() - gesture.time > 80 ? 0 : gesture.velocity;
      const count = current().cards.length;
      const index = galleryRelease(selectedIndex, velocity, count);
      moveTo(galleryProgress(index, count), true);
    } else {
      targetIndex = selectedIndex;
      stop();
    }
  }
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
    const outgoingStyle = oldGroup ? getComputedStyle(oldGroup.node) : null;
    const oldOpacity = outgoingStyle?.opacity ?? '1',
      oldTransform = outgoingStyle?.transform ?? 'none';
    if (oldGroup?.node.contains(document.activeElement))
      directory
        .querySelector<HTMLButtonElement>('.collection-tuner')!
        .focus({ preventScroll: true });
    finish(false);
    category = value;
    const group = current();
    const incomingStyle = !group.node.hidden ? getComputedStyle(group.node) : null;
    const incomingOpacity = incomingStyle?.opacity ?? '0',
      incomingTransform = incomingStyle?.transform;
    const index = reduced.matches || center ? 0 : -3;
    selectedIndex = targetIndex = index;
    departing = 0;
    for (const animation of transitions.values()) animation.cancel();
    transitions.clear();
    root.dataset.category = category;
    for (const radio of radios) radio.checked = radio.value === category;
    for (const group of groups) {
      group.node.hidden = group.node.dataset.category !== category;
      group.node.inert = group.node.hidden;
      group.node.removeAttribute('aria-hidden');
    }
    measureCards();
    progress = galleryProgress(index, group.cards.length);
    render();
    if (center && active && !reduced.matches) tune();
    tuner?.update(category, active && animate);
    if (animate && active && !reduced.matches && group.group) {
      const direction = turnDirection ?? (groups.indexOf(group) > oldIndex ? 1 : -1);
      oldGroup.node.hidden = false;
      oldGroup.node.inert = true;
      oldGroup.node.setAttribute('aria-hidden', 'true');
      const outgoing = oldGroup.node.animate(
        [
          { opacity: oldOpacity, transform: oldTransform },
          { opacity: 0, transform: `translateX(${-direction * 18}px)` },
        ],
        { duration: 220, easing: 'cubic-bezier(.22,1,.36,1)' },
      );
      outgoing.onfinish = () => {
        if (oldGroup !== current()) oldGroup.node.hidden = true;
        outgoing.cancel();
        transitions.delete(oldGroup.node);
      };
      transitions.set(oldGroup.node, outgoing);
      transitions.set(
        group.node,
        group.node.animate(
          [
            {
              opacity: incomingOpacity,
              transform: incomingTransform ?? `translateX(${direction * 14}px)`,
            },
            { opacity: 1, transform: 'none' },
          ],
          { duration: 220, easing: 'cubic-bezier(.22,1,.36,1)' },
        ),
      );
    }
    changed();
  }
  root.addEventListener(
    'keydown',
    () => {
      keyboardSelecting = true;
    },
    options,
  );
  tuner = collectionTuner(directory, (value, direction) => select(value, true, direction, true));
  radios.forEach((radio) =>
    radio.addEventListener('change', () => tuner!.select(radio.value), options),
  );
  root.addEventListener(
    'focusin',
    (event) => {
      if (press) return;
      const card = (event.target as Element).closest<HTMLElement>('.gallery-card');
      if (!card) return;
      const cards = current().cards,
        index = cards.indexOf(card);
      if (index >= 0 && Math.abs(index - targetIndex) > 0.5) {
        finish(false);
        if (keyboardSelecting) selectedIndex = index;
        moveTo(galleryProgress(index, cards.length));
        if (keyboardSelecting) render();
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
        !active ||
        reduced.matches ||
        !e.isPrimary ||
        e.button !== 0 ||
        current().cards.length < 1 ||
        press ||
        (e.target as Element).closest('button,input,select,textarea,[contenteditable="true"]')
      )
        return;
      press = {
        id: e.pointerId,
        x: e.clientX,
        y: e.clientY,
        index: selectedIndex,
        last: selectedIndex,
        time: performance.now(),
        velocity: 0,
        spacing,
        dragging: false,
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
      const now = performance.now(),
        count = current().cards.length;
      const index = Math.max(-4, Math.min(count + 3, press.index + dx / press.spacing));
      press.velocity =
        0.6 * press.velocity +
        (0.4 * (index - press.last)) / Math.max(0.008, (now - press.time) / 1000);
      press.last = index;
      press.time = now;
      moveTo(galleryProgress(index, count));
    },
    options,
  );
  window.addEventListener(
    'pointerup',
    (e) => {
      if (e.pointerId === press?.id) finish(true);
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
      finish(false);
      measureCards();
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
      for (const animation of transitions.values()) animation.cancel();
      transitions.clear();
      for (const group of groups) {
        group.node.hidden = group !== current();
        group.node.inert = group.node.hidden;
        group.node.removeAttribute('aria-hidden');
      }
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
    measureCards();
    render();
  });
  directoryObserver.observe(directory);
  directoryObserver.observe(space);
  return {
    update,
    active(value: boolean) {
      if (active === value) return;
      active = value;
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
      return tuner!.wheel(event);
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
      for (const animation of transitions.values()) animation.cancel();
      delete root.dataset.galleryIndex;
      delete root.dataset.galleryEmpty;
      for (const group of groups) {
        group.node.hidden = false;
        group.node.inert = false;
        group.node.removeAttribute('aria-hidden');
        group.group?.style.removeProperty('transform');
        for (const card of group.cards) {
          for (const property of [
            'transform',
            'visibility',
            '--gallery-blur',
            '--gallery-depth-opacity',
          ])
            card.style.removeProperty(property);
          delete card.dataset.front;
        }
      }
    },
  };
}
