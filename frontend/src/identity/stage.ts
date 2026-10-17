import type { FragmentGeometry, PhysicsResponse } from './physics/core';

export const IDENTITY_RESPONSE: PhysicsResponse = Object.freeze({
  homeStiffness: 4.84,
  linearDamping: 3.3,
  angularDamping: 4.86,
  restitution: 0.55,
  navigatorSpeedLimit: 2300,
});

export function identityStage(
  geometry: readonly FragmentGeometry[],
  bounds: { x: number; y: number; width: number; height: number },
) {
  const shiftX = 480 - bounds.x - bounds.width / 2;
  const shiftY = 310 - bounds.y - bounds.height / 2;
  return {
    width: bounds.width,
    height: bounds.height,
    response: IDENTITY_RESPONSE,
    geometry: geometry.map((g) => ({ ...g, home: { x: g.home.x + shiftX, y: g.home.y + shiftY } })),
  };
}
