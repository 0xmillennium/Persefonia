import type { Application } from "pixi.js";
import { deriveQualificationCompositingScene } from "../renderer/qualification-rig";
import { renderQualificationCompositingScene } from "../renderer/pixi-qualification-renderer";
import type { HomepageRuntimeFrame } from "./frame-state";

export function renderSpatialPrototype(application: Application, frame: HomepageRuntimeFrame): void {
  const scene = deriveQualificationCompositingScene(frame);
  renderQualificationCompositingScene(application, scene);
}
