export type FilmBox = readonly [number, number, number, number];
export type WindowBox = readonly [number, number, number, number, number];

/** Fit the complete authored 16:9 frame once; scrolling changes only the cutout. */
export function filmBox(width: number, height: number): FilmBox {
    const filmWidth = Math.min(width, height * 16 / 9);
    const filmHeight = filmWidth * 9 / 16;
    const left = (width - filmWidth) / 2, top = (height - filmHeight) / 2;
    return [left, top, left + filmWidth, top + filmHeight];
}

/** Cover the viewport at a fixed 16:9 scale, keeping the shared subject area in view. */
export function filmCoverBox(width: number, height: number): FilmBox {
    const filmWidth = Math.max(width, height * 16 / 9);
    const filmHeight = filmWidth * 9 / 16;
    const left = (width - filmWidth) * .7, top = (height - filmHeight) / 2;
    return [left, top, left + filmWidth, top + filmHeight];
}

export function heroWindow(width: number, height: number, expansion: number): WindowBox {
    const [left, top, right, bottom] = filmBox(width, height);
    // One shared opening contains both first-frame subjects at their authored scale.
    const subject = [.495, 0, .905, .94];
    const frameWidth = right - left, frameHeight = bottom - top;
    const x = left + subject[0]! * frameWidth, y = top + subject[1]! * frameHeight;
    const endX = left + subject[2]! * frameWidth, endY = top + subject[3]! * frameHeight;
    const remaining = 1 - expansion;
    return [x * remaining, y * remaining, endX + (width - endX) * expansion,
        endY + (height - endY) * expansion, (endX - x) * .5 * remaining];
}

/** The outer sheet minus a semicircular doorway, in viewport pixels. */
export function windowClip(width: number, height: number, box: WindowBox): string {
    const [left, top, right, bottom, requestedRadius] = box;
    const radius = Math.min(requestedRadius, (right - left) / 2, bottom - top);
    const arch = radius > 0 ? `A${radius} ${radius} 0 0 1 ${right} ${top + radius}` : `L${right} ${top}`;
    const otherArch = radius > 0 ? `A${radius} ${radius} 0 0 1 ${left + radius} ${top}` : `L${left} ${top}`;
    return `path(evenodd, "M0 0H${width}V${height}H0Z M${left + radius} ${top}H${right - radius}${arch}V${bottom}H${left}V${top + radius}${otherArch}Z")`;
}
