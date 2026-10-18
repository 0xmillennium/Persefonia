export const FRAME_COUNT = 192;
export const clamp = (value: number, min = 0, max = 1) => Math.min(max, Math.max(min, value));
export function frameAt(progress: number): number {
  return Math.round(clamp(Number.isFinite(progress) ? progress : 0) * (FRAME_COUNT - 1));
}
