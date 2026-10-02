export type ViewportState = {
  readonly width: number;
  readonly height: number;
  readonly aspectRatio: number;
  readonly devicePixelRatio: number;
  readonly orientation: "portrait" | "landscape" | "square";
};

export const COMPACT_MAX_WIDTH_PX = 672;
export type CompositionProfile = "wide" | "compact";

export function readViewportState(view: Window): ViewportState {
  const dimension = (value: number): number => Number.isFinite(value) ? Math.max(1, value) : 1;
  const width = dimension(view.innerWidth);
  const height = dimension(view.innerHeight);
  return {
    width, height, aspectRatio: width / height,
    devicePixelRatio: Number.isFinite(view.devicePixelRatio) && view.devicePixelRatio > 0 ? view.devicePixelRatio : 1,
    orientation: width > height ? "landscape" : height > width ? "portrait" : "square"
  };
}

export function deriveCompositionProfile(viewport: ViewportState): CompositionProfile {
  return viewport.width <= COMPACT_MAX_WIDTH_PX ? "compact" : "wide";
}
