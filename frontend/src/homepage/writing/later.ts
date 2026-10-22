import { windowProgress } from '../contracts/progress';
import { CURTAIN_START, GALLERY_HANDOFF } from '../contracts/choreography';

const SVG = 'http://www.w3.org/2000/svg';
const easeOut = (p: number) => 1 - Math.pow(1 - p, 5);
const easeIn = (p: number) => Math.pow(p, 4);

export function galleryCurtain(phase: number, count: number) {
  return Array.from({ length: count }, (_, i) => {
    const start = CURTAIN_START + i * GALLERY_HANDOFF.stagger;
    return easeOut(windowProgress(phase, start, start + GALLERY_HANDOFF.column));
  });
}

let nextIdentityId = 0;
export function laterWriting(root: HTMLElement) {
  const third = [...root.querySelectorAll<HTMLElement>('.capabilities li>span')];
  const fourth = [...root.querySelectorAll<HTMLElement>('.brand-statement>span>span')];
  const svg = root.querySelector<SVGSVGElement>('[data-physical-identity] svg')!;
  const defs = document.createElementNS(SVG, 'defs');
  const id = `identity-slots-${nextIdentityId++}`;
  // Keep the inner polygons' transforms exclusively owned by the physics renderer.
  const pieces = [...svg.querySelectorAll<SVGPolygonElement>('[data-fragment]')]
    .map((polygon, index) => {
      const box = polygon.getBBox(),
        matrix = polygon.transform.baseVal.consolidate()!.matrix;
      const bottom = box.y + box.height + matrix.f;
      const clip = document.createElementNS(SVG, 'clipPath');
      clip.id = `${id}-${index}`;
      const rect = document.createElementNS(SVG, 'rect');
      rect.setAttribute('x', '0');
      rect.setAttribute('y', '-440');
      rect.setAttribute('width', '960');
      rect.setAttribute('height', String(bottom + 440));
      clip.append(rect);
      defs.append(clip);
      const slot = document.createElementNS(SVG, 'g'),
        rise = document.createElementNS(SVG, 'g');
      slot.dataset.identitySlot = '';
      polygon.before(slot);
      slot.append(rise);
      rise.append(polygon);
      return {
        polygon,
        slot,
        rise,
        bottom,
        x: matrix.e,
        height: box.height,
        clipId: clip.id,
        animation: null as Animation | null,
      };
    })
    .sort((a, b) => b.bottom - a.bottom || a.x - b.x);
  svg.prepend(defs);
  const entranceDuration = 800 + (pieces.length - 1) * 55;
  let settled = false;
  const cancel = () => {
    for (const piece of pieces) {
      piece.animation?.cancel();
      piece.animation = null;
    }
  };
  function rows(
    lines: HTMLElement[],
    entry: number,
    exit: number,
    stagger: number,
    duration: number,
    reduced: boolean,
  ) {
    lines.forEach((line, i) => {
      if (reduced) {
        line.style.removeProperty('transform');
        line.style.removeProperty('opacity');
        return;
      }
      const emphasis = easeOut(
        windowProgress(entry, stagger * i * 0.4, stagger * i * 0.4 + duration),
      );
      const y = (1 - easeOut(entry)) * 8 - easeIn(exit) * 10;
      line.style.transform = `translateY(${y}%)`;
      line.style.opacity =
        entry === 1 && exit === 0 ? '' : String((0.72 + emphasis * 0.28) * (1 - easeIn(exit)));
    });
  }
  return {
    third(entry: number, exit: number, reduced: boolean) {
      rows(third, entry, exit, 0.09 / 0.62, 0.35 / 0.62, reduced);
    },
    fourth(textEntry: number, exit: number, entry: number, reduced: boolean) {
      rows(fourth, textEntry, exit, 0.0686 / 0.221, 0.1524 / 0.221, reduced);
      if (reduced) {
        cancel();
        settled = false;
        for (const piece of pieces) {
          piece.rise.style.removeProperty('transform');
          piece.slot.removeAttribute('clip-path');
        }
        return;
      }
      settled = entry === 1 && exit === 0;
      if (entry === 0 || entry === 1 || exit > 0) cancel();
      pieces.forEach((piece, i) => {
        if (settled) {
          piece.rise.style.removeProperty('transform');
          piece.slot.removeAttribute('clip-path');
        } else {
          piece.slot.setAttribute('clip-path', `url(#${piece.clipId})`);
          if (entry === 0 || exit > 0) {
            piece.rise.style.transform = `translateY(${piece.height * 1.2 * (exit > 0 ? easeIn(exit) : 1)}px)`;
          } else {
            piece.rise.style.removeProperty('transform');
            if (!piece.animation) {
              piece.animation = piece.rise.animate(
                { transform: [`translateY(${piece.height * 1.2}px)`, 'translateY(0px)'] },
                {
                  duration: 800,
                  delay: i * 55,
                  easing: 'cubic-bezier(0.16, 1, 0.3, 1)',
                  fill: 'both',
                },
              );
              piece.animation.pause();
            }
            piece.animation.currentTime = entry * entranceDuration;
          }
        }
      });
    },
    get settled() {
      return settled;
    },
    dispose() {
      cancel();
      for (const piece of pieces) {
        piece.slot.before(piece.polygon);
        piece.slot.remove();
      }
      defs.remove();
      for (const line of [...third, ...fourth]) {
        line.style.removeProperty('transform');
        line.style.removeProperty('opacity');
      }
    },
  };
}
