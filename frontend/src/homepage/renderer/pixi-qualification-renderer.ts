import { Graphics, type Application } from "pixi.js";
import { getOrderedCompositingDrawables, type CompositingLine, type CompositingSceneModel, type CompositingSurface } from "./compositing-model";

// Qualification constants, independent of production design tokens.
const WORLD = 0x0b0c0f;
const PALE = 0xf1eee7;
const MUTED = 0x9e9a92;
const WARM = 0xb6996b;
const COOL = 0x6f7e9f;
const TRACE = 0xc8d5c7;

const MATERIAL_COLORS = { "structural-opaque": PALE, "structural-translucent": COOL, evidence: WARM } as const;
const SURFACE_STYLES = {
  MEMORY: { fillAlpha: .14, strokeAlpha: .55, strokeWidth: 1.25 },
  ACTIVE: { fillAlpha: .32, strokeAlpha: .90, strokeWidth: 1.75 },
  UNVISITED: { fillAlpha: .06, strokeAlpha: .28, strokeWidth: 1 }
} as const;
const LINE_STYLES = {
  trace: { color: TRACE, width: 2, alpha: .78 },
  registration: { color: WARM, width: 1.5, alpha: .72 },
  structure: { color: MUTED, width: 1.25, alpha: .58 }
} as const;
const LINE_LIFECYCLES = {
  MEMORY: { alpha: .70, width: .90 },
  ACTIVE: { alpha: 1, width: 1 },
  UNVISITED: { alpha: .45, width: .85 }
} as const;

const ownedResources = new WeakMap<Application, Graphics[]>();

function polygon(graphics: Graphics, points: CompositingSurface["points"]): void {
  if (points.length < 3) throw new RangeError("Qualification polygon needs at least three points");
  graphics.moveTo(points[0]!.x, points[0]!.y);
  for (const point of points.slice(1)) graphics.lineTo(point.x, point.y);
  graphics.closePath();
}

function surface(graphics: Graphics, model: CompositingSurface): void {
  polygon(graphics, model.points);
  const color = MATERIAL_COLORS[model.materialRole];
  const style = SURFACE_STYLES[model.lifecycleRole];
  const clarified = model.clarification === "clarified";
  graphics.fill({ color, alpha: clarified ? Math.min(.50, style.fillAlpha + .12) : style.fillAlpha });
  graphics.stroke({ color: model.lifecycleRole === "ACTIVE" ? color : MUTED,
    alpha: clarified ? 1 : style.strokeAlpha, width: style.strokeWidth + (clarified ? .75 : 0) });
}

function line(graphics: Graphics, model: CompositingLine): void {
  if (model.points.length === 0 || (model.points.length === 1 && model.lineRole !== "trace")) {
    throw new RangeError("Qualification line needs a segment unless it is an origin trace");
  }
  graphics.moveTo(model.points[0]!.x, model.points[0]!.y);
  for (const point of model.points.slice(1)) graphics.lineTo(point.x, point.y);
  const style = LINE_STYLES[model.lineRole], lifecycle = LINE_LIFECYCLES[model.lifecycleRole];
  const clarified = model.clarification === "clarified";
  graphics.stroke({ color: style.color, alpha: clarified ? 1 : style.alpha * lifecycle.alpha,
    width: style.width * lifecycle.width + (clarified ? .75 : 0) });
}

export function renderQualificationCompositingScene(application: Application, scene: CompositingSceneModel): void {
  const previous = ownedResources.get(application) ?? [];
  // Release native mask effects before destroying their helpers.
  for (const graphics of previous) graphics.mask = null;
  for (const graphics of previous) graphics.parent?.removeChild(graphics);
  for (const graphics of previous) graphics.destroy();

  const current: Graphics[] = [];
  ownedResources.set(application, current);
  const graphic = (): Graphics => {
    const graphics = new Graphics();
    current.push(graphics);
    application.stage.addChild(graphics);
    return graphics;
  };

  graphic().rect(0, 0, scene.surface.width, scene.surface.height).fill({ color: WORLD, alpha: 1 });
  const masks = new Map<string, Graphics>();
  for (const clip of scene.clips) {
    if (masks.has(clip.id)) throw new Error(`Duplicate qualification clip ${clip.id}`);
    const mask = graphic();
    polygon(mask, clip.points);
    mask.fill({ color: PALE, alpha: 1 });
    // Native stencil masks are excluded from normal drawing, including unused helpers.
    mask.includeInBuild = false;
    masks.set(clip.id, mask);
  }

  for (const drawable of getOrderedCompositingDrawables(scene)) {
    const graphics = graphic();
    switch (drawable.kind) {
      case "surface": {
        surface(graphics, drawable);
        if (drawable.clipId !== undefined) {
          const mask = masks.get(drawable.clipId);
          if (!mask) throw new Error(`Missing qualification clip ${drawable.clipId}`);
          graphics.mask = mask;
        }
        break;
      }
      case "line":
        line(graphics, drawable);
        break;
      case "navigator":
        graphics.moveTo(-2, -11)
          .bezierCurveTo(-5, -11, -7, -8, -7, -4)
          .bezierCurveTo(-7, 0, -6, 4, -4, 6)
          .bezierCurveTo(-3, 8, -1, 10, 2, 10)
          .bezierCurveTo(5, 10, 7, 8, 7, 5)
          .bezierCurveTo(8, 1, 7, -3, 5, -5)
          .bezierCurveTo(4, -7, 2, -7, 1, -8)
          .bezierCurveTo(0, -10, -1, -11, -2, -11)
          .closePath().fill({ color: PALE, alpha: 1 }).stroke({ color: MUTED, alpha: .60, width: 1 });
        graphics.position.set(drawable.position.x, drawable.position.y);
        graphics.rotation = drawable.rotationRadians;
        break;
    }
  }
  application.render();
}
