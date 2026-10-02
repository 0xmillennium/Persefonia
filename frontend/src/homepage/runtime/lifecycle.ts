export type HomepageLifecycle = {
  readonly state: "running" | "suspended" | "destroyed";
  suspend(): void;
  resume(): void;
  destroy(): void;
};

export function subscribeHomepageLifecycle(view: Window, callbacks: {
  suspend(): void;
  resume(): void;
  destroy(): void;
}): HomepageLifecycle {
  let state: HomepageLifecycle["state"] = "running";
  const lifecycle: HomepageLifecycle = {
    get state() { return state; },
    suspend() {
      if (state !== "running") return;
      state = "suspended";
      callbacks.suspend();
    },
    resume() {
      if (state !== "suspended") return;
      state = "running";
      callbacks.resume();
    },
    destroy() {
      if (state === "destroyed") return;
      state = "destroyed";
      view.removeEventListener("pagehide", hide);
      view.removeEventListener("pageshow", show);
      callbacks.destroy();
    }
  };
  const hide = (event: PageTransitionEvent): void => {
    if (event.persisted) lifecycle.suspend();
    else lifecycle.destroy();
  };
  const show = (event: PageTransitionEvent): void => {
    if (event.persisted) lifecycle.resume();
  };
  view.addEventListener("pagehide", hide);
  view.addEventListener("pageshow", show);
  return lifecycle;
}
