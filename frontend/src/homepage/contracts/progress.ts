export const clamp = (value: number) => Math.max(0, Math.min(1, value));
export const windowProgress = (value: number, start: number, end: number) =>
  clamp((value - start) / (end - start));
export function regionProgress(y: number, top: number, height: number) {
  return clamp((y - top) / Math.max(1, height));
}
