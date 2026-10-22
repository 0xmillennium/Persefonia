export { WritingEntrance } from './entrance';
import { clamp } from '../contracts/progress';

/** The left side reaches each row's upper slit before the right side. */
export function writingPull(progress: number, position: number, row: number): number {
  const amount = clamp((progress - row * 0.14 - position * 0.28) / 0.58);
  return amount * amount * (3 - 2 * amount);
}

/** Decorative glyphs mirror validated SSR text; foreign markup keeps its semantic fallback. */
export function firstWriting(root: HTMLElement) {
  let progress = 0,
    reduced = false;
  const rows = [...root.querySelectorAll<HTMLElement>('.hero-writing-row')].map((slot) => {
    const text = slot.querySelector<HTMLElement>('.hero-writing-text')!;
    const visual = document.createElement('span');
    visual.className = 'hero-writing-visual';
    visual.hidden = true;
    visual.setAttribute('aria-hidden', 'true');
    slot.append(visual);
    return {
      slot,
      text,
      visual,
      glyphs: [] as { glyph: HTMLElement; position: number }[],
      height: 0,
      valid: false,
    };
  });
  function render() {
    rows.forEach((row, i) => {
      const animated = row.valid && !reduced && progress > 0 && progress < 1;
      row.text.style.opacity = !row.valid || reduced || progress === 0 ? '' : '0';
      row.visual.hidden = !animated;
      if (!animated) return;
      for (const item of row.glyphs) {
        const pull = writingPull(progress, item.position, i);
        item.glyph.style.transform = `translate(${pull * row.height * 0.12}px,${-pull * row.height * 1.25}px)`;
      }
    });
  }
  function measure() {
    for (const row of rows) {
      row.valid = false;
      if (row.text.parentNode !== row.slot) continue;
      const source = row.text.firstChild;
      if (row.text.childNodes.length !== 1 || source?.nodeType !== Node.TEXT_NODE) continue;
      const text = source.textContent ?? '';
      const bounds = row.slot.getBoundingClientRect();
      row.height = bounds.height;
      const style = getComputedStyle(row.text);
      for (const key of [
        'fontFamily',
        'fontSize',
        'fontWeight',
        'fontStyle',
        'fontStretch',
        'fontVariationSettings',
        'textTransform',
        'color',
        'letterSpacing',
      ] as const)
        row.visual.style[key] = style[key];
      const range = document.createRange();
      const glyphs: typeof row.glyphs = [];
      let offset = 0;
      try {
        for (const char of text) {
          range.setStart(source, offset);
          range.setEnd(source, offset + char.length);
          offset += char.length;
          const rect = range.getBoundingClientRect();
          const glyph = document.createElement('span');
          glyph.textContent = char;
          glyph.style.left = `${rect.left - bounds.left}px`;
          glyph.style.top = `${rect.top - bounds.top}px`;
          glyph.style.width = `${rect.width}px`;
          glyphs.push({ glyph, position: 0 });
        }
        glyphs.forEach((item, i) => {
          item.position = i / Math.max(1, glyphs.length - 1);
        });
        row.visual.replaceChildren(...glyphs.map((item) => item.glyph));
        row.glyphs = glyphs;
        row.valid = row.text.firstChild === source && row.text.textContent === text;
      } catch {
        // A foreign text owner may replace the source: keep its actual semantic text visible.
      }
    }
    render();
  }
  const observer = new MutationObserver(measure);
  for (const row of rows) {
    observer.observe(row.text, { childList: true, characterData: true, subtree: true });
    observer.observe(row.slot, { childList: true });
  }
  return {
    measure,
    update(value: number, preference: boolean) {
      progress = clamp(value);
      reduced = preference;
      render();
    },
    dispose() {
      observer.disconnect();
      for (const row of rows) {
        row.text.style.removeProperty('opacity');
        row.visual.remove();
      }
    },
  };
}
