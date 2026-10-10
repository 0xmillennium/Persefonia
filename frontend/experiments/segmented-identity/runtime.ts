import { PhysicsCore, PHYSICS } from "./core";
import { canonicalGeometry } from "./geometry";
import { Renderer } from "./renderer";
import { InputAdapter } from "./input";
import { FixedScheduler } from "./scheduler";
import { FORECAST } from "./forecast";

type RuntimeOptions = {
  manual?: boolean;
  createCore?: () => PhysicsCore;
  installDiagnostics?: (get: () => PhysicsCore, replace: (core: PhysicsCore) => void, render: () => void) => () => void;
};

/** Owns one experiment instance; disposal and explicit reset never affect sibling roots. */
export function mountIdentity(root: HTMLElement, options: RuntimeOptions = {}) {
  const required = <T extends Element>(selector: string): T => {
    const node = root.querySelector<T>(selector);
    if (!node) throw new Error(`Missing identity element: ${selector}`);
    return node;
  };
  const svg = required<SVGSVGElement>(".stage"), telemetry = required<HTMLElement>("#telemetry");
  const status = required<HTMLElement>("#status"), pause = required<HTMLButtonElement>("#pause");
  const anchors = required<HTMLInputElement>("#anchors"), details = required<HTMLDetailsElement>("details");
  const resetButton = required<HTMLButtonElement>("#reset");
  const canonical = [...svg.querySelectorAll<SVGPolygonElement>("[data-fragment]")].map(node => ({ node, transform: node.getAttribute("transform") }));
  const createCore = options.createCore ?? (() => new PhysicsCore(canonicalGeometry));
  let core = createCore(), paused = false, suspended = document.hidden, disposed = false, frame = 0, failure: string | null = null;
  const renderer = new Renderer(svg, PHYSICS.navigatorRadius), scheduler = new FixedScheduler();
  pause.disabled = anchors.disabled = false; pause.textContent = "Pause"; pause.setAttribute("aria-pressed", "false");
  renderer.showAnchors = anchors.checked;
  const reduced = matchMedia("(prefers-reduced-motion: reduce)");
  const input = new InputAdapter(svg, () => !disposed && !failure && !paused && !options.manual && !suspended, wake);
  const lifecycle = new AbortController(), listeners = { signal: lifecycle.signal };
  let telemetryTime = -Infinity, previousRest = true;
  const TELEMETRY_INTERVAL = 200;

  function restoreStatic() {
    for (const { node, transform } of canonical) {
      if (transform === null) node.removeAttribute("transform"); else node.setAttribute("transform", transform);
    }
    svg.querySelectorAll(".navigator, .anchors").forEach(node => node.setAttribute("visibility", "hidden"));
  }
  function enterFallback(reason: string) {
    if (failure || disposed) return;
    failure = reason.trim().slice(0, 160) || "Unexpected motion failure";
    cancelAnimationFrame(frame); frame = 0; scheduler.reset(); input.deactivate(); restoreStatic();
    pause.disabled = anchors.disabled = true;
    status.textContent = "Static identity · motion unavailable";
    if (details.open) telemetry.textContent = JSON.stringify({ runtimeFailure: failure }, null, 2);
  }
  function safely(operation: () => void) {
    if (disposed) return;
    try { operation(); } catch (error) { enterFallback(error instanceof Error ? error.message : "Unexpected motion failure"); }
  }
  function render(time = performance.now(), forceTelemetry = true) {
    if (failure) {
      if (details.open) telemetry.textContent = JSON.stringify({ runtimeFailure: failure }, null, 2);
      return true;
    }
    renderer.render(core.bodies, core.navigator, renderer.showAnchors ? core.bodies.flatMap(b => core.anchors(b)) : undefined);
    const rest = core.isRest();
    const label = reduced.matches ? "Reduced motion · static identity" : paused ? "Paused" : rest ? "REST" : "In motion";
    if (status.textContent !== label) status.textContent = label;
    if (details.open && (forceTelemetry || rest && !previousRest || time - telemetryTime >= TELEMETRY_INTERVAL)) {
      telemetry.textContent = JSON.stringify({ ...core.getSnapshot(), droppedPointerSamples: input.droppedSamples }, null, 2);
      telemetryTime = time;
    }
    previousRest = rest;
    return rest;
  }
  function reset() {
    safely(() => {
      const replacement = createCore();
      core = replacement; failure = null; pause.disabled = anchors.disabled = false;
      scheduler.reset(); input.deactivate(); render(); wake();
    });
  }
  resetButton.addEventListener("click", reset, listeners);
  pause.addEventListener("click", () => safely(() => {
    if (failure) return;
    paused = !paused; pause.textContent = paused ? "Resume" : "Pause";
    pause.setAttribute("aria-pressed", String(paused)); scheduler.reset(); input.deactivate(); render(); wake();
  }), listeners);
  anchors.addEventListener("change", () => safely(() => { renderer.showAnchors = anchors.checked; render(); }), listeners);
  details.addEventListener("toggle", () => safely(() => { render(); }), listeners);
  reduced.addEventListener("change", () => { if (!failure) reset(); }, listeners);
  function suspend() {
    suspended = true; cancelAnimationFrame(frame); frame = 0; scheduler.reset(); input.deactivate();
    core.navigator.active = false; core.navigator.velocity = { x: 0, y: 0 }; render();
  }
  function resume() { suspended = document.hidden; scheduler.reset(); input.deactivate(); render(); wake(); }
  document.addEventListener("visibilitychange", () => safely(() => { if (document.hidden) suspend(); else resume(); }), listeners);
  window.addEventListener("pagehide", e => {
    safely(suspend); if (!e.persisted) dispose();
  }, listeners);
  window.addEventListener("pageshow", e => { if (e.persisted) safely(resume); }, listeners);

  function wake() {
    safely(() => {
      if (!failure && !frame && !options.manual && !paused && !reduced.matches && !suspended && (input.pending || !core.isRest()))
        frame = requestAnimationFrame(animate);
    });
  }
  function animate(time: number) {
    frame = 0;
    safely(() => {
      if (failure || options.manual || paused || reduced.matches || suspended) return;
      scheduler.advance(time, budget => {
        const before = core.forecastWork.simulatedSteps;
        const samples = input.take(), forecastBudget = Math.min(FORECAST.workPerTick, budget);
        if (samples) core.stepStroke(samples, forecastBudget); else core.stepFixed(1, undefined, forecastBudget);
        const reason = core.runtimeFailure();
        if (reason) throw new Error(reason);
        return core.forecastWork.simulatedSteps - before;
      }, undefined, input.pending);
      const rest = render(time, false);
      if (input.pending || !rest) wake(); else scheduler.reset();
    });
  }
  const disposeDiagnostics = options.installDiagnostics?.(() => core, replacement => { core = replacement; }, render);
  function dispose() {
    if (disposed) return;
    disposed = true; cancelAnimationFrame(frame); frame = 0; scheduler.reset();
    input.dispose(); renderer.dispose(); disposeDiagnostics?.(); lifecycle.abort(); restoreStatic();
    status.textContent = "Static identity";
  }
  safely(() => { render(); wake(); });
  return { reset, dispose };
}
