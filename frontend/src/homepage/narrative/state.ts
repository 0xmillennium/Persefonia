import type { MentalWorldRegionId } from "./regions";

export type RegionLifecycle = "UNVISITED" | "ACTIVE" | "MEMORY";

export type NarrativeState = {
  readonly progress: number;
  readonly activeRegion: MentalWorldRegionId;
  readonly activeRegionProgress: number;
  readonly regions: Readonly<Record<MentalWorldRegionId, RegionLifecycle>>;
};
