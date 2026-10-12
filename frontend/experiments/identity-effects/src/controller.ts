import { dwell, IdentityAnimation } from "./animation";
import { timing, variants } from "./choreography";

export type Mode = "loading" | "hover";
export type Phase = "baseline" | "active" | "closing" | "closed" | "reveal" | "resolved" | "reset";

/** Triggers own intent; the animation owns poses. Every task has one lifetime. */
export class IdentityController {
  mode: Mode = "loading";
  paused = false;
  private pointer = false;
  private latched = false;
  private cycle = 0;
  private task = new AbortController();
  private lifetime = new AbortController();
  private reduced = matchMedia("(prefers-reduced-motion: reduce)");
  private instruction = document.querySelector<HTMLElement>("#instruction")!;
  private status = document.querySelector<HTMLElement>("#status")!;
  private pauseButton = document.querySelector<HTMLButtonElement>("#pause")!;
  private modeButtons = [...document.querySelectorAll<HTMLButtonElement>(".modes button")];

  constructor(private stage: HTMLElement, private trigger: HTMLButtonElement,
    private animation: IdentityAnimation) {
    const options = { signal: this.lifetime.signal };
    this.modeButtons.forEach(button => {
      button.disabled = false;
      button.addEventListener("click", () => this.setMode(button.dataset.mode as Mode), options);
    });
    trigger.disabled = false;
    this.pauseButton.disabled = false;
    this.pauseButton.addEventListener("click", () => {
      this.paused = !this.paused;
      this.refresh(); this.reconcile();
    }, options);
    trigger.addEventListener("pointerenter", event => {
      if (event.pointerType === "touch" || this.mode !== "hover" || this.pointer) return;
      const engaged = this.engaged;
      this.pointer = true;
      this.refresh();
      if (!engaged) this.reconcile();
    }, options);
    trigger.addEventListener("pointerleave", () => {
      if (!this.pointer) return;
      this.pointer = false;
      this.refresh();
      if (!this.engaged) this.reconcile();
    }, options);
    trigger.addEventListener("pointercancel", () => {
      this.pointer = false; this.latched = false;
      this.refresh(); this.reconcile();
    }, options);
    // Native activation covers Enter, Space, touch, and assistive technology.
    trigger.addEventListener("click", event => {
      if (this.mode !== "hover" || (event.detail > 0 && this.pointer)) return;
      const engaged = this.engaged;
      this.latched = !this.latched;
      this.refresh();
      if (engaged !== this.engaged) this.reconcile();
    }, options);
    trigger.addEventListener("blur", () => {
      if (!this.latched) return;
      this.latched = false;
      this.refresh();
      if (!this.engaged) this.reconcile();
    }, options);
    trigger.addEventListener("keydown", event => {
      if (event.key !== "Escape") return;
      this.pointer = false; this.latched = false;
      this.refresh(); this.reconcile();
    }, options);
    this.reduced.addEventListener("change", () => {
      if (this.reduced.matches) {
        // Remove vestibular motion immediately; don't unwind a rotated frame
        // through recovery after the user has requested reduced motion.
        this.task.abort(); this.animation.clear(); this.phase("baseline");
      }
      this.refresh(); this.reconcile();
    }, options);
    document.addEventListener("visibilitychange", () => {
      if (document.hidden) {
        this.task.abort(); this.animation.clear(); this.phase("baseline");
        this.pointer = false; this.latched = false;
        this.refresh();
      } else this.reconcile();
    }, options);
    this.refresh();
    this.reconcile();
  }

  private get engaged(): boolean { return this.pointer || this.latched; }

  setMode(mode: Mode): void {
    if (mode === this.mode) return;
    this.mode = mode;
    this.paused = false;
    this.pointer = false; this.latched = false;
    this.refresh(); this.reconcile();
  }

  private phase(phase: Phase): void { this.stage.dataset.state = phase; }

  private refresh(): void {
    this.stage.dataset.mode = this.mode;
    this.stage.dataset.reduced = String(this.reduced.matches);
    this.modeButtons.forEach(b => b.setAttribute("aria-pressed", String(b.dataset.mode === this.mode)));
    this.pauseButton.hidden = this.mode !== "loading";
    this.pauseButton.textContent = this.paused ? "Resume loop" : "Pause loop";
    this.pauseButton.setAttribute("aria-pressed", String(this.paused));
    this.trigger.setAttribute("aria-pressed", String(this.mode === "hover" && this.engaged));
    this.trigger.setAttribute("aria-label", this.mode === "hover" ? "Inspect identity animation" : "Identity animation preview");
    this.instruction.textContent = this.mode === "loading"
      ? (this.paused ? "Loop paused. The original identity is at rest." : `A continuous cycle. One identity, ${variants.length} movements.`)
      : "Hover to unfold. Enter, Space or tap to hold. Escape to reset.";
    document.querySelector<HTMLElement>("#motion-note")!.hidden = !this.reduced.matches;
    this.status.textContent = this.mode === "loading"
      ? (this.paused ? "Loading loop paused." : "Loading Mode. Identity animation repeating.")
      : (this.engaged ? "Inspecting signature. Activate again or leave to reset." : "Hover Mode. Identity at rest; focus the mark and press Enter to inspect.");
  }

  private reconcile(): void {
    this.task.abort();
    this.task = new AbortController();
    const signal = this.task.signal;
    void this.run(signal).catch(() => {
      if (signal.aborted) return;
      this.task.abort(); this.animation.clear(); this.phase("baseline");
      this.paused = true; this.refresh();
      this.status.textContent = "Animation unavailable. The original identity is shown; reload to retry.";
      this.instruction.textContent = this.status.textContent;
    });
  }

  private async run(signal: AbortSignal): Promise<void> {
    const reduced = this.reduced.matches;
    if (document.hidden) return;
    if (this.stage.dataset.state !== "baseline") {
      this.phase("reset");
      await this.animation.reset(reduced, signal);
      if (signal.aborted) return;
      this.phase("baseline");
    }
    while (!signal.aborted && !document.hidden
      && (this.mode === "loading" ? !this.paused : this.engaged)) {
      await dwell(this.mode === "loading" ? timing.rest : 0, signal);
      if (signal.aborted) return;
      const variant = this.cycle % variants.length;
      this.cycle++;
      this.stage.dataset.cycle = String(this.cycle);
      this.stage.dataset.variant = variants[variant]!.id;
      this.phase("active");
      await this.animation.open(variant, reduced, signal);
      if (signal.aborted) return;
      this.phase("closing");
      await this.animation.close(variant, reduced, signal);
      if (signal.aborted) return;
      this.phase("closed");
      await dwell(reduced ? 120 : timing.closed, signal);
      if (signal.aborted) return;
      this.phase("reveal");
      await this.animation.reveal(reduced, signal);
      if (signal.aborted) return;
      this.phase("resolved");
      // Hover has no timer/RAF loop while held. A new entry owns the next cycle.
      if (this.mode === "hover") return;
      await dwell(reduced ? timing.quietHold : timing.hold, signal);
      if (signal.aborted) return;
      this.phase("reset");
      await this.animation.reset(reduced, signal);
      if (signal.aborted) return;
      this.phase("baseline");
    }
  }

  destroy(): void {
    this.task.abort(); this.lifetime.abort(); this.animation.destroy();
  }
}
