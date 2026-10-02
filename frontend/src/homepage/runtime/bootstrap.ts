export async function bootstrapHomepageRuntime(mount: HTMLElement | null): Promise<(() => void) | undefined> {
  if (!mount) return;

  let application: import("pixi.js").Application | undefined;
  let initialized = false;
  try {
    // Temporary Stage 2.3 input; Stage 2.4 will derive progress from native document scroll.
    const attribute = mount.getAttribute("data-homepage-prototype-progress");
    const progress = attribute === null ? 0 : Number(attribute);
    if (attribute !== null && (attribute.trim() === "" || !Number.isFinite(progress))) {
      throw new RangeError("Homepage prototype progress must be a finite number");
    }
    const width = Math.max(1, Math.min(640, (mount.ownerDocument?.defaultView?.innerWidth ?? 656) - 16));
    const height = Math.round(width * 0.56);
    // The public CSP disallows unsafe-eval; Pixi supplies CSP-safe shader and uniform paths.
    await import("pixi.js/unsafe-eval");
    const { Application } = await import("pixi.js");
    const { renderSpatialPrototype } = await import("./spatial-prototype");
    application = new Application();
    await application.init({ preference: ["webgl"], width, height, autoStart: false, backgroundAlpha: 0 });
    initialized = true;
    renderSpatialPrototype(application, progress, width, height);
    application.canvas.setAttribute("aria-hidden", "true");
    mount.append(application.canvas);
    mount.setAttribute("data-homepage-runtime-ready", "true");
    return () => {
      const current = application;
      application = undefined;
      mount.removeAttribute("data-homepage-runtime-ready");
      current?.destroy(true, { children: true });
    };
  } catch (error) {
    try {
      if (initialized) application?.destroy(true, { children: true });
      else application?.renderer?.destroy(true);
    } catch (cleanupError) {
      console.error("Homepage candidate runtime cleanup failed", cleanupError);
    }
    console.error("Homepage candidate runtime could not initialize", error);
  }
}
