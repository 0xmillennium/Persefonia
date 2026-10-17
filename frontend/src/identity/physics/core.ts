import type { FragmentGeometry, Body, BodyFixture, PointerSample } from './model';
import { CollisionPermissions, pairKey, DEADLOCK } from './deadlock';
import type { PairObservation } from './deadlock';
import { GroupForecast, FORECAST } from './forecast';
import { orientationBias } from './home-response';
import { PointerStroke, POINTER_STROKE } from './pointer-stroke';
import type { ForecastRequest, ForecastBranch } from './forecast';
import {
  add,
  sub,
  mul,
  dot,
  cross,
  length,
  rotate,
  transform,
  polygonContact,
  polygonContacts,
  polygonGap,
  circleContact,
  circleCast,
  compoundSeparation,
  polygonBounds,
} from './math';
import type { Vec, Contact } from './math';

export const FIXED_DT = 1 / 120;
export const PHYSICS = Object.freeze({
  homeStiffness: 12,
  linearDamping: 3.2,
  angularDamping: 3.6,
  restitution: 0.08,
  navigatorRadius: 10,
  navigatorSpeedLimit: 720,
  solverIterations: 10,
  recoveryIterations: 20,
  microsteps: 4,
  contactMargin: 0.16,
  solidTolerance: 0.3,
  restError: 0.002,
  restSpeed: 0.002,
  restAngularSpeed: 0.0001,
});
export const navigatorTransfer = (
  speed: number,
  limit: number = PHYSICS.navigatorSpeedLimit,
): number => limit * Math.tanh(Math.max(0, speed) / limit);
export type PhysicsResponse = Readonly<{
  homeStiffness: number;
  linearDamping: number;
  angularDamping: number;
  restitution: number;
  navigatorSpeedLimit: number;
}>;

export type { FragmentGeometry, Body, BodyFixture, PointerSample } from './model';
export type EnergyLedger = {
  before: number;
  home: number;
  damping: number;
  fragments: number;
  navigator: number;
  boundaries: number;
  permissions: number;
  after: number;
};

function bodyEnergy(b: Body): number {
  return (
    0.5 * b.geometry.mass * dot(b.velocity, b.velocity) +
    0.5 * b.geometry.inertia * b.angularVelocity ** 2
  );
}
const pointVelocity = (b: Body, point: Vec): Vec => {
  const r = sub(point, b.position);
  return add(b.velocity, { x: -r.y * b.angularVelocity, y: r.x * b.angularVelocity });
};

const partIndices = new WeakMap<FragmentGeometry, readonly (readonly number[])[] | null>();
function transformedParts(
  geometry: FragmentGeometry,
  vertices: Vec[],
  position: Vec,
  angle: number,
): Vec[][] {
  let indices = partIndices.get(geometry);
  if (indices === undefined) {
    const mapped = geometry.parts.map((part) =>
      part.map((point) => geometry.vertices.indexOf(point)),
    );
    indices = mapped.some((part) => part.includes(-1)) ? null : mapped;
    partIndices.set(geometry, indices);
  }
  // Decomposition reuses contour vertices. Transform each unique point once;
  // the same numeric pose serves every triangle instead of allocating and
  // rotating duplicate points. Independently authored parts retain the old path.
  return indices
    ? indices.map((part) =>
        part.length === vertices.length && part.every((index, i) => index === i)
          ? vertices
          : part.map((index) => vertices[index]!),
      )
    : geometry.parts.map((part) => transform(part, position, angle));
}

type ForecastJob = {
  search: GroupForecast;
  poses: { id: string; position: Vec; angle: number; velocity: Vec; angularVelocity: number }[];
  navigator: { position: Vec; active: boolean };
  permissions: string;
  potential: number;
  center: Vec;
  velocity: Vec;
  walls: string;
  validating: boolean;
};

export class PhysicsCore {
  readonly response: PhysicsResponse;
  readonly bodies: readonly Body[];
  readonly permissions: CollisionPermissions;
  readonly navigator = { position: { x: -100, y: -100 }, velocity: { x: 0, y: 0 }, active: false };
  readonly width: number;
  readonly height: number;
  step = 0;
  ledger: EnergyLedger = {
    before: 0,
    home: 0,
    damping: 0,
    fragments: 0,
    navigator: 0,
    boundaries: 0,
    permissions: 0,
    after: 0,
  };
  maxEnergy = 0;
  private pairs: PairObservation[] = [];
  private recentContacts = new Map<string, Contact>();
  private polygonCache = new WeakMap<
    Body,
    { x: number; y: number; angle: number; vertices: Vec[]; parts: Vec[][] }
  >();

  // One entry per body pair, retaining only its last immutable world contours.
  private readonly contactCache = new Map<
    string,
    { a: readonly Vec[]; b: readonly Vec[]; contact: Contact | null }
  >();

  private readonly predicting: boolean;
  private forecast: ForecastJob | null = null;
  private lastForecastKey = '';
  private navigatorOrigin: Vec = { x: -100, y: -100 };
  private navigatorPath: readonly Vec[] = [];
  readonly forecastWork = { started: 0, cancelled: 0, completed: 0, simulatedSteps: 0 };

  constructor(
    geometry: readonly FragmentGeometry[],
    options: {
      width?: number;
      height?: number;
      response?: Partial<PhysicsResponse>;
      deadlock?: boolean;
      eventHistoryLimit?: number;
      forecastPermissions?: CollisionPermissions;
    } = {},
  ) {
    this.response = Object.freeze({ ...PHYSICS, ...options.response });
    if (
      ![this.response.homeStiffness, this.response.navigatorSpeedLimit].every(
        (v) => Number.isFinite(v) && v > 0,
      ) ||
      ![this.response.linearDamping, this.response.angularDamping, this.response.restitution].every(
        (v) => Number.isFinite(v) && v >= 0,
      ) ||
      this.response.restitution > 1
    )
      throw new Error('Invalid physics response');
    this.width = options.width ?? 960;
    this.height = options.height ?? 620;
    if (![this.width, this.height].every((v) => Number.isFinite(v) && v > 0))
      throw new Error('Invalid stage dimensions');
    if (new Set(geometry.map((g) => g.id)).size !== geometry.length)
      throw new Error('Duplicate fragment IDs');
    if (geometry.some((g) => ![g.mass, g.inertia].every((v) => Number.isFinite(v) && v > 0)))
      throw new Error('Invalid fragment mass or inertia');
    this.bodies = geometry.map((g) => ({
      geometry: g,
      position: add(g.home, { x: this.width / 2, y: this.height / 2 }),
      angle: 0,
      velocity: { x: 0, y: 0 },
      angularVelocity: 0,
      navigatorContact: false,
      contacts: [],
    }));
    this.predicting = !!options.forecastPermissions;
    this.permissions =
      options.forecastPermissions ??
      new CollisionPermissions(
        FIXED_DT,
        options.deadlock ?? true,
        false,
        options.eventHistoryLimit,
      );
    if (!this.predicting) this.inspectPairs();
  }
  energy(): number {
    return this.bodies.reduce((sum, b) => sum + bodyEnergy(b), 0);
  }
  home(b: Body): Vec {
    return add(b.geometry.home, { x: this.width / 2, y: this.height / 2 });
  }
  vertices(b: Body): readonly Vec[] {
    const cached = this.polygonCache.get(b);
    if (
      cached &&
      cached.x === b.position.x &&
      cached.y === b.position.y &&
      cached.angle === b.angle
    )
      return cached.vertices;
    const vertices = transform(b.geometry.vertices, b.position, b.angle);
    this.polygonCache.set(b, {
      x: b.position.x,
      y: b.position.y,
      angle: b.angle,
      vertices,
      parts: transformedParts(b.geometry, vertices, b.position, b.angle),
    });
    return vertices;
  }
  parts(b: Body): readonly (readonly Vec[])[] {
    this.vertices(b);
    return this.polygonCache.get(b)!.parts;
  }
  anchors(b: Body): { current: Vec; target: Vec }[] {
    return b.geometry.anchors.map((a) => ({
      current: add(b.position, rotate(a, b.angle)),
      target: add(this.home(b), a),
    }));
  }
  homeError(b: Body): number {
    return Math.sqrt(
      this.anchors(b).reduce(
        (sum, a) => sum + dot(sub(a.target, a.current), sub(a.target, a.current)),
        0,
      ) / b.geometry.anchors.length,
    );
  }
  homeForce(b: Body): { acceleration: Vec; torque: number } {
    let force: Vec = { x: 0, y: 0 },
      torque = 0;
    const coefficient = (b.geometry.mass * this.response.homeStiffness) / b.geometry.anchors.length;
    const home = this.home(b),
      c = Math.cos(b.angle),
      s = Math.sin(b.angle);
    for (const a of b.geometry.anchors) {
      const r = { x: a.x * c - a.y * s, y: a.x * s + a.y * c };
      const f = {
        x: (home.x + a.x - (b.position.x + r.x)) * coefficient,
        y: (home.y + a.y - (b.position.y + r.y)) * coefficient,
      };
      force = add(force, f);
      torque += cross(r, f);
    }
    return {
      acceleration: mul(force, 1 / b.geometry.mass),
      torque: torque + orientationBias(b.angle, b.geometry.inertia, this.response.homeStiffness),
    };
  }
  loadFixture(fixtures: readonly BodyFixture[]): void {
    if (this.step !== 0) throw new Error('Fixtures require a fresh simulation');
    if (new Set(fixtures.map((f) => f.id)).size !== fixtures.length)
      throw new Error('Duplicate fixture IDs');
    const prepared = fixtures.map((fixture) => {
      const b = this.bodies.find((b) => b.geometry.id === fixture.id);
      if (!b) throw new Error(`Unknown fragment ${fixture.id}`);
      const position = { ...(fixture.position ?? b.position) },
        angle = fixture.angle ?? b.angle;
      const velocity = { ...(fixture.velocity ?? b.velocity) },
        angularVelocity = fixture.angularVelocity ?? b.angularVelocity;
      if (
        ![position.x, position.y, angle, velocity.x, velocity.y, angularVelocity].every(
          Number.isFinite,
        )
      )
        throw new Error('Non-finite fixture');
      return { b, position, angle, velocity, angularVelocity };
    });
    for (const { b, position, angle, velocity, angularVelocity } of prepared) {
      b.position = position;
      b.angle = angle;
      b.velocity = velocity;
      b.angularVelocity = angularVelocity;
    }
    this.inspectPairs();
  }
  private impulse(b: Body, impulse: Vec, point: Vec): void {
    b.velocity = add(b.velocity, mul(impulse, 1 / b.geometry.mass));
    b.angularVelocity += cross(sub(point, b.position), impulse) / b.geometry.inertia;
  }
  private solveContact(
    a: Body,
    b: Body | null,
    c: Contact,
    externalVelocity: Vec = { x: 0, y: 0 },
  ): void {
    const ra = sub(c.point, a.position),
      rb = b ? sub(c.point, b.position) : { x: 0, y: 0 };
    const relative = dot(
      sub(b ? pointVelocity(b, c.point) : externalVelocity, pointVelocity(a, c.point)),
      c.normal,
    );
    if (relative >= 0) return;
    const inverseMass =
      1 / a.geometry.mass +
      (b ? 1 / b.geometry.mass : 0) +
      cross(ra, c.normal) ** 2 / a.geometry.inertia +
      (b ? cross(rb, c.normal) ** 2 / b.geometry.inertia : 0);
    const j = (-(1 + this.response.restitution) * relative) / inverseMass;
    this.impulse(a, mul(c.normal, -j), c.point);
    if (b) this.impulse(b, mul(c.normal, j), c.point);
  }
  private contact(a: Body, b: Body, key: string): Contact | null {
    const av = this.vertices(a),
      bv = this.vertices(b),
      cached = this.contactCache.get(key);
    if (cached?.a === av && cached.b === bv) return cached.contact;
    const contact = polygonContact(av, bv, this.parts(a), this.parts(b));
    this.contactCache.set(key, { a: av, b: bv, contact });
    return contact;
  }
  private solveFragments(recoverCompound = false, minimumDepth?: number): void {
    for (let i = 0; i < this.bodies.length; i++)
      for (let j = i + 1; j < this.bodies.length; j++) {
        const a = this.bodies[i]!,
          b = this.bodies[j]!,
          key = pairKey(a.geometry.id, b.geometry.id);
        if (this.permissions.ghosted(key)) continue;
        const c = this.contact(a, b, key);
        if (!c || (minimumDepth !== undefined && c.depth <= minimumDepth)) continue;
        this.recentContacts.set(key, c);
        this.solveContact(a, b, c);
        // Position-only geometric constraint projection. Never a velocity bias.
        // A ghosted pair never enters either this projection or impulse solver.
        const total = a.geometry.mass + b.geometry.mass;
        const correction =
          recoverCompound &&
          c.depth > PHYSICS.solidTolerance &&
          (a.geometry.parts.length > 1 || b.geometry.parts.length > 1)
            ? compoundSeparation(
                this.parts(a),
                this.parts(b),
                c.normal,
                (translation) =>
                  this.projectionAllowed(a, mul(translation, -b.geometry.mass / total)) &&
                  this.projectionAllowed(b, mul(translation, a.geometry.mass / total)),
              )
            : mul(c.normal, Math.max(0, c.depth - 0.00001));
        if (length(correction) > 0) {
          a.position = sub(a.position, mul(correction, b.geometry.mass / total));
          b.position = add(b.position, mul(correction, a.geometry.mass / total));
        }
      }
  }
  private projectionAllowed(body: Body, translation: Vec): boolean {
    const vertices = this.vertices(body),
      moved = vertices.map((p) => add(p, translation));
    const minx = Math.min(0, ...vertices.map((p) => p.x)),
      maxx = Math.max(this.width, ...vertices.map((p) => p.x));
    const miny = Math.min(0, ...vertices.map((p) => p.y)),
      maxy = Math.max(this.height, ...vertices.map((p) => p.y));
    if (
      moved.some(
        (p) =>
          p.x < minx - 0.00001 ||
          p.x > maxx + 0.00001 ||
          p.y < miny - 0.00001 ||
          p.y > maxy + 0.00001,
      )
    )
      return false;
    if (!this.navigator.active) return true;
    // Avoid a correction that the prescribed navigator will immediately undo.
    const depth = (points: readonly Vec[]) =>
      circleContact(points, this.navigator.position, PHYSICS.navigatorRadius)?.depth ?? 0;
    return depth(moved) <= Math.max(0, depth(vertices)) + 0.00001;
  }
  private solveNavigator(): void {
    if (!this.navigator.active) return;
    for (const b of this.bodies) {
      const c = circleContact(this.vertices(b), this.navigator.position, PHYSICS.navigatorRadius);
      if (!c) continue;
      b.navigatorContact = true;
      this.solveContact(b, null, c, this.navigator.velocity);
      const correction = mul(c.normal, -(Math.max(0, c.depth) + 0.00001));
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
      if (allowed < 1)
        this.navigator.position = sub(this.navigator.position, mul(correction, 1 - allowed));
    }
  }
  private solveBoundaries(): void {
    for (const b of this.bodies) {
      const box = polygonBounds(this.vertices(b));
      if (box.minx >= 0 && box.maxx <= this.width && box.miny >= 0 && box.maxy <= this.height)
        continue;
      for (const wall of [0, 1, 2, 3]) {
        const vertices = this.vertices(b);
        const value = (p: Vec) =>
          wall === 0 ? -p.x : wall === 1 ? p.x - this.width : wall === 2 ? -p.y : p.y - this.height;
        const depth = Math.max(...vertices.map(value));
        if (depth <= 0) continue;
        const points = vertices.filter((p) => depth - value(p) < 0.05);
        const point = mul(points.reduce(add, { x: 0, y: 0 }), 1 / points.length);
        const normal = [
          { x: -1, y: 0 },
          { x: 1, y: 0 },
          { x: 0, y: -1 },
          { x: 0, y: 1 },
        ][wall]!;
        this.solveContact(b, null, { normal, depth, point });
        b.position = sub(b.position, mul(normal, depth + 0.00001));
      }
    }
  }
  private account(
    channel: keyof Omit<EnergyLedger, 'before' | 'after'>,
    operation: () => void,
  ): void {
    const before = this.energy();
    operation();
    this.ledger[channel] += this.energy() - before;
  }
  stepFixed(
    count = 1,
    pointer?: PointerSample,
    forecastBudget: number = FORECAST.workPerTick,
  ): void {
    if (!Number.isInteger(forecastBudget) || forecastBudget < 0)
      throw new Error('Invalid forecast budget');
    if (!Number.isInteger(count) || count < 0 || count > 100_000)
      throw new Error('Invalid fixed-step count');
    for (let n = 0; n < count; n++) this.tick(n === 0 ? pointer : undefined, forecastBudget);
  }
  // Live observations represent motion already performed by the user. Consume
  // all of them in this tick, with bounded body integration and contour casts
  // for longer legs. Scalar stepFixed input retains exact sampled fixture replay.
  stepStroke(
    samples: readonly PointerSample[],
    forecastBudget: number = FORECAST.workPerTick,
  ): void {
    if (!Number.isInteger(forecastBudget) || forecastBudget < 0)
      throw new Error('Invalid forecast budget');
    if (samples.length > POINTER_STROKE.capacity)
      throw new Error('Pointer stroke exceeds capacity');
    for (const [index, p] of samples.entries()) {
      if (![p.x, p.y].every(Number.isFinite)) throw new Error('Invalid pointer sample');
      if (
        p.elapsedSeconds !== undefined &&
        (!Number.isFinite(p.elapsedSeconds) || p.elapsedSeconds <= 0)
      )
        throw new Error('Invalid pointer observation interval');
      if (index > 0 && p.active === false)
        throw new Error('Cancellation must precede the new stroke');
    }
    // Cancellation can be followed by re-entry before the next frame. Preserve
    // that discontinuity: the new stroke starts at its first fresh observation,
    // rather than casting from the pre-cancellation navigator position.
    const cancelled = samples[0]?.active === false;
    this.tick(
      cancelled ? samples[0] : undefined,
      forecastBudget,
      cancelled ? samples.slice(1) : samples,
    );
  }
  private tick(
    pointer: PointerSample | undefined,
    forecastBudget: number,
    samples?: readonly PointerSample[],
  ): void {
    const observedInterval = pointer?.elapsedSeconds ?? FIXED_DT;
    if (!Number.isFinite(observedInterval) || observedInterval <= 0)
      throw new Error('Invalid pointer observation interval');
    let end = this.navigator.position;
    if (pointer) {
      if (![pointer.x, pointer.y].every(Number.isFinite)) throw new Error('Invalid pointer sample');
      end = {
        x: Math.max(0, Math.min(this.width, pointer.x)),
        y: Math.max(0, Math.min(this.height, pointer.y)),
      };
      // Entry establishes presence, without interpreting a pointerenter as a
      // thousands-of-pixels strike from the inactive sentinel position.
      if (!this.navigator.active) this.navigator.position = end;
      this.navigator.active = pointer.active ?? true;
    }
    let stroke: PointerStroke | undefined;
    if (samples?.length) {
      if (!this.navigator.active)
        this.navigator.position = {
          x: Math.max(0, Math.min(this.width, samples[0]!.x)),
          y: Math.max(0, Math.min(this.height, samples[0]!.y)),
        };
      this.navigator.active = true;
      stroke = new PointerStroke(
        this.navigator.position,
        samples,
        this.width,
        this.height,
        FIXED_DT,
      );
      end = stroke.points.at(-1)!;
    }
    const delta = sub(end, this.navigator.position);
    this.navigatorOrigin = { ...this.navigator.position };
    this.navigatorPath = stroke?.points ?? [];
    const rawSpeed = length(delta) / observedInterval;
    this.navigator.velocity = mul(
      delta,
      navigatorTransfer(rawSpeed, this.response.navigatorSpeedLimit) / (length(delta) || 1),
    );
    // Fixed 120Hz external clock; body motion remains at <=1/480s. Scalar
    // fixtures sample at <=2 units; live strokes cap subdivisions and cast the
    // longer legs instead of imposing a replay speed on the user's pointer.
    const subdivisions = stroke
      ? PHYSICS.microsteps
      : Math.max(PHYSICS.microsteps, this.navigator.active ? Math.ceil(length(delta) / 2) : 0);
    const dt = FIXED_DT / subdivisions;
    const origin = this.navigator.position;
    this.recentContacts.clear();
    this.bodies.forEach((b) => {
      b.navigatorContact = false;
      b.contacts = [];
    });
    this.ledger = {
      before: this.energy(),
      home: 0,
      damping: 0,
      fragments: 0,
      navigator: 0,
      boundaries: 0,
      permissions: 0,
      after: 0,
    };
    for (let s = 1; s <= subdivisions; s++) {
      const legs = stroke?.take(
        s === subdivisions ? Infinity : (stroke.distance * s) / subdivisions,
        this.navigator.position,
      );
      const desired = stroke
        ? (legs?.at(-1)?.end ?? this.navigator.position)
        : add(origin, mul(delta, s / subdivisions));
      const travel = sub(desired, this.navigator.position);
      this.navigator.position = stroke
        ? desired
        : add(this.navigator.position, mul(travel, Math.min(1, 2 / (length(travel) || 1))));
      if (legs?.length) {
        const last = legs.at(-1)!;
        this.navigator.velocity = mul(
          last.delta,
          navigatorTransfer(
            length(last.delta) / last.elapsedSeconds,
            this.response.navigatorSpeedLimit,
          ) / (length(last.delta) || 1),
        );
      }
      this.account('home', () => {
        for (const b of this.bodies) {
          const f = this.homeForce(b);
          b.velocity = add(b.velocity, mul(f.acceleration, dt));
          b.angularVelocity += (f.torque / b.geometry.inertia) * dt;
        }
      });
      this.account('damping', () => {
        this.bodies.forEach((b) => {
          b.velocity = mul(b.velocity, Math.exp(-this.response.linearDamping * dt));
          b.angularVelocity *= Math.exp(-this.response.angularDamping * dt);
          b.position = add(b.position, mul(b.velocity, dt));
          b.angle += b.angularVelocity * dt;
        });
      });
      if (legs)
        this.account('navigator', () => {
          for (const leg of legs) {
            // Short legs still use the original local positional solver. Long
            // legs apply bounded momentum at every crossed body's first entry;
            // they do not drag a body across a fast stroke's entire distance.
            if (length(sub(leg.end, leg.start)) <= POINTER_STROKE.spacing) continue;
            const velocity = mul(
              leg.delta,
              navigatorTransfer(
                length(leg.delta) / leg.elapsedSeconds,
                this.response.navigatorSpeedLimit,
              ) / (length(leg.delta) || 1),
            );
            for (const b of this.bodies) {
              const c = circleCast(this.vertices(b), leg.start, leg.end, PHYSICS.navigatorRadius);
              if (!c) continue;
              b.navigatorContact = true;
              this.solveContact(b, null, c, velocity);
            }
          }
        });
      for (let iteration = 0; iteration < PHYSICS.solverIterations; iteration++) {
        const poses = this.bodies.map((b) => b.position);
        // Reserve compound recovery for overlap left by the ordinary passes.
        this.account('fragments', () =>
          this.solveFragments(iteration >= PHYSICS.solverIterations - 2),
        );
        this.account('boundaries', () => this.solveBoundaries());
        this.account('navigator', () => this.solveNavigator());
        if (this.bodies.every((b, i) => b.position === poses[i])) break;
      }
    }
    this.step++;
    this.inspectPairs();
    // A final compound correction can reintroduce another solid contact. New
    // fast-stroke poses exposed this in tightly packed concave groups. Recover
    // only residual violations, with bounded position/impulse passes and no
    // additional integration time, force, bias or permission changes. Renewed
    // strikes after rearming can couple three curved corners; reserve up to
    // twenty residual passes, exiting as soon as all solid contacts are valid.
    for (let pass = 0; pass < PHYSICS.recoveryIterations && this.unresolvedSolids(); pass++) {
      // Do not polish already acceptable contacts here: their shallow MTVs
      // can undo a repaired concave pair and keep the whole group oscillating.
      this.account('fragments', () => this.solveFragments(true, PHYSICS.solidTolerance));
      this.account('boundaries', () => this.solveBoundaries());
      this.account('navigator', () => this.solveNavigator());
      this.inspectPairs();
    }
    this.account('permissions', () =>
      this.permissions.observe(
        this.step,
        this.bodies.map((b) => {
          const response = this.homeForce(b);
          return {
            id: b.geometry.id,
            mass: b.geometry.mass,
            inertia: b.geometry.inertia,
            speed: length(b.velocity),
            angularSpeed: Math.abs(b.angularVelocity),
            velocityX: b.velocity.x,
            velocityY: b.velocity.y,
            energy: bodyEnergy(b),
            error: this.homeError(b),
            homeRequest:
              length(response.acceleration) + Math.abs(response.torque) / b.geometry.inertia,
            navigatorContact: b.navigatorContact,
          };
        }),
        this.pairs,
        (members) => {
          const metrics = this.homeMetrics(members, true);
          // Free response over the same 50 ms horizon, including angular work and
          // damping. Common translation has already been removed from this score.
          const freePower =
            metrics.power + 0.5 * FORECAST.horizonSteps * FIXED_DT * metrics.powerDerivative;
          return { actual: metrics.power, expected: freePower };
        },
      ),
    );
    if (!this.predicting) this.account('permissions', () => this.advanceForecast(forecastBudget));
    this.ledger.after = this.energy();
    this.maxEnergy = Math.max(this.maxEnergy, this.ledger.after);
  }
  private unresolvedSolids(): boolean {
    return this.pairs.some(
      (p) => !this.permissions.ghosted(p.key) && p.overlap > PHYSICS.solidTolerance,
    );
  }
  private homeMetrics(members: readonly string[], progress = false) {
    const bodies = this.bodies.filter((b) => members.includes(b.geometry.id));
    const mass = bodies.reduce((sum, b) => sum + b.geometry.mass, 0);
    const rows = bodies.map((b) => ({ b, anchors: this.anchors(b) }));
    const meanError = mul(
      rows.reduce(
        (sum, { b, anchors }) =>
          add(
            sum,
            mul(
              anchors.reduce((sum, a) => add(sum, sub(a.target, a.current)), { x: 0, y: 0 }),
              b.geometry.mass / anchors.length,
            ),
          ),
        { x: 0, y: 0 },
      ),
      1 / mass,
    );
    const meanVelocity = mul(
      bodies.reduce((sum, b) => add(sum, mul(b.velocity, b.geometry.mass)), { x: 0, y: 0 }),
      1 / mass,
    );
    const meanAnchorVelocity = progress
      ? mul(
          rows.reduce(
            (sum, { b, anchors }) =>
              add(
                sum,
                mul(
                  anchors.reduce((sum, a) => add(sum, pointVelocity(b, a.current)), { x: 0, y: 0 }),
                  b.geometry.mass / anchors.length,
                ),
              ),
            { x: 0, y: 0 },
          ),
          1 / mass,
        )
      : meanVelocity;
    let potential = 0,
      scale = 0,
      power = 0,
      powerDerivative = 0;
    for (const { b, anchors } of rows) {
      let force: Vec = { x: 0, y: 0 },
        torque = 0;
      const coefficient = (b.geometry.mass * this.response.homeStiffness) / anchors.length;
      let curvature = 0;
      const velocity = sub(b.velocity, meanVelocity);
      for (const a of anchors) {
        const r = sub(a.current, b.position);
        const anchorVelocity = progress
          ? sub(pointVelocity(b, a.current), meanAnchorVelocity)
          : { x: 0, y: 0 };
        const error = sub(sub(a.target, a.current), meanError),
          f = mul(error, coefficient);
        potential += 0.5 * coefficient * dot(error, error);
        force = add(force, f);
        torque += cross(r, f);
        if (progress)
          curvature +=
            coefficient *
            (dot(anchorVelocity, anchorVelocity) + dot(error, r) * b.angularVelocity ** 2);
      }
      scale +=
        (dot(force, force) / b.geometry.mass + torque ** 2 / b.geometry.inertia) /
        (2 * this.response.homeStiffness);
      power += dot(force, velocity) + torque * b.angularVelocity;
      if (progress) {
        const response = this.homeForce(b);
        powerDerivative +=
          dot(force, sub(response.acceleration, mul(velocity, this.response.linearDamping))) +
          torque *
            (response.torque / b.geometry.inertia -
              this.response.angularDamping * b.angularVelocity) -
          curvature;
      }
    }
    // Remove only common translation from the home objective. A fragment pair
    // cannot block that mode; it can block relative positions and orientations.
    // These are observation gradients, NEVER replacement forces for physics.
    return { potential, scale: Math.min(potential, scale), power, powerDerivative };
  }
  private potential(members: readonly string[]): number {
    return this.homeMetrics(members).potential;
  }
  private responseScale(members: readonly string[]): number {
    return this.homeMetrics(members).scale;
  }
  private center(members: readonly string[]): Vec {
    const bodies = this.bodies.filter((b) => members.includes(b.geometry.id));
    return mul(
      bodies.reduce((sum, b) => add(sum, mul(b.position, b.geometry.mass)), { x: 0, y: 0 }),
      1 / bodies.reduce((sum, b) => sum + b.geometry.mass, 0),
    );
  }
  private wallSignature(members: readonly string[]): string {
    return this.bodies
      .filter((b) => members.includes(b.geometry.id))
      .map((b) => {
        const vertices = this.vertices(b);
        return `${b.geometry.id}:${vertices.some((p) => p.x < 1)}:${vertices.some((p) => p.x > this.width - 1)}:${vertices.some((p) => p.y < 1)}:${vertices.some((p) => p.y > this.height - 1)}`;
      })
      .join('|');
  }
  private permissionSignature(): string {
    return this.permissions
      .snapshot()
      .filter((p) => p.state === 'GHOSTED_LATCHED')
      .map((p) => p.key)
      .sort()
      .join('|');
  }
  private forecastSafe(): boolean {
    if (!(
      this.energy() < 1e7 &&
      this.permissions.failures.length === 0 &&
      this.ledger.permissions === 0 &&
      Math.max(this.ledger.damping, this.ledger.fragments, this.ledger.boundaries) <= 1e-6 &&
      this.bodies.every(
        (b) =>
          [
            b.position.x,
            b.position.y,
            b.angle,
            b.velocity.x,
            b.velocity.y,
            b.angularVelocity,
          ].every(Number.isFinite) &&
          this.vertices(b).every(
            (p) =>
              p.x >= -1e-4 && p.x <= this.width + 1e-4 && p.y >= -1e-4 && p.y <= this.height + 1e-4,
          ) &&
          (!this.navigator.active ||
            (circleContact(this.vertices(b), this.navigator.position, PHYSICS.navigatorRadius)
              ?.depth ?? 0) <= 0.02),
      )
    ))
      return false;
    // Independently check solid geometry too: an alternative must not gain
    // apparent progress by leaving a different, still-solid relationship
    // illegally interpenetrating.
    for (let i = 0; i < this.bodies.length; i++)
      for (let j = i + 1; j < this.bodies.length; j++) {
        const a = this.bodies[i]!,
          b = this.bodies[j]!;
        if (
          !this.permissions.ghosted(pairKey(a.geometry.id, b.geometry.id)) &&
          (polygonContact(this.vertices(a), this.vertices(b), this.parts(a), this.parts(b))
            ?.depth ?? 0) > PHYSICS.solidTolerance
        )
          return false;
      }
    return true;
  }
  /** Tick guard uses final contact observations, without materializing diagnostics. */
  runtimeFailure(): string | null {
    if (this.permissions.failures.length) return this.permissions.failures[0]!;
    if (
      ![
        this.navigator.position.x,
        this.navigator.position.y,
        this.navigator.velocity.x,
        this.navigator.velocity.y,
        ...Object.values(this.ledger),
      ].every(Number.isFinite) ||
      this.bodies.some(
        (b) =>
          ![
            b.position.x,
            b.position.y,
            b.angle,
            b.velocity.x,
            b.velocity.y,
            b.angularVelocity,
          ].every(Number.isFinite),
      )
    )
      return 'Non-finite simulation state';
    if (
      this.ledger.after >= 1e7 ||
      this.ledger.permissions !== 0 ||
      Math.max(this.ledger.damping, this.ledger.fragments, this.ledger.boundaries) > 1e-6
    )
      return 'Simulation energy invariant failed';
    for (const b of this.bodies) {
      const vertices = this.vertices(b),
        box = polygonBounds(vertices);
      if (
        box.minx < -1e-4 ||
        box.maxx > this.width + 1e-4 ||
        box.miny < -1e-4 ||
        box.maxy > this.height + 1e-4
      )
        return 'Fragment left the stage';
      if (
        this.navigator.active &&
        (circleContact(vertices, this.navigator.position, PHYSICS.navigatorRadius)?.depth ?? 0) >
          0.02
      )
        return 'Navigator clearance invariant failed';
    }
    return this.unresolvedSolids() ? 'Solid contact recovery failed' : null;
  }
  private startForecast(
    request: ForecastRequest,
    validating = false,
  ): NonNullable<PhysicsCore['forecast']> {
    // Every alternative starts from the SAME frozen world, including latches.
    const fixtures = this.bodies.map((b) => ({
      id: b.geometry.id,
      position: { ...b.position },
      angle: b.angle,
      velocity: { ...b.velocity },
      angularVelocity: b.angularVelocity,
    }));
    const states = this.permissions.snapshot(),
      step = this.step;
    const navigator = { position: { ...this.navigator.position }, active: this.navigator.active };
    const create = (keys: readonly string[]): ForecastBranch => {
      const copy = new PhysicsCore(
        this.bodies.map((b) => b.geometry),
        {
          width: this.width,
          height: this.height,
          response: this.response,
          forecastPermissions: CollisionPermissions.forPrediction(
            FIXED_DT,
            states,
            keys,
            step,
            keys.length ? request.members : [],
          ),
        },
      );
      for (const fixture of fixtures) {
        const b = copy.bodies.find((b) => b.geometry.id === fixture.id)!;
        b.position = { ...fixture.position };
        b.angle = fixture.angle;
        b.velocity = { ...fixture.velocity };
        b.angularVelocity = fixture.angularVelocity;
      }
      copy.step = step;
      copy.navigator.position = { ...navigator.position };
      copy.navigator.active = navigator.active;
      return {
        advance: () => copy.stepFixed(),
        sample: (members) => ({ potential: copy.potential(members), safe: copy.forecastSafe() }),
      };
    };
    this.forecastWork.started++;
    return {
      search: new GroupForecast(
        {
          ...request,
          members: [...request.members],
          candidates: [...request.candidates],
          present: request.present ? [...request.present] : undefined,
          responseScale: this.responseScale(request.members),
        },
        create,
      ),
      poses: fixtures,
      navigator,
      permissions: this.permissionSignature(),
      potential: this.potential(request.members),
      center: this.center(request.members),
      velocity: this.meanVelocity(request.members),
      walls: this.wallSignature(request.members),
      validating,
    };
  }
  private meanVelocity(members: readonly string[]): Vec {
    const bodies = this.bodies.filter((b) => members.includes(b.geometry.id));
    const mass = bodies.reduce((sum, b) => sum + b.geometry.mass, 0);
    return mul(
      bodies.reduce((sum, b) => add(sum, mul(b.velocity, b.geometry.mass)), { x: 0, y: 0 }),
      1 / mass,
    );
  }
  private navigatorRelevant(members: readonly string[], points: readonly Vec[]): boolean {
    return this.bodies
      .filter((b) => members.includes(b.geometry.id))
      .some((b) => {
        const radius =
          Math.max(...b.geometry.vertices.map(length)) +
          PHYSICS.navigatorRadius +
          FORECAST.maximumTranslationDrift +
          this.response.navigatorSpeedLimit * FORECAST.horizonSteps * FIXED_DT;
        return points.some((point, index) => {
          const end = points[index + 1] ?? point,
            delta = sub(end, point);
          const t = Math.max(
            0,
            Math.min(1, dot(sub(b.position, point), delta) / (dot(delta, delta) || 1)),
          );
          return length(sub(b.position, add(point, mul(delta, t)))) <= radius;
        });
      });
  }
  private advanceForecast(budget: number): void {
    if (this.forecast) {
      const job = this.forecast,
        request = job.search.request;
      const current = this.permissions.requests.find((r) => r.key === request.key);
      const translation = sub(this.center(request.members), job.center);
      const progress =
        (job.potential - this.potential(request.members)) /
        Math.max(1e-12, request.responseScale ?? job.potential);
      const velocity = this.meanVelocity(request.members);
      const stale =
        !current ||
        request.candidates.length !== current.candidates.length ||
        request.candidates.some((key) => !current.candidates.includes(key)) ||
        this.step - request.step > FORECAST.maximumAgeSteps ||
        job.permissions !== this.permissionSignature() ||
        job.walls !== this.wallSignature(request.members) ||
        length(translation) > FORECAST.maximumTranslationDrift ||
        ((job.navigator.active !== this.navigator.active ||
          length(sub(job.navigator.position, this.navigator.position)) > 0.01 ||
          this.navigatorPath.some((p) => length(sub(p, job.navigator.position)) > 0.01)) &&
          this.navigatorRelevant(request.members, [
            job.navigator.position,
            this.navigatorOrigin,
            ...this.navigatorPath,
            this.navigator.position,
          ])) ||
        progress >= FORECAST.baselineProgress ||
        job.poses.some((p) => {
          const b = this.bodies.find((b) => b.geometry.id === p.id)!;
          const member = request.members.includes(p.id);
          const nearby =
            member ||
            this.bodies.some(
              (other) =>
                request.members.includes(other.geometry.id) &&
                Math.min(
                  length(sub(p.position, other.position)),
                  length(sub(b.position, other.position)),
                ) <
                  Math.max(...b.geometry.vertices.map(length)) +
                    Math.max(...other.geometry.vertices.map(length)) +
                    (length(p.velocity) + length(b.velocity)) *
                      FORECAST.maximumAgeSteps *
                      FIXED_DT +
                    FORECAST.maximumTranslationDrift,
            );
          // The initial comparison nominates a cohort. Fresh validation owns
          // release authority and monitors nearby outsiders as well as members.
          if (!nearby || (!member && !job.validating)) return false;
          return (
            length(sub(sub(b.position, p.position), member ? translation : { x: 0, y: 0 })) >
              FORECAST.maximumPositionDrift ||
            Math.abs(b.angle - p.angle) > FORECAST.maximumAngleDrift ||
            length(
              sub(
                sub(b.velocity, member ? velocity : { x: 0, y: 0 }),
                sub(p.velocity, member ? job.velocity : { x: 0, y: 0 }),
              ),
            ) > FORECAST.maximumVelocityDrift ||
            Math.abs(b.angularVelocity - p.angularVelocity) > FORECAST.maximumAngularVelocityDrift
          );
        });
      if (stale) {
        if (current && progress >= FORECAST.baselineProgress)
          this.permissions.deferPrediction(this.step, current.key);
        this.forecast = null;
        this.forecastWork.cancelled++;
      }
    }
    if (budget <= 0) return;
    if (!this.forecast && this.permissions.requests.length) {
      const requests = [...this.permissions.requests].sort((a, b) => a.key.localeCompare(b.key));
      const request = requests.find((r) => r.key > this.lastForecastKey) ?? requests[0]!;
      this.lastForecastKey = request.key;
      const metrics = this.homeMetrics(request.members);
      if (metrics.potential <= 1e-8 || metrics.scale <= 1e-12)
        this.permissions.deferPrediction(this.step, request.key);
      else this.forecast = this.startForecast(request);
    }
    if (!this.forecast) return;
    const before = this.forecast.search.simulatedSteps;
    const decision = this.forecast.search.advance(budget);
    this.forecastWork.simulatedSteps += this.forecast.search.simulatedSteps - before;
    if (!decision) return;
    if (decision.selected.length && !this.forecast.validating) {
      // Re-run the solid baseline and complete cohort from the current world.
      const current = this.permissions.requests.find((r) => r.key === decision.request.key);
      if (current) this.forecast = this.startForecast(current, true);
      return;
    }
    if (this.permissions.acceptPrediction(this.step, decision)) {
      this.forecastWork.completed++;
      this.forecast = null;
    }
  }
  private inspectPairs(): void {
    // Both live and shadow returns need solid neighbours to propagate
    // navigator contact and interrupt the same latched cohort.
    this.pairs = [];
    this.bodies.forEach((b) => {
      b.contacts = [];
    });
    const requests = this.bodies.map((b) => this.homeForce(b));
    const pointRequest = (index: number, point: Vec): Vec => {
      const body = this.bodies[index]!,
        request = requests[index]!;
      const r = sub(point, body.position),
        alpha = request.torque / body.geometry.inertia;
      return add(request.acceleration, { x: -alpha * r.y, y: alpha * r.x });
    };
    for (let i = 0; i < this.bodies.length; i++)
      for (let j = i + 1; j < this.bodies.length; j++) {
        const a = this.bodies[i]!,
          b = this.bodies[j]!,
          key = pairKey(a.geometry.id, b.geometry.id);
        const av = this.vertices(a),
          bv = this.vertices(b);
        const contacts = polygonContacts(av, bv, this.parts(a), this.parts(b));
        const c = contacts.reduce<Contact | null>(
          (deepest, c) => (!deepest || c.depth > deepest.depth ? c : deepest),
          null,
        );
        const gap = c ? 0 : polygonGap(av, bv),
          previous = this.recentContacts.get(key);
        if (previous) contacts.push(previous);
        const contact = !!c || !!previous || gap < PHYSICS.contactMargin;
        if (contact) {
          a.contacts.push(b.geometry.id);
          b.contacts.push(a.geometry.id);
        }
        this.pairs.push({
          key,
          a: a.geometry.id,
          b: b.geometry.id,
          contact,
          overlap: c?.depth ?? 0,
          gap,
          blocking: contacts.length
            ? Math.max(
                ...contacts.map((c) =>
                  dot(sub(pointRequest(i, c.point), pointRequest(j, c.point)), c.normal),
                ),
              )
            : 0,
        });
      }
  }
  isRest(): boolean {
    return (
      this.bodies.every(
        (b) =>
          this.homeError(b) < PHYSICS.restError &&
          length(b.velocity) < PHYSICS.restSpeed &&
          Math.abs(b.angularVelocity) < PHYSICS.restAngularSpeed &&
          this.vertices(b).every(
            (p) =>
              p.x >= -1e-4 && p.x <= this.width + 1e-4 && p.y >= -1e-4 && p.y <= this.height + 1e-4,
          ),
      ) && this.pairs.every((p) => p.overlap < 1e-4 && !this.permissions.ghosted(p.key))
    );
  }
  getSnapshot() {
    const permissions = this.permissions.snapshot();
    const fragments = this.bodies.map((b) => ({
      id: b.geometry.id,
      position: { ...b.position },
      rotation: b.angle,
      velocity: { ...b.velocity },
      angularVelocity: b.angularVelocity,
      kineticEnergy: bodyEnergy(b),
      homeAnchorError: this.homeError(b),
      contacts: [...b.contacts],
      navigatorContact: b.navigatorContact,
      navigatorOverlap: this.navigator.active
        ? (circleContact(this.vertices(b), this.navigator.position, PHYSICS.navigatorRadius)
            ?.depth ?? 0)
        : 0,
      vertices: this.vertices(b).map((p) => ({ ...p })),
      anchors: this.anchors(b),
      runtimeState:
        this.homeError(b) < PHYSICS.restError &&
        length(b.velocity) < PHYSICS.restSpeed &&
        Math.abs(b.angularVelocity) < PHYSICS.restAngularSpeed
          ? 'REST'
          : 'MOVING',
    }));
    const pairs = this.pairs.map((p) => {
      const permission = permissions.find((s) => s.key === p.key);
      return {
        ...p,
        state: 'SOLID' as 'SOLID' | 'GHOSTED_LATCHED',
        eligible: false,
        contactSteps: 0,
        startStep: null as number | null,
        separatedSteps: 0,
        rearmMargin: p.overlap === 0 && p.gap >= DEADLOCK.rearmMargin,
        ...permission,
        ghostStartTime:
          permission?.startStep === null || permission?.startStep === undefined
            ? null
            : permission.startStep * FIXED_DT,
        physicalContact: p.contact && permission?.state !== 'GHOSTED_LATCHED',
      };
    });
    const contained = fragments.every((b) =>
      b.vertices.every(
        (p) =>
          p.x >= -1e-4 && p.x <= this.width + 1e-4 && p.y >= -1e-4 && p.y <= this.height + 1e-4,
      ),
    );
    const finite = fragments.every((b) =>
      [
        b.position.x,
        b.position.y,
        b.rotation,
        b.velocity.x,
        b.velocity.y,
        b.angularVelocity,
        b.kineticEnergy,
        b.homeAnchorError,
      ].every(Number.isFinite),
    );
    const rest = this.isRest();
    return {
      step: this.step,
      time: this.step * FIXED_DT,
      width: this.width,
      height: this.height,
      fragments,
      pairs,
      clusters: this.permissions.clusters.map((c) => ({
        ...c,
        members: [...c.members],
        blockingPairs: [...c.blockingPairs],
        eligiblePairs: [...c.eligiblePairs],
        forecast: c.forecast ? { ...c.forecast } : null,
      })),
      forecastWork: { ...this.forecastWork },
      events: this.permissions.events.map((e) => ({ ...e })),
      eventCount: this.permissions.eventCount,
      failures: [...this.permissions.failures],
      navigator: {
        ...this.navigator,
        position: { ...this.navigator.position },
        velocity: { ...this.navigator.velocity },
      },
      energy: this.energy(),
      maxEnergy: this.maxEnergy,
      ledger: { ...this.ledger },
      rest,
      contained,
      finite,
    };
  }
}
export type Snapshot = ReturnType<PhysicsCore['getSnapshot']>;
