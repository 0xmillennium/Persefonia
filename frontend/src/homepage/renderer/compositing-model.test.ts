import { expect, test } from "vitest";
import {
  COMPOSITING_DEPTH_BANDS, COMPOSITING_LINE_ROLES, COMPOSITING_MATERIAL_ROLES,
  compareCompositingOrder, getOrderedCompositingDrawables,
  type CompositingSceneModel, type CompositingSurface
} from "./compositing-model";

function unorderedScene(): CompositingSceneModel {
  const surface = (id: string, band: CompositingSurface["order"]["band"]): CompositingSurface => ({
    id, kind: "surface", points: [], order: { band, withinBand: 10 },
    materialRole: "structural-opaque", lifecycleRole: "ACTIVE", clarification: "base"
  });
  return {
    surfaces: [surface("front", "front"), surface("rear", "rear"), surface("middle", "middle")],
    lines: [
      { id: "trace", kind: "line", points: [], order: { band: "front", withinBand: 20 },
        lineRole: "trace", lifecycleRole: "ACTIVE", clarification: "base" },
      { id: "structure", kind: "line", points: [], order: { band: "rear", withinBand: 20 },
        lineRole: "structure", lifecycleRole: "MEMORY", clarification: "base" }
    ],
    clips: [],
    navigator: { id: "navigator", kind: "navigator", position: { x: 1, y: 2 }, rotationRadians: 0,
      order: { band: "front", withinBand: 30 }, clarification: "base" },
    presentation: { composition: "wide", motion: "full" }, surface: { width: 1000, height: 600 }
  };
}

test("material vocabulary includes the intentionally available evidence role", () => {
  expect(COMPOSITING_MATERIAL_ROLES).toEqual(["structural-opaque", "structural-translucent", "evidence"]);
});

test("depth vocabulary is exact", () => {
  expect(COMPOSITING_DEPTH_BANDS).toEqual(["rear", "middle", "front"]);
});

test("line vocabulary is exact", () => {
  expect(COMPOSITING_LINE_ROLES).toEqual(["trace", "registration", "structure"]);
});

test("explicit band ranking takes precedence over within-band values", () => {
  const rear = { band: "rear", withinBand: 1_000_000 } as const;
  const middle = { band: "middle", withinBand: 0 } as const;
  const front = { band: "front", withinBand: -1_000_000 } as const;
  expect(compareCompositingOrder(rear, middle)).toBeLessThan(0);
  expect(compareCompositingOrder(middle, front)).toBeLessThan(0);
  expect(compareCompositingOrder(rear, front)).toBeLessThan(0);
  expect(compareCompositingOrder(front, rear)).toBeGreaterThan(0);
});

test.each(["rear", "middle", "front"] as const)("%s orders within-band values ascending", band => {
  expect(compareCompositingOrder({ band, withinBand: -5 }, { band, withinBand: 10 })).toBeLessThan(0);
  expect(compareCompositingOrder({ band, withinBand: 10 }, { band, withinBand: -5 })).toBeGreaterThan(0);
  expect(compareCompositingOrder({ band, withinBand: 10 }, { band, withinBand: 10 })).toBe(0);
});

test("ordered drawable IDs are deterministic across repeated calls", () => {
  const scene = unorderedScene();
  const expected = ["rear", "structure", "middle", "front", "trace", "navigator"];
  expect(getOrderedCompositingDrawables(scene).map(drawable => drawable.id)).toEqual(expected);
  expect(getOrderedCompositingDrawables(scene).map(drawable => drawable.id)).toEqual(expected);
});

test("ordering preserves frozen source arrays and their original element order", () => {
  const scene = unorderedScene();
  const before = structuredClone(scene);
  Object.freeze(scene.surfaces);
  Object.freeze(scene.lines);
  const ordered = getOrderedCompositingDrawables(scene);
  expect(scene).toEqual(before);
  expect(ordered).not.toBe(scene.surfaces);
  expect(ordered).not.toBe(scene.lines);
  expect(ordered.map(drawable => drawable.id)).toEqual(["rear", "structure", "middle", "front", "trace", "navigator"]);
});
