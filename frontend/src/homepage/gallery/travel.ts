import { galleryTravelPosition } from '../contracts/gallery';

/** Card travel may be rebased by tuning; the authored curtain geometry stays fixed. */
export class GalleryTravel {
  private top = 0;
  private height = 1;
  private entry = 0;

  constructor(
    public pathCount: number,
    public tuningShift = 0,
  ) {}

  centerAt(y: number): void {
    this.tuningShift = (y - this.first()) / this.step() - 3;
  }

  resetEntry(count: number): void {
    this.tuningShift = 0;
    this.pathCount = Math.max(1, count);
  }

  resetDefault(count: number, side: -1 | 1, previousEnd: number): void {
    this.pathCount = Math.max(1, count);
    this.tuningShift =
      side > 0 ? Math.max(0, (previousEnd - this.position(0.82)) / this.step()) : 0;
  }

  measure(top: number, height: number, entry: number): void {
    this.top = top;
    this.height = height;
    this.entry = entry;
  }

  position(progress: number): number {
    return this.top + this.entry + progress * (this.height - this.entry);
  }

  first(): number {
    return this.position(0.12 + 0.7 / (this.pathCount + 7));
  }
  step(): number {
    return (this.position(0.82) - this.position(0.12)) / (this.pathCount + 7);
  }
  travelFirst(): number {
    return this.first() + this.tuningShift * this.step();
  }
  end(count: number): number {
    return this.travelFirst() + (count + 6) * this.step();
  }
  travelPosition(progress: number, count: number): number {
    return galleryTravelPosition(progress, this.travelFirst(), this.step(), count);
  }
}
