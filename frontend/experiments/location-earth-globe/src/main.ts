import maskUrl from "../assets/land-mask.bin?url";
import { GlobeExperiment } from "./experiment";

const stage = document.querySelector<HTMLElement>("#stage")!;
try {
  const response = await fetch(maskUrl);
  if (!response.ok) throw new Error("Geography could not be loaded");
  new GlobeExperiment(stage, new Uint8Array(await response.arrayBuffer()));
} catch (error) {
  stage.dataset.ready = "false";
  document.querySelector<HTMLElement>("#fallback")!.textContent = "Istanbul, Türkiye · 41.0082° N, 28.9784° E. Interactive globe unavailable.";
  console.error("Location globe initialization failed", error);
}
