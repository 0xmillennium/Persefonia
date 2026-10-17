import { clamp } from '../timeline/pacing';

/** The left side reaches each row's upper slit before the right side. */
export function writingPull(progress: number, position: number, row: number): number {
  const amount = clamp((progress - row * 0.14 - position * 0.28) / 0.58);
  return amount * amount * (3 - 2 * amount);
}

export function firstWriting(root: HTMLElement) {
  const rows = [...root.querySelectorAll<HTMLElement>('.hero-writing-row')].map((slot) => {
    const text = slot.querySelector<HTMLElement>('.hero-writing-text')!;
    const visual = document.createElement('span');
    visual.className = 'hero-writing-visual';
    visual.hidden = true;
    visual.setAttribute('aria-hidden', 'true');
    const glyphs = [...(text.textContent ?? '')].map((char) => {
      const glyph = document.createElement('span');
      glyph.textContent = char;
      visual.append(glyph);
      return { glyph, position: 0 };
    });
    slot.append(visual);
    return { slot, text, visual, glyphs, height: 0 };
  });
  function measure() {
    for (const row of rows) {
      const bounds = row.slot.getBoundingClientRect();
      row.height = bounds.height;
      const style = getComputedStyle(row.text);
      // Variable-font settings can leave the computed shorthand empty.
      row.visual.style.fontFamily = style.fontFamily;
      row.visual.style.fontSize = style.fontSize;
      row.visual.style.fontWeight = style.fontWeight;
      row.visual.style.fontStyle = style.fontStyle;
      row.visual.style.fontStretch = style.fontStretch;
      row.visual.style.fontVariationSettings = style.fontVariationSettings;
      row.visual.style.textTransform = style.textTransform;
      row.visual.style.color = style.color;
      row.visual.style.letterSpacing = style.letterSpacing;
      const range = document.createRange();
      const text = row.text.firstChild!;
      for (let i = 0; i < row.glyphs.length; i++) {
        range.setStart(text, i);
        range.setEnd(text, i + 1);
        const rect = range.getBoundingClientRect();
        const item = row.glyphs[i]!;
        item.glyph.style.left = `${rect.left - bounds.left}px`;
        item.glyph.style.top = `${rect.top - bounds.top}px`;
        item.glyph.style.width = `${rect.width}px`;
        item.position = i / Math.max(1, row.glyphs.length - 1);
      }
    }
  }
  return {
    measure,
    update(progress: number, reduced: boolean) {
      for (let i = 0; i < rows.length; i++) {
        const row = rows[i]!;
        const animated = !reduced && progress > 0 && progress < 1;
        row.text.style.opacity = reduced || progress === 0 ? '' : '0';
        row.visual.hidden = !animated;
        if (!animated) continue;
        for (const item of row.glyphs) {
          const pull = writingPull(progress, item.position, i);
          item.glyph.style.transform = `translate(${pull * row.height * 0.12}px,${-pull * row.height * 1.25}px)`;
        }
      }
    },
    dispose() {
      for (const row of rows) {
        row.text.style.removeProperty('opacity');
        row.visual.remove();
      }
    },
  };
}
