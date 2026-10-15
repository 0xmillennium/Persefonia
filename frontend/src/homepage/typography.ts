export function glyphs(root: HTMLElement) {
    const abort = new AbortController();
    const elements = [...root.querySelectorAll<HTMLElement>('[data-glyph]')].map(link => {
        const slot = link.querySelector<HTMLElement>('[data-glyph-label]') ?? link;
        const label = document.createElement('span');
        label.className = 'sr-only';
        label.textContent = slot.textContent;
        const visual = document.createElement('span');
        visual.setAttribute('aria-hidden', 'true');
        visual.className = 'glyph-layer';
        const chars = [...(slot.textContent ?? '')].map(char => { const span = document.createElement('span'); span.textContent = char; visual.append(span); return { span, char, until: 0 }; });
        slot.replaceChildren(label, visual);
        link.addEventListener('pointerenter', event => { if (event.pointerType !== 'touch') link.dataset.previewActive = 'true'; }, { signal: abort.signal });
        link.addEventListener('pointerleave', () => { delete link.dataset.previewActive; }, { signal: abort.signal });
        return { link, slot, chars, rect: link.getBoundingClientRect() };
    });
    const reduced = matchMedia('(prefers-reduced-motion: reduce)');
    let timer = 0;
    const alphabet = '01<>/+*#';
    const settle = () => { timer = 0; const now = performance.now(); let remains = false; for (const { chars } of elements)
        for (const char of chars) {
            if (char.until > now) {
                remains = true;
                char.span.textContent = alphabet[Math.floor(now / 60) % alphabet.length]!;
            }
            else
                char.span.textContent = char.char;
        } if (remains)
        timer = window.setTimeout(settle, 60); };
    root.addEventListener('pointermove', e => {
        if (e.pointerType === 'touch' || reduced.matches)
            return;
        const now = performance.now();
        for (const { rect, chars } of elements) {
            if (e.clientY < rect.top - 15 || e.clientY > rect.bottom + 15)
                continue;
            chars.forEach((char, i) => { if (Math.abs(e.clientX - (rect.left + (i + .5) * rect.width / chars.length)) < 24)
                char.until = now + 300; });
        }
        if (!timer)
            settle();
    }, { signal: abort.signal });
    return { measure() { for (const item of elements)
        item.rect = item.link.getBoundingClientRect(); }, dispose() { abort.abort(); clearTimeout(timer); for (const { link, slot, chars } of elements) {
        slot.textContent = chars.map(c => c.char).join(''); delete link.dataset.previewActive;
    } } };
}
