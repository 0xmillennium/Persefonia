import source from "./geometry.json";
import { polygonProperties, sub, cross, dot, closestOnSegment, length } from "./math";
import type { Vec } from "./math";
import type { FragmentGeometry } from "./core";
import { fragmentGeometry } from "./geometry-loader";

// Subdivide shared cubics once, before mirroring/reusing them. The convex hull
// property bounds each curve's distance from its chord by curveTolerance.
function flatten(curve: readonly Vec[], result: Vec[]): void {
  const [a, b, c, d] = curve as readonly [Vec, Vec, Vec, Vec];
  const error = Math.max(length(sub(b, closestOnSegment(b, a, d))), length(sub(c, closestOnSegment(c, a, d))));
  if (error <= source.curveTolerance) { result.push(d); return; }
  const mid = (p: Vec, q: Vec): Vec => ({ x: (p.x + q.x) / 2, y: (p.y + q.y) / 2 });
  const ab = mid(a, b), bc = mid(b, c), cd = mid(c, d), abc = mid(ab, bc), bcd = mid(bc, cd), center = mid(abc, bcd);
  flatten([a, ab, abc, center], result); flatten([center, bcd, cd, d], result);
}
const curves = source.quarterCurves.map(c => c.map(([x, y]) => ({ x: x!, y: y! })));
if (!Number.isFinite(source.curveTolerance) || source.curveTolerance <= 0
  || curves.some(c => c.length !== 4 || c.some(p => !Number.isFinite(p.x) || !Number.isFinite(p.y))))
  throw new Error("Invalid cubic curve or sampling tolerance");
const cap: Vec[] = [curves[0]![0]!]; flatten(curves[0]!, cap);
const flank: Vec[] = [curves[1]![0]!]; curves.slice(1).forEach(c => flatten(c, flank));
const quarter = [...cap, ...flank.slice(1)];
const mirror = (p: Vec, x: number, y: number): Vec => ({ x: p.x * x, y: p.y * y });

// Each contour has three designed corners. Sampled curve nodes are smooth
// boundary approximations, not extra designed corners. Zero translations close
// these exact contours into the parent rectangle, without scaling or morphing.
export const canonicalContours = source.fragments.map(fragment => {
  let points: Vec[], corners: number[];
  if (fragment.family === "corner") {
    points = [quarter[0]!, { x: source.parent.width / 2, y: -source.parent.height / 2 }, ...[...quarter].reverse()];
    // The reversed quarter already ends at the initial point.
    points.pop(); corners = [0, 1, 2];
  } else if (fragment.family === "cap") {
    points = [{ x: 0, y: 0 }, ...[...cap].reverse().map(p => mirror(p, -1, 1)), ...cap.slice(1)];
    corners = [0, 1, points.length - 1];
  } else if (fragment.family === "flank") {
    points = [{ x: 0, y: 0 }, ...flank, ...flank.slice(0, -1).reverse().map(p => mirror(p, 1, -1))];
    corners = [0, 1, points.length - 1];
  } else throw new Error(`Unknown family: ${fragment.family}`);
  points = points.map(p => mirror(p, fragment.mirror[0]!, fragment.mirror[1]!));
  // SAT and ear clipping require positive winding.
  if (points.reduce((sum, p, i) => sum + cross(p, points[(i + 1) % points.length]!), 0) < 0) {
    corners = corners.map(i => points.length - 1 - i); points.reverse();
  }
  return { id: fragment.id, family: fragment.family, points, corners,
    translation: { x: fragment.translation[0]!, y: fragment.translation[1]! } };
});
export const canonicalGeometry: readonly FragmentGeometry[] = canonicalContours.map(c => fragmentGeometry(c.id,
  c.points.map(p => ({ x: p.x + c.translation.x, y: p.y + c.translation.y })), c.corners));

export function validateGeometry(): void {
  if (canonicalGeometry.length !== 8 || source.fragments.filter(f => f.family === "corner").length !== 4
    || source.fragments.filter(f => f.family === "cap").length !== 2
    || source.fragments.filter(f => f.family === "flank").length !== 2) throw new Error("Expected 4 corners + 2 caps + 2 flanks");
  for (const g of canonicalGeometry) {
    const reconstructed = g.parts.reduce((sum, part) => sum + polygonProperties(part).area, 0);
    if (Math.abs(reconstructed - polygonProperties(g.vertices).area) > 1e-5) throw new Error(`Invalid decomposition: ${g.id}`);
    const a = g.anchors[0]!, b = g.anchors[1]!, c = g.anchors[2]!;
    if (Math.abs(cross(sub(b, a), sub(c, a))) < 1e-4 || g.anchors.reduce((sum, p) => sum + dot(p, p), 0) < 1)
      throw new Error(`Degenerate anchors: ${g.id}`);
  }
}
validateGeometry();
