import type { RegionLifecycle } from "../narrative/state";
import type { PresentationProfile } from "../runtime/frame-state";
import type { ProjectedPoint } from "../spatial/projection";

export const COMPOSITING_MATERIAL_ROLES = [
  "structural-opaque",
  "structural-translucent",
  "evidence"
] as const;
export type CompositingMaterialRole = (typeof COMPOSITING_MATERIAL_ROLES)[number];

export const COMPOSITING_DEPTH_BANDS = ["rear", "middle", "front"] as const;
export type CompositingDepthBand = (typeof COMPOSITING_DEPTH_BANDS)[number];
export type CompositingOrder = {
  readonly band: CompositingDepthBand;
  readonly withinBand: number;
};

export const COMPOSITING_LINE_ROLES = ["trace", "registration", "structure"] as const;
export type CompositingLineRole = (typeof COMPOSITING_LINE_ROLES)[number];
export type CompositingClarification = "base" | "clarified";

export type CompositingSurface = {
  readonly id: string;
  readonly kind: "surface";
  readonly points: readonly ProjectedPoint[];
  readonly order: CompositingOrder;
  readonly materialRole: CompositingMaterialRole;
  readonly lifecycleRole: RegionLifecycle;
  readonly clarification: CompositingClarification;
  readonly clipId?: string;
};

export type CompositingLine = {
  readonly id: string;
  readonly kind: "line";
  readonly points: readonly ProjectedPoint[];
  readonly order: CompositingOrder;
  readonly lineRole: CompositingLineRole;
  readonly lifecycleRole: RegionLifecycle;
  readonly clarification: CompositingClarification;
};

export type CompositingNavigator = {
  readonly id: string;
  readonly kind: "navigator";
  readonly position: ProjectedPoint;
  readonly rotationRadians: number;
  readonly order: CompositingOrder;
  readonly clarification: CompositingClarification;
};

export type CompositingClip = {
  readonly id: string;
  readonly points: readonly ProjectedPoint[];
};

export type CompositingDrawable = CompositingSurface | CompositingLine | CompositingNavigator;
export type CompositingSceneModel = {
  readonly surfaces: readonly CompositingSurface[];
  readonly lines: readonly CompositingLine[];
  readonly clips: readonly CompositingClip[];
  readonly navigator: CompositingNavigator;
  readonly presentation: PresentationProfile;
  readonly surface: { readonly width: number; readonly height: number };
};

const DEPTH_RANK: Readonly<Record<CompositingDepthBand, number>> = { rear: 0, middle: 1, front: 2 };

export function compareCompositingOrder(left: CompositingOrder, right: CompositingOrder): number {
  return DEPTH_RANK[left.band] - DEPTH_RANK[right.band] || left.withinBand - right.withinBand;
}

export function getOrderedCompositingDrawables(scene: CompositingSceneModel): readonly CompositingDrawable[] {
  return [...scene.surfaces, ...scene.lines, scene.navigator].sort((left, right) => compareCompositingOrder(left.order, right.order));
}
