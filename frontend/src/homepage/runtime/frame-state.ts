import { deriveNarrativeState } from "../narrative/derive-narrative-state";
import type { NarrativeState } from "../narrative/state";
import { deriveSpatialSceneModel, type SpatialSceneModel } from "../spatial/scene-model";
import type { AttentionState } from "./attention";
import type { CapabilityState } from "./capabilities";
import { deriveCompositionProfile, type CompositionProfile, type ViewportState } from "./viewport";

export type MotionProfile = "full" | "reduced";
export type PresentationProfile = { readonly composition: CompositionProfile; readonly motion: MotionProfile };
export type HomepageRuntimeFrame = {
  readonly narrative: NarrativeState;
  readonly spatial: SpatialSceneModel;
  readonly attention: AttentionState;
  readonly viewport: ViewportState;
  readonly capabilities: CapabilityState;
  readonly presentation: PresentationProfile;
  readonly surface: { readonly width: number; readonly height: number };
};

export function derivePresentationProfile(viewport: ViewportState, capabilities: CapabilityState): PresentationProfile {
  return { composition: deriveCompositionProfile(viewport), motion: capabilities.reducedMotion ? "reduced" : "full" };
}

export function deriveHomepageRuntimeFrame(inputs: Pick<HomepageRuntimeFrame, "attention" | "viewport" | "capabilities" | "surface"> & {
  readonly progress: number;
}): HomepageRuntimeFrame {
  const narrative = deriveNarrativeState(inputs.progress);
  return {
    narrative, spatial: deriveSpatialSceneModel(narrative), attention: inputs.attention,
    viewport: inputs.viewport, capabilities: inputs.capabilities, surface: inputs.surface,
    presentation: derivePresentationProfile(inputs.viewport, inputs.capabilities)
  };
}
