import { focusNavigation } from '../../shared/focus';
import { windowProgress } from '../contracts/progress';
import { slitPosition } from '../../shared/motion/reveal';

/** The heading and tuner share the writing gates, independently of card travel. */
export function galleryWriting(root: HTMLElement) {
  const lines = [...root.querySelectorAll<HTMLElement>('.gallery-writing-rise')];
  const instrument = root.querySelector<HTMLFieldSetElement>('.gallery-instrument')!;
  const slot = instrument.querySelector<HTMLElement>('.tuner-reveal-slot')!;
  const button = instrument.querySelector<HTMLButtonElement>('.collection-tuner')!;
  const clear = () => {
    for (const node of [...lines, button]) {
      node.style.removeProperty('transform');
      node.style.removeProperty('opacity');
    }
    slot.style.removeProperty('overflow');
    instrument.style.removeProperty('visibility');
    instrument.inert = false;
  };
  return {
    update(entry: number, exit: number, reduced: boolean) {
      if (reduced) {
        clear();
        return;
      }
      lines.forEach((line, i) => {
        const emphasis = windowProgress(entry, i * 0.12, 0.76 + i * 0.12);
        line.style.transform = `translateY(${(1 - entry) * 8 - exit * 10}%)`;
        line.style.opacity = String((0.72 + 0.28 * emphasis) * (1 - exit));
      });
      const y = slitPosition(windowProgress(entry, 0.24, 1), exit);
      button.style.transform = `translateY(${y}%)`;
      // Clip only the moving strip; leave the menu and resting focus ring free.
      slot.style.overflow = y === 0 ? 'visible' : 'clip';
      const hidden = entry <= 0.24 || exit === 1;
      if (hidden && instrument.contains(document.activeElement)) {
        focusNavigation();
      }
      instrument.style.visibility = hidden ? 'hidden' : '';
      instrument.inert = hidden;
    },
    dispose: clear,
  };
}
