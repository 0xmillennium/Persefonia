import {
  filmRange,
  openingState,
  regionProgress,
  writingBeat,
  writingPacing,
  windowProgress,
} from './pacing';
import { galleryEntrance, galleryTravelProgress } from '../gallery/motion';
import type { SceneGeometry } from './geometry';

const pacing = writingPacing();

/** Presentation derives from one completed geometry snapshot, never live DOM reads. */
export function sceneState(
  y: number,
  scenes: Readonly<Record<string, SceneGeometry>>,
  gallery: {
    end: number;
    boundary: number;
    cue: number;
    first: number;
    authoredFirst: number;
    step: number;
    count: number;
    columns: number;
  },
) {
  const hero = scenes.hero!,
    mark = scenes.mark!;
  const playback = filmRange(hero.top, hero.height, mark.top, mark.height);
  const galleryProgress =
    y < gallery.end
      ? 0.12 + 0.7 * galleryTravelProgress(y, gallery.first, gallery.step, gallery.count)
      : 0.82 + 0.18 * regionProgress(y, gallery.end, gallery.boundary - gallery.end);
  const entrance = galleryEntrance(
    y,
    Math.ceil(playback.end),
    gallery.cue,
    gallery.authoredFirst,
    gallery.step,
    gallery.columns,
  );
  return {
    expansion: openingState(regionProgress(y, hero.top, hero.height)).expansion,
    film: regionProgress(y, playback.start, playback.end - playback.start),
    galleryProgress,
    entrance,
    galleryExit: windowProgress(galleryProgress, 0.82, 1),
    second: writingBeat(y, scenes.statements!.top),
    third: writingBeat(y, scenes.capabilities!.top),
    fourth: writingBeat(y, mark.top),
    logo: windowProgress(y, mark.top - pacing.gap, mark.top),
  };
}
