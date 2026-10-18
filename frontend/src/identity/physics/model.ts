import type { Vec } from './math';

export type FragmentGeometry = Readonly<{
  id: string;
  vertices: readonly Vec[];
  parts: readonly (readonly Vec[])[];
  anchors: readonly Vec[];
  home: Vec;
  mass: number;
  inertia: number;
}>;
export type Body = {
  readonly geometry: FragmentGeometry;
  position: Vec;
  angle: number;
  velocity: Vec;
  angularVelocity: number;
  navigatorContact: boolean;
  contacts: string[];
};
export type BodyFixture = {
  id: string;
  position?: Vec;
  angle?: number;
  velocity?: Vec;
  angularVelocity?: number;
};
export type PointerSample = { x: number; y: number; active?: boolean; elapsedSeconds?: number };
