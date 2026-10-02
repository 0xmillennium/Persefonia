import { Graphics, type Application } from "pixi.js";
import type { SpatialSceneModel } from "../spatial/scene-model";
import { deriveViewportProjection, projectSpatialPointToPlane, projectSpatialPointToViewport, type ProjectedPoint } from "../spatial/projection";
import type { SpatialPoint } from "../spatial/canonical-route";

export function renderSpatialPrototype(application: Application, scene: SpatialSceneModel, width: number, height: number): void {
  for (const child of application.stage.removeChildren()) child.destroy();
  const graphic = (): Graphics => application.stage.addChild(new Graphics());
  const projection = deriveViewportProjection(scene.macroMap.route, width, height);
  const project = (point: SpatialPoint): ProjectedPoint => projectSpatialPointToViewport(point, projection);
  const polyline = (points: readonly SpatialPoint[], color: number, strokeWidth: number, alpha = 1): Graphics => {
    const line = graphic();
    points.forEach((point, index) => {
      const projected = project(point);
      if (index === 0) line.moveTo(projected.x, projected.y);
      else line.lineTo(projected.x, projected.y);
    });
    line.stroke({ color, width: strokeWidth, alpha });
    return line;
  };

  polyline(scene.macroMap.route, 0x74818a, 2, 0.65);
  polyline(scene.ariadne.points, 0xc8d5c7, 4);

  const navigator = project(scene.navigator.position);
  const tangent = scene.navigator.tangent;
  const projectedTangent = projectSpatialPointToPlane(tangent);
  const marker = graphic().roundRect(-9, -4, 18, 8, 4).fill(0xf1eee3);
  marker.position.set(navigator.x, navigator.y);
  marker.rotation = Math.atan2(projectedTangent.y, projectedTangent.x);

  const insetWidth = Math.min(160, width * 0.28);
  const insetHeight = insetWidth * 0.68;
  const insetX = width - insetWidth - 8;
  const insetY = 8;
  const insetProjection = deriveViewportProjection(scene.macroMap.route, insetWidth, insetHeight, 0.12);
  const inset = graphic().roundRect(insetX, insetY, insetWidth, insetHeight, 6).fill({ color: 0x10161a, alpha: 0.94 });
  scene.macroMap.route.forEach((point, index) => {
    const projected = projectSpatialPointToViewport(point, insetProjection);
    if (index === 0) inset.moveTo(insetX + projected.x, insetY + projected.y);
    else inset.lineTo(insetX + projected.x, insetY + projected.y);
  });
  inset.stroke({ color: 0x879694, width: 1.5 });
  const current = projectSpatialPointToViewport(scene.macroMap.currentPosition, insetProjection);
  inset.circle(insetX + current.x, insetY + current.y, 3).fill(0xf1eee3);
  application.render();
}
