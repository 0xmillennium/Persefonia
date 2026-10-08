import { PhysicsCore, FIXED_DT, PHYSICS } from "./core";
import type { PointerSample, Snapshot, BodyFixture, FragmentGeometry } from "./core";
import { canonicalGeometry } from "./geometry";
import { add } from "./math";

export function seededRandom(seed: number): () => number {
  let value = seed >>> 0;
  return () => {
    value += 0x6D2B79F5;
    let t = Math.imul(value ^ value >>> 15, 1 | value);
    t ^= t + Math.imul(t ^ t >>> 7, 61 | t);
    return ((t ^ t >>> 14) >>> 0) / 4294967296;
  };
}
export function createSimulation(deadlock = true): PhysicsCore { return new PhysicsCore(canonicalGeometry, { deadlock }); }

export function seedFixture(seed: number, geometry: readonly Pick<FragmentGeometry, "id" | "home">[] = canonicalGeometry): { fragments: BodyFixture[]; path: PointerSample[] } {
  const random = seededRandom(seed);
  const signed = (max: number) => (random() * 2 - 1) * max;
  const fragments = geometry.map(g => ({ id: g.id,
    position: add(add(g.home, { x: 480, y: 310 }), { x: signed(100), y: signed(90) }),
    angle: signed(2.4), velocity: { x: signed(350), y: signed(350) }, angularVelocity: signed(4) }));
  const path: PointerSample[] = [{ x: 180, y: 230, active: true }];
  for (let i = 0; i < 24; i++) path.push({ x: 270 + random() * 420, y: 210 + random() * 200, active: true });
  path.push({ x: 0, y: 0, active: false });
  return { fragments, path };
}

export function applyPath(core: PhysicsCore, path: readonly PointerSample[]): void {
  path.forEach(p => core.stepFixed(1, p));
}

export function invariantFailure(s: Snapshot): string | null {
  if (!s.finite) return "non-finite state";
  if (!s.contained) return "stage escape";
  if (s.fragments.some(b => b.navigatorOverlap > .02)) return "navigator penetration";
  if (s.pairs.some(p => p.state === "SOLID" && p.overlap > PHYSICS.solidTolerance)) return "solid fragment penetration";
  if (s.energy > 10_000_000) return "runaway kinetic energy";
  if (s.ledger.permissions !== 0) return "permission controller injected energy";
  if (s.ledger.fragments > 1e-6 || s.ledger.boundaries > 1e-6 || s.ledger.damping > 1e-6) return "passive subsystem injected energy";
  if (s.failures.length) return s.failures.join(", ");
  return null;
}

export function stressRun(seed: number, maxSeconds = 45, onSimulation?: (core: PhysicsCore) => void) {
  const core = createSimulation(), fixture = seedFixture(seed);
  core.loadFixture(fixture.fragments); onSimulation?.(core);
  let failure: string | null = null, maximumEnergy = core.energy();
  for (const p of fixture.path) {
    core.stepFixed(1, p);
    const s = core.getSnapshot(); failure = invariantFailure(s);
    maximumEnergy = Math.max(maximumEnergy, s.energy);
    if (failure) return { seed, passed: false, failure, maximumEnergy, snapshot: s };
  }
  const limit = Math.ceil(maxSeconds / FIXED_DT);
  for (let step = 0; step < limit; step++) {
    core.stepFixed();
    // All invariant observations are sampled every fixed step, never only at
    // final rest. Transient escape/penetration must fail the originating seed.
    const s = core.getSnapshot(); failure = invariantFailure(s);
    maximumEnergy = Math.max(maximumEnergy, s.energy);
    if (failure || s.rest) return { seed, passed: !failure && s.rest, failure, maximumEnergy, snapshot: s };
  }
  return { seed, passed: false, failure: "did not reach REST within 45s", maximumEnergy, snapshot: core.getSnapshot() };
}
