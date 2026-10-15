import { clamp } from './scenes';

/** The reference spreads word starts across a window while overlapping their motion. */
function wordPhase(progress: number, index: number, count: number, share: number): number {
    const offset = index / Math.max(1, count - 1) * (1 - share);
    return clamp((progress - offset) / share);
}

export function secondWordState(entry: number, exit: number, index: number, count: number, fontSize: number) {
    const resolve = 1 - Math.pow(1 - wordPhase(entry, index, count, .55), 5);
    const blur = fontSize * .15 * (1 - resolve);
    const pull = Math.pow(wordPhase(exit, index, count, .6), 4);
    return { blur, opacity: blur > .3 ? .3 + .7 * resolve : 1, y: -100 * pull, visible: pull <= .95 };
}

const SVG = 'http://www.w3.org/2000/svg';
let nextWritingId = 0;
function element(name: string, attributes: Record<string, string>) {
    const node = document.createElementNS(SVG, name);
    for (const [key, value] of Object.entries(attributes)) node.setAttribute(key, value);
    return node;
}

/** Semantic SSR words resolve from goo, then lift into their own clipped slots. */
export function secondWriting(root: HTMLElement) {
    const paragraph = root.querySelector<HTMLElement>('[data-second-writing]')!;
    const defs = element('svg', { 'aria-hidden': 'true', width: '0', height: '0', class: 'second-writing-defs' });
    const filters = element('defs', {});
    const id = `second-writing-${nextWritingId++}`;
    const words = [...paragraph.querySelectorAll<HTMLElement>('.statement-word')].map((word, index) => {
        const filterId = `${id}-${index}`;
        const filter = element('filter', { id: filterId, x: '-70%', y: '-70%', width: '240%', height: '240%', 'color-interpolation-filters': 'sRGB' });
        const blur = element('feGaussianBlur', { in: 'SourceGraphic', stdDeviation: '0', result: 'blur' });
        const threshold = element('feColorMatrix', { in: 'blur', type: 'matrix', values: '1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 12.5 -1.5' });
        filter.append(blur, threshold);
        filters.append(filter);
        return { word, text: word.querySelector<HTMLElement>('.statement-word-text')!, blur, filterId };
    });
    defs.append(filters);
    paragraph.append(defs);
    let fontSize = 0;
    function measure() { fontSize = parseFloat(getComputedStyle(paragraph).fontSize); }
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
                const melting = state.blur > .3;
                if (melting) item.blur.setAttribute('stdDeviation', state.blur.toFixed(2));
                item.word.style.filter = melting ? `url(#${item.filterId})` : '';
                item.word.style.opacity = melting ? String(state.opacity) : '';
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
            defs.remove();
        }
    };
}
