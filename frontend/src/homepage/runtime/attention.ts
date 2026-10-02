export type AttentionState =
  | { readonly active: false; readonly modality: "none" }
  | {
      readonly active: true;
      readonly modality: "pointer" | "touch";
      readonly normalizedX: number;
      readonly normalizedY: number;
    };

export function createAttentionSource(view: Window) {
  let point: { pointerId: number; modality: "pointer" | "touch"; clientX: number; clientY: number } | undefined;
  let unsubscribe: (() => void) | undefined;
  const reset = (): void => { point = undefined; };
  return {
    read(viewport: { readonly width: number; readonly height: number }): AttentionState {
      if (!point) return { active: false, modality: "none" };
      const normalize = (coordinate: number, size: number): number =>
        Number.isFinite(coordinate) && Number.isFinite(size) && size > 0
          ? Math.min(1, Math.max(0, coordinate / size)) : 0;
      return {
        active: true, modality: point.modality,
        normalizedX: normalize(point.clientX, viewport.width),
        normalizedY: normalize(point.clientY, viewport.height)
      };
    },
    reset,
    subscribe(notify: () => void): () => void {
      if (unsubscribe) return unsubscribe;
      const clear = (): void => {
        if (!point) return;
        reset();
        notify();
      };
      const update = (event: PointerEvent): void => {
        if (!event.isPrimary) return;
        if (point?.modality === "touch" && point.pointerId !== event.pointerId) return;
        if (event.pointerType === "touch") {
          if (event.type !== "pointerdown" && (point?.modality !== "touch" || point.pointerId !== event.pointerId)) return;
        } else if (event.type !== "pointermove" || !["mouse", "pen"].includes(event.pointerType)) return;
        point = { pointerId: event.pointerId, modality: event.pointerType === "touch" ? "touch" : "pointer",
          clientX: event.clientX, clientY: event.clientY };
        notify();
      };
      const end = (event: PointerEvent): void => {
        if (point?.pointerId === event.pointerId && (event.type === "pointercancel" || point.modality === "touch")) clear();
      };
      const leave = (event: PointerEvent): void => {
        if (event.relatedTarget === null && point?.modality === "pointer" && point.pointerId === event.pointerId) clear();
      };
      const document = view.document;
      document.addEventListener("pointerdown", update, { passive: true });
      document.addEventListener("pointermove", update, { passive: true });
      document.addEventListener("pointerup", end, { passive: true });
      document.addEventListener("pointercancel", end, { passive: true });
      document.addEventListener("pointerout", leave, { passive: true });
      view.addEventListener("blur", clear);
      const dispose = (): void => {
        if (unsubscribe !== dispose) return;
        document.removeEventListener("pointerdown", update);
        document.removeEventListener("pointermove", update);
        document.removeEventListener("pointerup", end);
        document.removeEventListener("pointercancel", end);
        document.removeEventListener("pointerout", leave);
        view.removeEventListener("blur", clear);
        reset();
        unsubscribe = undefined;
      };
      unsubscribe = dispose;
      return dispose;
    }
  };
}
