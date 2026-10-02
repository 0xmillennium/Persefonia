import { describe, expect, test } from "vitest";
import type { AttentionState } from "../runtime/attention";
import { deriveHomepageRuntimeFrame, type HomepageRuntimeFrame } from "../runtime/frame-state";
import { deriveViewportProjection, projectSpatialPointToPlane, projectSpatialPointToViewport, type ProjectedPoint } from "../spatial/projection";
import { getOrderedCompositingDrawables, type CompositingSceneModel } from "./compositing-model";
import { deriveQualificationCompositingScene } from "./qualification-rig";

const DRAWABLE_IDS = ["rear-structure", "middle-section", "front-occluder", "translucent-evidence",
  "registration-seam", "structure-line", "ariadne-trace", "navigator"];
const ORDERED_IDS = ["rear-structure", "structure-line", "middle-section", "translucent-evidence",
  "registration-seam", "front-occluder", "ariadne-trace", "navigator"];
const ELIGIBLE_IDS = ["registration-seam", "structure-line", "translucent-evidence"];

function frame(composition: "wide" | "compact" = "wide", progress = .58): HomepageRuntimeFrame {
  const width = composition === "wide" ? 1200 : 400, height = composition === "wide" ? 700 : 800;
  return deriveHomepageRuntimeFrame({
    progress, viewport: { width, height, aspectRatio: width / height, devicePixelRatio: 1,
      orientation: composition === "wide" ? "landscape" : "portrait" },
    capabilities: { reducedMotion: false, hoverCapability: "hover", pointerPrecision: "fine" },
    attention: { active: false, modality: "none" }, surface: { width, height }
  });
}

function freezeNested(value: unknown): void {
  if (value !== null && typeof value === "object") {
    for (const nested of Object.values(value)) freezeNested(nested);
    Object.freeze(value);
  }
}

function surface(scene: CompositingSceneModel, id: string) {
  const result = scene.surfaces.find(current => current.id === id);
  if (!result) throw new Error(`Missing surface ${id}`);
  return result;
}

function line(scene: CompositingSceneModel, id: string) {
  const result = scene.lines.find(current => current.id === id);
  if (!result) throw new Error(`Missing line ${id}`);
  return result;
}

function polygonArea(points: readonly ProjectedPoint[]): number {
  return Math.abs(points.reduce((sum, current, index) => {
    const next = points[(index + 1) % points.length]!;
    return sum + current.x * next.y - next.x * current.y;
  }, 0)) / 2;
}

// The fixture polygons are rectangles; verify that before using rectangular intersection area.
function rectangleBounds(points: readonly ProjectedPoint[]) {
  const left = Math.min(...points.map(point => point.x)), right = Math.max(...points.map(point => point.x));
  const top = Math.min(...points.map(point => point.y)), bottom = Math.max(...points.map(point => point.y));
  expect(points).toHaveLength(4);
  expect(points).toEqual([{ x: left, y: top }, { x: right, y: top }, { x: right, y: bottom }, { x: left, y: bottom }]);
  expect(polygonArea(points)).toBeGreaterThan(0);
  return { left, right, top, bottom };
}

type Bounds = ReturnType<typeof rectangleBounds>;
function intersection(left: Bounds, right: Bounds): Bounds {
  return { left: Math.max(left.left, right.left), right: Math.min(left.right, right.right),
    top: Math.max(left.top, right.top), bottom: Math.min(left.bottom, right.bottom) };
}

function area(bounds: Bounds): number {
  return Math.max(0, bounds.right - bounds.left) * Math.max(0, bounds.bottom - bounds.top);
}

function withoutClarification(scene: CompositingSceneModel): CompositingSceneModel {
  return { ...scene, surfaces: scene.surfaces.map(current => ({ ...current, clarification: "base" })),
    lines: scene.lines.map(current => ({ ...current, clarification: "base" })),
    navigator: { ...scene.navigator, clarification: "base" } };
}

describe.each(["wide", "compact"] as const)("%s technical composition", composition => {
  const currentFrame = () => frame(composition);
  const activeAttention: AttentionState = { active: true, modality: "pointer", normalizedX: .50,
    normalizedY: composition === "wide" ? .48 : .52 };

  test("derivation is deterministic and does not mutate nested frozen input", () => {
    const input = currentFrame(), before = structuredClone(input);
    freezeNested(input);
    expect(deriveQualificationCompositingScene(input)).toEqual(deriveQualificationCompositingScene(input));
    expect(input).toEqual(before);
  });

  test("drawable and clip ID sets are exact", () => {
    const scene = deriveQualificationCompositingScene(currentFrame());
    expect(getOrderedCompositingDrawables(scene).map(current => current.id).sort()).toEqual([...DRAWABLE_IDS].sort());
    expect(scene.clips.map(current => current.id)).toEqual(["section-clip"]);
  });

  test("surface material assignments deliberately leave evidence vocabulary unused", () => {
    const scene = deriveQualificationCompositingScene(currentFrame());
    expect(Object.fromEntries(scene.surfaces.map(current => [current.id, current.materialRole]))).toEqual({
      "rear-structure": "structural-opaque", "middle-section": "structural-opaque",
      "front-occluder": "structural-opaque", "translucent-evidence": "structural-translucent"
    });
    expect(scene.surfaces.filter(current => current.materialRole === "evidence")).toEqual([]);
  });

  test("line assignments are exact", () => {
    const scene = deriveQualificationCompositingScene(currentFrame());
    expect(Object.fromEntries(scene.lines.map(current => [current.id, current.lineRole]))).toEqual({
      "registration-seam": "registration", "structure-line": "structure", "ariadne-trace": "trace"
    });
  });

  test("fixed lifecycle samples cover all three labels without labeling Navigator", () => {
    const scene = deriveQualificationCompositingScene(currentFrame());
    expect(Object.fromEntries([...scene.surfaces, ...scene.lines].map(current => [current.id, current.lifecycleRole]))).toEqual({
      "rear-structure": "MEMORY", "structure-line": "MEMORY", "middle-section": "ACTIVE",
      "translucent-evidence": "ACTIVE", "registration-seam": "ACTIVE", "front-occluder": "UNVISITED", "ariadne-trace": "ACTIVE"
    });
    expect(scene.navigator).not.toHaveProperty("lifecycleRole");
  });

  test("Navigator position and heading consume current spatial truth through existing projection", () => {
    const input = currentFrame();
    const projection = deriveViewportProjection(input.spatial.macroMap.route, input.surface.width, input.surface.height);
    const expectedPosition = projectSpatialPointToViewport(input.spatial.navigator.position, projection);
    const expectedTangent = projectSpatialPointToPlane(input.spatial.navigator.tangent);
    const scene = deriveQualificationCompositingScene(input);
    expect(scene.navigator.position).toEqual(expectedPosition);
    expect(scene.navigator.rotationRadians).toBe(Math.atan2(expectedTangent.y, expectedTangent.x));
  });

  test("Ariadne points equal the projected current traversed prefix", () => {
    const input = currentFrame();
    const projection = deriveViewportProjection(input.spatial.macroMap.route, input.surface.width, input.surface.height);
    const expected = input.spatial.ariadne.points.map(current => projectSpatialPointToViewport(current, projection));
    expect(line(deriveQualificationCompositingScene(input), "ariadne-trace").points).toEqual(expected);
  });

  test("nonterminal traversal exposes no projected full-route line or future-route ID", () => {
    const input = currentFrame();
    expect(input.narrative.progress).toBeLessThan(1);
    const projection = deriveViewportProjection(input.spatial.macroMap.route, input.surface.width, input.surface.height);
    const fullRoute = input.spatial.macroMap.route.map(current => projectSpatialPointToViewport(current, projection));
    const scene = deriveQualificationCompositingScene(input);
    for (const current of scene.lines) expect(current.points).not.toEqual(fullRoute);
    expect(getOrderedCompositingDrawables(scene).map(current => current.id)).toEqual(ORDERED_IDS);
    expect(getOrderedCompositingDrawables(scene).filter(current => /macro|future|full|canonical/.test(current.id))).toEqual([]);
  });

  test("explicit finite orders yield the complete compositing sequence", () => {
    const ordered = getOrderedCompositingDrawables(deriveQualificationCompositingScene(currentFrame()));
    expect(ordered.map(current => current.id)).toEqual(ORDERED_IDS);
    expect(ordered.map(current => current.order)).toEqual([
      { band: "rear", withinBand: 10 }, { band: "rear", withinBand: 20 },
      { band: "middle", withinBand: 10 }, { band: "middle", withinBand: 20 }, { band: "middle", withinBand: 30 },
      { band: "front", withinBand: 10 }, { band: "front", withinBand: 20 }, { band: "front", withinBand: 30 }
    ]);
    for (const current of ordered) expect(Number.isFinite(current.order.withinBand)).toBe(true);
  });

  test("middle overlaps rear without hiding the whole rear, including after clipping", () => {
    const scene = deriveQualificationCompositingScene(currentFrame());
    const rear = rectangleBounds(surface(scene, "rear-structure").points);
    const middle = rectangleBounds(surface(scene, "middle-section").points);
    const clipped = intersection(middle, rectangleBounds(scene.clips[0]!.points));
    for (const foreground of [middle, clipped]) {
      const overlap = area(intersection(rear, foreground));
      expect(overlap).toBeGreaterThan(0);
      expect(overlap).toBeLessThan(area(rear));
    }
  });

  test("front partially overlaps middle without hiding it, including after clipping", () => {
    const scene = deriveQualificationCompositingScene(currentFrame());
    const middle = rectangleBounds(surface(scene, "middle-section").points);
    const front = rectangleBounds(surface(scene, "front-occluder").points);
    const clipped = intersection(middle, rectangleBounds(scene.clips[0]!.points));
    for (const underlying of [middle, clipped]) {
      const overlap = area(intersection(front, underlying));
      expect(overlap).toBeGreaterThan(0);
      expect(overlap).toBeLessThan(area(underlying));
    }
  });

  test("translucent surface overlaps structural material", () => {
    const scene = deriveQualificationCompositingScene(currentFrame());
    const translucent = rectangleBounds(surface(scene, "translucent-evidence").points);
    const structural = scene.surfaces.filter(current => current.materialRole === "structural-opaque");
    expect(structural.some(current => area(intersection(translucent, rectangleBounds(current.points))) > 0)).toBe(true);
  });

  test("middle references a nondegenerate clip that removes part of its polygon", () => {
    const scene = deriveQualificationCompositingScene(currentFrame());
    const middle = surface(scene, "middle-section");
    expect(middle.clipId).toBe("section-clip");
    const clip = scene.clips.find(current => current.id === middle.clipId)!;
    expect(clip).toBeDefined();
    expect(clip.points.length).toBeGreaterThanOrEqual(3);
    expect(polygonArea(clip.points)).toBeGreaterThan(0);
    const middleBounds = rectangleBounds(middle.points);
    const visibleArea = area(intersection(middleBounds, rectangleBounds(clip.points)));
    expect(visibleArea).toBeGreaterThan(0);
    expect(visibleArea).toBeLessThan(area(middleBounds));
  });

  test("all clip references resolve exactly once and clip IDs are unique", () => {
    const scene = deriveQualificationCompositingScene(currentFrame());
    expect(new Set(scene.clips.map(current => current.id)).size).toBe(scene.clips.length);
    for (const current of scene.surfaces) {
      if (current.clipId !== undefined) expect(scene.clips.filter(clip => clip.id === current.clipId)).toHaveLength(1);
    }
  });

  test("drawable IDs are unique", () => {
    const drawables = getOrderedCompositingDrawables(deriveQualificationCompositingScene(currentFrame()));
    expect(new Set(drawables.map(current => current.id)).size).toBe(drawables.length);
  });

  test("registration crosses clipped middle and translucent material, distinct from structure", () => {
    const scene = deriveQualificationCompositingScene(currentFrame());
    const seam = line(scene, "registration-seam").points;
    expect(seam).toHaveLength(2);
    for (const point of seam) {
      expect(Number.isFinite(point.x)).toBe(true);
      expect(Number.isFinite(point.y)).toBe(true);
    }
    const start = seam[0]!, end = seam[1]!;
    const middle = intersection(rectangleBounds(surface(scene, "middle-section").points), rectangleBounds(scene.clips[0]!.points));
    const translucent = rectangleBounds(surface(scene, "translucent-evidence").points);
    for (const field of [middle, translucent]) {
      if (composition === "wide") {
        expect(start.y).toBe(end.y);
        expect(start.y).toBeGreaterThan(field.top);
        expect(start.y).toBeLessThan(field.bottom);
        expect(start.x).toBeLessThan(field.left);
        expect(end.x).toBeGreaterThan(field.right);
      } else {
        expect(start.x).toBe(end.x);
        expect(start.x).toBeGreaterThan(field.left);
        expect(start.x).toBeLessThan(field.right);
        expect(start.y).toBeLessThan(field.top);
        expect(end.y).toBeGreaterThan(field.bottom);
      }
    }
    const structure = line(scene, "structure-line").points;
    expect(structure).not.toEqual(seam);
    if (composition === "wide") {
      expect(structure[0]!.x).toBe(structure[1]!.x);
      expect(structure[0]!.x).toBeLessThan(start.x);
    } else {
      expect(structure[0]!.y).toBe(structure[1]!.y);
      expect(structure[0]!.y).toBeLessThan(start.y);
    }
  });

  test("inactive attention leaves every primitive at base clarification", () => {
    const scene = deriveQualificationCompositingScene({ ...currentFrame(), attention: { active: false, modality: "none" } });
    for (const current of getOrderedCompositingDrawables(scene)) expect(current.clarification).toBe("base");
  });

  test("active current attention deterministically clarifies eligible material and registration", () => {
    const input = { ...currentFrame(), attention: activeAttention };
    const scene = deriveQualificationCompositingScene(input);
    expect(line(scene, "registration-seam").clarification).toBe("clarified");
    expect(surface(scene, "translucent-evidence").clarification).toBe("clarified");
    const clarified = getOrderedCompositingDrawables(scene).filter(current => current.clarification === "clarified");
    expect(clarified.length).toBeGreaterThan(0);
    for (const current of clarified) expect(ELIGIBLE_IDS).toContain(current.id);
    expect(deriveQualificationCompositingScene(input)).toEqual(scene);
  });

  test("attention changes only clarification and never clarifies ineligible primitives", () => {
    const input = currentFrame();
    const inactive = deriveQualificationCompositingScene(input);
    const positions = [activeAttention,
      { active: true, modality: "touch", normalizedX: composition === "wide" ? .20 : .40,
        normalizedY: composition === "wide" ? .40 : .20 } as const];
    for (const attention of positions) {
      const active = deriveQualificationCompositingScene({ ...input, attention });
      expect(withoutClarification(active)).toEqual(withoutClarification(inactive));
      for (const current of getOrderedCompositingDrawables(active)) {
        if (!ELIGIBLE_IDS.includes(current.id)) expect(current.clarification).toBe("base");
      }
    }
  });

  test("attention is reversible from explicit inactive, active, inactive inputs", () => {
    const input = currentFrame();
    const first = deriveQualificationCompositingScene(input);
    const active = deriveQualificationCompositingScene({ ...input, attention: activeAttention });
    const final = deriveQualificationCompositingScene(input);
    expect(active).not.toEqual(first);
    expect(final).toEqual(first);
  });

  test("full and reduced motion differ only in the returned motion profile", () => {
    const input = { ...currentFrame(), attention: activeAttention };
    const full = deriveQualificationCompositingScene(input);
    const reduced = deriveQualificationCompositingScene({ ...input, presentation: { ...input.presentation, motion: "reduced" } });
    expect(full.presentation.motion).toBe("full");
    expect(reduced.presentation.motion).toBe("reduced");
    expect({ ...reduced, presentation: { ...reduced.presentation, motion: "full" } }).toEqual(full);
  });

  test("resize reprojects Navigator and Ariadne while preserving IDs and spatial input", () => {
    const input = currentFrame(), before = structuredClone(input.spatial);
    freezeNested(input.spatial);
    const original = deriveQualificationCompositingScene(input);
    const resized = deriveQualificationCompositingScene({ ...input,
      surface: { width: input.surface.width * .75, height: input.surface.height * .80 } });
    expect(resized.navigator.position).not.toEqual(original.navigator.position);
    expect(line(resized, "ariadne-trace").points.some((point, index) =>
      point.x !== line(original, "ariadne-trace").points[index]!.x || point.y !== line(original, "ariadne-trace").points[index]!.y)).toBe(true);
    expect(getOrderedCompositingDrawables(resized).map(current => current.id)).toEqual(ORDERED_IDS);
    expect(resized.clips.map(current => current.id)).toEqual(original.clips.map(current => current.id));
    expect(input.spatial).toEqual(before);
  });

  test("backscroll consumes shorter current Ariadne truth without retained later history", () => {
    const earlier = frame(composition, .20), later = frame(composition, .80);
    expect(earlier.spatial.ariadne.points.length).toBeLessThan(later.spatial.ariadne.points.length);
    const laterScene = deriveQualificationCompositingScene(later);
    const earlierScene = deriveQualificationCompositingScene(earlier);
    expect(line(earlierScene, "ariadne-trace").points.length).toBeLessThan(line(laterScene, "ariadne-trace").points.length);
    expect(line(earlierScene, "ariadne-trace").points).toHaveLength(earlier.spatial.ariadne.points.length);
    expect(line(laterScene, "ariadne-trace").points).toHaveLength(later.spatial.ariadne.points.length);
  });

  test("structure is selectable and attention outside the field restores base", () => {
    const input = currentFrame();
    const onStructure: AttentionState = { active: true, modality: "touch", normalizedX: composition === "wide" ? .20 : .40,
      normalizedY: composition === "wide" ? .40 : .20 };
    expect(line(deriveQualificationCompositingScene({ ...input, attention: onStructure }), "structure-line").clarification).toBe("clarified");
    const outside = deriveQualificationCompositingScene({ ...input,
      attention: { active: true, modality: "pointer", normalizedX: .99, normalizedY: .99 } });
    for (const current of getOrderedCompositingDrawables(outside)) expect(current.clarification).toBe("base");
  });
});

test("wide and compact preserve semantic identity but recompose geometry at the same surface size", () => {
  const input = frame();
  const wide = deriveQualificationCompositingScene(input);
  const compact = deriveQualificationCompositingScene({ ...frame("compact"), surface: input.surface });
  const semantics = (scene: CompositingSceneModel) => ({
    surfaces: scene.surfaces.map(({ points: _points, ...roles }) => roles),
    lines: scene.lines.map(({ points: _points, ...roles }) => roles),
    clips: scene.clips.map(current => current.id), navigator: scene.navigator
  });
  expect(semantics(compact)).toEqual(semantics(wide));
  expect(getOrderedCompositingDrawables(compact).map(current => current.id)).toEqual(ORDERED_IDS);
  expect(compact.clips.map(current => current.id)).toEqual(["section-clip"]);
  expect(surface(compact, "middle-section").points).not.toEqual(surface(wide, "middle-section").points);
  const wideSeam = line(wide, "registration-seam").points, compactSeam = line(compact, "registration-seam").points;
  expect(wideSeam[0]!.y).toBe(wideSeam[1]!.y);
  expect(compactSeam[0]!.x).toBe(compactSeam[1]!.x);
  expect(line(compact, "ariadne-trace").points).toEqual(line(wide, "ariadne-trace").points);
});

test.each([0, -1, NaN, Infinity])("invalid surface dimension %s is rejected by existing projection validation", width => {
  expect(() => deriveQualificationCompositingScene({ ...frame(), surface: { width, height: 700 } })).toThrow(RangeError);
});
