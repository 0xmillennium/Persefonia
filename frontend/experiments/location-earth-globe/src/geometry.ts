export type Vec3 = readonly [number, number, number];
export interface Orientation { pitch: number; spin: number }
export interface Camera { width: number; height: number; radius: number; focal: number }
export const CAMERA_DISTANCE = 5;
export const dot = (a: Vec3, b: Vec3): number => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];

// East-positive longitude, north-positive latitude; Greenwich faces +Z.
export function geographicPoint(latitude: number, longitude: number): Vec3 {
  const lat = latitude * Math.PI / 180, lon = longitude * Math.PI / 180;
  return [Math.cos(lat) * Math.sin(lon), Math.sin(lat), Math.cos(lat) * Math.cos(lon)];
}

// PitchRig × SpinRig. CPU projection and shader use this same column-major matrix.
export function orientationMatrix({ pitch, spin }: Orientation): Float32Array {
  const c = Math.cos(spin), s = Math.sin(spin), cp = Math.cos(pitch), sp = Math.sin(pitch);
  return new Float32Array([c, sp * s, -cp * s, 0, cp, sp, s, -sp * c, cp * c]);
}

export function transformPoint(point: Vec3, orientation: Orientation): Vec3 {
  const [x, y, z] = point, c = Math.cos(orientation.spin), s = Math.sin(orientation.spin);
  const sx = c * x + s * z, sz = -s * x + c * z;
  const cp = Math.cos(orientation.pitch), sp = Math.sin(orientation.pitch);
  return [sx, cp * y - sp * sz, sp * y + cp * sz];
}

export function cameraFor(width: number, height: number): Camera {
  const radius = Math.min(width, height) * 0.42;
  return { width, height, radius, focal: radius * Math.sqrt(CAMERA_DISTANCE ** 2 - 1) };
}

export function projectPoint(point: Vec3, camera: Camera): { x: number; y: number; facing: number } {
  const scale = camera.focal / (CAMERA_DISTANCE - point[2]);
  return { x: camera.width / 2 + point[0] * scale, y: camera.height / 2 - point[1] * scale,
    facing: CAMERA_DISTANCE * point[2] - dot(point, point) };
}

export function sphereIntersection(x: number, y: number, camera: Camera): Vec3 | null {
  const dx = (x - camera.width / 2) / camera.focal, dy = (camera.height / 2 - y) / camera.focal;
  const length = Math.hypot(dx, dy, 1);
  const ray: Vec3 = [dx / length, dy / length, -1 / length];
  const b = CAMERA_DISTANCE * ray[2], discriminant = b * b - (CAMERA_DISTANCE ** 2 - 1);
  if (discriminant < 0) return null;
  const t = -b - Math.sqrt(discriminant);
  return [ray[0] * t, ray[1] * t, CAMERA_DISTANCE + ray[2] * t];
}
