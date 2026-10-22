import { clamp } from '../contracts/progress';

/** Whole authored lines depart together; no glyph slicing or duplicate semantic text. */
export function firstWriting(root: HTMLElement) {
  const rows = [...root.querySelectorAll<HTMLElement>('.hero-writing-text')];
  const clear = () => {
    for (const row of rows) {
      row.style.removeProperty('transform');
      row.style.removeProperty('opacity');
    }
  };
  return {
    update(progress: number, reduced: boolean) {
      if (reduced || progress === 0) {
        clear();
        return;
      }
      const p = clamp(progress);
      rows.forEach((row, i) => {
        row.style.transform = `translateY(${-p * (8 + i * 4)}%)`;
        row.style.opacity = String(1 - p * p);
      });
    },
    dispose: clear,
  };
}
