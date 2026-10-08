export type ForecastRequest = Readonly<{ step: number; key: string; members: readonly string[]; candidates: readonly string[]; present?: readonly string[]; responseScale?: number }>;
export type ForecastSample = Readonly<{ potential: number; safe: boolean; residualBlocks?: number; futurePotential?: number }>;
export type ForecastBranch = { advance(): void; sample(members: readonly string[], final?: boolean): ForecastSample };
export type ForecastResult = Readonly<{ keys: readonly string[]; potential: number; safe: boolean; canCommit?: boolean; residualBlocks?: number; futurePotential?: number }>;
export type ForecastDecision = Readonly<{
  request: ForecastRequest; selected: readonly string[]; baselineReduction: number;
  selectedReduction: number; trials: number; simulatedSteps: number;
}>;
// Velocity drift is limited to half the pose-error envelope over the 50 ms
// prediction horizon: 30 * .05 = 1.5 units, .8 * .05 = .04 radians.
export const FORECAST = Object.freeze({ horizonSteps: 6, workPerTick: 2, homeOnlySteps: 120,
  maximumCandidates: 6, maximumTrials: 12, baselineProgress: .01,
  minimumBenefit: .01, comparableBenefit: .95, maximumAgeSteps: 48,
  maximumTranslationDrift: 32, maximumPositionDrift: 3, maximumAngleDrift: .08, maximumVelocityDrift: 30, maximumAngularVelocityDrift: .8 });

// Compare whole-island spring potential. Physical islands nominate contacts;
// closing scores rank trials, while
// only counterfactual improvement justifies relaxing them. Prefer a smaller
// set when its benefit is close to the best observed safe alternative.
export function selectRelease(initial: number, baseline: ForecastResult, alternatives: readonly ForecastResult[], responseScale = initial): ForecastResult | null {
  if (!baseline.safe || ![initial, baseline.potential, responseScale].every(Number.isFinite) || initial <= 0 || responseScale <= 0 || (initial - baseline.potential) / responseScale >= FORECAST.baselineProgress) return null;
  const safe = alternatives.filter(p => p.safe && Number.isFinite(p.potential) && (baseline.potential - p.potential) / responseScale >= FORECAST.minimumBenefit);
  if (!safe.length) return null;
  // Useful current contacts take precedence over speculative combinations
  // that require another touch. A better hypothetical future must not prevent
  // acting on a proven, currently available group release.
  const available = safe.filter(p => p.canCommit !== false);
  const choices = available.length ? available : safe;
  // Prefer plans that restore internal home response in all remaining contact
  // components. Whole-cluster translation does not certify an internal escape.
  const unlocked = choices.filter(p => p.residualBlocks === 0);
  const progressing = unlocked.length ? unlocked : choices;
  const order = (a: ForecastResult, b: ForecastResult) => a.keys.length - b.keys.length
    || a.potential - b.potential || a.keys.join("/").localeCompare(b.keys.join("/"));
  if (unlocked.length && progressing.every(p => p.futurePotential !== undefined && Number.isFinite(p.futurePotential))) {
    // A cheap home-only estimate catches weak-torque orientations that look
    // fast initially and then linger. It ranks already validated alternatives;
    // it never supplies a deadlock certificate or a pose for the live world.
    const best = Math.min(...progressing.map(p => p.futurePotential!));
    return progressing.filter(p => p.futurePotential! <= best * 1.05 + responseScale * 1e-6).sort(order)[0]!;
  }
  const best = Math.max(...progressing.map(p => baseline.potential - p.potential));
  return progressing.filter(p => baseline.potential - p.potential >= best * FORECAST.comparableBenefit).sort(order)[0]!;
}

export class GroupForecast {
  private readonly plans: string[][];
  private branch: ForecastBranch | null = null;
  private branchSteps = 0;
  private index = 0;
  private initial = 0;
  private responseScale = 0;
  private readonly results: ForecastResult[] = [];
  private steps = 0;
  constructor(readonly request: ForecastRequest, private readonly create: (keys: readonly string[]) => ForecastBranch, validationPlans?: readonly (readonly string[])[]) {
    const candidates = request.candidates.slice(0, FORECAST.maximumCandidates);
    const plans: string[][] = [[], ...candidates.map(key => [key])];
    if ((request.present?.length ?? 0) > 1) plans.push([...request.present!]);
    if (request.candidates.length > 1) plans.push([...request.candidates]);
    // Joint choices are tested even if none of the single removals is useful.
    for (let i = 0; i < Math.min(3, candidates.length); i++) for (let j = i + 1; j < Math.min(3, candidates.length); j++)
      plans.push([candidates[i]!, candidates[j]!]);
    if (candidates.length > 3) plans.push(candidates.slice(0, 3));
    if (request.candidates.length > 3) plans.push(request.candidates.slice(0, -1));
    const unique = new Map((validationPlans ?? plans).map(keys => { const sorted = [...keys].sort(); return [sorted.join("|"), sorted]; }));
    this.plans = [...unique.values()].slice(0, FORECAST.maximumTrials);
  }
  get simulatedSteps(): number { return this.steps; }
  advance(budget: number = FORECAST.workPerTick): ForecastDecision | null {
    while (budget > 0 && this.index < this.plans.length) {
      if (!this.branch) {
        this.branch = this.create(this.plans[this.index]!);
        if (this.index === 0) {
          this.initial = this.branch.sample(this.request.members).potential;
          this.responseScale = this.request.responseScale ?? this.initial;
        }
        this.branchSteps = 0;
      }
      this.branch.advance(); this.branchSteps++; this.steps++; budget--;
      const sample = this.branch.sample(this.request.members, this.branchSteps === FORECAST.horizonSteps);
      if (!sample.safe || this.branchSteps === FORECAST.horizonSteps) {
        this.results.push({ keys: this.plans[this.index]!, ...sample,
          canCommit: this.plans[this.index]!.every(key => (this.request.present ?? this.request.candidates).includes(key)) });
        this.branch = null; this.index++;
        // Normal recovery needs no alternative trials.
        if (this.index === 1 && (!sample.safe || (this.initial - sample.potential) / Math.max(1e-12, this.responseScale) >= FORECAST.baselineProgress)) break;
      }
    }
    const baseline = this.results[0];
    if (!baseline || (this.index < this.plans.length && baseline.safe
      && (this.initial - baseline.potential) / Math.max(1e-12, this.responseScale) < FORECAST.baselineProgress)) return null;
    const selected = selectRelease(this.initial, baseline, this.results.slice(1), this.responseScale);
    return { request: this.request, selected: selected?.keys ?? [],
      baselineReduction: (this.initial - baseline.potential) / Math.max(1e-12, this.responseScale),
      selectedReduction: (this.initial - (selected?.potential ?? baseline.potential)) / Math.max(1e-12, this.responseScale),
      trials: this.results.length, simulatedSteps: this.steps };
  }
}
