import { normalizeNarrativeProgress } from "./progress";
import { PROVISIONAL_REGION_INTERVALS, type MentalWorldRegionId } from "./regions";
import type { NarrativeState, RegionLifecycle } from "./state";

export function deriveNarrativeState(progress: number): NarrativeState {
  const normalizedProgress = normalizeNarrativeProgress(progress);
  const activeIndex = PROVISIONAL_REGION_INTERVALS.findIndex(
    ({ end }, index) => normalizedProgress < end ||
      (index === PROVISIONAL_REGION_INTERVALS.length - 1 && normalizedProgress === end)
  );
  const activeInterval = PROVISIONAL_REGION_INTERVALS[activeIndex];

  if (!activeInterval) {
    throw new Error("No mental-world region contains the normalized progress");
  }

  const regions = Object.fromEntries(PROVISIONAL_REGION_INTERVALS.map(({ id }, index) => [
    id,
    index < activeIndex ? "MEMORY" : index === activeIndex ? "ACTIVE" : "UNVISITED"
  ])) as Record<MentalWorldRegionId, RegionLifecycle>;

  return {
    progress: normalizedProgress,
    activeRegion: activeInterval.id,
    activeRegionProgress: Math.min(1, Math.max(0,
      (normalizedProgress - activeInterval.start) / (activeInterval.end - activeInterval.start)
    )),
    regions
  };
}
