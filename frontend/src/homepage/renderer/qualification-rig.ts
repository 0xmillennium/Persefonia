import type { HomepageRuntimeFrame } from "../runtime/frame-state";
import { deriveViewportProjection, projectSpatialPointToPlane, projectSpatialPointToViewport, type ProjectedPoint } from "../spatial/projection";
import type { CompositingClarification, CompositingSceneModel } from "./compositing-model";

export type QualificationCompositingInput = Pick<HomepageRuntimeFrame, "spatial" | "attention" | "presentation" | "surface">;

export function deriveQualificationCompositingScene(input: QualificationCompositingInput): CompositingSceneModel {
  const { width, height } = input.surface;
  const point = (x: number, y: number): ProjectedPoint => ({ x: x * width, y: y * height });
  const rectangle = (left: number, top: number, right: number, bottom: number): readonly ProjectedPoint[] =>
    [point(left, top), point(right, top), point(right, bottom), point(left, bottom)];

  // Screen-space qualification fixtures, independent of route and final homepage geometry.
  const compact = input.presentation.composition === "compact";
  const rear = compact ? rectangle(.12, .12, .70, .44) : rectangle(.12, .20, .56, .72);
  const middle = compact ? rectangle(.24, .32, .82, .72) : rectangle(.34, .32, .76, .80);
  const front = compact ? rectangle(.46, .62, .90, .90) : rectangle(.62, .50, .88, .90);
  const translucent = compact ? rectangle(.18, .40, .66, .64) : rectangle(.44, .26, .70, .62);
  const clip = compact ? rectangle(.28, .36, .78, .68) : rectangle(.38, .38, .72, .74);
  const registration = compact ? [point(.50, .22), point(.50, .82)] : [point(.22, .48), point(.80, .48)];
  const structure = compact ? [point(.16, .20), point(.64, .20)] : [point(.20, .24), point(.20, .68)];

  const projection = deriveViewportProjection(input.spatial.macroMap.route, width, height);
  const ariadne = input.spatial.ariadne.points.map(current => projectSpatialPointToViewport(current, projection));
  const navigatorPosition = projectSpatialPointToViewport(input.spatial.navigator.position, projection);
  const tangent = projectSpatialPointToPlane(input.spatial.navigator.tangent);

  // Only the axis-aligned fixture lines and translucent rectangle participate in attention selection.
  const clarification = (points: readonly ProjectedPoint[], padding = 0): CompositingClarification => {
    if (!input.attention.active) return "base";
    const x = input.attention.normalizedX * width, y = input.attention.normalizedY * height;
    const xs = points.map(current => current.x), ys = points.map(current => current.y);
    return x >= Math.min(...xs) - padding && x <= Math.max(...xs) + padding &&
      y >= Math.min(...ys) - padding && y <= Math.max(...ys) + padding ? "clarified" : "base";
  };
  const lineSelectionPadding = Math.min(width, height) * .025;

  // Lifecycle samples exercise presentation capabilities; they do not represent actual narrative regions.
  return {
    surfaces: [
      { id: "rear-structure", kind: "surface", points: rear, order: { band: "rear", withinBand: 10 },
        materialRole: "structural-opaque", lifecycleRole: "MEMORY", clarification: "base" },
      { id: "middle-section", kind: "surface", points: middle, order: { band: "middle", withinBand: 10 },
        materialRole: "structural-opaque", lifecycleRole: "ACTIVE", clarification: "base", clipId: "section-clip" },
      { id: "front-occluder", kind: "surface", points: front, order: { band: "front", withinBand: 10 },
        materialRole: "structural-opaque", lifecycleRole: "UNVISITED", clarification: "base" },
      { id: "translucent-evidence", kind: "surface", points: translucent, order: { band: "middle", withinBand: 20 },
        materialRole: "structural-translucent", lifecycleRole: "ACTIVE", clarification: clarification(translucent) }
    ],
    lines: [
      { id: "registration-seam", kind: "line", points: registration, order: { band: "middle", withinBand: 30 },
        lineRole: "registration", lifecycleRole: "ACTIVE", clarification: clarification(registration, lineSelectionPadding) },
      { id: "structure-line", kind: "line", points: structure, order: { band: "rear", withinBand: 20 },
        lineRole: "structure", lifecycleRole: "MEMORY", clarification: clarification(structure, lineSelectionPadding) },
      { id: "ariadne-trace", kind: "line", points: ariadne, order: { band: "front", withinBand: 20 },
        lineRole: "trace", lifecycleRole: "ACTIVE", clarification: "base" }
    ],
    clips: [{ id: "section-clip", points: clip }],
    navigator: { id: "navigator", kind: "navigator", position: navigatorPosition,
      rotationRadians: Math.atan2(tangent.y, tangent.x), order: { band: "front", withinBand: 30 }, clarification: "base" },
    presentation: input.presentation,
    surface: input.surface
  };
}
