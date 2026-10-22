import { clamp } from '../contracts/progress';

export function secondWordState(
  entry: number,
  exit: number,
  index: number,
  count: number,
  fontSize: number,
) {
  const group = Math.floor(index / 3),
    groups = Math.ceil(count / 3);
  const phase = clamp((entry - (group / Math.max(1, groups - 1)) * 0.28) / 0.72);
  const resolve = 1 - Math.pow(1 - phase, 3);
  const pull = Math.pow(clamp(exit), 3);
  return {
    blur: Math.min(0.6, fontSize * 0.01) * (1 - resolve),
    opacity: (0.72 + 0.28 * resolve) * (1 - pull),
    y: -10 * pull,
    visible: pull < 1,
  };
}

/** Phrase groups resolve continuously while semantic SSR words remain intact. */
export function secondWriting(root: HTMLElement) {
  const paragraph = root.querySelector<HTMLElement>('[data-second-writing]')!;
  const words = [...paragraph.querySelectorAll<HTMLElement>('.statement-word')].map((word) => ({
    word,
    text: word.querySelector<HTMLElement>('.statement-word-text')!,
  }));
  let fontSize = 0;
  function measure() {
    fontSize = parseFloat(getComputedStyle(paragraph).fontSize);
  }
  return {
    measure,
    update(entry: number, exit: number, reduced: boolean) {
      for (let index = 0; index < words.length; index++) {
        const item = words[index]!;
        if (reduced) {
          item.word.style.removeProperty('filter');
          item.word.style.removeProperty('opacity');
          item.text.style.removeProperty('transform');
          item.text.style.removeProperty('opacity');
          item.text.style.removeProperty('will-change');
          continue;
        }
        const state = secondWordState(entry, exit, index, words.length, fontSize);
        const melting = state.blur > 0.3;
        item.word.style.filter = melting ? `blur(${state.blur.toFixed(2)}px)` : '';
        item.word.style.opacity = state.opacity === 1 ? '' : String(state.opacity);
        item.text.style.transform = state.y === 0 ? '' : `translateY(${state.y}%)`;
        item.text.style.opacity = state.visible ? '' : '0';
        item.text.style.willChange = exit > 0 && exit < 1 ? 'transform' : '';
      }
    },
    dispose() {
      for (const { word, text } of words) {
        word.style.removeProperty('filter');
        word.style.removeProperty('opacity');
        text.style.removeProperty('transform');
        text.style.removeProperty('opacity');
        text.style.removeProperty('will-change');
      }
    },
  };
}
