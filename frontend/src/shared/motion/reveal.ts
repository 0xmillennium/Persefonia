/** Shared gate motion for text rows and the gallery instrument. */
export function slitPosition(entry: number, exit: number): number {
  const easeOut = 1 - Math.pow(1 - entry, 5);
  return 100 * (1 - easeOut) - 100 * Math.pow(exit, 4);
}
