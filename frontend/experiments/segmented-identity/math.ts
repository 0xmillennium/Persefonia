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

export type Contact = { normal: Vec; depth: number; point: Vec };

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

type Bounds = { minx: number; maxx: number; miny: number; maxy: number };
const boundsCache = new WeakMap<readonly Vec[], Bounds>();
function bounds(p: readonly Vec[]): Bounds {
  const cached = boundsCache.get(p);
  if (cached) return cached;
  const result = { minx: Math.min(...p.map(v => v.x)), maxx: Math.max(...p.map(v => v.x)),
    miny: Math.min(...p.map(v => v.y)), maxy: Math.max(...p.map(v => v.y)) };
  boundsCache.set(p, result); return result;
}
export function boundsGap(a: readonly Vec[], b: readonly Vec[]): number {
  const ab = bounds(a), bb = bounds(b);
  return Math.hypot(Math.max(0, ab.minx - bb.maxx, bb.minx - ab.maxx),
    Math.max(0, ab.miny - bb.maxy, bb.miny - ab.maxy));
}

export function polygonContact(a: readonly Vec[], b: readonly Vec[],
  aparts: readonly (readonly Vec[])[], bparts: readonly (readonly Vec[])[]): Contact | null {
  if (boundsGap(a, b) > 0) return null;
  let contact: Contact | null = null;
  for (const ap of aparts) for (const bp of bparts) {
    const c = convexContact(ap, bp);
    if (c && (!contact || c.depth > contact.depth)) contact = c;
  }
  return contact;
}

// Observation manifold only: keep the impulse/projection solver's deepest
// contact unchanged, while deadlock detection can inspect every touching part.
export function polygonContacts(a: readonly Vec[], b: readonly Vec[],
  aparts: readonly (readonly Vec[])[], bparts: readonly (readonly Vec[])[]): Contact[] {
  if (boundsGap(a, b) > 0) return [];
  const contacts: Contact[] = [];
  for (const ap of aparts) for (const bp of bparts) {
    const contact = convexContact(ap, bp);
    if (contact) contacts.push(contact);
  }
  return contacts;
}

// SAT uses the same polygon vertices that SVG renders. Convex input is validated
// by the geometry loader; a concave shape must be explicitly decomposed there.
export function convexContact(a: readonly Vec[], b: readonly Vec[]): Contact | null {
  const ab = bounds(a), bb = bounds(b);
  if (ab.maxx < bb.minx || bb.maxx < ab.minx || ab.maxy < bb.miny || bb.maxy < ab.miny) return null;
  let depth = Infinity, normal: Vec = { x: 1, y: 0 };
  for (const polygon of [a, b]) {
    for (let i = 0; i < polygon.length; i++) {
      const edge = sub(polygon[(i + 1) % polygon.length]!, polygon[i]!);
      const axis = unit({ x: -edge.y, y: edge.x });
      let amin = Infinity, amax = -Infinity, bmin = Infinity, bmax = -Infinity;
      for (const p of a) { const d = dot(p, axis); amin = Math.min(amin, d); amax = Math.max(amax, d); }
      for (const p of b) { const d = dot(p, axis); bmin = Math.min(bmin, d); bmax = Math.max(bmax, d); }
      const forward = amax - bmin, backward = bmax - amin;
      if (forward < 0 || backward < 0) return null;
      const overlap = Math.min(forward, backward);
      if (overlap < depth) { depth = overlap; normal = mul(axis, forward < backward ? 1 : -1); }
    }
  }
  // Support-face averages avoid selecting an arbitrary end of a flat seam.
  const support = (polygon: readonly Vec[], axis: Vec) => {
    const max = Math.max(...polygon.map(p => dot(p, axis)));
    const face = polygon.filter(p => max - dot(p, axis) < 0.05);
    return mul(face.reduce(add, { x: 0, y: 0 }), 1 / face.length);
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
