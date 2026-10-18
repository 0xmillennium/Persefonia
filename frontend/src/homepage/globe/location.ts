import {
  geographicPoint,
  projectPoint,
  transformPoint,
  type Camera,
  type Orientation,
} from './geometry';

export interface GlobeLocation {
  readonly id: string;
  readonly name: string;
  readonly latitude: number;
  readonly longitude: number;
}
export const LOCATIONS: readonly GlobeLocation[] = Object.freeze([
  Object.freeze({
    id: 'istanbul',
    name: 'Istanbul, Türkiye',
    latitude: 41.0082,
    longitude: 28.9784,
  }),
]);
export const ISTANBUL = LOCATIONS[0]!;

export function projectLocation(location: GlobeLocation, orientation: Orientation, camera: Camera) {
  const world = transformPoint(geographicPoint(location.latitude, location.longitude), orientation);
  return { ...projectPoint(world, camera), world };
}

export const PROXIMITY = Object.freeze({
  enter: 32,
  leave: 52,
  horizonEnter: 0.22,
  horizonLeave: 0.08,
});
export class LocationProximity {
  visible = false;
  open = false;
  update(
    facing: number,
    distance: number,
    sphereHit: boolean,
    suppressed: boolean,
    pinned = false,
  ): boolean {
    this.visible = facing > (this.visible ? PROXIMITY.horizonLeave : PROXIMITY.horizonEnter);
    if (!this.visible || suppressed) this.open = false;
    else
      this.open =
        pinned || (sphereHit && distance <= (this.open ? PROXIMITY.leave : PROXIMITY.enter));
    return this.open;
  }
  clear(): void {
    this.visible = false;
    this.open = false;
  }
}
