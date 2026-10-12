// Turquoise pigment/highlight family from Experiment 02. Material is independent.
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
out vec3 pigment;
out float luminance;
void main() {
  vec3 world = orientation * position;
  float depth = 5.0 - world.z;
  gl_Position = vec4(world.xy * focal * 2.0 / viewport, 0.0, depth);
  float facing = 5.0 * world.z - 1.0;
  gl_PointSize = pointSize * 5.0 / depth * mix(0.65, 1.0, smoothstep(0.0, 1.5, facing));
  float front = smoothstep(-0.03, 0.22, facing);
  float response = pow(max(dot(world, normalize(vec3(-0.3, 0.5, 1.0))), 0.0), 0.8);
  float distance2 = dot(world - hoverPoint, world - hoverPoint);
  float glow = exp(-distance2 * 32.0) * hoverActive * front;
  luminance = mix(0.008, mix(0.012, 0.42 + response * 0.32, land), front);
  luminance += glow * mix(0.085, 0.5, land);
  pigment = mix(vec3(0.08, 0.85, 0.69), vec3(0.22, 0.76, 0.65), glow * 0.45);
}`;

export const fragment = `#version 300 es
precision highp float;
in vec3 pigment;
in float luminance;
out vec4 color;
void main() {
  vec2 p = (gl_PointCoord - 0.5) * 2.0;
  float coverage = 1.0 - smoothstep(0.45, 1.0, length(p));
  color = vec4(pigment * luminance * coverage, luminance * coverage);
}`;
