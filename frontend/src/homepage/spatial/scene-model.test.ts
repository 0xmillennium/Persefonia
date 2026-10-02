import { expect, test } from "vitest";
import { deriveNarrativeState } from "../narrative/derive-narrative-state";
import { flattenCanonicalRoute, PROVISIONAL_CANONICAL_ROUTE } from "./canonical-route";
import { buildTraversedRoutePrefix, sampleCanonicalRoute } from "./route-sampler";
import { deriveSpatialSceneModel } from "./scene-model";

test("scene projections share route authority and mirror narrative lifecycle", () => {
  const state = deriveNarrativeState(.58);
  const scene = deriveSpatialSceneModel(state);
  expect(scene.routeSections.map(section => [section.region, section.lifecycle])).toEqual(
    PROVISIONAL_CANONICAL_ROUTE.sections.map(section => [section.region, state.regions[section.region]])
  );
  expect(scene.navigator).toEqual(sampleCanonicalRoute(state));
  expect(scene.ariadne.points).toEqual(buildTraversedRoutePrefix(state));
  expect(scene.macroMap.route).toEqual(flattenCanonicalRoute(PROVISIONAL_CANONICAL_ROUTE));
  expect(scene.macroMap.currentPosition).toEqual(scene.navigator.position);
  expect(deriveSpatialSceneModel(state)).toEqual(scene);
});

test("histories ending at the same progress produce the same scene", () => {
  const deriveHistory = (values: number[]) => values.map(value => deriveSpatialSceneModel(deriveNarrativeState(value))).at(-1);
  const direct = deriveSpatialSceneModel(deriveNarrativeState(.42));
  expect(deriveHistory([0, .4, .7, .42])).toEqual(direct);
  expect(deriveHistory([0, .42])).toEqual(direct);
  expect(deriveHistory([1, .6, .42])).toEqual(direct);
});

test("backscroll clears later geometry in every scene projection", () => {
  deriveSpatialSceneModel(deriveNarrativeState(0));
  const later = deriveSpatialSceneModel(deriveNarrativeState(.82));
  const earlier = deriveSpatialSceneModel(deriveNarrativeState(.28));
  const direct = deriveSpatialSceneModel(deriveNarrativeState(.28));
  expect(earlier).toEqual(direct);
  expect(earlier.navigator.position).toEqual(direct.navigator.position);
  expect(earlier.navigator.tangent).toEqual(direct.navigator.tangent);
  expect(earlier.navigator.region).toEqual(direct.navigator.region);
  expect(earlier.macroMap.currentPosition).toEqual(direct.macroMap.currentPosition);
  expect(earlier.ariadne.points.length).toBeLessThan(later.ariadne.points.length);
  expect(earlier.ariadne.points).not.toContainEqual(later.navigator.position);
});
