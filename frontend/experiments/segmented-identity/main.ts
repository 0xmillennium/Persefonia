import { PhysicsCore } from "./core";
import { canonicalGeometry } from "./geometry";
import { Renderer } from "./renderer";
import { InputAdapter } from "./input";
import { FixedScheduler } from "./scheduler";
import { FORECAST } from "./forecast";

const svg = document.querySelector<SVGSVGElement>(".stage")!;
const renderer = new Renderer(svg), scheduler = new FixedScheduler();
const reduced = matchMedia("(prefers-reduced-motion: reduce)");
const manual = import.meta.env.DEV && new URLSearchParams(location.search).get("test") === "1";
let core = new PhysicsCore(canonicalGeometry), paused = false, suspended = document.hidden, frame = 0;
const input = new InputAdapter(svg, () => !paused && !manual && !suspended, wake);
const telemetry = document.querySelector<HTMLElement>("#telemetry")!;
const status = document.querySelector<HTMLElement>("#status")!;
const pause = document.querySelector<HTMLButtonElement>("#pause")!;
const details = document.querySelector<HTMLDetailsElement>("details")!;
const lifecycle = new AbortController(), options = { signal: lifecycle.signal };
let disposeApi: (() => void) | undefined;

function render() {
  renderer.render(core);
  const rest = core.isRest();
  const label = reduced.matches ? "Reduced motion · static identity" : paused ? "Paused" : rest ? "REST" : "In motion";
  if (status.textContent !== label) status.textContent = label;
  if (details.open) telemetry.textContent = JSON.stringify({ ...core.getSnapshot(), droppedPointerSamples: input.droppedSamples }, null, 2);
  return rest;
}
document.querySelector("#reset")!.addEventListener("click", () => {
  core = new PhysicsCore(canonicalGeometry); scheduler.reset(); input.deactivate(); render();
}, options);
pause.addEventListener("click", () => {
  paused = !paused; pause.textContent = paused ? "Resume" : "Pause";
  pause.setAttribute("aria-pressed", String(paused)); scheduler.reset(); input.deactivate(); render(); wake();
}, options);
document.querySelector<HTMLInputElement>("#anchors")!.addEventListener("change", e => {
  renderer.showAnchors = (e.target as HTMLInputElement).checked; render();
}, options);
details.addEventListener("toggle", render, options);
reduced.addEventListener("change", () => {
  core = new PhysicsCore(canonicalGeometry); scheduler.reset(); input.deactivate(); render(); wake();
}, options);
function suspend() {
  suspended = true; cancelAnimationFrame(frame); frame = 0; scheduler.reset(); input.deactivate();
  core.navigator.active = false; core.navigator.velocity = { x: 0, y: 0 }; render();
}
function resume() {
  suspended = document.hidden; scheduler.reset(); input.deactivate(); render(); wake();
}
document.addEventListener("visibilitychange", () => document.hidden ? suspend() : resume(), options);
window.addEventListener("pagehide", e => {
  suspend();
  if (!e.persisted) { input.dispose(); renderer.dispose(); disposeApi?.(); lifecycle.abort(); }
}, options);
window.addEventListener("pageshow", e => { if (e.persisted) resume(); }, options);

function wake() {
  if (!frame && !manual && !paused && !reduced.matches && !suspended)
    frame = requestAnimationFrame(animate);
}
function animate(time: number) {
  frame = 0;
  if (!manual && !paused && !reduced.matches && !suspended) {
    scheduler.advance(time, budget => {
      const before = core.forecastWork.simulatedSteps;
      core.stepFixed(1, input.take(core.navigator.position, core.navigator.active), Math.min(FORECAST.workPerTick, budget));
      return core.forecastWork.simulatedSteps - before;
    });
    const rest = render();
    if (input.pending || !rest) wake(); else scheduler.reset();
  }
}
if (manual) {
  const { installTestApi } = await import("./test-support");
  disposeApi = installTestApi(() => core, replacement => { core = replacement; }, render);
}
render(); wake();
