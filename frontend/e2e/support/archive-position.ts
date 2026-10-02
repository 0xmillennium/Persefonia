export function renderedArchivePositionMatches(observed: number, target: number): boolean {
  // The target uses achieved scrollY, so only floating-point geometry noise is allowed here.
  return Math.abs(observed - target) <= .000001;
}
