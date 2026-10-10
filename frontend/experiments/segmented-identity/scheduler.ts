import { FIXED_DT } from "./core";

export const FRAME = Object.freeze({ secondsLimit: .05, ticksLimit: 2, millisecondsBudget: 10, forecastSteps: 4 });

// Budget between deterministic ticks; never change physics dt to catch up.
// A tick cannot be preempted. Live strokes bound subdivisions independently of
// travel distance; a freshly woken pointer is consumed on its first frame.
export class FixedScheduler {
  private previous: number | undefined;
  private accumulator = 0;
  reset(): void { this.previous = undefined; this.accumulator = 0; }
  advance(time: number, tick: (forecastBudget: number) => number, now = () => performance.now(), inputPending = false): number {
    this.accumulator += this.previous === undefined ? inputPending ? FIXED_DT : 0
      : Math.min(Math.max(0, (time - this.previous) / 1000), FRAME.secondsLimit);
    this.previous = time;
    const started = now(); let count = 0, forecastBudget: number = FRAME.forecastSteps;
    while (this.accumulator + 1e-12 >= FIXED_DT && count < FRAME.ticksLimit && now() - started < FRAME.millisecondsBudget) {
      forecastBudget = Math.max(0, forecastBudget - tick(forecastBudget));
      this.accumulator -= FIXED_DT; count++;
    }
    // Discard delayed wall-clock time instead of accumulating runaway work.
    this.accumulator = Math.min(this.accumulator, FIXED_DT);
    return count;
  }
}
