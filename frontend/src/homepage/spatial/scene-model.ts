import type { MentalWorldRegionId } from "../narrative/regions";
import type { NarrativeState, RegionLifecycle } from "../narrative/state";
import { flattenCanonicalRoute, PROVISIONAL_CANONICAL_ROUTE, type CanonicalRoute, type SpatialPoint } from "./canonical-route";
import { buildTraversedRoutePrefix, sampleCanonicalRoute, type RouteSample } from "./route-sampler";

export type SpatialRouteSectionState = {
  readonly region: MentalWorldRegionId;
  readonly lifecycle: RegionLifecycle;
  readonly points: readonly SpatialPoint[];
};
export type TemporaryMacroMapModel = { readonly route: readonly SpatialPoint[]; readonly currentPosition: SpatialPoint };
export type SpatialSceneModel = {
  readonly routeSections: readonly SpatialRouteSectionState[];
  readonly navigator: RouteSample;
  readonly ariadne: { readonly points: readonly SpatialPoint[] };
  readonly macroMap: TemporaryMacroMapModel;
};

export function deriveSpatialSceneModel(state: NarrativeState, route: CanonicalRoute = PROVISIONAL_CANONICAL_ROUTE): SpatialSceneModel {
  const navigator = sampleCanonicalRoute(state, route);
  return {
    routeSections: route.sections.map(section => ({ region: section.region, lifecycle: state.regions[section.region], points: section.points })),
    navigator,
    ariadne: { points: buildTraversedRoutePrefix(state, route) },
    macroMap: { route: flattenCanonicalRoute(route), currentPosition: navigator.position }
  };
}
