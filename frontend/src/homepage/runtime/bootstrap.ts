import { createAttentionSource } from "./attention";
import { createCapabilitySource } from "./capabilities";
import { deriveHomepageRuntimeFrame } from "./frame-state";
import { subscribeHomepageLifecycle } from "./lifecycle";
import { readDocumentScrollProgress, subscribeDocumentScroll } from "./scroll";
import { readViewportState } from "./viewport";

const runtimes = new WeakMap<HTMLElement, Promise<(() => void) | undefined>>();
const diagnostics = ["ready", "progress", "active-region", "navigator", "ariadne-points", "state",
  "attention-active", "attention-modality", "attention-x", "attention-y", "composition-profile", "motion-profile",
  "hover-capability", "pointer-precision", "viewport-width", "viewport-height", "orientation"];

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
  const attention = createAttentionSource(view);
  let capabilities: ReturnType<typeof createCapabilitySource> | undefined;
  let unsubscribeAttention: (() => void) | undefined;
  let unsubscribeCapabilities: (() => void) | undefined;
  let render: typeof import("./spatial-prototype").renderSpatialPrototype | undefined;
  const native = mount.getAttribute("data-homepage-prototype-mode") === "native";
  let fixedProgress = 0;

  const stop = (): void => {
    unsubscribeScroll?.();
    unsubscribeScroll = undefined;
    unsubscribeAttention?.();
    unsubscribeAttention = undefined;
    attention.reset();
    unsubscribeCapabilities?.();
    unsubscribeCapabilities = undefined;
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
      mount.setAttribute("data-homepage-runtime-attention-active", "false");
      mount.setAttribute("data-homepage-runtime-attention-modality", "none");
      mount.removeAttribute("data-homepage-runtime-attention-x");
      mount.removeAttribute("data-homepage-runtime-attention-y");
      mount.setAttribute("data-homepage-runtime-state", "suspended");
    },
    resume() { refresh(); start(); },
    destroy: cleanup
  });
  const destroyed = (): boolean => lifecycle.state === "destroyed";
  const refresh = (): void => {
    if (!initialized || lifecycle.state !== "running" || !application || !render || !capabilities) return;
    try {
      const viewport = readViewportState(view);
      const width = Math.max(1, Math.min(640, viewport.width - 16));
      const height = Math.round(width * 0.56);
      // Size and attach before measuring: an in-flow opt-in canvas can change document height.
      application.renderer.resize(width, height);
      application.canvas.setAttribute("aria-hidden", "true");
      if (native && application.canvas.parentElement !== mount) mount.append(application.canvas);
      const progress = native ? readDocumentScrollProgress(view) : fixedProgress;
      const current = deriveHomepageRuntimeFrame({ progress, viewport, capabilities: capabilities.read(),
        attention: attention.read(viewport), surface: { width, height } });
      const { narrative, spatial: scene } = current;
      render(application, current);
      mount.setAttribute("data-homepage-runtime-progress", String(narrative.progress));
      mount.setAttribute("data-homepage-runtime-active-region", narrative.activeRegion);
      mount.setAttribute("data-homepage-runtime-navigator", JSON.stringify(scene.navigator.position));
      mount.setAttribute("data-homepage-runtime-ariadne-points", String(scene.ariadne.points.length));
      mount.setAttribute("data-homepage-runtime-attention-active", String(current.attention.active));
      mount.setAttribute("data-homepage-runtime-attention-modality", current.attention.modality);
      if (current.attention.active) {
        mount.setAttribute("data-homepage-runtime-attention-x", String(current.attention.normalizedX));
        mount.setAttribute("data-homepage-runtime-attention-y", String(current.attention.normalizedY));
      } else {
        mount.removeAttribute("data-homepage-runtime-attention-x");
        mount.removeAttribute("data-homepage-runtime-attention-y");
      }
      mount.setAttribute("data-homepage-runtime-composition-profile", current.presentation.composition);
      mount.setAttribute("data-homepage-runtime-motion-profile", current.presentation.motion);
      mount.setAttribute("data-homepage-runtime-hover-capability", current.capabilities.hoverCapability);
      mount.setAttribute("data-homepage-runtime-pointer-precision", current.capabilities.pointerPrecision);
      mount.setAttribute("data-homepage-runtime-viewport-width", String(viewport.width));
      mount.setAttribute("data-homepage-runtime-viewport-height", String(viewport.height));
      mount.setAttribute("data-homepage-runtime-orientation", viewport.orientation);
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
    if (!unsubscribeAttention) unsubscribeAttention = attention.subscribe(schedule);
    if (capabilities && !unsubscribeCapabilities) unsubscribeCapabilities = capabilities.subscribe(schedule);
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
    capabilities = createCapabilitySource(view);
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
