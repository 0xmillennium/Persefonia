import type { Background } from './film-scenes';

export type FilmBox = readonly [number, number, number, number];
export type WindowBox = readonly [number, number, number, number, number];
export type WindowCircle = readonly [number, number, number];
export type WindowDividers = readonly [x: number, y: number, thickness: number];
export interface HeroOpening { box: WindowBox; circles: readonly WindowCircle[]; extraBox?: WindowBox; dividers?: WindowDividers; capsules?: readonly WindowBox[] }
export interface HeroWritingBounds { top: number; bottom: number; ceiling: number; floor: number; right?: number }

/** Fit the complete authored 16:9 frame once; scrolling changes only the cutout. */
export function filmBox(width: number, height: number): FilmBox {
    const filmWidth = Math.min(width, height * 16 / 9);
    const filmHeight = filmWidth * 9 / 16;
    const left = (width - filmWidth) / 2, top = (height - filmHeight) / 2;
    return [left, top, left + filmWidth, top + filmHeight];
}

/** Cover the viewport at a fixed 16:9 scale, keeping the shared subject area in view. */
export function filmCoverBox(width: number, height: number, film: Background = 'robotic-arm'): FilmBox {
    const filmWidth = Math.max(width, height * 16 / 9);
    const filmHeight = filmWidth * 9 / 16;
    // Portrait framing centres the constellation's subject area, rather than
    // clipping its upper-right star. This crop is fixed throughout scrolling.
    const portrait = Math.max(0, Math.min(1, (height / width - 1) * 2));
    const landscapeLeft = (width - filmWidth) * .7;
    const portraitLeft = Math.min(0, Math.max(width - filmWidth, width * .5 - filmWidth * .67));
    const left = film === 'waterfall' ? (width - filmWidth) / 2 : landscapeLeft + (portraitLeft - landscapeLeft) * portrait;
    const top = (height - filmHeight) / 2;
    return [left, top, left + filmWidth, top + filmHeight];
}

function initialTreeWindow(width: number, height: number, writingBottom = 0): WindowBox {
    const [left, top, right, bottom] = filmCoverBox(width, height);
    // Fit the tree's crown and trunk in the actual cover crop. The wider arch
    // shifts its centre right; portrait retains a visible photo margin.
    const subject = [.5, .025, .9, .91];
    const frameWidth = right - left, frameHeight = bottom - top;
    const narrow = Math.max(0, Math.min(1, (1200 - width) / 300));
    const mobile = Math.max(0, Math.min(1, (700 - width) / 200))
        * Math.max(0, Math.min(1, (height / width - 1) * 2));
    // Crop the upper branches on portrait phones so the small visible tree
    // reveals its full scale as the opening grows. The film stays fixed.
    const trunk = left + frameWidth * .68;
    const fullX = Math.max(0, left + subject[0]! * frameWidth);
    const narrowX = Math.max(14, Math.min(width * .49, trunk - 28));
    const initialX = fullX + (narrowX - fullX) * narrow;
    const initialEndX = Math.min(width - (width < height ? 24 : 0), left + subject[2]! * frameWidth);
    const shift = ((initialX + initialEndX) / 2 - trunk) * mobile;
    const x = initialX - shift;
    const initialY = Math.max(height * .16 * narrow, top + subject[1]! * frameHeight);
    const y = initialY + (Math.max(height * .4, writingBottom) - initialY) * mobile;
    const endX = initialEndX - shift, endY = Math.min(height, top + subject[3]! * frameHeight);
    return [x, y, endX, endY, (endX - x) / 2];
}

// Measured in the qualified 1600×900 first frame. The sixth opening includes
// the close lower pair; the small satellite points need no separate windows.
const STARS: readonly WindowCircle[] = [
    [1011, 102, 58], [1228, 222, 46], [922, 325, 54], [916, 511, 42],
    [1093, 564, 54], [1182, 624, 60], [1093, 734, 28]
];

function initialOpening(width: number, height: number, film: Background, writing?: HeroWritingBounds): HeroOpening {
    if (film === 'tree' && width > 960) {
        const initial = initialTreeWindow(width, height);
        const inset = (initial[2] - initial[0]) * .125;
        const gap = Math.max(14, Math.min(22, (initial[2] - initial[0]) * .028));
        const radius = (initial[2] - initial[0] - inset * 2 - gap * 2) / 6;
        // Six equal arched windows, matching the phone's doorway shape.
        const top = writing?.ceiling ?? initial[1], bottom = writing?.floor ?? initial[3];
        const middle = (top + bottom) / 2;
        const box: WindowBox = [initial[0] + inset, top, initial[2] - inset, bottom, radius];
        const thickness = gap;
        return { box, circles: [], dividers: [(box[0] + box[2]) / 2,
            middle, thickness] };
    }
    if (film === 'tree' && width <= 700 && height > width * 1.2 && writing) {
        const gap = 16;
        const upperBottom = writing.top - gap, lowerTop = writing.bottom + gap;
        const doorHeight = Math.max(1, Math.min(upperBottom - writing.ceiling, writing.floor - lowerTop));
        const doorWidth = Math.min(width * .47, doorHeight * .9);
        const left = (width - doorWidth) / 2, right = width - left;
        const window = (top: number, bottom: number): WindowBox => [left, top, right, bottom, doorWidth / 2];
        return { box: window(lowerTop, lowerTop + doorHeight), extraBox: window(upperBottom - doorHeight, upperBottom), circles: [] };
    }
    if (film === 'waterfall') {
        const doorWidth = Math.min(width * .56, height * .36) * (width > 960 ? .85 : 1);
        const left = width > 960 && writing?.right !== undefined ? writing.right + 20 : (width - doorWidth) / 2;
        if (width > 960) {
            const top = height * .16, bottom = height * .9, centerY = (top + bottom) / 2;
            const gap = Math.max(24, Math.min(36, width * .025));
            // Fit three progressively smaller windows to the right while
            // preserving the first window and its gap from the writing.
            const available = Math.max(0, width - left - doorWidth - gap * 3 - 24) / doorWidth;
            let scale = .68;
            if (scale + scale ** 2 + scale ** 3 > available) {
                let low = 0, high = scale;
                for (let i = 0; i < 24; i++) {
                    const candidate = (low + high) / 2;
                    if (candidate + candidate ** 2 + candidate ** 3 <= available) low = candidate;
                    else high = candidate;
                }
                scale = low;
            }
            const window = (x: number, y: number, w: number, h: number): WindowBox => [x, y, x + w, y + h, w / 2];
            let x = left;
            const capsules = Array.from({ length: 4 }, (_, index) => {
                const factor = scale ** index, w = doorWidth * factor, h = (bottom - top) * factor;
                const capsule = window(x, centerY - h / 2, w, h);
                x += w + gap;
                return capsule;
            });
            return { box: capsules[0]!, circles: [], capsules };
        }
        const box: WindowBox = [left, height * .16, width - left, height * .9, doorWidth / 2];
        return { box, circles: [], capsules: [box] };
    }
    if (film === 'tree') return { box: initialTreeWindow(width, height, writing ? writing.bottom + 16 : 0), circles: [] };
    const [left, top, right] = filmCoverBox(width, height);
    const scale = (right - left) / 1600;
    const circles = STARS.map(([sx, sy, radius]): WindowCircle => {
        const x = left + sx * scale, y = top + sy * scale;
        return [x, y, radius * scale];
    });
    return { box: [0, 0, width, height, 0], circles };
}

/** Project one rigid window layout toward the viewer; gaps and curves scale together.
 * The photograph and film remain screen-sized, as in the approved reference.
 */
export function heroOpening(width: number, height: number, expansion: number, film: Background, writing?: HeroWritingBounds): HeroOpening {
    const progress = Math.max(0, Math.min(1, expansion));
    if (progress === 1) return { box: [0, 0, width, height, 0], circles: [] };
    const initial = initialOpening(width, height, film, writing);
    if (progress === 0) return initial;
    let focus = initial.box;
    if (initial.dividers) {
        const [, middle, gap] = initial.dividers;
        const paneWidth = (focus[2] - focus[0] - gap * 2) / 3;
        // Travel through the middle lower pane, rather than an opaque mullion.
        focus = [focus[0] + paneWidth + gap, middle + gap / 2,
            focus[0] + paneWidth * 2 + gap, focus[3], focus[4]];
    }
    const circle = initial.circles[2];
    const centerX = circle?.[0] ?? (focus[0] + focus[2]) / 2;
    const centerY = circle?.[1] ?? (focus[1] + focus[3]) / 2;
    // This inscribed circle guarantees the chosen opening covers all corners
    // before removing the sheet, including short or unusually wide viewports.
    const innerRadius = circle?.[2] ?? Math.min(focus[2] - focus[0], focus[3] - focus[1]) / 4;
    const fullScale = Math.hypot(width, height) / (2 * Math.max(1, innerRadius)) + 2;
    const scale = 1 / (1 - progress * (1 - 1 / fullScale));
    const offsetX = centerX * (1 - scale) + (width / 2 - centerX) * progress;
    const offsetY = centerY * (1 - scale) + (height / 2 - centerY) * progress;
    const box = ([left, top, right, bottom, radius]: WindowBox): WindowBox =>
        [left * scale + offsetX, top * scale + offsetY, right * scale + offsetX, bottom * scale + offsetY, radius * scale];
    return {
        box: box(initial.box),
        circles: initial.circles.map(([x, y, radius]) => [x * scale + offsetX, y * scale + offsetY, radius * scale]),
        ...(initial.extraBox && { extraBox: box(initial.extraBox) }),
        ...(initial.dividers && { dividers: [initial.dividers[0] * scale + offsetX,
            initial.dividers[1] * scale + offsetY, initial.dividers[2] * scale] as WindowDividers }),
        ...(initial.capsules && { capsules: initial.capsules.map(box) })
    };
}

/** Intersect the opaque outside of every circle: overlapping holes stay open. */
export function applyHeroOpening(sheet: HTMLElement, width: number, height: number, opening: HeroOpening): void {
    let mask = opening.circles.map(([x, y, radius]) =>
        `radial-gradient(circle at ${x}px ${y}px, transparent ${Math.max(0, radius - .5)}px, #000 ${radius + .5}px)`).join(',');
    if (opening.extraBox) {
        // Intersect opaque sheets so growing arches form a union, including
        // their overlap. Even-odd holes in one path would refill that overlap.
        mask = [opening.box, opening.extraBox].map(box => {
            const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}"><path fill="black" fill-rule="evenodd" d="M0 0H${width}V${height}H0Z ${windowPath(box)}"/></svg>`;
            return `url("data:image/svg+xml,${encodeURIComponent(svg)}")`;
        }).join(',');
    }
    if (opening.dividers) {
        const [, y, thickness] = opening.dividers;
        const [left, top, right, bottom, radius] = opening.box;
        const half = thickness / 2;
        const paneWidth = (right - left - thickness * 2) / 3;
        const panes = Array.from({ length: 6 }, (_, index): WindowBox => {
            const x = left + (index % 3) * (paneWidth + thickness);
            return [x, index < 3 ? top : y + half, x + paneWidth, index < 3 ? y - half : bottom, radius];
        });
        const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}"><path fill="black" fill-rule="evenodd" d="M0 0H${width}V${height}H0Z ${panes.map(box => windowPath(box)).join(' ')}"/></svg>`;
        mask = `url("data:image/svg+xml,${encodeURIComponent(svg)}")`;
    }
    if (opening.capsules) {
        mask = opening.capsules.map(box => {
            const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}"><path fill="black" fill-rule="evenodd" d="M0 0H${width}V${height}H0Z ${windowPath(box, true)}"/></svg>`;
            return `url("data:image/svg+xml,${encodeURIComponent(svg)}")`;
        }).join(',');
    }
    const clip = mask ? '' : windowClip(width, height, opening.box);
    if (sheet.style.clipPath !== clip) sheet.style.clipPath = clip;
    if (sheet.style.maskImage !== mask) sheet.style.maskImage = mask;
    sheet.style.maskComposite = mask ? 'intersect' : '';
}

/** The outer sheet minus a semicircular doorway, in viewport pixels. */
export function windowClip(width: number, height: number, box: WindowBox): string {
    return `path(evenodd, "M0 0H${width}V${height}H0Z ${windowPath(box)}")`;
}

function windowPath(box: WindowBox, roundBottom = false): string {
    const [left, top, right, bottom, requestedRadius] = box;
    const radius = Math.min(requestedRadius, (right - left) / 2, (bottom - top) / (roundBottom ? 2 : 1));
    const arch = radius > 0 ? `A${radius} ${radius} 0 0 1 ${right} ${top + radius}` : `L${right} ${top}`;
    const otherArch = radius > 0 ? `A${radius} ${radius} 0 0 1 ${left + radius} ${top}` : `L${left} ${top}`;
    const base = roundBottom && radius > 0
        ? `V${bottom - radius}A${radius} ${radius} 0 0 1 ${right - radius} ${bottom}H${left + radius}A${radius} ${radius} 0 0 1 ${left} ${bottom - radius}`
        : `V${bottom}H${left}`;
    return `M${left + radius} ${top}H${right - radius}${arch}${base}V${top + radius}${otherArch}Z`;
}
