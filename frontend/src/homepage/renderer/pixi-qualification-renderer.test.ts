import type { Application } from "pixi.js";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { deriveHomepageRuntimeFrame } from "../runtime/frame-state";
import { getOrderedCompositingDrawables, type CompositingLine, type CompositingSceneModel, type CompositingSurface } from "./compositing-model";
import { deriveQualificationCompositingScene } from "./qualification-rig";
import { renderQualificationCompositingScene } from "./pixi-qualification-renderer";

const pixi = vi.hoisted(() => {
  type Command = readonly [string, ...unknown[]];
  class FakeGraphics {
    commands: Command[] = [];
    parent: { removeChild: (graphics: FakeGraphics) => void } | null = null;
    mask: FakeGraphics | null = null;
    includeInBuild = true;
    rotation = 0;
    position = { set: vi.fn<(x: number, y: number) => void>() };
    destroy = vi.fn(() => {
      if (this.parent !== null) throw new Error("Resource destroyed while attached");
      if (this.mask !== null) throw new Error("Mask relationship not released");
    });
    constructor() { instances.push(this); }
    moveTo(x: number, y: number) { this.commands.push(["moveTo", x, y]); return this; }
    lineTo(x: number, y: number) { this.commands.push(["lineTo", x, y]); return this; }
    bezierCurveTo(...values: number[]) { this.commands.push(["bezierCurveTo", ...values]); return this; }
    closePath() { this.commands.push(["closePath"]); return this; }
    rect(x: number, y: number, width: number, height: number) { this.commands.push(["rect", x, y, width, height]); return this; }
    fill(style: unknown) { this.commands.push(["fill", style]); return this; }
    stroke(style: unknown) { this.commands.push(["stroke", style]); return this; }
  }
  const instances: FakeGraphics[] = [];
  return { Graphics: FakeGraphics, instances };
});
vi.mock("pixi.js", () => ({ Graphics: pixi.Graphics }));
type FakeGraphics = (typeof pixi.instances)[number];

function applicationFixture() {
  const children: FakeGraphics[] = [];
  const stage = {
    children,
    addChild: vi.fn((graphics: FakeGraphics) => { children.push(graphics); graphics.parent = stage; return graphics; }),
    removeChild: vi.fn((graphics: FakeGraphics) => {
      const index = children.indexOf(graphics);
      if (index === -1) throw new Error("Removing unattached resource");
      children.splice(index, 1);
      graphics.parent = null;
    })
  };
  const render = vi.fn(), destroy = vi.fn();
  return { application: { stage, render, destroy } as unknown as Application, stage, render, destroy };
}

function rig(progress = .58): CompositingSceneModel {
  return deriveQualificationCompositingScene(deriveHomepageRuntimeFrame({
    progress, viewport: { width: 1200, height: 700, aspectRatio: 1200 / 700, devicePixelRatio: 1, orientation: "landscape" },
    capabilities: { reducedMotion: false, hoverCapability: "hover", pointerPrecision: "fine" },
    attention: { active: false, modality: "none" }, surface: { width: 1200, height: 700 }
  }));
}

const points = [{ x: 13, y: 29 }, { x: 47, y: 83 }, { x: 101, y: 7 }];
const manualSurface: CompositingSurface = { id: "manual-surface", kind: "surface", points,
  materialRole: "structural-opaque", lifecycleRole: "ACTIVE", clarification: "base", order: { band: "middle", withinBand: 1 } };
const manualLine: CompositingLine = { id: "manual-line", kind: "line", points,
  lineRole: "trace", lifecycleRole: "ACTIVE", clarification: "base", order: { band: "middle", withinBand: 2 } };
function manualScene(surfaces: readonly CompositingSurface[] = [], lines: readonly CompositingLine[] = []): CompositingSceneModel {
  return { ...rig(), surfaces, lines, clips: [] };
}
function paths(graphics: FakeGraphics) {
  return graphics.commands.filter(([name]) => ["moveTo", "lineTo", "closePath", "bezierCurveTo", "rect"].includes(name));
}
function path(points: CompositingLine["points"], closed = false) {
  return points.map((point, index) => [index === 0 ? "moveTo" : "lineTo", point.x, point.y])
    .concat(closed ? [["closePath"]] : []);
}
function style(graphics: FakeGraphics, name: "fill" | "stroke") {
  return graphics.commands.filter(([command]) => command === name);
}
function draw(scene = rig()) {
  const fixture = applicationFixture();
  renderQualificationCompositingScene(fixture.application, scene);
  return fixture;
}
function drawableGraphics(scene: CompositingSceneModel, children: FakeGraphics[], id: string): FakeGraphics {
  const index = getOrderedCompositingDrawables(scene).findIndex(drawable => drawable.id === id);
  if (index < 0) throw new Error(`Unknown drawable ${id}`);
  return children[1 + scene.clips.length + index]!;
}

beforeEach(() => { pixi.instances.length = 0; });

describe("qualification ownership and ordering", () => {
  test("PR-1: removes and destroys every prior owned resource exactly once, preserving unrelated children", () => {
    const f = draw(), previous = [...f.stage.children];
    const unrelated = new pixi.Graphics();
    f.stage.addChild(unrelated);
    f.render.mockImplementationOnce(() => {
      for (const resource of previous) expect(resource.destroy).toHaveBeenCalledTimes(1);
    });
    renderQualificationCompositingScene(f.application, rig(0));
    for (const resource of previous) {
      expect(f.stage.removeChild.mock.calls.filter(([child]) => child === resource)).toHaveLength(1);
      expect(resource.destroy).toHaveBeenCalledTimes(1);
      expect(resource.parent).toBeNull();
      expect(f.stage.children).not.toContain(resource);
    }
    expect(f.stage.children).toContain(unrelated);
    expect(unrelated.destroy).not.toHaveBeenCalled();
    expect(f.destroy).not.toHaveBeenCalled();
  });

  test("PR-2: exactly one backdrop with supplied surface bounds and opaque WORLD fill", () => {
    const scene = rig(), f = draw(scene);
    expect(f.stage.children.filter(graphics => graphics.commands.some(([name]) => name === "rect"))).toEqual([f.stage.children[0]]);
    expect(f.stage.children[0]!.commands).toEqual([
      ["rect", 0, 0, 1200, 700], ["fill", { color: 0x0b0c0f, alpha: 1 }]
    ]);
  });

  test("PR-3: exactly one helper with the exact closed section-clip polygon", () => {
    const scene = rig(), f = draw(scene), mask = f.stage.children[1]!;
    expect(paths(mask)).toEqual(path(scene.clips[0]!.points, true));
    expect(style(mask, "fill")).toEqual([["fill", { color: 0xf1eee7, alpha: 1 }]]);
    expect(style(mask, "stroke")).toEqual([]);
    expect(mask.includeInBuild).toBe(false);
    expect(f.stage.children.filter(graphics => !graphics.includeInBuild)).toEqual([mask]);
  });

  test("PR-4: each of the eight model drawables gets one Graphics using its exact geometry", () => {
    const scene = rig(), f = draw(scene);
    expect(f.stage.children.slice(2)).toHaveLength(8);
    expect(new Set(f.stage.children.slice(2)).size).toBe(8);
    for (const drawable of getOrderedCompositingDrawables(scene)) {
      if (drawable.kind === "navigator") continue;
      expect(paths(drawableGraphics(scene, f.stage.children, drawable.id))).toEqual(path(drawable.points, drawable.kind === "surface"));
    }
  });

  test.each([0, .58, 1])("PR-5: exactly ten owned Graphics at progress %s, including origin trace", progress => {
    const f = draw(rig(progress));
    expect(pixi.instances).toHaveLength(10);
    expect(f.stage.children).toEqual(pixi.instances);
    for (const resource of pixi.instances) expect(resource.parent).toBe(f.stage);
  });

  test("PR-6: realizes drawables solely in explicit model order, including changed orders", () => {
    const original = rig();
    const scene = { ...original, surfaces: original.surfaces.map((surface, index) => ({ ...surface, order: { band: "front" as const, withinBand: 100 - index } })) };
    const f = draw(scene), ordered = getOrderedCompositingDrawables(scene);
    expect(ordered.map(drawable => drawable.id)).toEqual([
      "structure-line", "registration-seam", "ariadne-trace", "navigator", "translucent-evidence", "front-occluder", "middle-section", "rear-structure"
    ]);
    ordered.forEach((drawable, index) => {
      expect(paths(f.stage.children[index + 2]!)).toEqual(drawable.kind === "navigator" ? navigatorPath : path(drawable.points, drawable.kind === "surface"));
    });
  });

  test("PR-7: middle-section uses the exact section-clip Graphics identity in the same parent subtree", () => {
    const scene = rig(), f = draw(scene), middle = drawableGraphics(scene, f.stage.children, "middle-section");
    expect(middle.mask).toBe(f.stage.children[1]);
    expect(middle.mask!.parent).toBe(middle.parent);
    for (const resource of f.stage.children.filter(resource => resource !== middle)) expect(resource.mask).toBeNull();
  });

  test("PR-8: missing clip throws with no unclipped render", () => {
    const f = applicationFixture();
    expect(() => renderQualificationCompositingScene(f.application, { ...rig(), clips: [] })).toThrow("Missing qualification clip section-clip");
    expect(f.render).not.toHaveBeenCalled();
  });

  test("duplicate clip IDs and short surface/clip polygons fail explicitly", () => {
    const scene = rig();
    expect(() => draw({ ...scene, clips: [scene.clips[0]!, scene.clips[0]!] })).toThrow("Duplicate qualification clip section-clip");
    for (const count of [0, 1, 2]) {
      expect(() => draw(manualScene([{ ...manualSurface, points: points.slice(0, count) }]))).toThrow("Qualification polygon needs at least three points");
      expect(() => draw({ ...scene, clips: [{ ...scene.clips[0]!, points: points.slice(0, count) }] })).toThrow("Qualification polygon needs at least three points");
    }
  });
});

describe("qualification styles", () => {
  test.each([
    ["PR-9", "structural-opaque", 0xf1eee7],
    ["PR-10", "structural-translucent", 0x6f7e9f],
    ["PR-11", "evidence", 0xb6996b]
  ] as const)("%s: %s maps to its exact ACTIVE material style", (_, materialRole, color) => {
    const f = draw(manualScene([{ ...manualSurface, materialRole }]));
    expect(style(f.stage.children[1]!, "fill")).toEqual([["fill", { color, alpha: .32 }]]);
    expect(style(f.stage.children[1]!, "stroke")).toEqual([["stroke", { color, alpha: .90, width: 1.75 }]]);
  });

  test.each([
    ["MEMORY", .14, 0x9e9a92, .55, 1.25],
    ["ACTIVE", .32, 0xf1eee7, .90, 1.75],
    ["UNVISITED", .06, 0x9e9a92, .28, 1]
  ] as const)("PR-12: %s surface lifecycle has exact fill and boundary treatment", (lifecycleRole, alpha, color, strokeAlpha, width) => {
    const f = draw(manualScene([{ ...manualSurface, lifecycleRole }]));
    expect(style(f.stage.children[1]!, "fill")).toEqual([["fill", { color: 0xf1eee7, alpha }]]);
    expect(style(f.stage.children[1]!, "stroke")).toEqual([["stroke", { color, alpha: strokeAlpha, width }]]);
  });

  test.each([
    ["MEMORY", .26, 0x9e9a92, 2], ["ACTIVE", .44, 0x6f7e9f, 2.5], ["UNVISITED", .18, 0x9e9a92, 1.75]
  ] as const)("PR-13: clarified %s surface changes only specified style values", (lifecycleRole, alpha, color, width) => {
    const model = { ...manualSurface, materialRole: "structural-translucent" as const, lifecycleRole };
    const base = draw(manualScene([model])).stage.children[1]!;
    const clarified = draw(manualScene([{ ...model, clarification: "clarified" }])).stage.children[1]!;
    expect(paths(clarified)).toEqual(path(points, true));
    expect(paths(clarified)).toEqual(paths(base));
    expect(style(clarified, "fill")).toEqual([["fill", { color: 0x6f7e9f, alpha }]]);
    expect(style(clarified, "stroke")).toEqual([["stroke", { color, alpha: 1, width }]]);
  });

  test.each([
    ["trace", 0xc8d5c7, 2, .78], ["registration", 0xb6996b, 1.5, .72], ["structure", 0x9e9a92, 1.25, .58]
  ] as const)("PR-14: %s line-role base style is exact", (lineRole, color, width, alpha) => {
    const f = draw(manualScene([], [{ ...manualLine, lineRole }]));
    expect(style(f.stage.children[1]!, "stroke")).toEqual([["stroke", { color, width, alpha }]]);
    expect(style(f.stage.children[1]!, "fill")).toEqual([]);
  });

  test("trace remains stronger than registration, and registration stronger than MEMORY structure", () => {
    const scene = rig(), f = draw(scene);
    const strokes = ["ariadne-trace", "registration-seam", "structure-line"].map(id =>
      style(drawableGraphics(scene, f.stage.children, id), "stroke")[0]![1] as { width: number; alpha: number });
    const [trace, registration, structure] = strokes;
    expect(structure!.width).toBe(1.125);
    expect(structure!.alpha).toBeCloseTo(.406, 12);
    expect(trace!.width).toBeGreaterThan(registration!.width);
    expect(trace!.alpha).toBeGreaterThan(registration!.alpha);
    expect(registration!.width).toBeGreaterThan(structure!.width);
    expect(registration!.alpha).toBeGreaterThan(structure!.alpha);
  });

  const lineRoles = [
    ["trace", 0xc8d5c7, 2, .78], ["registration", 0xb6996b, 1.5, .72], ["structure", 0x9e9a92, 1.25, .58]
  ] as const;
  describe.each(lineRoles)("%s lifecycle", (lineRole, color, baseWidth, baseAlpha) => {
    test.each([["MEMORY", .70, .90], ["ACTIVE", 1, 1], ["UNVISITED", .45, .85]] as const)(
      "PR-15: %s line lifecycle applies exact alpha and width multipliers", (lifecycleRole, alpha, width) => {
        const f = draw(manualScene([], [{ ...manualLine, lineRole, lifecycleRole }]));
        expect(style(f.stage.children[1]!, "stroke")).toEqual([["stroke", { color, alpha: baseAlpha * alpha, width: baseWidth * width }]]);
      });
    test.each([["MEMORY", .90], ["ACTIVE", 1], ["UNVISITED", .85]] as const)(
      "PR-16: clarified %s line preserves points/color and adjusts style after lifecycle", (lifecycleRole, multiplier) => {
        const f = draw(manualScene([], [{ ...manualLine, lineRole, lifecycleRole, clarification: "clarified" }]));
        expect(paths(f.stage.children[1]!)).toEqual(path(points));
        expect(style(f.stage.children[1]!, "stroke")).toEqual([["stroke", { color, alpha: 1, width: baseWidth * multiplier + .75 }]]);
      });
  });
});

const navigatorPath = [
  ["moveTo", -2, -11],
  ["bezierCurveTo", -5, -11, -7, -8, -7, -4],
  ["bezierCurveTo", -7, 0, -6, 4, -4, 6],
  ["bezierCurveTo", -3, 8, -1, 10, 2, 10],
  ["bezierCurveTo", 5, 10, 7, 8, 7, 5],
  ["bezierCurveTo", 8, 1, 7, -3, 5, -5],
  ["bezierCurveTo", 4, -7, 2, -7, 1, -8],
  ["bezierCurveTo", 0, -10, -1, -11, -2, -11],
  ["closePath"]
];

describe("exact trace and Navigator geometry", () => {
  test("PR-17A: one-point trace consumes exact origin with one moveTo, no invented geometry and one resource", () => {
    const supplied = [{ x: 19, y: 71 }], scene = manualScene([], [{ ...manualLine, points: supplied }]);
    const f = applicationFixture();
    expect(() => renderQualificationCompositingScene(f.application, scene)).not.toThrow();
    expect(pixi.instances).toHaveLength(3);
    expect(f.stage.children[1]!.commands).toEqual([
      ["moveTo", 19, 71], ["stroke", { color: 0xc8d5c7, alpha: .78, width: 2 }]
    ]);
    expect(f.stage.children).toEqual(pixi.instances);
    expect(supplied).toEqual([{ x: 19, y: 71 }]);
    const origin = rig(0), originFixture = draw(origin);
    expect(origin.lines.find(line => line.lineRole === "trace")!.points).toHaveLength(1);
    expect(paths(drawableGraphics(origin, originFixture.stage.children, "ariadne-trace"))).toEqual(
      path(origin.lines.find(line => line.lineRole === "trace")!.points));
    expect(originFixture.stage.children).toHaveLength(10);
  });

  test.each(["trace", "registration", "structure"] as const)("PR-17B: %s uses exactly every distinctive supplied point, without smoothing or extension", lineRole => {
    const f = draw(manualScene([], [{ ...manualLine, lineRole }]));
    expect(paths(f.stage.children[1]!)).toEqual([["moveTo", 13, 29], ["lineTo", 47, 83], ["lineTo", 101, 7]]);
  });

  test.each([
    ["trace", 0], ["registration", 0], ["structure", 0], ["registration", 1], ["structure", 1]
  ] as const)("PR-17C: %s with %s points throws and never renders", (lineRole, count) => {
    const f = applicationFixture();
    expect(() => renderQualificationCompositingScene(f.application, manualScene([], [{ ...manualLine, lineRole, points: points.slice(0, count) }]))).toThrow(RangeError);
    expect(f.render).not.toHaveBeenCalled();
  });

  test("PR-18: one Navigator Graphics with the exact continuous one-move, seven-Bezier closed contour", () => {
    const scene = rig(), f = draw(scene), navigator = drawableGraphics(scene, f.stage.children, "navigator");
    expect(f.stage.children.filter(resource => resource.commands.some(([name]) => name === "bezierCurveTo"))).toEqual([navigator]);
    expect(paths(navigator)).toEqual(navigatorPath);
    expect(navigator.commands.filter(([name]) => name === "moveTo")).toHaveLength(1);
    expect(navigator.commands.filter(([name]) => name === "bezierCurveTo")).toHaveLength(7);
    expect(navigator.commands.filter(([name]) => name === "closePath")).toHaveLength(1);
    expect(navigator.commands.filter(([name]) => ["circle", "ellipse", "roundRect", "rect"].includes(name))).toEqual([]);
  });

  test("PR-19: Navigator position is the exact model transform", () => {
    const scene = rig();
    const changed = { ...scene, navigator: { ...scene.navigator, position: { x: 37, y: -61 } } };
    const f = draw(changed);
    expect(drawableGraphics(changed, f.stage.children, "navigator").position.set.mock.calls).toEqual([[37, -61]]);
  });

  test("PR-20: Navigator rotation is the exact supplied value", () => {
    const scene = rig(), changed = { ...scene, navigator: { ...scene.navigator, rotationRadians: -1.234 } };
    const f = draw(changed);
    expect(drawableGraphics(changed, f.stage.children, "navigator").rotation).toBe(-1.234);
  });

  test.each(["base", "clarified"] as const)("PR-21: Navigator %s uses identical exact PALE/MUTED style", clarification => {
    const scene = rig(), changed = { ...scene, navigator: { ...scene.navigator, clarification } }, f = draw(changed);
    const navigator = drawableGraphics(changed, f.stage.children, "navigator");
    expect(style(navigator, "fill")).toEqual([["fill", { color: 0xf1eee7, alpha: 1 }]]);
    expect(style(navigator, "stroke")).toEqual([["stroke", { color: 0x9e9a92, alpha: .60, width: 1 }]]);
  });

  test("PR-22: presentation profile alone changes no path, style or transform commands", () => {
    const scene = rig(), first = draw(scene);
    const second = draw({ ...scene, presentation: { composition: "compact", motion: "reduced" } });
    expect(second.stage.children.map(resource => resource.commands)).toEqual(first.stage.children.map(resource => resource.commands));
    expect(second.stage.children.map(resource => resource.position.set.mock.calls)).toEqual(first.stage.children.map(resource => resource.position.set.mock.calls));
    expect(second.stage.children.map(resource => resource.rotation)).toEqual(first.stage.children.map(resource => resource.rotation));
  });

  test("PR-23: origin-to-traversal replacement retains ten new resources without accumulation", () => {
    const f = draw(rig(0)), previous = [...pixi.instances];
    renderQualificationCompositingScene(f.application, rig(.58));
    expect(pixi.instances).toHaveLength(20);
    expect(f.stage.children).toEqual(pixi.instances.slice(10));
    for (const resource of previous) expect(resource.destroy).toHaveBeenCalledTimes(1);
    for (const resource of f.stage.children) expect(resource.destroy).not.toHaveBeenCalled();
  });

  test("PR-24: exactly one Application.render per successful invocation", () => {
    const f = draw(rig(0));
    expect(f.render).toHaveBeenCalledTimes(1);
    renderQualificationCompositingScene(f.application, rig(.58));
    expect(f.render).toHaveBeenCalledTimes(2);
  });

  test("rendering errors propagate and partial owned resources can be replaced", () => {
    const f = applicationFixture(), failure = new Error("render failed");
    f.render.mockImplementationOnce(() => { throw failure; });
    expect(() => renderQualificationCompositingScene(f.application, rig())).toThrow(failure);
    const previous = [...f.stage.children];
    renderQualificationCompositingScene(f.application, rig(0));
    for (const resource of previous) expect(resource.destroy).toHaveBeenCalledTimes(1);
    expect(f.stage.children).toHaveLength(10);
  });
});
