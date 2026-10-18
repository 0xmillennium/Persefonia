import { polygonProperties, decompose, sub, cross, dot } from './math';
import type { Vec } from './math';
import type { FragmentGeometry } from './core';

// Render and collision contours share these vertices. Mass, centroid and
// inertia belong to the actual polygon, rather than rounded design diagnostics.
export function fragmentGeometry(
  id: string,
  world: readonly Vec[],
  anchorIndices: readonly number[],
): FragmentGeometry {
  if (
    !/^[a-zA-Z0-9_-]+$/.test(id) ||
    world.length < 3 ||
    !world.every((p) => Number.isFinite(p.x) && Number.isFinite(p.y))
  )
    throw new Error(`Invalid geometry: ${id}`);
  // SAT and ear clipping require a simple contour. Area agreement alone does
  // not detect every crossing or a doubled-back edge. Validate once at load.
  const onSegment = (p: Vec, a: Vec, b: Vec) =>
    Math.abs(cross(sub(b, a), sub(p, a))) <= 1e-10 && dot(sub(p, a), sub(p, b)) <= 1e-10;
  for (let i = 0; i < world.length; i++) {
    const a = world[i]!,
      b = world[(i + 1) % world.length]!,
      c = world[(i + 2) % world.length]!;
    if (
      dot(sub(b, a), sub(b, a)) <= 1e-16 ||
      (Math.abs(cross(sub(b, a), sub(c, b))) <= 1e-10 && dot(sub(b, a), sub(c, b)) < 0)
    )
      throw new Error(`Degenerate edge: ${id}`);
    for (let j = i + 2; j < world.length; j++) {
      if (i === 0 && j === world.length - 1) continue;
      const d = world[j]!,
        e = world[(j + 1) % world.length]!;
      const abD = cross(sub(b, a), sub(d, a)),
        abE = cross(sub(b, a), sub(e, a));
      const deA = cross(sub(e, d), sub(a, d)),
        deB = cross(sub(e, d), sub(b, d));
      if (
        (abD * abE < 0 && deA * deB < 0) ||
        onSegment(d, a, b) ||
        onSegment(e, a, b) ||
        onSegment(a, d, e) ||
        onSegment(b, d, e)
      )
        throw new Error(`Non-simple polygon: ${id}`);
    }
  }
  const properties = polygonProperties(world);
  if (
    ![properties.area, properties.inertiaPerMass, properties.center.x, properties.center.y].every(
      Number.isFinite,
    ) ||
    properties.area <= 0 ||
    properties.inertiaPerMass <= 0
  )
    throw new Error(`Invalid mass properties: ${id}`);
  const vertices = world.map((p) => Object.freeze(sub(p, properties.center)));
  if (
    anchorIndices.length < 3 ||
    anchorIndices.length > 5 ||
    new Set(anchorIndices).size !== anchorIndices.length ||
    anchorIndices.some((i) => !Number.isInteger(i) || i < 0 || i >= world.length)
  )
    throw new Error(`Invalid anchors: ${id}`);
  const anchors = anchorIndices.map((i) => vertices[i]!);
  if (
    !anchors.some((p) => Math.abs(cross(sub(anchors[1]!, anchors[0]!), sub(p, anchors[0]!))) > 1e-8)
  )
    throw new Error(`Degenerate anchors: ${id}`);
  const twiceArea = world.reduce((sum, p, i) => sum + cross(p, world[(i + 1) % world.length]!), 0);
  if (twiceArea < 0) vertices.reverse();
  const mass = properties.area / 3500;
  return Object.freeze({
    id,
    vertices: Object.freeze(vertices),
    parts: Object.freeze(decompose(vertices).map((p) => Object.freeze(p))),
    anchors: Object.freeze(anchors),
    home: Object.freeze(properties.center),
    mass,
    inertia: mass * properties.inertiaPerMass,
  });
}
