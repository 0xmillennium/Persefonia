// Shared by offline classification and runtime geometry. Independent equal-area
// candidate sets give oceans breathing room without sacrificing coastlines.
export const LAND_CANDIDATES = 32_000;
export const WATER_CANDIDATES = 9_000;
const GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

export function surfaceSamples() {
  const samples = [];
  for (const [surface, count] of [
    ['land', LAND_CANDIDATES],
    ['water', WATER_CANDIDATES],
  ]) {
    for (let i = 0; i < count; i++) {
      samples.push({
        latitude: (Math.asin(1 - (2 * (i + 0.5)) / count) * 180) / Math.PI,
        longitude: (((i * GOLDEN_ANGLE) % (2 * Math.PI)) * 180) / Math.PI - 180,
        surface,
      });
    }
  }
  return samples;
}
