import { measureRegions, type SceneRegion, type SceneGeometry } from './geometry';
import { filmRange, writingPacing, touchGalleryDistance, HOMEPAGE_SCROLL_SPEED } from './pacing';
import { GALLERY_HANDOFF } from '../contracts/choreography';

interface MeasurementsOptions {
  root: HTMLElement;
  regions: SceneRegion[];
  environment: HTMLElement;
  curtainBars: HTMLElement[];
  gallerySpace: HTMLElement;
  viewport: { readonly height: number };
  reduced: MediaQueryList;
  touch: MediaQueryList;
  count: () => number;
  travel: {
    readonly pathCount: number;
    measure(top: number, height: number, entry: number): void;
    end(count: number): number;
    position(progress: number): number;
  };
}

/** Owns layout reads and their CSS distance writes; no animation or input ownership. */
export class HomepageMeasurements {
  geometry: Readonly<Record<string, SceneGeometry>> = Object.freeze({});
  curtainHeight = 0;
  surfaceHeight = 0;
  curtainCount = 0;
  galleryClipLeft = 0;
  galleryClipTop = 0;
  private galleryEntryDistance = 0;
  private measuredSizes = new WeakMap<Element, string>();
  private distances = (() => {
    const pacing = writingPacing();
    return {
      '--first-writing-distance': pacing.hero,
      '--statement-writing-distance': pacing.statement,
      '--writing-distance': pacing.body,
      '--writing-gap': pacing.gap,
    };
  })();

  constructor(private options: MeasurementsOptions) {}

  private sizeOf(node: Element) {
    return `${node.clientWidth}:${node.clientHeight}`;
  }
  changed(node: Element) {
    return this.measuredSizes.get(node) !== this.sizeOf(node);
  }
  private clearDistances() {
    for (const property of Object.keys(this.distances))
      this.options.root.style.removeProperty(property);
  }
  measure() {
    const { root, regions, environment, curtainBars, viewport, reduced, touch, count, travel } =
      this.options;
    const filmRegion = regions[0]!;
    const markRegion = regions.find((r) => r.name === 'mark')!;
    const galleryRegion = regions.find((r) => r.name === 'gallery')!;
    const distances = this.distances;
    const clearDistances = () => this.clearDistances();
    const measuredY = scrollY;
    this.galleryEntryDistance = 0;
    galleryRegion.node.style.setProperty('--gallery-entry-distance', '0px');
    galleryRegion.node.style.setProperty('--gallery-tail-distance', '0px');
    const maxCount = Math.max(
      ...[...root.querySelectorAll('.gallery-group')].map((g) => g.children.length),
      1,
    );
    galleryRegion.node.style.setProperty(
      '--gallery-distance',
      String(
        touch.matches
          ? touchGalleryDistance(travel.pathCount)
          : (3 + (maxCount + 8) * 1.4) / HOMEPAGE_SCROLL_SPEED,
      ),
    );
    if (reduced.matches) clearDistances();
    else
      for (const [property, distance] of Object.entries(distances))
        root.style.setProperty(property, `${distance}px`);
    const readRegions = () => {
      this.geometry = measureRegions(regions, scrollY);
    };
    readRegions();
    this.curtainHeight = viewport.height;
    if (!reduced.matches) {
      const playback = filmRange(
        filmRegion.top,
        filmRegion.height,
        markRegion.top,
        markRegion.height,
      );
      this.galleryEntryDistance = touch.matches
        ? 0
        : Math.max(
            0,
            Math.ceil(playback.end) +
              this.curtainHeight * GALLERY_HANDOFF.entryDistance -
              galleryRegion.top -
              galleryRegion.height * 0.12,
          );
      galleryRegion.node.style.setProperty(
        '--gallery-entry-distance',
        `${this.galleryEntryDistance}px`,
      );
      readRegions();
    }
    travel.measure(galleryRegion.top, galleryRegion.height, this.galleryEntryDistance);
    if (!reduced.matches) {
      const tail = Math.max(0, travel.end(count()) - travel.position(0.82));
      galleryRegion.node.style.setProperty('--gallery-tail-distance', `${tail}px`);
      readRegions();
      if (tail > 0 && scrollY < measuredY) scrollTo({ top: measuredY, behavior: 'instant' });
    }
    this.surfaceHeight = environment.clientHeight;
    this.curtainCount = curtainBars.filter(
      (bar) => getComputedStyle(bar).display !== 'none',
    ).length;
  }
  resizeSurface() {
    const { root, environment, gallerySpace } = this.options;
    this.surfaceHeight = environment.clientHeight;
    const box = gallerySpace.getBoundingClientRect();
    this.galleryClipLeft = box.left;
    this.galleryClipTop = box.top;
    for (const node of [root, environment]) this.measuredSizes.set(node, this.sizeOf(node));
  }
  dispose() {
    this.clearDistances();
    for (const name of [
      '--gallery-entry-distance',
      '--gallery-tail-distance',
      '--gallery-distance',
    ])
      this.options.regions
        .find((region) => region.name === 'gallery')!
        .node.style.removeProperty(name);
  }
}
