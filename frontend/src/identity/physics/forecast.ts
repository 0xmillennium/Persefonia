export type ForecastRequest = Readonly<{
  step: number;
  key: string;
  members: readonly string[];
  candidates: readonly string[];
  present?: readonly string[];
  responseScale?: number;
}>;
export type ForecastSample = Readonly<{ potential: number; safe: boolean }>;
export type ForecastBranch = {
  advance(): void;
  sample(members: readonly string[]): ForecastSample;
};
export type ForecastResult = Readonly<{
  keys: readonly string[];
  potential: number;
  safe: boolean;
}>;
export type ForecastDecision = Readonly<{
  request: ForecastRequest;
  selected: readonly string[];
  baselineReduction: number;
  selectedReduction: number;
  trials: number;
  simulatedSteps: number;
}>;
// Velocity drift is limited to half the pose-error envelope over the 50 ms
// prediction horizon: 30 * .05 = 1.5 units, .8 * .05 = .04 radians.
export const FORECAST = Object.freeze({
  horizonSteps: 6,
  workPerTick: 2,
  maximumTrials: 2,
  baselineProgress: 0.01,
  minimumBenefit: 0.01,
  maximumAgeSteps: 48,
  maximumTranslationDrift: 32,
  maximumPositionDrift: 3,
  maximumAngleDrift: 0.08,
  maximumVelocityDrift: 30,
  maximumAngularVelocityDrift: 0.8,
});

// The detector nominates a cohort; the exact solver checks whether its whole
// return permission improves home response without compromising solid outsiders.
export function selectRelease(
  initial: number,
  baseline: ForecastResult,
  alternatives: readonly ForecastResult[],
  responseScale = initial,
): ForecastResult | null {
  if (
    !baseline.safe ||
    ![initial, baseline.potential, responseScale].every(Number.isFinite) ||
    initial <= 0 ||
    responseScale <= 0 ||
    (initial - baseline.potential) / responseScale >= FORECAST.baselineProgress
  )
    return null;
  return (
    alternatives.find(
      (p) =>
        p.safe &&
        Number.isFinite(p.potential) &&
        (baseline.potential - p.potential) / responseScale >= FORECAST.minimumBenefit,
    ) ?? null
  );
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
  constructor(
    readonly request: ForecastRequest,
    private readonly create: (keys: readonly string[]) => ForecastBranch,
  ) {
    const plans: string[][] = [[], [...request.candidates]];
    const unique = new Map(
      plans.map((keys) => {
        const sorted = [...keys].sort();
        return [sorted.join('|'), sorted];
      }),
    );
    this.plans = [...unique.values()].slice(0, FORECAST.maximumTrials);
  }
  get simulatedSteps(): number {
    return this.steps;
  }
  advance(budget: number = FORECAST.workPerTick): ForecastDecision | null {
    if (!Number.isInteger(budget) || budget < 0) throw new Error('Invalid forecast budget');
    while (budget > 0 && this.index < this.plans.length) {
      if (!this.branch) {
        this.branch = this.create(this.plans[this.index]!);
        if (this.index === 0) {
          this.initial = this.branch.sample(this.request.members).potential;
          this.responseScale = this.request.responseScale ?? this.initial;
        }
        this.branchSteps = 0;
      }
      this.branch.advance();
      this.branchSteps++;
      this.steps++;
      budget--;
      const sample = this.branch.sample(this.request.members);
      if (!sample.safe || this.branchSteps === FORECAST.horizonSteps) {
        this.results.push({ keys: this.plans[this.index]!, ...sample });
        this.branch = null;
        this.index++;
        // Normal recovery needs no alternative trials.
        if (
          this.index === 1 &&
          (!sample.safe ||
            (this.initial - sample.potential) / Math.max(1e-12, this.responseScale) >=
              FORECAST.baselineProgress)
        )
          break;
      }
    }
    const baseline = this.results[0];
    if (
      !baseline ||
      (this.index < this.plans.length &&
        baseline.safe &&
        (this.initial - baseline.potential) / Math.max(1e-12, this.responseScale) <
          FORECAST.baselineProgress)
    )
      return null;
    const selected = selectRelease(
      this.initial,
      baseline,
      this.results.slice(1),
      this.responseScale,
    );
    return {
      request: this.request,
      selected: selected?.keys ?? [],
      baselineReduction: (this.initial - baseline.potential) / Math.max(1e-12, this.responseScale),
      selectedReduction:
        (this.initial - (selected?.potential ?? baseline.potential)) /
        Math.max(1e-12, this.responseScale),
      trials: this.results.length,
      simulatedSteps: this.steps,
    };
  }
}
