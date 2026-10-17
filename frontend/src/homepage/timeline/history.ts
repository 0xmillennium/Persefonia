export interface HomepagePosition {
  y: number;
  width: number;
  height: number;
}

/** Restore only positions measured for the same viewport; preserve native fallback. */
export function storedPosition(state: unknown, width: number, height: number): number | null {
  if (!state || typeof state !== 'object') return null;
  const value = state as Partial<HomepagePosition>;
  return typeof value.y === 'number' &&
    Number.isFinite(value.y) &&
    value.y >= 0 &&
    value.width === width &&
    value.height === height
    ? value.y
    : null;
}
