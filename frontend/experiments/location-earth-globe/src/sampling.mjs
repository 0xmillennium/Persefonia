// Shared by the offline land classifier and runtime geometry. Regular angular
// latitude spacing; longitude count follows ring circumference.
export const LATITUDE_ROWS = 180;
export const EQUATOR_SAMPLES = 360;

export function surfaceSamples() {
  const samples = [];
  for (let row = 0; row < LATITUDE_ROWS; row++) {
    const latitude = -90 + (row + 0.5) * 180 / LATITUDE_ROWS;
    const count = Math.max(3, Math.round(EQUATOR_SAMPLES * Math.cos(latitude * Math.PI / 180)));
    for (let column = 0; column < count; column++) {
      samples.push({ latitude, longitude: -180 + (column + 0.5) * 360 / count });
    }
  }
  return samples;
}
