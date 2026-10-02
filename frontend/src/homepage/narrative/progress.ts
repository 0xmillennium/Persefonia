export function normalizeNarrativeProgress(progress: number): number {
  if (!Number.isFinite(progress)) {
    throw new RangeError("Narrative progress must be finite");
  }

  return Math.min(1, Math.max(0, progress));
}
