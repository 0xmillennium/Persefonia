import { it, expect } from "vitest";
import { compoundSeparation, decompose, polygonContact, polygonContacts, convexContact, transform, length, rotate, cross, dot } from "../math";
import type { Contact } from "../math";
import { canonicalGeometry } from "../geometry";
import { createSimulation, seedFixture, invariantFailure } from "../scenarios";

const corner = [{ x: 0, y: 0 }, { x: 40, y: 0 }, { x: 40, y: 10 },
  { x: 10, y: 10 }, { x: 10, y: 40 }, { x: 0, y: 40 }];
const parts = decompose(corner);
const square = [{ x: 5, y: 5 }, { x: 25, y: 5 }, { x: 25, y: 25 }, { x: 5, y: 25 }];

it("compound broad phase preserves the exhaustive SAT manifold and deepest contact across fresh and reused poses", () => {
  for (let sample = 0; sample < 36; sample++) {
    const a = canonicalGeometry[sample % canonicalGeometry.length]!, b = canonicalGeometry[(sample * 3 + 1) % canonicalGeometry.length]!;
    const angle = sample * .37, position = { x: 40 * Math.sin(sample), y: 30 * Math.cos(sample) };
    const av = transform(a.vertices, { x: 0, y: 0 }, angle), bv = transform(b.vertices, position, -angle);
    const ap = a.parts.map(p => transform(p, { x: 0, y: 0 }, angle)), bp = b.parts.map(p => transform(p, position, -angle));
    const exhaustive = ap.flatMap(first => bp.flatMap(second => {
      const contact = convexContact(first, second); return contact ? [contact] : [];
    }));
    const deepest = exhaustive.reduce<Contact | null>((best, c) => !best || c.depth > best.depth ? c : best, null);
    for (let repeat = 0; repeat < 2; repeat++) {
      expect(polygonContacts(av, bv, ap, bp)).toEqual(exhaustive);
      expect(polygonContact(av, bv, ap, bp)).toEqual(deepest);
    }
  }
});
it("compound broad phase retains tangency at a shared edge and vertex", () => {
  const box = [{ x: 0, y: 0 }, { x: 10, y: 0 }, { x: 10, y: 10 }, { x: 0, y: 10 }];
  for (const position of [{ x: 10, y: 0 }, { x: 10, y: 10 }]) {
    const other = transform(box, position, 0), contact = convexContact(box, other);
    expect(contact?.depth).toBe(0);
    expect(polygonContact(box, other, [box], [other])).toEqual(contact);
    expect(polygonContacts(box, other, [box], [other])).toEqual([contact]);
  }
});

it("separates every compound subpart, including another part reached along the translation", () => {
  const translation = compoundSeparation(parts, [square], { x: 1, y: 0 }, p => p.x > 0 && p.y === 0);
  expect(translation.x).toBeCloseTo(35.00001, 8); expect(translation.y).toBe(0);
  const moved = transform(square, translation, 0);
  expect(polygonContact(corner, moved, parts, [moved])).toBeNull();
  const partial = transform(square, { x: 5.00001, y: 0 }, 0);
  expect(polygonContact(corner, partial, parts, [partial])).not.toBeNull();
});
it("keeps the concave cavity empty and does not substitute a convex hull", () => {
  const cavity = square.map(p => ({ x: p.x / 2 + 15, y: p.y / 2 + 15 }));
  expect(polygonContact(corner, cavity, parts, [cavity])).toBeNull();
  expect(length(compoundSeparation(parts, [cavity], { x: 1, y: 0 }))).toBe(0);
});
it("does not let numerical clearance move into the next subpart", () => {
  const box = (min: number, max: number) => [{ x: min, y: 0 }, { x: max, y: 0 }, { x: max, y: 10 }, { x: min, y: 10 }];
  const fragments = [box(0, 10), box(11.000005, 20)], body = box(9, 10);
  const translation = compoundSeparation(fragments, [body], { x: 1, y: 0 }, p => p.x > 0 && p.y === 0);
  expect(translation.x).toBeCloseTo(11.00001, 8);
  const moved = transform(body, translation, 0);
  expect(fragments.every(part => convexContact(part, moved) === null)).toBe(true);
});
it.each([-.71, .39, 1.58])("separates rotated compound boundaries at angle %f", angle => {
  const origin = { x: 200, y: 300 }, direction = rotate({ x: 1, y: 0 }, angle);
  const contour = transform(corner, origin, angle), rotatedParts = parts.map(p => transform(p, origin, angle));
  const body = transform(square, origin, angle);
  const translation = compoundSeparation(rotatedParts, [body], direction,
    p => Math.abs(cross(p, direction)) < 1e-8 && dot(p, direction) > 0);
  expect(length(translation)).toBeCloseTo(35.00001, 8);
  const moved = transform(body, translation, 0);
  expect(polygonContact(contour, moved, rotatedParts, [moved])).toBeNull();
});
it("retains a finite separating fallback when other constraints reject every candidate", () => {
  const translation = compoundSeparation(parts, [square], { x: 1, y: 0 }, () => false);
  const moved = transform(square, translation, 0);
  expect(Number.isFinite(length(translation))).toBe(true);
  expect(polygonContact(corner, moved, parts, [moved])).toBeNull();
});
it.each([6, 24, 35])("resolves the initial compound/navigator penetration from seed %i", seed => {
  const core = createSimulation(), fixture = seedFixture(seed);
  core.loadFixture(fixture.fragments);
  for (const point of fixture.path.slice(0, 2)) {
    core.stepFixed(1, point);
    expect(invariantFailure(core.getSnapshot())).toBeNull();
  }
});
