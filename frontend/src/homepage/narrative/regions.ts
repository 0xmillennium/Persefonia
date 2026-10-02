export type MentalWorldRegionId =
  | "threshold"
  | "work"
  | "writing"
  | "research"
  | "architecture"
  | "return"
  | "person"
  | "final";

export type RegionInterval = {
  readonly id: MentalWorldRegionId;
  readonly start: number;
  readonly end: number;
};

// These intervals are provisional semantic scaffolding, not final narrative pacing.
export const PROVISIONAL_REGION_INTERVALS = [
  { id: "threshold", start: 0, end: 0.125 },
  { id: "work", start: 0.125, end: 0.25 },
  { id: "writing", start: 0.25, end: 0.375 },
  { id: "research", start: 0.375, end: 0.5 },
  { id: "architecture", start: 0.5, end: 0.625 },
  { id: "return", start: 0.625, end: 0.75 },
  { id: "person", start: 0.75, end: 0.875 },
  { id: "final", start: 0.875, end: 1 }
] as const satisfies readonly RegionInterval[];
