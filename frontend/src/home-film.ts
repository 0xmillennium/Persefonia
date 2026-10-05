/** The film completes while its stage is pinned and fully observable. */
export function filmProgress(top: number, height: number, _scrollY: number, stageHeight: number): number {
  const travel = height - stageHeight;
  if (travel <= 0 || stageHeight <= 0) return 0;
  return Math.min(1, Math.max(0, -top / travel));
}
