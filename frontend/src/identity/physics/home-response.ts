import type { Body } from './model';
import type { Vec } from './math';

export const ORIENTATION = Object.freeze({ band: 0.01, bias: 0.002 });

// The distributed springs have a free equilibrium at precisely half a turn.
// A small restoring term breaks that equilibrium only inside a 0.01 rad band.
// It is part of the home force, never a collision permission or random impulse.
export function orientationBias(angle: number, inertia: number, stiffness: number): number {
  const wrapped = Math.atan2(Math.sin(angle), Math.cos(angle));
  const weight = Math.max(0, 1 - (Math.PI - Math.abs(wrapped)) / ORIENTATION.band);
  if (weight === 0) return 0;
  return -Math.sign(wrapped) * inertia * stiffness * ORIENTATION.bias * weight ** 2;
}

const moments = new WeakMap<Body['geometry'], { x: number; y: number; variance: number }>();
function anchorMoments(geometry: Body['geometry']) {
  const cached = moments.get(geometry);
  if (cached) return cached;
  const n = geometry.anchors.length;
  const x = geometry.anchors.reduce((sum, a) => sum + a.x, 0) / n;
  const y = geometry.anchors.reduce((sum, a) => sum + a.y, 0) / n;
  const variance =
    geometry.anchors.reduce((sum, a) => sum + (a.x - x) ** 2 + (a.y - y) ** 2, 0) / n;
  const result = { x, y, variance };
  moments.set(geometry, result);
  return result;
}

// Algebraic aggregation of the existing distributed-anchor spring. Used only
// for force/progress verification, including the same narrow orientation bias.
export function homeResponse(body: Body, home: Vec, stiffness: number) {
  const mean = anchorMoments(body.geometry),
    c = Math.cos(body.angle),
    s = Math.sin(body.angle);
  const rx = mean.x * c - mean.y * s,
    ry = mean.x * s + mean.y * c;
  const dx = home.x - body.position.x + mean.x - rx,
    dy = home.y - body.position.y + mean.y - ry;
  return {
    acceleration: { x: stiffness * dx, y: stiffness * dy },
    torque:
      body.geometry.mass * stiffness * (rx * dy - ry * dx - s * mean.variance) +
      orientationBias(body.angle, body.geometry.inertia, stiffness),
  };
}
