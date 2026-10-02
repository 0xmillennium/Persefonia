import { expect, test } from "vitest";
import { deriveHomepageRuntimeFrame, derivePresentationProfile } from "./frame-state";
import type { AttentionState } from "./attention";
import type { CapabilityState } from "./capabilities";
import type { ViewportState } from "./viewport";

const viewport: ViewportState = { width: 1000, height: 800, aspectRatio: 1.25, devicePixelRatio: 2, orientation: "landscape" };
const capabilities: CapabilityState = { reducedMotion: false, hoverCapability: "none", pointerPrecision: "coarse" };
const inputs = { progress: .58, viewport, capabilities, attention: { active: false, modality: "none" } as AttentionState,
  surface: { width: 640, height: 358 } };

test.each([[1000, false, "wide", "full"], [1000, true, "wide", "reduced"],
  [672, false, "compact", "full"], [672, true, "compact", "reduced"]] as const)(
  "width %s with reduced motion %s independently selects %s/%s", (width, reducedMotion, composition, motion) => {
    expect(derivePresentationProfile({ ...viewport, width }, { ...capabilities, reducedMotion })).toEqual({ composition, motion });
  }
);

test("attention, viewport and capabilities change current presentation without changing narrative or route-derived spatial truth", () => {
  const baseline = deriveHomepageRuntimeFrame(inputs);
  const variants = [
    { attention: { active: true, modality: "pointer", normalizedX: .2, normalizedY: .3 } as AttentionState },
    { attention: { active: true, modality: "touch", normalizedX: .8, normalizedY: .4 } as AttentionState },
    { viewport: { ...viewport, width: 600, aspectRatio: .75, orientation: "portrait" as const } },
    { capabilities: { reducedMotion: true, hoverCapability: "hover", pointerPrecision: "fine" } as CapabilityState }
  ];
  for (const change of variants) {
    const current = deriveHomepageRuntimeFrame({ ...inputs, ...change });
    expect(current.narrative).toEqual(baseline.narrative);
    expect(current.spatial).toEqual(baseline.spatial);
    for (const [key, value] of Object.entries(change)) expect(current[key as keyof typeof current]).toEqual(value);
  }
  expect(baseline.presentation).toEqual({ composition: "wide", motion: "full" });
  expect(baseline.surface).toEqual({ width: 640, height: 358 });
  expect(Object.keys(baseline).sort()).toEqual(["attention", "capabilities", "narrative", "presentation", "spatial", "surface", "viewport"]);
});
