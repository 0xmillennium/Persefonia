export async function bootstrapHomepageRuntime(mount: HTMLElement | null): Promise<(() => void) | undefined> {
  if (!mount) return;

  let application: import("pixi.js").Application | undefined;
  let initialized = false;
  try {
    // The public CSP disallows unsafe-eval; Pixi supplies CSP-safe shader and uniform paths.
    await import("pixi.js/unsafe-eval");
    const { Application } = await import("pixi.js");
    application = new Application();
    await application.init({ preference: ["webgl"], width: 1, height: 1, autoStart: false, backgroundAlpha: 0 });
    initialized = true;
    application.canvas.setAttribute("aria-hidden", "true");
    mount.append(application.canvas);
    return () => {
      const current = application;
      application = undefined;
      current?.destroy(true);
    };
  } catch (error) {
    try {
      if (initialized) application?.destroy(true);
      else application?.renderer?.destroy(true);
    } catch (cleanupError) {
      console.error("Homepage candidate runtime cleanup failed", cleanupError);
    }
    console.error("Homepage candidate runtime could not initialize", error);
  }
}
