// Preserve the approved 60 Hz response at any display refresh rate.
const SCALE_RESPONSE = -60 * Math.log(0.8);
export function pointerScale(current: number, target: number, seconds: number) {
  return current + (target - current) * (1 - Math.exp(-SCALE_RESPONSE * seconds));
}
