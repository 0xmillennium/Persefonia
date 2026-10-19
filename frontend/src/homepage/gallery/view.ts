import { galleryDepth } from './motion';
export interface GalleryGroup {
  node: HTMLElement;
  group: HTMLElement | null;
  cards: HTMLElement[];
}

/** Card geometry, DOM painting, live announcements and collection transitions. */
export function galleryView(root: HTMLElement, signal: AbortSignal) {
  const groups = [...root.querySelectorAll<HTMLElement>('.gallery-category')].map((node) => ({
    node,
    group: node.querySelector<HTMLElement>('.gallery-group'),
    cards: [...node.querySelectorAll<HTMLElement>('.gallery-card')],
  }));
  const radios = [...root.querySelectorAll<HTMLInputElement>('[name="gallery-category"]')];
  const output = root.querySelector<HTMLOutputElement>('[data-gallery-position]')!;
  const space = root.querySelector<HTMLElement>('.gallery-space')!;
  const directory = root.querySelector<HTMLElement>('.gallery-instrument')!;
  const options = { signal };
  const transitions = new Map<HTMLElement, Animation>();
  let cardWidth = 1,
    spacing = 1;
  function measureCards(group: GalleryGroup) {
    const card = group.cards[0] ?? groups.find((group) => group.cards.length)?.cards[0];
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
  function render(
    group: GalleryGroup,
    selectedIndex: number,
    targetIndex: number,
    tiltX: number,
    tiltY: number,
    reduced: boolean,
    announce: boolean,
  ) {
    const rotation = reduced ? '' : `rotateY(${tiltX}deg) rotateX(${tiltY}deg)`;
    if (group.group && rendered.get(group.group) !== rotation) {
      group.group.style.setProperty('transform', rotation);
      rendered.set(group.group, rotation);
    }
    group.cards.forEach((card, i) => {
      const offset = selectedIndex - i,
        distance = Math.abs(offset);
      const state = `${offset}:${spacing}:${cardWidth}:${reduced}`;
      if (rendered.get(card) === state) return;
      rendered.set(card, state);
      card.style.transform = reduced
        ? ''
        : `translateX(${offset * spacing}px) translateZ(${-Math.min(distance, 2) * cardWidth * (0.85 / 2.6)}px) rotateY(${-Math.max(-1, Math.min(1, offset / 2)) * (180 / 3.4)}deg)`;
      const depth = galleryDepth(distance);
      if (reduced) {
        card.style.removeProperty('--gallery-blur');
        card.style.removeProperty('--gallery-depth-opacity');
      } else {
        card.style.setProperty('--gallery-blur', `${depth.blur}px`);
        card.style.setProperty('--gallery-depth-opacity', String(depth.opacity));
      }
      card.dataset.front = String(distance < 0.5);
      card.style.visibility = distance > 3 && !reduced ? 'hidden' : 'inherit';
    });
    const centered = targetIndex >= 0 && targetIndex <= group.cards.length - 1;
    const text = `${centered ? Math.round(targetIndex) + 1 : 0} / ${group.cards.length}`;
    if (announce && output.value !== text) output.value = text;
    root.dataset.galleryIndex = String(selectedIndex);
    root.dataset.galleryEmpty = String(
      !group.cards.length ||
        (!reduced && (selectedIndex < -3 || selectedIndex > group.cards.length + 2)),
    );
  }
  function cancelTransitions(group: GalleryGroup) {
    for (const animation of transitions.values()) animation.cancel();
    transitions.clear();
    for (const candidate of groups) {
      candidate.node.hidden = candidate !== group;
      candidate.node.inert = candidate.node.hidden;
      candidate.node.removeAttribute('aria-hidden');
    }
  }
  return {
    groups,
    radios,
    directory,
    space,
    measureCards,
    render,
    cancelTransitions,
    get spacing() {
      return spacing;
    },
    select(group: GalleryGroup, oldGroup?: GalleryGroup) {
      const outgoingStyle = oldGroup ? getComputedStyle(oldGroup.node) : null;
      const oldOpacity = outgoingStyle?.opacity ?? '1',
        oldTransform = outgoingStyle?.transform ?? 'none';
      if (oldGroup?.node.contains(document.activeElement))
        directory
          .querySelector<HTMLButtonElement>('.collection-tuner')!
          .focus({ preventScroll: true });
      const incomingStyle = !group.node.hidden ? getComputedStyle(group.node) : null;
      const incomingOpacity = incomingStyle?.opacity ?? '0',
        incomingTransform = incomingStyle?.transform;
      for (const animation of transitions.values()) animation.cancel();
      transitions.clear();
      const category = group.node.dataset.category!;
      root.dataset.category = category;
      for (const radio of radios) radio.checked = radio.value === category;
      for (const group of groups) {
        group.node.hidden = group.node.dataset.category !== category;
        group.node.inert = group.node.hidden;
        group.node.removeAttribute('aria-hidden');
      }
      return (direction: number) => {
        if (!oldGroup) return;
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
          if (oldGroup.node.dataset.category !== root.dataset.category) oldGroup.node.hidden = true;
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
      };
    },
    dispose() {
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
