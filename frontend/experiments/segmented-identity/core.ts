import { CollisionPermissions, pairKey, DEADLOCK } from "./deadlock";
import type { PairObservation } from "./deadlock";
import { GroupForecast, FORECAST } from "./forecast";
import { projectHomeOnly, orientationBias } from "./home-response";
import type { ForecastRequest, ForecastBranch } from "./forecast";
import { add, sub, mul, dot, cross, length, rotate, transform, polygonContact,
  polygonContacts, polygonGap, circleContact } from "./math";
import type { Vec, Contact } from "./math";

export const FIXED_DT = 1 / 120;
export const PHYSICS = Object.freeze({ homeStiffness: 12, linearDamping: 3.2,
  angularDamping: 3.6, restitution: .08, navigatorRadius: 10,
  navigatorSpeedLimit: 720, solverIterations: 10, microsteps: 4,
  contactMargin: .16, solidTolerance: .3, restError: .002, restSpeed: .002, restAngularSpeed: .0001 });
export const navigatorTransfer = (speed: number): number =>
  PHYSICS.navigatorSpeedLimit * Math.tanh(Math.max(0, speed) / PHYSICS.navigatorSpeedLimit);

export type FragmentGeometry = Readonly<{
  id: string; vertices: readonly Vec[]; parts: readonly (readonly Vec[])[]; anchors: readonly Vec[];
  home: Vec; mass: number; inertia: number;
}>;
export type Body = {
  geometry: FragmentGeometry; position: Vec; angle: number; velocity: Vec; angularVelocity: number;
  navigatorContact: boolean; contacts: string[];
};
export type BodyFixture = { id: string; position?: Vec; angle?: number; velocity?: Vec; angularVelocity?: number };
export type PointerSample = { x: number; y: number; active?: boolean; elapsedSeconds?: number };
export type EnergyLedger = { before: number; home: number; damping: number; fragments: number;
  navigator: number; boundaries: number; permissions: number; after: number };

function bodyEnergy(b: Body): number {
  return .5 * b.geometry.mass * dot(b.velocity, b.velocity)
    + .5 * b.geometry.inertia * b.angularVelocity ** 2;
}
const pointVelocity = (b: Body, point: Vec): Vec => {
  const r = sub(point, b.position);
  return add(b.velocity, { x: -r.y * b.angularVelocity, y: r.x * b.angularVelocity });
};

type ForecastJob = { search: GroupForecast; poses: { id: string; position: Vec; angle: number; velocity: Vec; angularVelocity: number }[];
    navigator: { position: Vec; active: boolean }; permissions: string; potential: number; center: Vec; velocity: Vec; walls: string; validating: boolean };

export class PhysicsCore {
  readonly bodies: Body[];
  readonly permissions: CollisionPermissions;
  readonly navigator = { position: { x: -100, y: -100 }, velocity: { x: 0, y: 0 }, active: false };
  readonly width: number;
  readonly height: number;
  step = 0;
  ledger: EnergyLedger = { before: 0, home: 0, damping: 0, fragments: 0,
    navigator: 0, boundaries: 0, permissions: 0, after: 0 };
  maxEnergy = 0;
  private pairs: PairObservation[] = [];
  private recentContacts = new Map<string, Contact>();
  private polygonCache = new WeakMap<Body, { x: number; y: number; angle: number; vertices: Vec[]; parts: Vec[][] }>();

  private readonly predicting: boolean;
  private forecast: ForecastJob | null = null;
  private lastForecastKey = "";
  private navigatorOrigin: Vec = { x: -100, y: -100 };
  readonly forecastWork = { started: 0, cancelled: 0, completed: 0, simulatedSteps: 0 };

  constructor(geometry: readonly FragmentGeometry[], options: { width?: number; height?: number; deadlock?: boolean; eventHistoryLimit?: number; forecastPermissions?: CollisionPermissions } = {}) {
    this.width = options.width ?? 960; this.height = options.height ?? 620;
    this.bodies = geometry.map(g => ({ geometry: g, position: add(g.home, { x: this.width / 2, y: this.height / 2 }),
      angle: 0, velocity: { x: 0, y: 0 }, angularVelocity: 0, navigatorContact: false, contacts: [] }));
    this.predicting = !!options.forecastPermissions;
    this.permissions = options.forecastPermissions ?? new CollisionPermissions(FIXED_DT, options.deadlock ?? true, false, options.eventHistoryLimit);
    if (!this.predicting) this.inspectPairs();
  }
  energy(): number { return this.bodies.reduce((sum, b) => sum + bodyEnergy(b), 0); }
  home(b: Body): Vec { return add(b.geometry.home, { x: this.width / 2, y: this.height / 2 }); }
  vertices(b: Body): Vec[] {
    const cached = this.polygonCache.get(b);
    if (cached && cached.x === b.position.x && cached.y === b.position.y && cached.angle === b.angle) return cached.vertices;
    const vertices = transform(b.geometry.vertices, b.position, b.angle);
    this.polygonCache.set(b, { x: b.position.x, y: b.position.y, angle: b.angle, vertices,
      parts: b.geometry.parts.map(p => transform(p, b.position, b.angle)) });
    return vertices;
  }
  parts(b: Body): Vec[][] { this.vertices(b); return this.polygonCache.get(b)!.parts; }
  anchors(b: Body): { current: Vec; target: Vec }[] {
    return b.geometry.anchors.map(a => ({ current: add(b.position, rotate(a, b.angle)), target: add(this.home(b), a) }));
  }
  homeError(b: Body): number {
    return Math.sqrt(this.anchors(b).reduce((sum, a) => sum + dot(sub(a.target, a.current), sub(a.target, a.current)), 0)
      / b.geometry.anchors.length);
  }
  homeForce(b: Body): { acceleration: Vec; torque: number } {
    let force: Vec = { x: 0, y: 0 }, torque = 0;
    const coefficient = b.geometry.mass * PHYSICS.homeStiffness / b.geometry.anchors.length;
    const home = this.home(b), c = Math.cos(b.angle), s = Math.sin(b.angle);
    for (const a of b.geometry.anchors) {
      const r = { x: a.x * c - a.y * s, y: a.x * s + a.y * c };
      const f = { x: (home.x + a.x - (b.position.x + r.x)) * coefficient,
        y: (home.y + a.y - (b.position.y + r.y)) * coefficient };
      force = add(force, f); torque += cross(r, f);
    }
    return { acceleration: mul(force, 1 / b.geometry.mass),
      torque: torque + orientationBias(b.angle, b.geometry.inertia, PHYSICS.homeStiffness) };
  }
  loadFixture(fixtures: readonly BodyFixture[]): void {
    if (this.step !== 0) throw new Error("Fixtures require a fresh simulation");
    for (const fixture of fixtures) {
      const b = this.bodies.find(b => b.geometry.id === fixture.id);
      if (!b) throw new Error(`Unknown fragment ${fixture.id}`);
      if (fixture.position) b.position = { ...fixture.position };
      if (fixture.angle !== undefined) b.angle = fixture.angle;
      if (fixture.velocity) b.velocity = { ...fixture.velocity };
      if (fixture.angularVelocity !== undefined) b.angularVelocity = fixture.angularVelocity;
      if (![b.position.x, b.position.y, b.angle, b.velocity.x, b.velocity.y, b.angularVelocity].every(Number.isFinite))
        throw new Error("Non-finite fixture");
    }
    this.inspectPairs();
  }
  private impulse(b: Body, impulse: Vec, point: Vec): void {
    b.velocity = add(b.velocity, mul(impulse, 1 / b.geometry.mass));
    b.angularVelocity += cross(sub(point, b.position), impulse) / b.geometry.inertia;
  }
  private solveContact(a: Body, b: Body | null, c: Contact, externalVelocity: Vec = { x: 0, y: 0 }): void {
    const ra = sub(c.point, a.position), rb = b ? sub(c.point, b.position) : { x: 0, y: 0 };
    const relative = dot(sub(b ? pointVelocity(b, c.point) : externalVelocity, pointVelocity(a, c.point)), c.normal);
    if (relative >= 0) return;
    const inverseMass = 1 / a.geometry.mass + (b ? 1 / b.geometry.mass : 0)
      + cross(ra, c.normal) ** 2 / a.geometry.inertia + (b ? cross(rb, c.normal) ** 2 / b.geometry.inertia : 0);
    const j = -(1 + PHYSICS.restitution) * relative / inverseMass;
    this.impulse(a, mul(c.normal, -j), c.point);
    if (b) this.impulse(b, mul(c.normal, j), c.point);
  }
  private solveFragments(): void {
    for (let i = 0; i < this.bodies.length; i++) for (let j = i + 1; j < this.bodies.length; j++) {
      const a = this.bodies[i]!, b = this.bodies[j]!, key = pairKey(a.geometry.id, b.geometry.id);
      if (this.permissions.ghosted(key)) continue;
      const c = polygonContact(this.vertices(a), this.vertices(b), this.parts(a), this.parts(b));
      if (!c) continue;
      this.recentContacts.set(key, c);
      this.solveContact(a, b, c);
      // Position-only geometric constraint projection. Never a velocity bias.
      // A ghosted pair never enters either this projection or impulse solver.
      const total = a.geometry.mass + b.geometry.mass;
      const correction = Math.max(0, c.depth - .00001);
      if (correction > 0) {
        a.position = sub(a.position, mul(c.normal, correction * b.geometry.mass / total));
        b.position = add(b.position, mul(c.normal, correction * a.geometry.mass / total));
      }
    }
  }
  private solveNavigator(): void {
    if (!this.navigator.active) return;
    for (const b of this.bodies) {
      const c = circleContact(this.vertices(b), this.navigator.position, PHYSICS.navigatorRadius);
      if (!c) continue;
      b.navigatorContact = true;
      this.solveContact(b, null, c, this.navigator.velocity);
      const correction = mul(c.normal, -(Math.max(0, c.depth) + .00001));
      const vertices = this.vertices(b);
      let allowed = 1;
      for (const p of vertices) {
        if (correction.x > 0) allowed = Math.min(allowed, (this.width - p.x) / correction.x);
        if (correction.x < 0) allowed = Math.min(allowed, -p.x / correction.x);
        if (correction.y > 0) allowed = Math.min(allowed, (this.height - p.y) / correction.y);
        if (correction.y < 0) allowed = Math.min(allowed, -p.y / correction.y);
      }
      allowed = Math.max(0, Math.min(1, allowed));
      b.position = add(b.position, mul(correction, allowed));
      // A prescribed pointer position can request an impossible squeeze against
      // a wall. The navigator is constrained by that contact rather than letting
      // a fragment escape the stage. Neither body velocity nor ghost permissions
      // are changed by this geometric constraint.
      if (allowed < 1) this.navigator.position = sub(this.navigator.position, mul(correction, 1 - allowed));
    }
  }
  private solveBoundaries(): void {
    for (const b of this.bodies) {
      for (const wall of [0, 1, 2, 3]) {
        const vertices = this.vertices(b);
        const value = (p: Vec) => wall === 0 ? -p.x : wall === 1 ? p.x - this.width
          : wall === 2 ? -p.y : p.y - this.height;
        const depth = Math.max(...vertices.map(value));
        if (depth <= 0) continue;
        const points = vertices.filter(p => depth - value(p) < .05);
        const point = mul(points.reduce(add, { x: 0, y: 0 }), 1 / points.length);
        const normal = [{ x: -1, y: 0 }, { x: 1, y: 0 }, { x: 0, y: -1 }, { x: 0, y: 1 }][wall]!;
        this.solveContact(b, null, { normal, depth, point });
        b.position = sub(b.position, mul(normal, depth + .00001));
      }
    }
  }
  private account(channel: keyof Omit<EnergyLedger, "before" | "after">, operation: () => void): void {
    const before = this.energy(); operation(); this.ledger[channel] += this.energy() - before;
  }
  stepFixed(count = 1, pointer?: PointerSample, forecastBudget: number = FORECAST.workPerTick): void {
    if (!Number.isInteger(forecastBudget) || forecastBudget < 0) throw new Error("Invalid forecast budget");
    if (!Number.isInteger(count) || count < 0 || count > 100_000) throw new Error("Invalid fixed-step count");
    for (let n = 0; n < count; n++) this.tick(n === 0 ? pointer : undefined, forecastBudget);
  }
  private tick(pointer: PointerSample | undefined, forecastBudget: number): void {
    let end = this.navigator.position;
    if (pointer) {
      if (![pointer.x, pointer.y].every(Number.isFinite)) throw new Error("Invalid pointer sample");
      end = { x: Math.max(0, Math.min(this.width, pointer.x)), y: Math.max(0, Math.min(this.height, pointer.y)) };
      // Entry establishes presence, without interpreting a pointerenter as a
      // thousands-of-pixels strike from the inactive sentinel position.
      if (!this.navigator.active) this.navigator.position = end;
      this.navigator.active = pointer.active ?? true;
    }
    const delta = sub(end, this.navigator.position);
    this.navigatorOrigin = { ...this.navigator.position };
    const observedInterval = pointer?.elapsedSeconds ?? FIXED_DT;
    if (!Number.isFinite(observedInterval) || observedInterval <= 0) throw new Error("Invalid pointer observation interval");
    const rawSpeed = length(delta) / observedInterval;
    this.navigator.velocity = mul(delta, navigatorTransfer(rawSpeed) / (length(delta) || 1));
    // Fixed 120Hz external clock. Deterministic internal subdivisions resolve a
    // swept navigator at <=2px intervals, and body motion at <=1/480s.
    const subdivisions = Math.max(PHYSICS.microsteps, this.navigator.active ? Math.ceil(length(delta) / 2) : 0);
    const dt = FIXED_DT / subdivisions;
    const origin = this.navigator.position;
    this.recentContacts.clear();
    this.bodies.forEach(b => { b.navigatorContact = false; b.contacts = []; });
    this.ledger = { before: this.energy(), home: 0, damping: 0, fragments: 0,
      navigator: 0, boundaries: 0, permissions: 0, after: 0 };
    for (let s = 1; s <= subdivisions; s++) {
      const desired = add(origin, mul(delta, s / subdivisions));
      const travel = sub(desired, this.navigator.position);
      this.navigator.position = add(this.navigator.position, mul(travel, Math.min(1, 2 / (length(travel) || 1))));
      this.account("home", () => {
        for (const b of this.bodies) {
          const f = this.homeForce(b);
          b.velocity = add(b.velocity, mul(f.acceleration, dt));
          b.angularVelocity += f.torque / b.geometry.inertia * dt;
        }
      });
      this.account("damping", () => {
        this.bodies.forEach(b => {
          b.velocity = mul(b.velocity, Math.exp(-PHYSICS.linearDamping * dt));
          b.angularVelocity *= Math.exp(-PHYSICS.angularDamping * dt);
          b.position = add(b.position, mul(b.velocity, dt)); b.angle += b.angularVelocity * dt;
        });
      });
      for (let iteration = 0; iteration < PHYSICS.solverIterations; iteration++) {
        const poses = this.bodies.map(b => b.position);
        this.account("fragments", () => this.solveFragments());
        this.account("boundaries", () => this.solveBoundaries());
        this.account("navigator", () => this.solveNavigator());
        if (this.bodies.every((b, i) => b.position === poses[i])) break;
      }
    }
    this.step++;
    if (this.predicting) this.inspectPredictionPairs(); else this.inspectPairs();
    this.account("permissions", () => this.permissions.observe(this.step, this.predicting ? [] : this.bodies.map(b => ({
      id: b.geometry.id, mass: b.geometry.mass, inertia: b.geometry.inertia, speed: length(b.velocity), angularSpeed: Math.abs(b.angularVelocity),
      velocityX: b.velocity.x, velocityY: b.velocity.y, energy: bodyEnergy(b), error: this.homeError(b), homeRequest: length(this.homeForce(b).acceleration)
        + Math.abs(this.homeForce(b).torque) / b.geometry.inertia, navigatorContact: b.navigatorContact
    })), this.pairs));
    if (!this.predicting) this.account("permissions", () => this.advanceForecast(forecastBudget));
    this.ledger.after = this.energy(); this.maxEnergy = Math.max(this.maxEnergy, this.ledger.after);
  }
  private homeMetrics(members: readonly string[], source = this.bodies) {
    const bodies = source.filter(b => members.includes(b.geometry.id));
    const mass = bodies.reduce((sum, b) => sum + b.geometry.mass, 0);
    const rows = bodies.map(b => ({ b, anchors: this.anchors(b) }));
    const meanError = mul(rows.reduce((sum, { b, anchors }) => add(sum,
      mul(anchors.reduce((sum, a) => add(sum, sub(a.target, a.current)), { x: 0, y: 0 }),
        b.geometry.mass / anchors.length)), { x: 0, y: 0 }), 1 / mass);
    const meanVelocity = mul(bodies.reduce((sum, b) => add(sum, mul(b.velocity, b.geometry.mass)), { x: 0, y: 0 }), 1 / mass);
    let potential = 0, scale = 0, power = 0, relativeEnergy = 0, maximumRelativeSpeed = 0;
    for (const { b, anchors } of rows) {
      let force: Vec = { x: 0, y: 0 }, torque = 0;
      const coefficient = b.geometry.mass * PHYSICS.homeStiffness / anchors.length;
      for (const a of anchors) {
        const error = sub(sub(a.target, a.current), meanError), f = mul(error, coefficient);
        potential += .5 * coefficient * dot(error, error);
        force = add(force, f); torque += cross(sub(a.current, b.position), f);
      }
      const velocity = sub(b.velocity, meanVelocity);
      scale += (dot(force, force) / b.geometry.mass + torque ** 2 / b.geometry.inertia) / (2 * PHYSICS.homeStiffness);
      power += dot(force, velocity) + torque * b.angularVelocity;
      relativeEnergy += .5 * b.geometry.mass * dot(velocity, velocity) + .5 * b.geometry.inertia * b.angularVelocity ** 2;
      maximumRelativeSpeed = Math.max(maximumRelativeSpeed, length(velocity));
    }
    // Remove only common translation from the home objective. A fragment pair
    // cannot block that mode; it can block relative positions and orientations.
    // These are observation gradients, NEVER replacement forces for physics.
    return { potential, scale: Math.min(potential, scale), power, relativeEnergy, maximumRelativeSpeed };
  }
  private potential(members: readonly string[]): number { return this.homeMetrics(members).potential; }
  private responseScale(members: readonly string[]): number { return this.homeMetrics(members).scale; }
  private projectedPotential(members: readonly string[]): number | undefined {
    const bodies = this.bodies.filter(b => members.includes(b.geometry.id));
    const projected = projectHomeOnly(bodies, bodies.map(b => this.home(b)), { steps: FORECAST.homeOnlySteps,
      dt: FIXED_DT, stiffness: PHYSICS.homeStiffness, damping: PHYSICS.linearDamping,
      angularDamping: PHYSICS.angularDamping, width: this.width, height: this.height,
      navigator: { active: this.navigator.active, position: this.navigator.position, radius: PHYSICS.navigatorRadius } });
    return projected ? this.homeMetrics(members, projected).potential : undefined;
  }
  private residualBlocks(members: readonly string[]): number {
    this.inspectPairs();
    const contacts = this.pairs.filter(p => p.contact && !this.permissions.ghosted(p.key)
      && members.includes(p.a) && members.includes(p.b));
    const visited = new Set<string>(); let blocked = 0;
    for (const id of members) {
      if (visited.has(id)) continue;
      const component: string[] = [], queue = [id];
      while (queue.length) {
        const id = queue.pop()!; if (visited.has(id)) continue;
        visited.add(id); component.push(id);
        for (const p of contacts) if (p.a === id) queue.push(p.b); else if (p.b === id) queue.push(p.a);
      }
      if (!contacts.some(p => component.includes(p.a) && p.blocking > .1)) continue;
      const bodies = this.bodies.filter(b => component.includes(b.geometry.id));
      const mass = bodies.reduce((sum, b) => sum + b.geometry.mass, 0);
      const inertia = bodies.reduce((sum, b) => sum + b.geometry.inertia, 0);
      const metrics = this.homeMetrics(component);
      const limit = .5 * (mass * DEADLOCK.speed ** 2 + inertia * DEADLOCK.angularSpeed ** 2);
      if (metrics.relativeEnergy < 4 * limit && bodies.some(b => this.homeError(b) > DEADLOCK.error)
        && metrics.maximumRelativeSpeed < DEADLOCK.instantaneousMultiplier * DEADLOCK.speed
        && bodies.every(b => Math.abs(b.angularVelocity) < DEADLOCK.instantaneousMultiplier * DEADLOCK.angularSpeed)
        && metrics.scale > 1e-12
        && metrics.power < metrics.scale * FORECAST.baselineProgress / (FORECAST.horizonSteps * FIXED_DT)) blocked++;
    }
    return blocked;
  }
  private center(members: readonly string[]): Vec {
    const bodies = this.bodies.filter(b => members.includes(b.geometry.id));
    return mul(bodies.reduce((sum, b) => add(sum, mul(b.position, b.geometry.mass)), { x: 0, y: 0 }),
      1 / bodies.reduce((sum, b) => sum + b.geometry.mass, 0));
  }
  private wallSignature(members: readonly string[]): string {
    return this.bodies.filter(b => members.includes(b.geometry.id)).map(b => {
      const vertices = this.vertices(b);
      return `${b.geometry.id}:${vertices.some(p => p.x < 1)}:${vertices.some(p => p.x > this.width - 1)}:${vertices.some(p => p.y < 1)}:${vertices.some(p => p.y > this.height - 1)}`;
    }).join("|");
  }
  private permissionSignature(): string {
    return this.permissions.snapshot().filter(p => p.state === "GHOSTED_LATCHED").map(p => p.key).sort().join("|");
  }
  private forecastSafe(): boolean {
    return this.energy() < 1e7 && this.permissions.failures.length === 0 && this.ledger.permissions === 0
      && Math.max(this.ledger.damping, this.ledger.fragments, this.ledger.boundaries) <= 1e-6
      && this.bodies.every(b => [b.position.x, b.position.y, b.angle, b.velocity.x, b.velocity.y,
        b.angularVelocity].every(Number.isFinite)
        && this.vertices(b).every(p => p.x >= -1e-4 && p.x <= this.width + 1e-4 && p.y >= -1e-4 && p.y <= this.height + 1e-4)
        && (!this.navigator.active || (circleContact(this.vertices(b), this.navigator.position, PHYSICS.navigatorRadius)?.depth ?? 0) <= .02));
  }
  private startForecast(request: ForecastRequest, selected?: readonly string[]): NonNullable<PhysicsCore["forecast"]> {
    // Every alternative starts from the SAME frozen world, including latches.
    const fixtures = this.bodies.map(b => ({ id: b.geometry.id, position: { ...b.position }, angle: b.angle,
      velocity: { ...b.velocity }, angularVelocity: b.angularVelocity }));
    const states = this.permissions.snapshot(), step = this.step;
    const navigator = { position: { ...this.navigator.position }, active: this.navigator.active };
    const create = (keys: readonly string[]): ForecastBranch => {
      const copy = new PhysicsCore(this.bodies.map(b => b.geometry), { width: this.width, height: this.height,
        forecastPermissions: CollisionPermissions.forPrediction(FIXED_DT, states, keys, step) });
      for (const fixture of fixtures) {
        const b = copy.bodies.find(b => b.geometry.id === fixture.id)!;
        b.position = { ...fixture.position }; b.angle = fixture.angle;
        b.velocity = { ...fixture.velocity }; b.angularVelocity = fixture.angularVelocity;
      }
      copy.step = step; copy.navigator.position = { ...navigator.position }; copy.navigator.active = navigator.active;
      return { advance: () => copy.stepFixed(), sample: (members, final) => ({ potential: copy.potential(members), safe: copy.forecastSafe(),
        residualBlocks: final && keys.length ? copy.residualBlocks(members) : undefined,
        futurePotential: final && keys.length ? copy.projectedPotential(members) : undefined }) };
    };
    this.forecastWork.started++;
    return { search: new GroupForecast({ ...request, members: [...request.members], candidates: [...request.candidates], present: request.present ? [...request.present] : undefined, responseScale: this.responseScale(request.members) }, create, selected ? [[], [...selected]] : undefined),
      poses: fixtures, navigator,
      permissions: this.permissionSignature(), potential: this.potential(request.members),
      center: this.center(request.members), velocity: this.meanVelocity(request.members),
      walls: this.wallSignature(request.members), validating: selected !== undefined };
  }
  private meanVelocity(members: readonly string[]): Vec {
    const bodies = this.bodies.filter(b => members.includes(b.geometry.id));
    const mass = bodies.reduce((sum, b) => sum + b.geometry.mass, 0);
    return mul(bodies.reduce((sum, b) => add(sum, mul(b.velocity, b.geometry.mass)), { x: 0, y: 0 }), 1 / mass);
  }
  private navigatorRelevant(members: readonly string[], points: readonly Vec[]): boolean {
    return this.bodies.filter(b => members.includes(b.geometry.id)).some(b => {
      const radius = Math.max(...b.geometry.vertices.map(length)) + PHYSICS.navigatorRadius
        + FORECAST.maximumTranslationDrift + PHYSICS.navigatorSpeedLimit * FORECAST.horizonSteps * FIXED_DT;
      return points.some((point, index) => {
        const end = points[index + 1] ?? point, delta = sub(end, point);
        const t = Math.max(0, Math.min(1, dot(sub(b.position, point), delta) / (dot(delta, delta) || 1)));
        return length(sub(b.position, add(point, mul(delta, t)))) <= radius;
      });
    });
  }
  private advanceForecast(budget: number): void {
    if (this.forecast) {
      const job = this.forecast, request = job.search.request;
      const current = this.permissions.requests.find(r => r.key === request.key);
      const translation = sub(this.center(request.members), job.center);
      const progress = (job.potential - this.potential(request.members)) / Math.max(1e-12, request.responseScale ?? job.potential);
      const velocity = this.meanVelocity(request.members);
      const stale = !current || request.candidates.length !== current.candidates.length || request.candidates.some(key => !current.candidates.includes(key))
        || this.step - request.step > FORECAST.maximumAgeSteps || job.permissions !== this.permissionSignature()
        || job.walls !== this.wallSignature(request.members) || length(translation) > FORECAST.maximumTranslationDrift
        || ((job.navigator.active !== this.navigator.active || length(sub(job.navigator.position, this.navigator.position)) > .01)
          && this.navigatorRelevant(request.members, [job.navigator.position, this.navigatorOrigin, this.navigator.position]))
        || progress >= FORECAST.baselineProgress
        || job.poses.some(p => { const b = this.bodies.find(b => b.geometry.id === p.id)!;
          const member = request.members.includes(p.id);
          const nearby = member || this.bodies.some(other => request.members.includes(other.geometry.id)
            && Math.min(length(sub(p.position, other.position)), length(sub(b.position, other.position)))
              < Math.max(...b.geometry.vertices.map(length)) + Math.max(...other.geometry.vertices.map(length))
                + (length(p.velocity) + length(b.velocity)) * FORECAST.maximumAgeSteps * FIXED_DT + FORECAST.maximumTranslationDrift);
          // Outside motion can invalidate a candidate ranking, but restarting
          // that search continually starves a calm island. The fresh baseline
          // and selected batch own final authority and monitor those bodies.
          if (!nearby || !member && !job.validating) return false;
          return length(sub(sub(b.position, p.position), member ? translation : { x: 0, y: 0 })) > FORECAST.maximumPositionDrift
            || Math.abs(b.angle - p.angle) > FORECAST.maximumAngleDrift
            || length(sub(sub(b.velocity, member ? velocity : { x: 0, y: 0 }),
              sub(p.velocity, member ? job.velocity : { x: 0, y: 0 }))) > FORECAST.maximumVelocityDrift
            || Math.abs(b.angularVelocity - p.angularVelocity) > FORECAST.maximumAngularVelocityDrift; });
      if (stale) {
        if (current && progress >= FORECAST.baselineProgress) this.permissions.deferPrediction(this.step, current.key);
        this.forecast = null; this.forecastWork.cancelled++;
      }
    }
    if (budget <= 0) return;
    if (!this.forecast && this.permissions.requests.length) {
      const requests = [...this.permissions.requests].sort((a, b) => a.key.localeCompare(b.key));
      const request = requests.find(r => r.key > this.lastForecastKey) ?? requests[0]!;
      this.lastForecastKey = request.key;
      const metrics = this.homeMetrics(request.members);
      if (metrics.potential <= 1e-8 || metrics.scale <= 1e-12) this.permissions.deferPrediction(this.step, request.key);
      else this.forecast = this.startForecast(request);
    }
    if (!this.forecast) return;
    const before = this.forecast.search.simulatedSteps;
    const decision = this.forecast.search.advance(budget);
    this.forecastWork.simulatedSteps += this.forecast.search.simulatedSteps - before;
    if (!decision) return;
    if (decision.selected.length && !this.forecast.validating) {
      // Re-run just the baseline and selected batch from the current world.
      // Prepared approaching contacts wait for all physical edges to exist.
      const states = this.permissions.snapshot();
      if (!decision.selected.every(key => states.some(p => p.key === key && p.state === "SOLID"
        && p.contactSteps * FIXED_DT >= DEADLOCK.contactSeconds))) return;
      const current = this.permissions.requests.find(r => r.key === decision.request.key);
      if (current) this.forecast = this.startForecast(current, decision.selected);
      return;
    }
    if (this.permissions.acceptPrediction(this.step, decision)) {
      this.forecastWork.completed++; this.forecast = null;
    }
  }
  private inspectPredictionPairs(): void {
    // Shadow worlds only need separation observations for existing latches.
    // Solid contacts are already handled by the identical impulse solver.
    this.pairs = this.permissions.snapshot().filter(p => p.state === "GHOSTED_LATCHED").map(p => {
      const a = this.bodies.find(b => b.geometry.id === p.a)!, b = this.bodies.find(b => b.geometry.id === p.b)!;
      const av = this.vertices(a), bv = this.vertices(b);
      const contact = polygonContact(av, bv, this.parts(a), this.parts(b));
      const gap = contact ? 0 : polygonGap(av, bv);
      return { key: p.key, a: p.a, b: p.b, contact: !!contact || gap < PHYSICS.contactMargin,
        overlap: contact?.depth ?? 0, gap, blocking: 0 };
    });
  }
  private inspectPairs(): void {
    this.pairs = [];
    const requests = this.bodies.map(b => this.homeForce(b));
    const pointRequest = (index: number, point: Vec): Vec => {
      const body = this.bodies[index]!, request = requests[index]!;
      const r = sub(point, body.position), alpha = request.torque / body.geometry.inertia;
      return add(request.acceleration, { x: -alpha * r.y, y: alpha * r.x });
    };
    for (let i = 0; i < this.bodies.length; i++) for (let j = i + 1; j < this.bodies.length; j++) {
      const a = this.bodies[i]!, b = this.bodies[j]!, key = pairKey(a.geometry.id, b.geometry.id);
      const av = this.vertices(a), bv = this.vertices(b);
      const contacts = polygonContacts(av, bv, this.parts(a), this.parts(b));
      const c = contacts.reduce<Contact | null>((deepest, c) => !deepest || c.depth > deepest.depth ? c : deepest, null);
      const gap = c ? 0 : polygonGap(av, bv), previous = this.recentContacts.get(key);
      if (previous) contacts.push(previous);
      const contact = !!c || !!previous || gap < PHYSICS.contactMargin;
      if (contact) { a.contacts.push(b.geometry.id); b.contacts.push(a.geometry.id); }
      this.pairs.push({ key, a: a.geometry.id, b: b.geometry.id, contact, overlap: c?.depth ?? 0, gap,
        blocking: contacts.length ? Math.max(...contacts.map(c =>
          dot(sub(pointRequest(i, c.point), pointRequest(j, c.point)), c.normal))) : 0 });
    }
  }
  isRest(): boolean {
    return this.bodies.every(b => this.homeError(b) < PHYSICS.restError && length(b.velocity) < PHYSICS.restSpeed
      && Math.abs(b.angularVelocity) < PHYSICS.restAngularSpeed
      && this.vertices(b).every(p => p.x >= -1e-4 && p.x <= this.width + 1e-4 && p.y >= -1e-4 && p.y <= this.height + 1e-4))
      && this.pairs.every(p => p.overlap < 1e-4 && !this.permissions.ghosted(p.key));
  }
  getSnapshot() {
    const permissions = this.permissions.snapshot();
    const fragments = this.bodies.map(b => ({ id: b.geometry.id, position: { ...b.position }, rotation: b.angle,
      velocity: { ...b.velocity }, angularVelocity: b.angularVelocity, kineticEnergy: bodyEnergy(b),
      homeAnchorError: this.homeError(b), contacts: [...b.contacts], navigatorContact: b.navigatorContact,
      navigatorOverlap: this.navigator.active ? circleContact(this.vertices(b), this.navigator.position, PHYSICS.navigatorRadius)?.depth ?? 0 : 0,
      vertices: this.vertices(b).map(p => ({ ...p })), anchors: this.anchors(b),
      runtimeState: this.homeError(b) < PHYSICS.restError && length(b.velocity) < PHYSICS.restSpeed
        && Math.abs(b.angularVelocity) < PHYSICS.restAngularSpeed ? "REST" : "MOVING" }));
    const pairs = this.pairs.map(p => {
      const permission = permissions.find(s => s.key === p.key);
      return { ...p, state: "SOLID" as "SOLID" | "GHOSTED_LATCHED", eligible: false,
        contactSteps: 0, startStep: null as number | null, separatedSteps: 0,
        rearmMargin: p.overlap === 0 && p.gap >= DEADLOCK.rearmMargin,
        ...permission, ghostStartTime: permission?.startStep === null || permission?.startStep === undefined
        ? null : permission.startStep * FIXED_DT, physicalContact: p.contact && permission?.state !== "GHOSTED_LATCHED" };
    });
    const contained = fragments.every(b => b.vertices.every(p => p.x >= -1e-4 && p.x <= this.width + 1e-4
      && p.y >= -1e-4 && p.y <= this.height + 1e-4));
    const finite = fragments.every(b => [b.position.x, b.position.y, b.rotation, b.velocity.x, b.velocity.y,
      b.angularVelocity, b.kineticEnergy, b.homeAnchorError].every(Number.isFinite));
    const rest = this.isRest();
    return { step: this.step, time: this.step * FIXED_DT, width: this.width, height: this.height,
      fragments, pairs, clusters: this.permissions.clusters.map(c => ({ ...c, members: [...c.members],
        blockingPairs: [...c.blockingPairs], eligiblePairs: [...c.eligiblePairs], forecast: c.forecast ? { ...c.forecast } : null })),
      forecastWork: { ...this.forecastWork }, events: this.permissions.events.map(e => ({ ...e })), eventCount: this.permissions.eventCount, failures: [...this.permissions.failures],
      navigator: { ...this.navigator, position: { ...this.navigator.position }, velocity: { ...this.navigator.velocity } },
      energy: this.energy(), maxEnergy: this.maxEnergy, ledger: { ...this.ledger }, rest, contained, finite };
  }
}
export type Snapshot = ReturnType<PhysicsCore["getSnapshot"]>;
