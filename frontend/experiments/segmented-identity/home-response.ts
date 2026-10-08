import type { Body } from "./core";
import type { Vec } from "./math";

export const ORIENTATION = Object.freeze({ band: .01, bias: .002 });

// The distributed springs have a free equilibrium at precisely half a turn.
// A small restoring term breaks that equilibrium only inside a 0.01 rad band.
// It is part of the home force, never a collision permission or random impulse.
export function orientationBias(angle: number, inertia: number, stiffness: number): number {
  const wrapped = Math.atan2(Math.sin(angle), Math.cos(angle));
  const weight = Math.max(0, 1 - (Math.PI - Math.abs(wrapped)) / ORIENTATION.band);
  if (weight === 0) return 0;
  return -Math.sign(wrapped) * inertia * stiffness * ORIENTATION.bias * weight ** 2;
}

const moments = new WeakMap<Body["geometry"], { x: number; y: number; variance: number; radius: number }>();
function anchorMoments(geometry: Body["geometry"]) {
  const cached = moments.get(geometry); if (cached) return cached;
  const n = geometry.anchors.length;
  const x = geometry.anchors.reduce((sum, a) => sum + a.x, 0) / n;
  const y = geometry.anchors.reduce((sum, a) => sum + a.y, 0) / n;
  const variance = geometry.anchors.reduce((sum, a) => sum + (a.x - x) ** 2 + (a.y - y) ** 2, 0) / n;
  const result = { x, y, variance, radius: Math.max(...geometry.vertices.map(p => Math.hypot(p.x, p.y))) };
  moments.set(geometry, result); return result;
}

// Algebraic aggregation of the existing distributed-anchor spring. Used only
// for an inexpensive ranking estimate, including the same narrow orientation bias.
export function homeResponse(body: Body, home: Vec, stiffness: number) {
  const mean = anchorMoments(body.geometry), c = Math.cos(body.angle), s = Math.sin(body.angle);
  const rx = mean.x * c - mean.y * s, ry = mean.x * s + mean.y * c;
  const dx = home.x - body.position.x + mean.x - rx, dy = home.y - body.position.y + mean.y - ry;
  return { acceleration: { x: stiffness * dx, y: stiffness * dy },
    torque: body.geometry.mass * stiffness * (rx * dy - ry * dx - s * mean.variance) + orientationBias(body.angle, body.geometry.inertia, stiffness) };
}

export function projectHomeOnly(bodies: readonly Body[], homes: readonly Vec[], settings: {
  steps: number; dt: number; stiffness: number; damping: number; angularDamping: number;
  width: number; height: number; navigator: { active: boolean; position: Vec; radius: number };
}): Body[] | null {
  const copies = bodies.map(b => ({ ...b, position: { ...b.position }, velocity: { ...b.velocity }, contacts: [...b.contacts] }));
  const linear = Math.exp(-settings.damping * settings.dt), angular = Math.exp(-settings.angularDamping * settings.dt);
  for (let step = 0; step < settings.steps; step++) for (let i = 0; i < copies.length; i++) {
    const b = copies[i]!, response = homeResponse(b, homes[i]!, settings.stiffness);
    b.velocity.x = (b.velocity.x + response.acceleration.x * settings.dt) * linear;
    b.velocity.y = (b.velocity.y + response.acceleration.y * settings.dt) * linear;
    b.angularVelocity = (b.angularVelocity + response.torque / b.geometry.inertia * settings.dt) * angular;
    b.position.x += b.velocity.x * settings.dt; b.position.y += b.velocity.y * settings.dt;
    b.angle += b.angularVelocity * settings.dt;
    // This estimate ignores fragment contacts. If its conservative swept disks
    // could encounter a wall or navigator, decline this ranking estimate and
    // let the exact short-horizon comparison decide instead.
    const radius = anchorMoments(b.geometry).radius;
    if (b.position.x - radius < 0 || b.position.x + radius > settings.width
      || b.position.y - radius < 0 || b.position.y + radius > settings.height
      || settings.navigator.active && Math.hypot(b.position.x - settings.navigator.position.x,
        b.position.y - settings.navigator.position.y) < radius + settings.navigator.radius) return null;
  }
  return copies;
}
