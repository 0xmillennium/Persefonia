export interface SceneRegion {
  node: HTMLElement;
  face: HTMLElement;
  name: string;
  top: number;
  height: number;
}

export interface SceneGeometry {
  readonly top: number;
  readonly height: number;
}

/** Geometry comes from the rendered SSR sections, including variable content. */
export function measureRegions(
  regions: SceneRegion[],
  y: number,
): Readonly<Record<string, SceneGeometry>> {
  const snapshot: Record<string, SceneGeometry> = {};
  for (const region of regions) {
    const box = region.node.getBoundingClientRect();
    region.top = box.top + y;
    region.height = box.height;
    snapshot[region.name] = Object.freeze({ top: region.top, height: region.height });
  }
  return Object.freeze(snapshot);
}
