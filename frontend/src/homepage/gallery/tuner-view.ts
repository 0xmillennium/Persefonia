import { collectionStop } from './tuner-stops';

/** Paints the cylindrical scale and collection labels; owns no selection or inertia. */
export function tunerView(directory: HTMLElement) {
  const button = directory.querySelector<HTMLButtonElement>('.collection-tuner')!;
  const list = directory.querySelector<HTMLElement>('.collection-options')!;
  const radios = [...list.querySelectorAll<HTMLInputElement>('input')];
  const name = directory.querySelector<HTMLElement>('.tuner-name')!;
  const names = directory.querySelector<HTMLElement>('.tuner-names')!;
  const scale = directory.querySelector<HTMLElement>('.tuner-scale')!;
  const labels = radios.map((radio) => radio.nextElementSibling!.textContent!.trim());
  const label = button.getAttribute('aria-label')!;
  const ticks = Array.from({ length: 57 }, () => {
    const tick = document.createElement('i');
    scale.append(tick);
    return tick;
  });
  const reels = Array.from({ length: 7 }, () => {
    const node = document.createElement('span');
    node.className = 'tuner-label';
    names.append(node);
    return node;
  });
  let radius = button.clientWidth * 0.48;
  const wrapped = (index: number) => collectionStop(index, radios.length);
  function render(position: number) {
    const displayed = Math.round(position);
    const text = labels[wrapped(displayed)]!;
    if (name.textContent !== text) name.textContent = text;
    const centerTick = Math.floor(position * 8);
    for (let index = centerTick - 28; index <= centerTick + 28; index++) {
      const tick = ticks[collectionStop(index, ticks.length)]!;
      const distance = (index - position * 8) / 18;
      const tickClass = index % 8 === 0 ? 'tuner-major' : '';
      if (tick.className !== tickClass) tick.className = tickClass;
      tick.style.transform = `translateX(${Math.sin((Math.max(-1, Math.min(1, distance)) * Math.PI) / 2) * radius}px)`;
      tick.style.opacity =
        Math.abs(distance) < 1
          ? String(Math.pow(Math.cos((distance * Math.PI) / 2), 1.5) * 0.7)
          : '0';
    }
    const center = Math.round(position);
    for (let index = center - 3; index <= center + 3; index++) {
      const node = reels[collectionStop(index, reels.length)]!;
      const offset = index - position,
        distance = Math.abs(offset);
      const angle = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, offset * 0.78));
      const text = labels[wrapped(index)]!;
      if (node.textContent !== text) node.textContent = text;
      node.dataset.current = String(index === center);
      node.dataset.offset = String(index - center);
      node.style.transform = `translate3d(${Math.sin(angle) * radius}px,${(1 - Math.cos(angle)) * 5}px,${(Math.cos(angle) - 1) * radius}px) rotateY(${(angle * 180) / Math.PI}deg)`;
      node.style.opacity = String(Math.max(0, 1 - Math.pow(distance / 1.8, 1.2)));
      node.style.visibility = distance < 1.8 ? 'inherit' : 'hidden';
    }
    directory.dataset.tunerPosition = String(position);
  }
  function open(value: boolean, focusIndex?: number) {
    list.hidden = !value;
    button.setAttribute('aria-expanded', String(value));
    if (value && focusIndex !== undefined)
      radios[wrapped(focusIndex)]!.focus({ preventScroll: true });
  }
  return {
    button,
    list,
    radios,
    render,
    open,
    label(index: number) {
      button.setAttribute('aria-label', `${label}: ${labels[index]}`);
    },
    measure() {
      const next = button.clientWidth * 0.48;
      const changed = next !== radius;
      radius = next;
      return changed;
    },
    dispose() {
      open(false);
      scale.replaceChildren();
      names.replaceChildren();
      delete directory.dataset.tunerPosition;
    },
  };
}
