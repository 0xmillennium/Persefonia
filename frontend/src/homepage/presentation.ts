import { focusNavigation } from '../shared/focus';
import { regionProgress } from './contracts/progress';
import { galleryTravelProgress } from './contracts/gallery';
import { CURTAIN_START } from './contracts/choreography';
import type { SceneRegion } from './timeline/geometry';
import type { HomepageMeasurements } from './timeline/measurements';
import type { sceneState } from './timeline/scene-state';

interface ScenePresentation {
  y: number;
  firstExit: number;
  first: number;
  step: number;
  gap: number;
  frame: ReturnType<typeof sceneState>;
  wipeBands: number[];
  gallery: {
    readonly empty: boolean;
    readonly count: number;
    update(progress: number): void;
    active(value: boolean): void;
  };
}

/** Owns scene visibility, focus eligibility and curtain/clip painting. */
export function homepagePresentation(
  root: HTMLElement,
  regions: SceneRegion[],
  measurements: HomepageMeasurements,
) {
  const wipe = root.querySelector<HTMLElement>('[data-scene-wipe]')!;
  const gallerySpace = root.querySelector<HTMLElement>('.gallery-space')!;
  const markRegion = regions.find((r) => r.name === 'mark')!;
  return {
    render({ y, firstExit, first, step, gap, frame, wipeBands, gallery }: ScenePresentation) {
      const { entrance, galleryExit } = frame;
      const locationLead = innerHeight * 0.1;
      const coverPhase = entrance.phase;
      const curtainSolid = entrance.darkness;
      for (const region of regions) {
        const p = regionProgress(y, region.top, region.height);
        const lead =
          region.name === 'gallery'
            ? markRegion.height * 0.12
            : region.name === 'location'
              ? locationLead
              : region.name === 'mark'
                ? gap
                : 0;
        const visible = y >= region.top - lead && y < region.top + region.height;
        region.face.dataset.visible = String(visible);
        const unavailable =
          !visible ||
          (region.name === 'gallery' &&
            ((y < Math.round(first) &&
              gallery.empty &&
              (gallery.count > 0 || entrance.opacity === 0)) ||
              galleryExit >= 0.5));
        if (unavailable && region.face.contains(document.activeElement)) {
          focusNavigation();
        }
        region.face.inert = unavailable;
        if (region.name === 'gallery') {
          gallery.update(galleryTravelProgress(y, first, step, gallery.count));
          gallery.active(!unavailable);
        }
        if (!visible) continue;
        region.face.style.setProperty('--local-progress', String(p));
        if (region.name === 'hero') {
          region.face
            .querySelectorAll<HTMLElement>('.home-overline,.hero-scroll')
            .forEach((node) => (node.style.visibility = firstExit > 0.12 ? 'hidden' : ''));
        }
        if (region.name === 'gallery') {
          region.face.style.setProperty('--gallery-entry-opacity', String(entrance.opacity));
          region.face.style.setProperty('--gallery-exit', String(galleryExit));
          region.face.style.clipPath = 'none';
          if (wipeBands.at(-1)! < 1) {
            const frontier = wipeBands.flatMap((p, i) => [
              `${(i * innerWidth) / measurements.curtainCount - measurements.galleryClipLeft}px ${(1 - p) * measurements.surfaceHeight - measurements.galleryClipTop}px`,
              `${((i + 1) * innerWidth) / measurements.curtainCount - measurements.galleryClipLeft}px ${(1 - p) * measurements.surfaceHeight - measurements.galleryClipTop}px`,
            ]);
            gallerySpace.style.clipPath = `polygon(0 100%,${frontier.join(',')},100% 100%)`;
            region.face.style.zIndex = '11';
          } else {
            gallerySpace.style.clipPath = 'none';
            region.face.style.zIndex = '11';
          }
        }
        if (region.name === 'location')
          region.face.style.transform = `translateY(${-Math.max(0, y - (region.top + region.height - innerHeight))}px)`;
      }
      wipe.hidden = coverPhase <= CURTAIN_START;
      wipe.style.setProperty('--curtain-solid', String(curtainSolid));
      wipeBands.forEach((p, i) => {
        (wipe.children[i] as HTMLElement).style.transform = `translateY(${(1 - p) * 100}%)`;
      });
    },
    reduced() {
      gallerySpace.style.removeProperty('clip-path');
      wipe.hidden = true;
      for (const region of regions) {
        region.face.dataset.visible = 'true';
        region.face.inert = false;
        for (const name of [
          'clip-path',
          'transform',
          'z-index',
          '--gallery-exit',
          '--gallery-entry-opacity',
        ])
          region.face.style.removeProperty(name);
        region.face
          .querySelectorAll<HTMLElement>('.home-overline,.hero-scroll')
          .forEach((node) => node.style.removeProperty('visibility'));
      }
    },
    dispose() {
      gallerySpace.style.removeProperty('clip-path');
      root.querySelectorAll<HTMLElement>('.scene-face').forEach((face) => {
        face.inert = false;
        delete face.dataset.visible;
        for (const property of [
          'transform',
          'clip-path',
          'z-index',
          '--local-progress',
          '--gallery-exit',
          '--gallery-entry-opacity',
        ])
          face.style.removeProperty(property);
      });
    },
  };
}
