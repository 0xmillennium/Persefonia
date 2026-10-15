export function siteName(link: HTMLElement) {
    return letterReels(link);
}

export function letterReels(link: HTMLElement, proportional = false) {
    const text = link.textContent ?? '';
    const abort = new AbortController();
    const reduced = matchMedia('(prefers-reduced-motion: reduce)');
    const label = document.createElement('span');
    label.className = 'sr-only';
    label.textContent = text;
    const visual = document.createElement('span');
    visual.className = 'rolling-letters';
    if (proportional) visual.classList.add('rolling-letters--proportional');
    visual.setAttribute('aria-hidden', 'true');
    const fontSize = parseFloat(getComputedStyle(link).fontSize);
    const range = document.createRange();
    const originalWhiteSpace = link.style.whiteSpace;
    if (proportional) link.style.whiteSpace = 'pre';
    let offset = 0, word: HTMLElement | null = null;
    const cells = [...text].map(char => {
        let advance = 0;
        if (proportional) {
            range.setStart(link.firstChild!, offset);
            offset += char.length;
            range.setEnd(link.firstChild!, offset);
            advance = range.getBoundingClientRect().width / fontSize;
        }
        if (proportional && /\s/.test(char)) {
            visual.append(document.createTextNode(char));
            word = null;
            return {restore() {}};
        }
        const cell = document.createElement('span');
        cell.className = 'rolling-letter';
        if (proportional) cell.style.width = `${advance}em`;
        cell.textContent = char;
        if (proportional) {
            if (!word) {
                word = document.createElement('span');
                word.className = 'rolling-word';
                visual.append(word);
            }
            word.append(cell);
        } else visual.append(cell);
        let last = -Infinity;
        const restore = () => { cell.textContent = char; };
        const roll = () => {
            const now = performance.now();
            if (reduced.matches || now - last < 120) return;
            const alphabet = /[a-z]/.test(char) ? 'abcdefhiklmnorstuvwxz' : /[A-Z]/.test(char) ? 'ABCDEFGHIJKLMNOPQRSTUVWXYZ' : /[0-9]/.test(char) ? '0123456789' : '';
            if (!alphabet) return;
            last = now;
            const reel = document.createElement('span');
            reel.className = 'rolling-reel';
            for (const glyph of [char, ...Array.from({length:3}, () => alphabet[Math.floor(Math.random() * alphabet.length)]!), char]) {
                const row = document.createElement('span');
                row.textContent = glyph;
                reel.append(row);
            }
            cell.replaceChildren(reel);
        };
        cell.addEventListener('pointerenter', roll, {signal:abort.signal});
        cell.addEventListener('pointerdown', roll, {signal:abort.signal});
        cell.addEventListener('animationend', restore, {signal:abort.signal});
        return {restore};
    });
    if (proportional) {
        if (originalWhiteSpace) link.style.whiteSpace = originalWhiteSpace;
        else link.style.removeProperty('white-space');
    }
    link.replaceChildren(label, visual);
    reduced.addEventListener('change', () => { if (reduced.matches) for (const cell of cells) cell.restore(); }, {signal:abort.signal});
    return {dispose() { abort.abort(); link.textContent = text; }};
}
