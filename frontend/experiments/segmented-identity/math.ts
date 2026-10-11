export type Vec = Readonly<{ x: number; y: number }>;
export const add = (a: Vec, b: Vec): Vec => ({ x: a.x + b.x, y: a.y + b.y });
export const sub = (a: Vec, b: Vec): Vec => ({ x: a.x - b.x, y: a.y - b.y });
export const mul = (a: Vec, k: number): Vec => ({ x: a.x * k, y: a.y * k });
export const dot = (a: Vec, b: Vec): number => a.x * b.x + a.y * b.y;
export const cross = (a: Vec, b: Vec): number => a.x * b.y - a.y * b.x;
export const length = (a: Vec): number => Math.hypot(a.x, a.y);
export const unit = (a: Vec): Vec => mul(a, 1 / (length(a) || 1));
export const rotate = (a: Vec, angle: number): Vec => ({
  x: a.x * Math.cos(angle) - a.y * Math.sin(angle),
  y: a.x * Math.sin(angle) + a.y * Math.cos(angle)
});
export function transform(points: readonly Vec[], position: Vec, angle: number): Vec[] {
  const c = Math.cos(angle), s = Math.sin(angle);
  return points.map(p => ({ x: position.x + (p.x * c - p.y * s), y: position.y + (p.x * s + p.y * c) }));
}

export function polygonProperties(points: readonly Vec[]) {
  let twiceArea = 0, cx = 0, cy = 0, inertia = 0;
  points.forEach((a, i) => {
    const b = points[(i + 1) % points.length]!;
    const c = cross(a, b);
    twiceArea += c;
    cx += (a.x + b.x) * c;
    cy += (a.y + b.y) * c;
    inertia += c * (dot(a, a) + dot(a, b) + dot(b, b));
  });
  if (Math.abs(twiceArea) < 1e-8) throw new Error("Degenerate polygon");
  const center = { x: cx / (3 * twiceArea), y: cy / (3 * twiceArea) };
  return { area: Math.abs(twiceArea / 2), center,
    inertiaPerMass: inertia / (6 * twiceArea) - dot(center, center) };
}

export function closestOnSegment(p: Vec, a: Vec, b: Vec): Vec {
  const ab = sub(b, a);
  return add(a, mul(ab, Math.max(0, Math.min(1, dot(sub(p, a), ab) / (dot(ab, ab) || 1)))));
}

export function inside(p: Vec, polygon: readonly Vec[]): boolean {
  let result = false;
  polygon.forEach((a, i) => {
    const b = polygon[(i + 1) % polygon.length]!;
    if ((a.y > p.y) !== (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x)
      result = !result;
  });
  return result;
}

export type Contact = Readonly<{ normal: Vec; depth: number; point: Vec }>;

export const isConvex = (points: readonly Vec[]): boolean => points.every((p, i) =>
  cross(sub(points[(i + 1) % points.length]!, p),
    sub(points[(i + 2) % points.length]!, points[(i + 1) % points.length]!)) >= -1e-7);

// Ear clipping plus convex merging preserves the exact simple polygon, including
// its concave inner contour. Neither the hull nor a circle substitutes for it.
export function decompose(polygon: readonly Vec[]): Vec[][] {
  if (isConvex(polygon)) return [[...polygon]];
  const indices = polygon.map((_, i) => i), parts: Vec[][] = [];
  const inTriangle = (p: Vec, a: Vec, b: Vec, c: Vec) =>
    cross(sub(b, a), sub(p, a)) >= -1e-8 && cross(sub(c, b), sub(p, b)) >= -1e-8
      && cross(sub(a, c), sub(p, c)) >= -1e-8;
  while (indices.length > 3) {
    let found = false;
    for (let i = 0; i < indices.length; i++) {
      const ai = indices[(i + indices.length - 1) % indices.length]!, bi = indices[i]!, ci = indices[(i + 1) % indices.length]!;
      const a = polygon[ai]!, b = polygon[bi]!, c = polygon[ci]!;
      if (cross(sub(b, a), sub(c, b)) <= 1e-8) continue;
      if (indices.some(j => j !== ai && j !== bi && j !== ci && inTriangle(polygon[j]!, a, b, c))) continue;
      parts.push([a, b, c]); indices.splice(i, 1); found = true; break;
    }
    if (!found) throw new Error("Non-simple or degenerate polygon cannot be decomposed");
  }
  parts.push(indices.map(i => polygon[i]!));
  let merged = true;
  while (merged) {
    merged = false;
    outer: for (let i = 0; i < parts.length; i++) for (let j = i + 1; j < parts.length; j++) {
      const a = parts[i]!, b = parts[j]!;
      for (let k = 0; k < a.length; k++) for (let l = 0; l < b.length; l++) {
        if (a[k] !== b[(l + 1) % b.length] || a[(k + 1) % a.length] !== b[l]) continue;
        const candidate = [...Array.from({ length: a.length }, (_, n) => a[(k + 1 + n) % a.length]!),
          ...Array.from({ length: b.length - 2 }, (_, n) => b[(l + 2 + n) % b.length]!)];
        if (!isConvex(candidate)) continue;
        parts[i] = candidate; parts.splice(j, 1); merged = true; break outer;
      }
    }
  }
  return parts;
}

type Bounds = Readonly<{ minx: number; maxx: number; miny: number; maxy: number }>;
const boundsCache = new WeakMap<readonly Vec[], Bounds>();
export function polygonBounds(p: readonly Vec[]): Bounds {
  const cached = boundsCache.get(p);
  if (cached) return cached;
  let minx = Infinity, maxx = -Infinity, miny = Infinity, maxy = -Infinity;
  for (const v of p) {
    minx = Math.min(minx, v.x); maxx = Math.max(maxx, v.x);
    miny = Math.min(miny, v.y); maxy = Math.max(maxy, v.y);
  }
  const result = { minx, maxx, miny, maxy };
  boundsCache.set(p, result); return result;
}
function projectionRange(points: readonly Vec[], axis: Vec): { min: number; max: number } {
  let min = Infinity, max = -Infinity;
  for (const p of points) { const value = dot(p, axis); min = Math.min(min, value); max = Math.max(max, value); }
  return { min, max };
}
type AxisRange = Readonly<{ axis: Vec; min: number; max: number }>;
const axesCache = new WeakMap<readonly Vec[], (AxisRange | undefined)[]>();
function cachedAxes(polygon: readonly Vec[]): (AxisRange | undefined)[] {
  let axes = axesCache.get(polygon);
  if (!axes) { axes = []; axesCache.set(polygon, axes); }
  return axes;
}
function axisRange(polygon: readonly Vec[], axes: (AxisRange | undefined)[], index: number): AxisRange {
  const cached = axes[index];
  if (cached) return cached;
  // Like bounds, these values belong to immutable world-contour arrays. A new
  // pose creates new arrays, so no invalidation or rounded pose keys are used.
  // Reuse a curved part's self-projections across all opposing convex parts.
  // Populate lazily so SAT can still stop at its first separating axis.
  const edge = sub(polygon[(index + 1) % polygon.length]!, polygon[index]!);
  const axis = unit({ x: -edge.y, y: edge.x });
  const result = { axis, ...projectionRange(polygon, axis) };
  axes[index] = result; return result;
}
export function boundsGap(a: readonly Vec[], b: readonly Vec[]): number {
  const ab = polygonBounds(a), bb = polygonBounds(b);
  return Math.hypot(Math.max(0, ab.minx - bb.maxx, bb.minx - ab.maxx),
    Math.max(0, ab.miny - bb.maxy, bb.miny - ab.maxy));
}

const boundsOverlap = (a: Bounds, b: Bounds): boolean =>
  a.maxx >= b.minx && b.maxx >= a.minx && a.maxy >= b.miny && b.maxy >= a.miny;
const partBoundsCache = new WeakMap<readonly (readonly Vec[])[], readonly Bounds[]>();
function partBounds(parts: readonly (readonly Vec[])[]): readonly Bounds[] {
  let bounds = partBoundsCache.get(parts);
  if (!bounds) { bounds = parts.map(polygonBounds); partBoundsCache.set(parts, bounds); }
  return bounds;
}

export function polygonContact(a: readonly Vec[], b: readonly Vec[],
  aparts: readonly (readonly Vec[])[], bparts: readonly (readonly Vec[])[]): Contact | null {
  if (!boundsOverlap(polygonBounds(a), polygonBounds(b))) return null;
  const ab = partBounds(aparts), bb = partBounds(bparts);
  let contact: Contact | null = null;
  for (let i = 0; i < aparts.length; i++) for (let j = 0; j < bparts.length; j++) {
    if (!boundsOverlap(ab[i]!, bb[j]!)) continue;
    const c = convexContactNarrow(aparts[i]!, bparts[j]!);
    if (c && (!contact || c.depth > contact.depth)) contact = c;
  }
  return contact;
}

// Observation manifold only: keep the impulse/projection solver's deepest
// contact unchanged, while deadlock detection can inspect every touching part.
export function polygonContacts(a: readonly Vec[], b: readonly Vec[],
  aparts: readonly (readonly Vec[])[], bparts: readonly (readonly Vec[])[]): Contact[] {
  if (!boundsOverlap(polygonBounds(a), polygonBounds(b))) return [];
  const ab = partBounds(aparts), bb = partBounds(bparts);
  const contacts: Contact[] = [];
  for (let i = 0; i < aparts.length; i++) for (let j = 0; j < bparts.length; j++) {
    if (!boundsOverlap(ab[i]!, bb[j]!)) continue;
    const contact = convexContactNarrow(aparts[i]!, bparts[j]!);
    if (contact) contacts.push(contact);
  }
  return contacts;
}

// A deepest convex-part MTV need not separate two concave bodies: another
// subpart can still intersect, or the next MTV can undo the first. For deep
// unresolved overlap, find a whole-compound translation along six directions.
// Each SAT pair defines an interval of translations that would intersect. The
// connected union containing zero ends at a verified separating displacement.
// This is the shortest of these candidates, not a global minimum translation.
export function compoundSeparation(aparts: readonly (readonly Vec[])[], bparts: readonly (readonly Vec[])[], preferred: Vec,
  allowed: (translation: Vec) => boolean = () => true): Vec {
  // A part's own support ranges are shared across all opposing parts, rather
  // than recomputed (and allocated) for every pair and candidate direction.
  const prepare = (parts: readonly (readonly Vec[])[]) => parts.map(polygon => {
    const axes = cachedAxes(polygon);
    return { polygon, axes: Array.from({ length: polygon.length }, (_, i) => axisRange(polygon, axes, i)) };
  });
  const preparedA = prepare(aparts), preparedB = prepare(bparts);
  const constraints = preparedA.flatMap(a => preparedB.map(b => {
    const first = a.axes.map(c => {
      const other = projectionRange(b.polygon, c.axis);
      return { axis: c.axis, min: c.min - other.max, max: c.max - other.min };
    });
    const second = b.axes.map(c => {
      const other = projectionRange(a.polygon, c.axis);
      return { axis: c.axis, min: other.min - c.max, max: other.max - c.min };
    });
    return [...first, ...second];
  }));
  let best: Vec = { x: Infinity, y: Infinity }, bestDistance = Infinity;
  let fallback: Vec = best, fallbackDistance = Infinity;
  const normal = unit(preferred);
  for (const direction of [normal, mul(normal, -1), { x: 1, y: 0 }, { x: -1, y: 0 }, { x: 0, y: 1 }, { x: 0, y: -1 }]) {
    const intervals: { enter: number; exit: number }[] = [];
    for (const pair of constraints) {
      let enter = -Infinity, exit = Infinity;
      for (const c of pair) {
        const speed = dot(direction, c.axis);
        if (Math.abs(speed) < 1e-12) {
          if (c.min > 0 || c.max < 0) { enter = Infinity; break; }
        } else {
          const a = c.min / speed, b = c.max / speed;
          enter = Math.max(enter, Math.min(a, b)); exit = Math.min(exit, Math.max(a, b));
        }
        // Intersecting more constraints cannot restore an empty interval.
        if (enter > exit) break;
      }
      if (enter !== Infinity && enter <= exit && exit >= 0) intervals.push({ enter, exit });
    }
    intervals.sort((a, b) => a.enter - b.enter);
    let distance = 0;
    for (const interval of intervals) {
      if (interval.enter > distance + 1e-9) break;
      // Include clearance while merging: adding it afterwards could land in
      // the next subpart interval when the intervening gap is smaller than it.
      distance = Math.max(distance, interval.exit + .00001);
    }
    const translation = mul(direction, distance);
    if (distance < fallbackDistance) { fallbackDistance = distance; fallback = translation; }
    if (distance < bestDistance && allowed(translation)) { bestDistance = distance; best = translation; }
  }
  if (!Number.isFinite(fallbackDistance)) throw new Error("No finite compound separation");
  return Number.isFinite(bestDistance) ? best : fallback;
}

// SAT uses the same polygon vertices that SVG renders. Convex input is validated
// by the geometry loader; a concave shape must be explicitly decomposed there.
export function convexContact(a: readonly Vec[], b: readonly Vec[]): Contact | null {
  if (!boundsOverlap(polygonBounds(a), polygonBounds(b))) return null;
  return convexContactNarrow(a, b);
}
function convexContactNarrow(a: readonly Vec[], b: readonly Vec[]): Contact | null {
  let depth = Infinity, normal: Vec = { x: 1, y: 0 };
  for (let side = 0; side < 2; side++) {
    const first = side === 0;
    const polygon = first ? a : b, axes = cachedAxes(polygon);
    for (let i = 0; i < polygon.length; i++) {
      const own = axisRange(polygon, axes, i);
      const { axis } = own;
      // Keep the opposing support interval scalar in this hot loop; allocating
      // a range object for every SAT axis adds substantial collection work.
      let min = Infinity, max = -Infinity;
      for (const point of first ? b : a) {
        const value = dot(point, axis); min = Math.min(min, value); max = Math.max(max, value);
      }
      const amin = first ? own.min : min, amax = first ? own.max : max;
      const bmin = first ? min : own.min, bmax = first ? max : own.max;
      const forward = amax - bmin, backward = bmax - amin;
      if (forward < 0 || backward < 0) return null;
      const overlap = Math.min(forward, backward);
      if (overlap < depth) { depth = overlap; normal = mul(axis, forward < backward ? 1 : -1); }
    }
  }
  // Support-face averages avoid selecting an arbitrary end of a flat seam.
  const support = (polygon: readonly Vec[], axis: Vec) => {
    let max = -Infinity;
    for (const point of polygon) max = Math.max(max, dot(point, axis));
    let x = 0, y = 0, count = 0;
    for (const point of polygon) if (max - dot(point, axis) < .05) {
      x += point.x; y += point.y; count++;
    }
    return mul({ x, y }, 1 / count);
  };
  return { normal, depth, point: mul(add(support(a, normal), support(b, mul(normal, -1))), .5) };
}

export function polygonGap(a: readonly Vec[], b: readonly Vec[]): number {
  const lowerBound = boundsGap(a, b);
  if (lowerBound > 1) return lowerBound;
  let squaredGap = Infinity;
  for (const [first, second] of [[a, b], [b, a]]) {
    for (const p of first!) {
      for (let i = 0; i < second!.length; i++) {
        const start = second![i]!, end = second![(i + 1) % second!.length]!;
        const vx = end.x - start.x, vy = end.y - start.y, px = p.x - start.x, py = p.y - start.y;
        const t = Math.max(0, Math.min(1, (px * vx + py * vy) / (vx * vx + vy * vy || 1)));
        const dx = px - t * vx, dy = py - t * vy;
        squaredGap = Math.min(squaredGap, dx * dx + dy * dy);
      }
    }
  }
  return Math.sqrt(squaredGap);
}

export function circleContact(polygon: readonly Vec[], center: Vec, radius: number): Contact | null {
  const box = polygonBounds(polygon);
  // Conservative broad phase only. Touching boxes still enter the exact
  // contour test, including concave cavities and zero-distance edge contacts.
  if (center.x + radius < box.minx || center.x - radius > box.maxx
    || center.y + radius < box.miny || center.y - radius > box.maxy) return null;
  let nearest: Vec = polygon[0]!, distance = Infinity, nearestEdge = sub(polygon[1]!, polygon[0]!);
  polygon.forEach((a, i) => {
    const p = closestOnSegment(center, a, polygon[(i + 1) % polygon.length]!);
    const d = length(sub(center, p));
    if (d < distance) { nearest = p; distance = d; nearestEdge = sub(polygon[(i + 1) % polygon.length]!, a); }
  });
  const contained = inside(center, polygon);
  if (!contained && distance > radius) return null;
  // Normal points from body toward navigator. Positional correction moves body
  // oppositely, including when an explicitly loaded fixture contains the circle.
  // Exact vertex/edge contact has zero distance. Its normal comes from the
  // positively wound contour edge, never from normalizing a zero vector.
  const normal = distance < 1e-9 ? unit({ x: nearestEdge.y, y: -nearestEdge.x })
    : unit(contained ? sub(nearest, center) : sub(center, nearest));
  return { normal, depth: contained ? radius + distance : radius - distance, point: nearest };
}

// Earliest entry of a translating circle into a fixed simple, positively wound
// contour. Cast against each finite edge's outward offset and each vertex's
// radius disk, rather than the convex hull (which would fill concave cavities).
// This is a linear shape query at the current body pose, not rotating-body CCD.
export function circleCast(polygon: readonly Vec[], start: Vec, end: Vec, radius: number): (Contact & { fraction: number }) | null {
  const box = polygonBounds(polygon);
  if (Math.max(start.x, end.x) + radius < box.minx || Math.min(start.x, end.x) - radius > box.maxx
    || Math.max(start.y, end.y) + radius < box.miny || Math.min(start.y, end.y) - radius > box.maxy) return null;
  const delta = sub(end, start), squared = dot(delta, delta);
  const initial = circleContact(polygon, start, radius);
  if (initial && (initial.depth > 1e-8 || dot(delta, initial.normal) < 0)) return { ...initial, fraction: 0 };
  if (squared < 1e-16) return null;
  let first = Infinity, hitPoint: Vec = polygon[0]!, hitNormal: Vec = { x: 0, y: 0 };
  const accept = (fraction: number, point: Vec, normal: Vec) => {
    if (fraction >= -1e-10 && fraction <= 1 && fraction < first) {
      first = Math.max(0, fraction); hitPoint = point; hitNormal = normal;
    }
  };
  for (let i = 0; i < polygon.length; i++) {
    const a = polygon[i]!, b = polygon[(i + 1) % polygon.length]!;
    const edge = sub(b, a), extent = length(edge), normal = { x: edge.y / extent, y: -edge.x / extent };
    const relative = sub(start, a), speed = dot(delta, normal);
    if (speed < -1e-12) {
      const fraction = (radius - dot(relative, normal)) / speed;
      if (fraction >= -1e-10 && fraction <= 1 && fraction < first) {
        const center = add(start, mul(delta, fraction)), along = dot(sub(center, a), edge) / (extent * extent);
        if (along >= 0 && along <= 1) accept(fraction, add(a, mul(edge, along)), normal);
      }
    }
    const projection = dot(relative, delta), constant = dot(relative, relative) - radius * radius;
    const discriminant = projection * projection - squared * constant;
    if (discriminant >= 0) {
      const fraction = (-projection - Math.sqrt(discriminant)) / squared;
      if (fraction >= -1e-10 && fraction <= 1 && fraction < first) {
        const normal = unit(add(relative, mul(delta, fraction)));
        if (dot(delta, normal) < -1e-12) accept(fraction, a, normal);
      }
    }
  }
  return Number.isFinite(first) ? { fraction: first, point: hitPoint, normal: hitNormal, depth: 0 } : null;
}
