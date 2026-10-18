// Solid turquoise land cores, sparse smaller ocean dots and local hover light.
export const vertex = `#version 300 es
precision highp float;
layout(location=0) in vec3 position;
layout(location=1) in float land;
uniform mat3 orientation;
uniform vec2 viewport;
uniform float focal;
uniform float pointSize;
uniform vec3 hoverPoint;
uniform float hoverActive;
uniform float reveal;
uniform float entranceTime;
uniform vec3 entrancePoint;
out vec3 pigment;
out float luminance;
out float coreRadius;
out float halo;
void main() {
  vec3 world = orientation * position;
  float depth = 5.0 - world.z;
  gl_Position = vec4(world.xy * focal * 2.0 / viewport, 0.0, depth);
  float facing = 5.0 * world.z - 1.0;
  float front = step(0.001, facing);
  // The perspective horizon matches CPU hit testing, rather than showing the
  // far hemisphere through the oceans.
  if (front == 0.0) gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
  float foreshortening = 0.45 + 0.55 * clamp(facing / 4.0, 0.0, 1.0);
  float distance2 = dot(world - hoverPoint, world - hoverPoint);
  float glow = exp(-distance2 * 32.0) * hoverActive * front;
  halo = glow * land;
  coreRadius = 1.0 / (1.0 + halo * 0.8);
  gl_PointSize = pointSize * foreshortening * mix(0.5, 1.0, land)
    * (1.0 + halo * 0.45) / coreRadius;
  luminance = mix(0.13, 1.0, land) * front;
  pigment = mix(vec3(0.75, 0.82, 0.80), mix(vec3(0.314, 0.804, 0.722), vec3(0.70, 0.98, 0.91), halo), land);
  if (entranceTime < 1.15) {
    float row = (1.0 - world.y) * 0.5;
    float head = reveal * 1.16 - 0.08;
    float mask = smoothstep(row - 0.06, row + 0.06, head);
    float beam = max(0.0, 1.0 - abs(row - head) / 0.055) * sin(reveal * 3.14159265);
    luminance = (luminance + beam * front * 0.65) * mask;
    pigment = mix(pigment, vec3(0.65, 0.95, 0.85), beam * land * 0.45);
    gl_PointSize *= 1.0 + beam * 0.2;
  }
  if (entranceTime >= 1.8 && entranceTime < 2.9) {
    float progress = (entranceTime - 1.8) / 1.1;
    float distance = length(world - entrancePoint);
    float wave = pow(max(0.0, 1.0 - abs(distance - progress * 1.1) / 0.08), 2.0);
    wave *= sin(progress * 3.14159265) * front;
    luminance += wave * 0.7;
    pigment = mix(pigment, vec3(0.65, 0.95, 0.85), wave * land * 0.55);
  }
}`;

export const fragment = `#version 300 es
precision highp float;
in vec3 pigment;
in float luminance;
in float coreRadius;
in float halo;
out vec4 color;
void main() {
  vec2 p = (gl_PointCoord - 0.5) * 2.0;
  float radius = length(p);
  float edge = max(fwidth(radius) * 0.5, 0.01);
  float core = 1.0 - smoothstep(coreRadius - edge, coreRadius, radius);
  float coverage = core + (1.0 - core) * exp(-6.0 * radius * radius) * halo * 0.18;
  if (coverage == 0.0) discard;
  color = vec4(pigment * luminance * coverage, luminance * coverage);
}`;
