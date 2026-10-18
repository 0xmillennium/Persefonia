/** One material identity for GPU and compatibility rendering; CSS-pixel units. */
export const PIGMENT = {
  slowRadius: 52,
  fastRadius: 18,
  wakeLength: 30,
  force: 0.42,
  fade: 0.95,
  drag: 1.1,
  refraction: 100,
  maxRefraction: 96,
  opacity: 0.72,
};
export function absorbedPigment(source: number, pigment: number, absorption: number): number {
  return source * absorption * 0.35 + pigment * 0.65;
}
export function illuminatedPigment(
  source: number,
  pigment: number,
  absorption: number,
  light: number,
): number {
  const body = absorbedPigment(source, pigment, absorption),
    screen = 1 - (1 - source) * (1 - pigment);
  return body + (screen - body) * light;
}
export function pigmentBands(x: number, y: number, across: number): [number, number] {
  const bend = Math.sin(x * 0.019 + y * 0.027) * 2.4 + Math.sin(x * 0.041 - y * 0.017) * 0.9;
  const band = 0.5 + 0.5 * Math.sin(across * 0.13 + bend);
  return [band, Math.pow(0.5 + 0.5 * Math.sin(across * 0.31 + bend + band * 2), 8)];
}
export const pigmentSeed = `float bend=sin(p.x*.019+p.y*.027)*2.4+sin(p.x*.041-p.y*.017)*.9;
float band=.5+.5*sin(across*.13+bend);
float vein=pow(.5+.5*sin(across*.31+bend+band*2.),8.);`;
