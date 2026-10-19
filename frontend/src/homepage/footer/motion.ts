import { windowProgress } from '../contracts/progress';

/** Footer rows emerge after the gallery has cleared, followed by the globe. */
export function footerRowProgress(phase: number, index: number, detail = false) {
  const start = (detail ? 0.45 : 0) + index * (detail ? 0.05 : 0.08);
  return windowProgress(phase, start, start + (detail ? 0.35 : 0.45));
}
