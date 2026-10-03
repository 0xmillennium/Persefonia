import type { Application } from "pixi.js";
import { expect, test, vi } from "vitest";
import type { CompositingSceneModel } from "../renderer/compositing-model";
import { renderQualificationCompositingScene } from "../renderer/pixi-qualification-renderer";
import { deriveQualificationCompositingScene } from "../renderer/qualification-rig";
import type { HomepageRuntimeFrame } from "./frame-state";
import { renderSpatialPrototype } from "./spatial-prototype";

vi.mock("../renderer/qualification-rig", () => ({ deriveQualificationCompositingScene: vi.fn() }));
vi.mock("../renderer/pixi-qualification-renderer", () => ({ renderQualificationCompositingScene: vi.fn() }));

test("adapter derives once and passes the identical application and derived scene to the renderer", () => {
  // Opaque sentinels fail if the adapter starts inspecting frame, scene or Graphics state.
  const frame = Object.freeze({}) as HomepageRuntimeFrame;
  const application = Object.freeze({}) as Application;
  const scene = Object.freeze({}) as CompositingSceneModel;
  vi.mocked(deriveQualificationCompositingScene).mockReturnValueOnce(scene);

  renderSpatialPrototype(application, frame);

  expect(deriveQualificationCompositingScene).toHaveBeenCalledTimes(1);
  expect(vi.mocked(deriveQualificationCompositingScene).mock.calls[0]).toHaveLength(1);
  expect(vi.mocked(deriveQualificationCompositingScene).mock.calls[0]![0]).toBe(frame);
  expect(renderQualificationCompositingScene).toHaveBeenCalledTimes(1);
  expect(vi.mocked(renderQualificationCompositingScene).mock.calls[0]).toHaveLength(2);
  expect(vi.mocked(renderQualificationCompositingScene).mock.calls[0]![0]).toBe(application);
  expect(vi.mocked(renderQualificationCompositingScene).mock.calls[0]![1]).toBe(scene);
});
