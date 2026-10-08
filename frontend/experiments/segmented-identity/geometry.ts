import source from "./geometry.json";
import { polygonProperties, decompose, sub, cross, dot } from "./math";
import type { FragmentGeometry } from "./core";

// Area centroids and exact uniform-density polygon inertia are derived here,
// never copied from the design tool's rounded diagnostic values.
export const canonicalGeometry: readonly FragmentGeometry[] = source.fragments.map(fragment => {
  const world = fragment.vertices.map(([x, y]) => ({ x: x!, y: y! }));
  const properties = polygonProperties(world);
  const vertices = world.map(p => sub(p, properties.center));
  const parts = decompose(vertices);
  const mass = properties.area / 3500;
  const anchors = fragment.anchorCandidates.map(a => vertices[a.contourVertexIndex]!);
  if (anchors.length < 3 || anchors.length > 5) throw new Error(`Invalid anchors: ${fragment.id}`);
  if (anchors.some(p => !p)) throw new Error(`Invalid contour anchor: ${fragment.id}`);
  if (!vertices.every(p => Number.isFinite(p.x) && Number.isFinite(p.y))) throw new Error("Invalid geometry");
  return Object.freeze({ id: fragment.id, vertices: Object.freeze(vertices.map(p => Object.freeze(p))),
    parts: Object.freeze(parts.map(p => Object.freeze(p))), anchors: Object.freeze(anchors),
    home: Object.freeze(properties.center), mass, inertia: mass * properties.inertiaPerMass });
});

export function validateGeometry(): void {
  if (canonicalGeometry.length !== 9 || source.fragments.filter(f => f.shape === "left").length !== 5
    || source.fragments.filter(f => f.shape === "right").length !== 4) throw new Error("Expected 5 + 4 fragments");
  for (const g of canonicalGeometry) {
    const reconstructed = g.parts.reduce((sum, part) => sum + polygonProperties(part).area, 0);
    if (Math.abs(reconstructed - polygonProperties(g.vertices).area) > 1e-5) throw new Error(`Invalid decomposition: ${g.id}`);
    // Meaningful non-collinear anchors provide both translation and lever arms.
    const a = g.anchors[0]!, b = g.anchors[1]!, c = g.anchors[2]!;
    if (Math.abs(cross(sub(b, a), sub(c, a))) < 1e-4 || g.anchors.reduce((sum, p) => sum + dot(p, p), 0) < 1)
      throw new Error(`Degenerate anchors: ${g.id}`);
  }
}
validateGeometry();
