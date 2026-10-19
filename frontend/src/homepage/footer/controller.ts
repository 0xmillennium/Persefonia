import { focusNavigation } from '../../shared/focus';
import { footerRowProgress } from './motion';
import { windowProgress } from '../contracts/progress';

export function mountFooter(root: HTMLElement) {
  const menu = [...root.querySelectorAll<HTMLElement>('.location-links nav a')];
  const details = [...root.querySelectorAll<HTMLElement>('.location-links>p,.contact-entry')];
  const rows = [...menu, ...details].map((node, index) => {
    const rise = document.createElement('span');
    rise.className = 'footer-rise';
    rise.append(...node.childNodes);
    node.append(rise);
    node.classList.add('footer-slot');
    return {
      node,
      rise,
      index: index < menu.length ? index : index - menu.length,
      detail: index >= menu.length,
    };
  });
  const globe = root.querySelector<HTMLElement>('.location-system')!;
  const strip = root.querySelector<HTMLElement>('.location-strip')!;
  const availability = (node: HTMLElement, available: boolean) => {
    if (!available && node.contains(document.activeElement)) focusNavigation();
    node.inert = !available;
  };
  return {
    update(phase: number, reduced: boolean) {
      for (const row of rows) {
        const p = reduced ? 1 : footerRowProgress(phase, row.index, row.detail);
        if (reduced) {
          row.rise.style.removeProperty('transform');
          row.rise.style.removeProperty('opacity');
        } else {
          row.rise.style.transform = `translateY(${Math.pow(1 - p, 5) * 100}%)`;
          row.rise.style.opacity = p === 0 ? '0' : '1';
        }
        availability(row.node, p === 1);
      }
      const opacity = windowProgress(phase, 0.5, 1);
      const stripOpacity = windowProgress(phase, -0.05, 0.15);
      if (reduced) {
        globe.style.removeProperty('opacity');
        strip.style.removeProperty('opacity');
      } else {
        globe.style.opacity = String(opacity);
        strip.style.opacity = String(stripOpacity);
      }
      availability(globe, reduced || opacity === 1);
      availability(strip, reduced || stripOpacity === 1);
      return reduced || opacity > 0.2;
    },
    dispose() {
      for (const { node, rise } of rows) {
        node.replaceChildren(...rise.childNodes);
        node.classList.remove('footer-slot');
        node.inert = false;
      }
      for (const node of [globe, strip]) {
        node.style.removeProperty('opacity');
        node.inert = false;
      }
    },
  };
}
