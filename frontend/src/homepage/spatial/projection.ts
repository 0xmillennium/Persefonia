import type { SpatialPoint } from "./canonical-route";

export type ProjectedPoint = { readonly x: number; readonly y: number };
export type ViewportProjection = { readonly scale: number; readonly offsetX: number; readonly offsetY: number };

export function projectSpatialPointToPlane(point: SpatialPoint): ProjectedPoint {
  return { x: point.x + point.depth * 0.35, y: point.y - point.depth * 0.20 };
}

export function deriveViewportProjection(points: readonly SpatialPoint[], width: number, height: number, paddingRatio = 0.1): ViewportProjection {
  if (!Number.isFinite(width) || width <= 0 || !Number.isFinite(height) || height <= 0 ||
      !Number.isFinite(paddingRatio) || paddingRatio < 0 || paddingRatio >= 0.5 || points.length === 0) {
    throw new RangeError("Viewport dimensions, padding, and route points must be valid");
  }
  const projected = points.map(projectSpatialPointToPlane);
  if (projected.some(point => !Number.isFinite(point.x) || !Number.isFinite(point.y))) throw new RangeError("Projected route points must be finite");
  const xs = projected.map(point => point.x);
  const ys = projected.map(point => point.y);
  const minX = Math.min(...xs), maxX = Math.max(...xs), minY = Math.min(...ys), maxY = Math.max(...ys);
  const extentX = maxX - minX, extentY = maxY - minY;
  if (extentX <= 0 && extentY <= 0) throw new RangeError("Projected route must have positive extent");
  const scale = Math.min(extentX > 0 ? width * (1 - 2 * paddingRatio) / extentX : Infinity,
    extentY > 0 ? height * (1 - 2 * paddingRatio) / extentY : Infinity);
  return {
    scale,
    offsetX: (width - extentX * scale) / 2 - minX * scale,
    offsetY: (height - extentY * scale) / 2 - minY * scale
  };
}

export function projectSpatialPointToViewport(point: SpatialPoint, projection: ViewportProjection): ProjectedPoint {
  const plane = projectSpatialPointToPlane(point);
  return { x: plane.x * projection.scale + projection.offsetX, y: plane.y * projection.scale + projection.offsetY };
}
