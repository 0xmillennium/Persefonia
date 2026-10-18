import { GalleryTravel } from './travel';

interface GalleryPort {
  readonly category: string;
  readonly count: number;
  active(value: boolean): void;
  select(category: string): void;
}
/** Own default-collection re-entry and restored travel; the page owns rendered geometry. */
export class GalleryHandoff {
  readonly travel: GalleryTravel;
  private side: -1 | 0 | 1 | null = null;
  constructor(
    private projectsCount: number,
    saved: unknown,
    restoring: boolean,
  ) {
    const value = saved as { pathCount?: number; tuningShift?: number } | undefined;
    this.travel = new GalleryTravel(
      restoring && Number.isInteger(value?.pathCount) && value!.pathCount! > 0
        ? value!.pathCount!
        : Math.max(1, projectsCount),
      restoring && Number.isFinite(value?.tuningShift) ? value!.tuningShift! : 0,
    );
  }
  update(y: number, cue: number, footer: number, gallery: GalleryPort): boolean {
    const side = y <= Math.round(cue) ? -1 : y >= Math.round(footer) ? 1 : 0;
    let remeasure = false;
    if (
      side &&
      side !== this.side &&
      !(
        gallery.category === 'projects' &&
        this.travel.tuningShift === 0 &&
        this.travel.pathCount === Math.max(1, this.projectsCount)
      )
    ) {
      const end = this.travel.end(gallery.count);
      gallery.active(false);
      gallery.select('projects');
      this.travel.resetDefault(this.projectsCount, side, end);
      remeasure = side < 0;
    }
    this.side = side;
    return remeasure;
  }
}
