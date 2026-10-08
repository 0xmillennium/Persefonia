import type { ForecastRequest, ForecastDecision } from "./forecast";
import { FORECAST } from "./forecast";
export const pairKey = (a: string, b: string): string => [a, b].sort().join("/");
export type BodyObservation = Readonly<{
  id: string; mass: number; inertia: number; speed: number; angularSpeed: number; energy: number;
  velocityX?: number; velocityY?: number; error: number; homeRequest: number; navigatorContact: boolean;
}>;
export type PairObservation = Readonly<{
  key: string; a: string; b: string; contact: boolean; overlap: number;
  gap: number; blocking: number;
}>;
export type GhostState = {
  key: string; a: string; b: string; state: "SOLID" | "GHOSTED_LATCHED";
  contactSteps: number; eligible: boolean; startStep: number | null;
  separatedSteps: number; rearmMargin: boolean;
};
export type ClusterState = {
  members: string[]; energy: number; meanEnergy: number; relativeEnergy: number; meanRelativeEnergy: number; rmsRelativeSpeed: number; energyLimit: number; rmsSpeed: number; rmsAngularSpeed: number; error: number; errorSlope: number;
  stallDuration: number; deadlocked: boolean; blockingPairs: string[]; eligiblePairs: string[];
  forecast: { baselineReduction: number; selectedReduction: number; trials: number; simulatedSteps: number } | null;
};
export type PermissionEvent = {
  step: number; key: string; type: "ghost" | "rearm"; gap: number;
  overlap: number; separatedSteps: number;
};
export const DEADLOCK = Object.freeze({
  contactSeconds: .025, calmSeconds: .05, retrySeconds: .2, preparationMargin: 4, speed: 3, angularSpeed: .12, instantaneousMultiplier: 4,
  error: 1,
  rearmMargin: .65, rearmSteps: 8, ghostFailureSeconds: 25
});

const motionObservation = (bodies: readonly BodyObservation[]) => {
  const mass = bodies.reduce((sum, b) => sum + b.mass, 0);
  const speed2 = bodies.reduce((sum, b) => sum + b.mass * b.speed ** 2, 0) / mass;
  const energy = bodies.reduce((sum, b) => sum + b.energy, 0);
  // Legacy scalar observations remain conservative. Never infer a velocity
  // direction when an observer did not supply one.
  const known = bodies.every(b => b.velocityX !== undefined && b.velocityY !== undefined);
  const vx = known ? bodies.reduce((sum, b) => sum + b.mass * b.velocityX!, 0) / mass : 0;
  const vy = known ? bodies.reduce((sum, b) => sum + b.mass * b.velocityY!, 0) / mass : 0;
  return { speed2, energy,
    relativeSpeed2: known ? bodies.reduce((sum, b) => sum + b.mass * ((b.velocityX! - vx) ** 2 + (b.velocityY! - vy) ** 2), 0) / mass : speed2,
    relativeEnergy: known ? Math.max(0, energy - .5 * mass * (vx ** 2 + vy ** 2)) : energy,
    angular2: bodies.reduce((sum, b) => sum + b.inertia * b.angularSpeed ** 2, 0) / bodies.reduce((sum, b) => sum + b.inertia, 0) };
};
const calmContact = (a: BodyObservation, b: BodyObservation): boolean => {
  const known = a.velocityX !== undefined && a.velocityY !== undefined && b.velocityX !== undefined && b.velocityY !== undefined;
  const calm = known ? Math.hypot(a.velocityX! - b.velocityX!, a.velocityY! - b.velocityY!) < DEADLOCK.instantaneousMultiplier * DEADLOCK.speed
    : a.speed < DEADLOCK.instantaneousMultiplier * DEADLOCK.speed && b.speed < DEADLOCK.instantaneousMultiplier * DEADLOCK.speed;
  return calm && a.angularSpeed < DEADLOCK.instantaneousMultiplier * DEADLOCK.angularSpeed
    && b.angularSpeed < DEADLOCK.instantaneousMultiplier * DEADLOCK.angularSpeed;
};
const meanError = (bodies: readonly BodyObservation[]) => bodies.reduce((sum, b) => sum + b.error, 0) / bodies.length;

// Deliberately receives only scalar, read-only observations. No references to
// mutable bodies, forces, impulses, poses or velocities cross this boundary.
export class CollisionPermissions {
  private pairs = new Map<string, GhostState>();
  private assessed = new Map<string, { step: number; signature: string; decision: ForecastDecision | null }>();
  requests: ForecastRequest[] = [];
  private observations = new Map<string, PairObservation>();
  private frames: BodyObservation[][] = [];
  clusters: ClusterState[] = [];
  readonly events: PermissionEvent[] = [];
  eventCount = 0;
  private record(event: PermissionEvent): void {
    this.eventCount++; this.events.push(event);
    if (this.events.length > this.historyLimit) this.events.shift();
  }
  failures: string[] = [];
  constructor(private readonly dt: number, private readonly enabled = true, private readonly predicting = false,
    private readonly historyLimit = 512) {
    if (!(historyLimit === Infinity || Number.isInteger(historyLimit) && historyLimit > 0)) throw new Error("Invalid history limit");
  }

  static forPrediction(dt: number, states: readonly GhostState[], proposed: readonly string[], step: number): CollisionPermissions {
    const copy = new CollisionPermissions(dt, false, true);
    copy.pairs = new Map(states.map(p => [p.key, { ...p }]));
    for (const key of proposed) {
      const state = copy.pairs.get(key);
      if (!state) throw new Error(`Unknown predicted pair ${key}`);
      state.state = "GHOSTED_LATCHED"; state.startStep = step; state.separatedSteps = 0;
    }
    return copy;
  }
  ghosted(key: string): boolean { return this.pairs.get(key)?.state === "GHOSTED_LATCHED"; }
  snapshot(): GhostState[] { return [...this.pairs.values()].map(p => ({ ...p })); }

  observe(step: number, bodies: readonly BodyObservation[], observations: readonly PairObservation[]): void {
    for (const p of observations) {
      let state = this.pairs.get(p.key);
      if (!state) {
        state = { key: p.key, a: p.a, b: p.b, state: "SOLID", contactSteps: 0,
          eligible: false, startStep: null, separatedSteps: 0, rearmMargin: false };
        this.pairs.set(p.key, state);
      }
      state.contactSteps = p.contact ? state.contactSteps + 1 : 0;
      state.eligible = false;
      state.rearmMargin = p.overlap === 0 && p.gap >= DEADLOCK.rearmMargin;
      if (state.state === "GHOSTED_LATCHED") {
        state.separatedSteps = state.rearmMargin ? state.separatedSteps + 1 : 0;
        if (state.separatedSteps >= DEADLOCK.rearmSteps) {
          state.state = "SOLID";
          this.record({ step, key: p.key, type: "rearm", gap: p.gap,
            overlap: p.overlap, separatedSteps: state.separatedSteps });
          state.startStep = null;
          state.contactSteps = 0;
        } else if ((step - state.startStep!) * this.dt > DEADLOCK.ghostFailureSeconds) {
          const failure = `Ghost pair ${p.key} failed to separate`;
          if (!this.failures.includes(failure)) this.failures.push(failure);
        }
      }
    }
    if (this.predicting) return;
    this.observations = new Map(observations.map(p => [p.key, { ...p }]));
    const windowSteps = Math.ceil(DEADLOCK.calmSeconds / this.dt);
    this.frames.push(bodies.map(b => ({ ...b })));
    if (this.frames.length > windowSteps + 1) this.frames.shift();
    // Preserve the navigator veto over current physical contact groups and
    // already latched relationships, including edges that are not blocking.
    const navigatorVeto = new Set(bodies.filter(b => b.navigatorContact).map(b => b.id));
    let propagated = true;
    while (propagated) {
      propagated = false;
      for (const p of this.pairs.values()) {
        if ((p.state !== "GHOSTED_LATCHED" && !this.observations.get(p.key)?.contact)
          || (!navigatorVeto.has(p.a) && !navigatorVeto.has(p.b))) continue;
        for (const id of [p.a, p.b]) if (!navigatorVeto.has(id)) { navigatorVeto.add(id); propagated = true; }
      }
    }
    const byId = new Map(bodies.map(b => [b.id, b]));
    const potentialContacts = observations.filter(p => !this.ghosted(p.key)
      && (p.contact || p.gap < DEADLOCK.preparationMargin)
      && [byId.get(p.a)!, byId.get(p.b)!].every(b => !navigatorVeto.has(b.id)
        && b.angularSpeed < DEADLOCK.instantaneousMultiplier * DEADLOCK.angularSpeed)
      && calmContact(byId.get(p.a)!, byId.get(p.b)!));
    const adjacency = new Map(bodies.map(b => [b.id, new Set<string>()]));
    potentialContacts.forEach(p => { adjacency.get(p.a)!.add(p.b); adjacency.get(p.b)!.add(p.a); });
    const visited = new Set<string>(), live = new Set<string>();
    this.clusters = []; this.requests = [];
    for (const body of bodies) {
      if (visited.has(body.id) || adjacency.get(body.id)!.size === 0) continue;
      const members: string[] = [], queue = [body.id];
      while (queue.length) {
        const id = queue.pop()!;
        if (visited.has(id)) continue;
        visited.add(id); members.push(id); queue.push(...adjacency.get(id)!);
      }
      members.sort();
      const key = members.join("|"); live.add(key);
      const contacts = potentialContacts.filter(p => members.includes(p.a) && members.includes(p.b))
        .sort((a, b) => b.blocking - a.blocking || a.key.localeCompare(b.key));
      // Contact normals may flicker across convex parts in an otherwise stable
      // jam. Nominate its entire physical/near-contact island; counterfactual
      // benefit, not an instantaneous normal score, selects its useful edges.
      if (!contacts.some(p => p.blocking > .1 || !p.contact)) continue;
      const signature = contacts.map(p => p.key).sort().join("|");
      const stats = this.statistics(this.frames.map(frame => frame.filter(b => members.includes(b.id))));
      const previous = this.assessed.get(key);
      const assessment = previous?.signature === signature ? previous : undefined;
      const ready = this.frames.length === windowSteps + 1
        && stats.meanRelativeEnergy < 4 * stats.energyLimit && stats.relativeEnergy < 4 * stats.energyLimit
        && stats.rmsRelativeSpeed < 2 * DEADLOCK.speed && stats.rmsAngularSpeed < 2 * DEADLOCK.angularSpeed
        && members.some(id => byId.get(id)!.error > DEADLOCK.error)
        && members.some(id => byId.get(id)!.homeRequest > .1);
      const deadlocked = ready && !!assessment?.decision?.selected.length;
      this.clusters.push({ members, ...stats, stallDuration: ready ? DEADLOCK.calmSeconds : 0,
        deadlocked, blockingPairs: contacts.map(p => p.key), eligiblePairs: [],
        forecast: assessment?.decision ? { baselineReduction: assessment.decision.baselineReduction,
          selectedReduction: assessment.decision.selectedReduction, trials: assessment.decision.trials,
          simulatedSteps: assessment.decision.simulatedSteps } : null });
      if (ready && (!assessment || (step - assessment.step) * this.dt >= DEADLOCK.retrySeconds))
        this.requests.push({ step, key, members, candidates: contacts.map(p => p.key), present: contacts.filter(p => p.contact).map(p => p.key) });
    }
    for (const key of this.assessed.keys()) if (!live.has(key)) this.assessed.delete(key);
  }

  deferPrediction(step: number, key: string): void {
    const request = this.requests.find(r => r.key === key);
    if (!request) return;
    this.assessed.set(key, { step, signature: [...request.candidates].sort().join("|"), decision: null });
    this.requests = this.requests.filter(r => r.key !== key);
  }

  acceptPrediction(step: number, decision: ForecastDecision): boolean {
    const request = this.requests.find(r => r.key === decision.request.key);
    if (!request || decision.selected.some(key => !request.candidates.includes(key))
      || ![decision.baselineReduction, decision.selectedReduction].every(Number.isFinite)
      || step - decision.request.step > FORECAST.maximumAgeSteps
      || decision.request.candidates.some(key => !request.candidates.includes(key))
      || new Set(decision.selected).size !== decision.selected.length
      || decision.selected.length > 0 && (decision.baselineReduction >= FORECAST.baselineProgress
        || decision.selectedReduction - decision.baselineReduction + 1e-12 < FORECAST.minimumBenefit)) return false;
    const cluster = this.clusters.find(c => c.members.join("|") === request.key)!;
    const selected = decision.selected.filter(key => {
      const state = this.pairs.get(key)!;
      return this.observations.get(key)!.contact && state.state === "SOLID" && state.contactSteps * this.dt >= DEADLOCK.contactSeconds;
    });
    // A prepared forecast may include an approaching contact. Commit the whole
    // batch only when every selected contact physically exists; never partially
    // apply a jointly necessary plan or exempt a navigator-vetoed relationship.
    if (selected.length !== decision.selected.length) return false;
    this.assessed.set(request.key, { step, signature: [...request.candidates].sort().join("|"), decision });
    cluster.deadlocked = selected.length > 0;
    cluster.eligiblePairs = [...selected];
    cluster.forecast = { baselineReduction: decision.baselineReduction, selectedReduction: decision.selectedReduction,
      trials: decision.trials, simulatedSteps: decision.simulatedSteps };
    for (const key of selected) this.pairs.get(key)!.eligible = true;
    if (!this.enabled) return true;
    for (const key of selected) {
      const state = this.pairs.get(key)!;
      state.state = "GHOSTED_LATCHED"; state.startStep = step; state.separatedSteps = 0;
      const observation = this.observations.get(key)!;
      this.record({ step, key, type: "ghost", gap: observation.gap, overlap: observation.overlap, separatedSteps: 0 });
    }
    return true;
  }

  private statistics(samples: readonly (readonly BodyObservation[])[]) {
    const component = samples[samples.length - 1]!;
    const mass = component.reduce((sum, b) => sum + b.mass, 0);
    const inertia = component.reduce((sum, b) => sum + b.inertia, 0);
    const motion = samples.map(motionObservation);
    return {
      energy: motion[motion.length - 1]!.energy,
      meanEnergy: motion.reduce((sum, m) => sum + m.energy, 0) / motion.length,
      relativeEnergy: motion[motion.length - 1]!.relativeEnergy,
      meanRelativeEnergy: motion.reduce((sum, m) => sum + m.relativeEnergy, 0) / motion.length,
      rmsRelativeSpeed: Math.sqrt(motion.reduce((sum, m) => sum + m.relativeSpeed2, 0) / motion.length),
      energyLimit: .5 * (mass * DEADLOCK.speed ** 2 + inertia * DEADLOCK.angularSpeed ** 2),
      rmsSpeed: Math.sqrt(motion.reduce((sum, m) => sum + m.speed2, 0) / motion.length),
      rmsAngularSpeed: Math.sqrt(motion.reduce((sum, m) => sum + m.angular2, 0) / motion.length),
      error: meanError(component),
      errorSlope: (meanError(component) - meanError(samples[0]!)) / Math.max(this.dt, (samples.length - 1) * this.dt)
    };
  }
}
