export type DocumentScrollMetrics = {
  readonly scrollY: number;
  readonly scrollHeight: number;
  readonly viewportHeight: number;
};

export function deriveDocumentScrollProgress({ scrollY, scrollHeight, viewportHeight }: DocumentScrollMetrics): number {
  if (!Number.isFinite(scrollY) || !Number.isFinite(scrollHeight) || !Number.isFinite(viewportHeight)) return 0;
  const scrollRange = Math.max(scrollHeight - viewportHeight, 0);
  return scrollRange > 0 ? Math.min(1, Math.max(0, scrollY / scrollRange)) : 0;
}

export function readDocumentScrollProgress(view: Window): number {
  return deriveDocumentScrollProgress({
    scrollY: view.scrollY,
    scrollHeight: view.document.documentElement.scrollHeight,
    viewportHeight: view.innerHeight
  });
}

export function subscribeDocumentScroll(view: Window, notify: () => void): () => void {
  view.addEventListener("scroll", notify, { passive: true });
  return () => view.removeEventListener("scroll", notify);
}
