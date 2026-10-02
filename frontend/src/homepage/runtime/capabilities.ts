export type CapabilityState = {
  readonly reducedMotion: boolean;
  readonly hoverCapability: "hover" | "none";
  readonly pointerPrecision: "fine" | "coarse" | "none";
};

export function createCapabilitySource(view: Window) {
  const reducedMotion = view.matchMedia("(prefers-reduced-motion: reduce)");
  const hover = view.matchMedia("(hover: hover)");
  const fine = view.matchMedia("(pointer: fine)");
  const coarse = view.matchMedia("(pointer: coarse)");
  const queries = [reducedMotion, hover, fine, coarse];
  let unsubscribe: (() => void) | undefined;
  return {
    read(): CapabilityState {
      return { reducedMotion: reducedMotion.matches, hoverCapability: hover.matches ? "hover" : "none",
        pointerPrecision: fine.matches ? "fine" : coarse.matches ? "coarse" : "none" };
    },
    subscribe(notify: () => void): () => void {
      if (unsubscribe) return unsubscribe;
      for (const query of queries) query.addEventListener("change", notify);
      const dispose = (): void => {
        if (unsubscribe !== dispose) return;
        for (const query of queries) query.removeEventListener("change", notify);
        unsubscribe = undefined;
      };
      unsubscribe = dispose;
      return dispose;
    }
  };
}
