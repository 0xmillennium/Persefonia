import { deriveNarrativeState } from "../narrative/derive-narrative-state";
import { deriveSpatialSceneModel } from "../spatial/scene-model";
import { subscribeHomepageLifecycle } from "./lifecycle";
import { readDocumentScrollProgress, subscribeDocumentScroll } from "./scroll";

const runtimes = new WeakMap<HTMLElement, Promise<(() => void) | undefined>>();
const diagnostics = ["ready", "progress", "active-region", "navigator", "ariadne-points", "state"];

export function bootstrapHomepageRuntime(mount: HTMLElement | null): Promise<(() => void) | undefined> {
  if (!mount) return Promise.resolve(undefined);
  const existing = runtimes.get(mount);
  if (existing) return existing;
  const runtime = initializeHomepageRuntime(mount);
  runtimes.set(mount, runtime);
  // Failed initialization releases mount ownership too, including synchronous validation failures.
  void runtime.then(dispose => { if (!dispose) runtimes.delete(mount); });
  return runtime;
}

async function initializeHomepageRuntime(mount: HTMLElement): Promise<(() => void) | undefined> {
  const view = mount.ownerDocument.defaultView;
  if (!view) return;
  let application: import("pixi.js").Application | undefined;
  let initialized = false;
  let initializing = true;
  let frame: number | undefined;
  let unsubscribeScroll: (() => void) | undefined;
  let render: typeof import("./spatial-prototype").renderSpatialPrototype | undefined;
  const native = mount.getAttribute("data-homepage-prototype-mode") === "native";
  let fixedProgress = 0;

  const stop = (): void => {
    unsubscribeScroll?.();
    unsubscribeScroll = undefined;
    view.removeEventListener("resize", schedule);
    if (frame !== undefined) view.cancelAnimationFrame(frame);
    frame = undefined;
  };
  const cleanup = (): void => {
    stop();
    if (initializing) return;
    const current = application;
    application = undefined;
    const canvas = initialized ? current?.canvas : undefined;
    for (const diagnostic of diagnostics) mount.removeAttribute(`data-homepage-runtime-${diagnostic}`);
    try {
      if (initialized) current?.destroy(true, { children: true });
      else current?.renderer?.destroy(true);
    } catch (error) {
      console.error("Homepage candidate runtime cleanup failed", error);
    } finally {
      canvas?.remove();
      runtimes.delete(mount);
    }
  };
  const lifecycle = subscribeHomepageLifecycle(view, {
    suspend() {
      stop();
      mount.setAttribute("data-homepage-runtime-state", "suspended");
    },
    resume() { refresh(); start(); },
    destroy: cleanup
  });
  const destroyed = (): boolean => lifecycle.state === "destroyed";
  const refresh = (): void => {
    if (!initialized || lifecycle.state !== "running" || !application || !render) return;
    try {
      const width = Math.max(1, Math.min(640, view.innerWidth - 16));
      const height = Math.round(width * 0.56);
      // Size and attach before measuring: an in-flow opt-in canvas can change document height.
      application.renderer.resize(width, height);
      application.canvas.setAttribute("aria-hidden", "true");
      if (native && application.canvas.parentElement !== mount) mount.append(application.canvas);
      const progress = native ? readDocumentScrollProgress(view) : fixedProgress;
      const narrative = deriveNarrativeState(progress);
      const scene = deriveSpatialSceneModel(narrative);
      render(application, scene, width, height);
      mount.setAttribute("data-homepage-runtime-progress", String(narrative.progress));
      mount.setAttribute("data-homepage-runtime-active-region", narrative.activeRegion);
      mount.setAttribute("data-homepage-runtime-navigator", JSON.stringify(scene.navigator.position));
      mount.setAttribute("data-homepage-runtime-ariadne-points", String(scene.ariadne.points.length));
      mount.setAttribute("data-homepage-runtime-state", "running");
      if (application.canvas.parentElement !== mount) mount.append(application.canvas);
      mount.setAttribute("data-homepage-runtime-ready", "true");
    } catch (error) {
      lifecycle.destroy();
      console.error("Homepage candidate runtime could not initialize or refresh", error);
    }
  };
  function schedule(): void {
    if (lifecycle.state !== "running" || frame !== undefined) return;
    frame = view!.requestAnimationFrame(() => {
      frame = undefined;
      refresh();
    });
  }
  function start(): void {
    if (!initialized || lifecycle.state !== "running") return;
    if (native && !unsubscribeScroll) unsubscribeScroll = subscribeDocumentScroll(view!, schedule);
    view!.addEventListener("resize", schedule);
  }

  try {
    if (!native) {
      const attribute = mount.getAttribute("data-homepage-prototype-progress");
      fixedProgress = attribute === null ? 0 : Number(attribute);
      if (attribute !== null && (attribute.trim() === "" || !Number.isFinite(fixedProgress))) {
        throw new RangeError("Homepage prototype progress must be a finite number");
      }
    }
    // The public CSP disallows unsafe-eval; Pixi supplies CSP-safe shader and uniform paths.
    await import("pixi.js/unsafe-eval");
    const { Application } = await import("pixi.js");
    ({ renderSpatialPrototype: render } = await import("./spatial-prototype"));
    if (destroyed()) return;
    application = new Application();
    await application.init({ preference: ["webgl"], width: 1, height: 1, autoStart: false, backgroundAlpha: 0 });
    initialized = true;
    initializing = false;
    if (destroyed()) { cleanup(); return; }
    refresh();
    if (destroyed()) return;
    start();
    return () => lifecycle.destroy();
  } catch (error) {
    initializing = false;
    lifecycle.destroy();
    console.error("Homepage candidate runtime could not initialize", error);
  } finally {
    initializing = false;
    if (lifecycle.state === "destroyed") cleanup();
  }
}
