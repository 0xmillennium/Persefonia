export interface Pose { x: number; y: number; angle: number; }
interface Home { id: string; x: number; y: number; }

// Eight authored expressions, selected in order in BOTH modes. No random
// trajectory or state-dependent geometry; every variant shares exact endpoints.
export const variants = [
  { id: "aperture", stagger: 0 },
  { id: "counterturn", stagger: 0 },
  { id: "diagonal-relay", stagger: 24 },
  { id: "rail-lock", stagger: 0 },
  { id: "quarter-index", stagger: 0 },
  { id: "front-flip", stagger: 0 },
  { id: "back-flip", stagger: 0 },
  { id: "floor-bounce", stagger: 0 }
] as const;

export const timing = {
  open: 480, close: 560, closed: 120, reveal: 220, hold: 720,
  write: 1100, penLift: 110, period: 160, reflection: 680, reset: 320, rest: 420, quietClose: 400, quietHold: 3000
} as const;

export function openingPose(home: Home, variant: number): Pose {
  const corner = home.id.startsWith("corner");
  const sx = Math.sign(home.x), sy = Math.sign(home.y);
  const diagonal = home.id === "corner-top-right" || home.id === "corner-bottom-left";
  if (variant === 0) {
    return { x: home.x * (corner ? 1.65 : 2.2), y: home.y * (corner ? 1.65 : 2.2),
      angle: corner ? (diagonal ? 14 : -14) : 0 };
  }
  if (variant === 1) {
    return { x: home.x * (corner ? 1.35 : 2.5), y: home.y * (corner ? 1.35 : 2.5),
      angle: corner ? (diagonal ? -8 : 8) : (home.id === "inner-top" || home.id === "inner-left" ? 180 : -180) };
  }
  if (variant === 3) {
    const rail = sx || (home.id === "inner-top" ? -1 : 1);
    return { x: home.x + rail * 24, y: home.y, angle: 0 };
  }
  if (variant === 4) return { x: home.x * 1.45, y: home.y * 1.45, angle: 0 };
  if (variant >= 5) return { x: home.x, y: home.y, angle: 0 };
  // The corner travel follows the emblem's 57-degree diagonal channels.
  return { x: home.x + sx * 10, y: home.y + sy * (corner ? 15.398649638146 : 16), angle: 0 };
}

export function registrationPose(variant: number): Pose {
  if (variant === 4) return { x: 0, y: 0, angle: 90 };
  return { x: 0, y: 0, angle: 0 };
}

export function transform(pose: Pose): string {
  return `translate(${pose.x}px, ${pose.y}px) rotate(${pose.angle}deg)`;
}

export function openingDelay(home: Home, variant: number): number {
  if (variant !== 2) return 0;
  const order = home.id === "corner-top-right" || home.id === "corner-bottom-left" ? 0
    : home.id === "inner-top" || home.id === "inner-bottom" ? 2
    : home.id.startsWith("corner") ? 4 : 6;
  return order * variants[variant]!.stagger;
}
