export const FILMS = ['robotic-arm', 'tree'] as const;
export type Film = (typeof FILMS)[number];
export const FRAME_COUNT = 192;
export const clamp = (value: number, min = 0, max = 1) => Math.min(max, Math.max(min, value));
export function frameAt(progress: number): number {
  return Math.round(clamp(Number.isFinite(progress) ? progress : 0) * (FRAME_COUNT - 1));
}
export type FrameKey = `${Film}:${number}`;
