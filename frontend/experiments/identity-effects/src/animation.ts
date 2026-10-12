import { openingDelay, openingPose, registrationPose, transform, timing } from "./choreography";

export interface Segment {
  element: SVGGElement;
  id: string;
  x: number;
  y: number;
}

const ease = "cubic-bezier(0.77, 0, 0.175, 1)";

/** One retained WAAPI effect per element. No per-frame JavaScript work. */
export class IdentityAnimation {
  private effects = new Map<Element, Animation>();
  private rotations = new Map<Element, { from: number; to: number; duration: number; delay: number }>();
  readonly segments: Segment[];
  private surface: SVGRectElement;
  private mark: SVGGElement;
  private registration: SVGGElement;
  private reflection: SVGGElement;
  private writingTraces: SVGPathElement[];
  private writingPoints: SVGGElement[];
  private ink: SVGGElement;
  private periodTrace: SVGPathElement;
  private periodPoint: SVGGElement;
  private finish: SVGGElement;
  private plane: HTMLElement;
  private shadow: HTMLElement;

  constructor(svg: SVGSVGElement) {
    this.segments = [...svg.querySelectorAll<SVGGElement>(".segment")].map(element => ({
      element, id: element.dataset.fragment!,
      x: Number(element.dataset.homeX), y: Number(element.dataset.homeY)
    }));
    this.surface = svg.querySelector<SVGRectElement>(".surface")!;
    this.mark = svg.querySelector<SVGGElement>(".m-mark")!;
    this.registration = svg.querySelector<SVGGElement>(".registration")!;
    this.reflection = svg.querySelector<SVGGElement>(".reflection")!;
    this.writingTraces = [...svg.querySelectorAll<SVGPathElement>(".writing-trace")];
    this.writingPoints = [...svg.querySelectorAll<SVGGElement>(".writing-point")];
    this.ink = svg.querySelector<SVGGElement>(".written-ink")!;
    this.periodTrace = svg.querySelector<SVGPathElement>(".period-trace")!;
    this.periodPoint = svg.querySelector<SVGGElement>(".period-point")!;
    this.finish = svg.querySelector<SVGGElement>(".settled-finish")!;
    this.plane = svg.closest(".identity-plane")!;
    this.shadow = svg.closest("button")!.querySelector(".floor-shadow")!;
  }

  private async to(element: Element, target: Keyframe, duration: number, signal: AbortSignal,
    delay = 0, intermediate: Keyframe[] = [], easing?: string): Promise<void> {
    if (signal.aborted) return;
    // Read the rendered pose before canceling, including interrupted effects.
    const style = getComputedStyle(element);
    const from: Keyframe = {};
    const targetAngle = typeof target.transform === "string"
      ? target.transform.match(/rotate\((-?[\d.]+)deg\)/u)?.[1] : undefined;
    if (targetAngle !== undefined) {
      // WebKit omits SVG presentation transforms from computed CSS. Baseline
      // still renders that attribute; sample it rather than inventing identity.
      const attribute = style.transform === "none" && element instanceof SVGGraphicsElement
        ? element.transform.baseVal.consolidate()?.matrix : undefined;
      const matrix = style.transform === "none"
        ? new DOMMatrix(attribute ? [attribute.a, attribute.b, attribute.c, attribute.d, attribute.e, attribute.f] : undefined)
        : new DOMMatrix(style.transform);
      let angle = Math.atan2(matrix.b, matrix.a) * 180 / Math.PI;
      const previous = this.rotations.get(element);
      const currentTime = this.effects.get(element)?.currentTime;
      const progress = previous && typeof currentTime === "number"
        ? Math.min(1, Math.max(0, (currentTime - previous.delay) / previous.duration)) : 0;
      const reference = previous ? previous.from + (previous.to - previous.from) * progress : angle;
      // Matrices encode orientation, not winding. Keep the sampled pose but
      // unwrap its angle onto the previous authored branch, including ±180°.
      angle += 360 * Math.round((reference - angle) / 360);
      from.transform = transform({ x: matrix.e, y: matrix.f, angle });
      this.rotations.set(element, { from: angle, to: Number(targetAngle), duration, delay });
    } else if (target.transform !== undefined) from.transform = style.transform;
    if (target.opacity !== undefined) from.opacity = style.opacity;
    if (target.strokeDashoffset !== undefined) from.strokeDashoffset = style.strokeDashoffset;
    if (target.offsetDistance !== undefined) from.offsetDistance = style.offsetDistance;
    if (target.maskImage !== undefined) from.maskImage = style.maskImage;
    this.effects.get(element)?.cancel();
    const effect = element.animate([from, ...intermediate, target], { duration, delay,
      easing: easing ?? (target.opacity === undefined ? ease : "cubic-bezier(0.23, 1, 0.32, 1)"), fill: "both" });
    this.effects.set(element, effect);
    const cleanup = new AbortController();
    // Cancellation is owned by the next recovery: aborting a task must retain
    // the current visual pose until it can be sampled, rather than snap home.
    const aborted = () => effect.pause();
    signal.addEventListener("abort", aborted, { once: true });
    try {
      await Promise.race([effect.finished.catch(() => undefined), new Promise<void>(resolve => {
        signal.addEventListener("abort", () => resolve(), { once: true, signal: cleanup.signal });
      })]);
    } finally {
      signal.removeEventListener("abort", aborted);
      cleanup.abort();
    }
  }

  async open(variant: number, reduced: boolean, signal: AbortSignal): Promise<void> {
    if (reduced) return;
    const pieces = this.segments.map(s => {
      const pose = openingPose(s, variant);
      return this.to(s.element, { transform: transform(pose) }, timing.open, signal,
        openingDelay(s, variant));
    });
    if (variant === 4) pieces.push(this.to(this.registration,
      { transform: transform(registrationPose(variant)) }, variant === 4 ? 360 : timing.open,
      signal, variant === 4 ? 120 : 0));
    if (variant >= 5) pieces.push(this.airborne(variant, signal));
    await Promise.all(pieces);
  }

  async close(variant: number, reduced: boolean, signal: AbortSignal): Promise<void> {
    const pieces = this.segments.map(async s => {
      if (variant === 3 && !reduced) {
        // Register X, then Y; each leg keeps the authored curve and duration.
        await this.to(s.element, { transform: transform({ x: 0, y: s.y, angle: 0 }) }, 336, signal);
        if (signal.aborted) return;
        await this.to(s.element, { transform: transform({ x: 0, y: 0, angle: 0 }) }, 224, signal);
      } else await this.to(s.element, { transform: transform({ x: 0, y: 0, angle: 0 }) },
        reduced ? timing.quietClose : timing.close, signal);
    });
    if (variant === 4 && !reduced) pieces.push(this.to(this.registration,
      { transform: transform({ x: 0, y: 0, angle: 0 }) }, timing.close, signal));
    await Promise.all(pieces);
    if (!signal.aborted) await this.to(this.surface, { opacity: 1 }, 90, signal);
  }

  private async airborne(variant: number, signal: AbortSignal): Promise<void> {
    // Percent travel scales with the SVG viewport, including a live resize.
    // Contact compression offsets the centre so the feet stay on the floor.
    const pose = (y: number, pitch = 0, sx = 1, sy = 1, roll = 0) =>
      `translateY(${y}%) rotateX(${pitch}deg) rotateZ(${roll}deg) scale(${sx}, ${sy})`;
    const flip = variant !== 7;
    const direction = variant === 5 ? -1 : 1;
    const duration = flip ? (variant === 5 ? 1000 : 1080) : 1250;
    const frames: Keyframe[] = flip ? [
      { offset: .08, transform: pose(3, 0, 1.1, .91), easing: "cubic-bezier(.2, .6, .35, 1)" },
      { offset: .16, transform: pose(0, direction * 12, .96, 1.04) },
      { offset: .36, transform: pose(-13, direction * 100) },
      { offset: .52, transform: pose(-15, direction * 205) },
      { offset: .68, transform: pose(-10, direction * 290), easing: "cubic-bezier(.5, 0, .85, .6)" },
      { offset: .82, transform: pose(0, direction * 360) },
      { offset: .87, transform: pose(4, direction * 360, 1.11, .88) },
      { offset: .94, transform: pose(-1, direction * 360, .99, 1.02) }
    ] : [
      { offset: .10, transform: pose(-2, -5, .98, 1.03), easing: "cubic-bezier(.55, 0, .85, .55)" },
      { offset: .28, transform: pose(7, -8, .98, 1.05, -2), easing: "cubic-bezier(.5, 0, .8, .5)" },
      { offset: .44, transform: pose(14) },
      { offset: .49, transform: pose(21.4, 0, 1.18, .78), easing: "cubic-bezier(.15, .65, .35, 1)" },
      { offset: .56, transform: pose(10, 0, .94, 1.07) },
      { offset: .73, transform: pose(-7, 16, 1, 1, 2), easing: "cubic-bezier(.5, 0, .8, .6)" },
      { offset: .88, transform: pose(2, -3, 1.03, .97) },
      { offset: .96, transform: pose(-1, 0, .99, 1.01) }
    ];
    await Promise.all([
      this.to(this.plane, { transform: pose(0, flip ? direction * 360 : 0) }, duration, signal, 0, frames, "linear"),
      this.to(this.shadow, { opacity: 0, transform: "scale(.65, 1)" }, duration, signal, 0, [
        { offset: .08, opacity: .7, transform: "scale(1, 1)" },
        { offset: flip ? .52 : .28, opacity: .15, transform: "scale(.55, .7)" },
        { offset: flip ? .87 : .49, opacity: .9, transform: "scale(1.2, 1)" },
        { offset: .94, opacity: .45, transform: "scale(.85, 1)" }
      ], "linear")
    ]);
  }

  async reveal(reduced: boolean, signal: AbortSignal): Promise<void> {
    if (reduced) {
      await Promise.all([...this.writingTraces, this.periodTrace].map(trace =>
        this.to(trace, { strokeDashoffset: "0", opacity: 1 }, 0, signal)));
      await this.to(this.ink, { maskImage: "none" }, 0, signal);
      await this.to(this.finish, { opacity: 1 }, 0, signal);
      if (!signal.aborted) await this.to(this.mark, { opacity: 1 }, 180, signal);
      return;
    }
    // Photo checkpoints establish the additions; hidden travel is inferred.
    // Separate nibs hide repositioning between disconnected flourishes.
    await this.to(this.mark, { opacity: 1 }, 70, signal);
    if (signal.aborted) return;
    const total = this.writingTraces.reduce((sum, trace) => sum + trace.getTotalLength(), 0);
    let elapsed = 0;
    await Promise.all([
      ...this.writingTraces.flatMap((trace, i) => {
        // Give the short trailing stroke time to read as writing, not a flash.
        const duration = Math.max(120, timing.write * trace.getTotalLength() / total);
        const drawing = this.to(trace, { strokeDashoffset: "0", opacity: 1 }, duration, signal, elapsed,
          [{ opacity: 1, offset: .001 }], "linear");
        const pen = this.to(this.writingPoints[i]!, { offsetDistance: "100%", opacity: 0 }, duration, signal, elapsed,
          [{ opacity: 1, offset: .04 }, { opacity: 1, offset: .92 }], "linear");
        elapsed += duration + (i < this.writingTraces.length - 1 ? 70 : 0);
        return [drawing, pen];
      })
    ]);
    if (signal.aborted) return;
    // Full nib coverage is complete. Drop the temporary raster mask so thin
    // reference strokes use the same crisp vector painting as the final asset.
    await this.to(this.ink, { maskImage: "none" }, 0, signal);
    await dwell(timing.penLift, signal);
    if (signal.aborted) return;
    await Promise.all([
      this.to(this.periodTrace, { strokeDashoffset: "0", opacity: 1 }, timing.period, signal, 0,
        [{ opacity: 1, offset: .001 }], "linear"),
      this.to(this.periodPoint, { offsetDistance: "100%", opacity: 0 }, timing.period, signal, 0,
        [{ opacity: 1, offset: .15 }, { opacity: 1, offset: .7 }], "linear")
    ]);
    if (signal.aborted) return;
    await this.to(this.finish, { opacity: 1 }, 80, signal);
    // One broad turquoise reflection across the written letter, then stillness.
    await this.to(this.reflection, { opacity: 0, transform: "translateX(100px)" },
      timing.reflection, signal, 0, [
        { opacity: .95, offset: .22 }, { opacity: .95, offset: .78 }
      ], "linear");
  }

  async reset(reduced: boolean, signal: AbortSignal): Promise<void> {
    if (Number(getComputedStyle(this.mark).opacity) > 0) {
      await this.to(this.mark, { opacity: 0 },
        reduced ? 120 : 80, signal);
    }
    if (signal.aborted) return;
    // The signature is hidden before the reflection returns to its start.
    this.effects.get(this.reflection)?.cancel();
    this.effects.delete(this.reflection);
    for (const element of [...this.writingTraces, ...this.writingPoints, this.ink, this.periodTrace, this.periodPoint, this.finish]) {
      this.effects.get(element)?.cancel();
      this.effects.delete(element);
    }
    if (Number(getComputedStyle(this.surface).opacity) > 0) {
      await this.to(this.surface, { opacity: 0 }, 70, signal);
    }
    if (signal.aborted) return;
    await Promise.all([
      this.to(this.plane, { transform: "translateY(0%) rotateX(0deg) scale(1, 1)" }, reduced ? 0 : timing.reset, signal),
      this.to(this.shadow, { opacity: 0, transform: "scale(.65, 1)" }, reduced ? 0 : timing.reset, signal),
      ...this.segments.map(s => this.to(s.element,
        { transform: transform({ x: s.x, y: s.y, angle: 0 }) }, reduced ? timing.quietClose : timing.reset, signal)),
      this.to(this.registration, { transform: transform({ x: 0, y: 0, angle: 0 }) },
        reduced ? timing.quietClose : timing.reset, signal)
    ]);
    if (!signal.aborted) this.clear();
  }

  clear(): void {
    this.effects.forEach(effect => effect.cancel());
    this.effects.clear();
    this.rotations.clear();
  }

  destroy(): void { this.clear(); }
}

/** Abort-aware dwell; canceled cycles cannot resume or create another loop. */
export function dwell(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise(resolve => {
    if (signal.aborted) { resolve(); return; }
    const finish = () => { clearTimeout(timer); signal.removeEventListener("abort", finish); resolve(); };
    const timer = setTimeout(finish, ms);
    signal.addEventListener("abort", finish, { once: true });
  });
}
