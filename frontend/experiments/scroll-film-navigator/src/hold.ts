import { clamp } from "./transport";
export type HoldPhase = "IDLE" | "GROWING" | "CANCELING" | "COVERED_WAIT_READY" | "REVEALING" | "SUCCESS_WAIT_RELEASE";
export type Modality = "mouse" | "pen" | "touch" | "keyboard";
export interface Point { x: number; y: number }
export const HOLD = {
  duration: 3, countdownThresholds: [1/3, 2/3] as const,
  expansionResponse: 14, contractionRate: 0.72, retargetDamping: 9,
  revealDuration: .36, coverageThreshold: .999, touchSlop: 12, readinessTimeout: 12
};
/** Gesture ownership is independent of readiness and of the renderer's persistent material. */
export class Hold {
  phase: HoldPhase = "IDLE";
  progress = 0;
  expansion = 0;
  revealProgress = 0;
  pressed = false;
  modality: Modality = "mouse";
  center: Point = {x:0,y:0};
  target: Point = {x:0,y:0};
  origin: Point = {x:0,y:0};
  revealCenter: Point = {x:0,y:0};
  private waiting = 0;
  cycles = 0;
  failure: string|null = null;
  press(point: Point, modality: Modality): boolean {
    if(this.pressed || this.phase==="REVEALING" || this.phase==="COVERED_WAIT_READY" || this.phase==="SUCCESS_WAIT_RELEASE") return false;
    this.pressed=true; this.modality=modality; this.origin={...point}; this.target={...point};
    if(this.phase==="IDLE") this.center={...point};
    this.phase="GROWING"; this.waiting=0; this.failure=null; return true;
  }
  select(point: Point): void {
    if(this.phase==="REVEALING" || this.phase==="COVERED_WAIT_READY") return;
    if(this.phase==="IDLE" || this.phase==="SUCCESS_WAIT_RELEASE") {this.reset();this.center={...point};}
    this.pressed=false;this.waiting=0;this.modality="keyboard";this.target={...point};
    this.progress=1; this.phase="COVERED_WAIT_READY";this.failure=null;
  }
  move(point: Point): void {
    if(!this.pressed || this.phase==="REVEALING" || this.phase==="SUCCESS_WAIT_RELEASE") return;
    if(this.modality==="touch") {
      if(Math.hypot(point.x-this.origin.x,point.y-this.origin.y)>HOLD.touchSlop && this.phase==="GROWING") this.release();
    } else this.target={...point};
  }
  release(): void {
    this.pressed=false;
    if(this.phase==="GROWING") this.phase="CANCELING";
    else if(this.phase==="SUCCESS_WAIT_RELEASE") this.reset();
    // Earned completion survives release during readiness wait and reveal.
  }
  cancel(): void {
    this.pressed=false;
    if(this.phase==="REVEALING") return;
    if(this.phase==="SUCCESS_WAIT_RELEASE") this.reset();
    else if(this.active)this.phase="CANCELING";
  }
  reset(): void {this.phase="IDLE";this.pressed=false;this.progress=0;this.expansion=0;this.revealProgress=0;this.waiting=0;}
  /** Returns exactly one commit when the aperture finishes. Pair readiness pauses reveal. */
  step(seconds: number, ready: boolean, coverage: number, reduced = false): boolean {
    const clock=Number.isFinite(seconds)?Math.max(0,seconds):0;
    if(this.phase!=="REVEALING" && this.phase!=="SUCCESS_WAIT_RELEASE") {
      const follow=1-Math.exp(-HOLD.retargetDamping*clock);
      this.center.x+=(this.target.x-this.center.x)*follow;this.center.y+=(this.target.y-this.center.y)*follow;
    }
    if(this.phase==="GROWING") {
      this.progress=clamp(this.progress+clock/HOLD.duration);
      if(this.progress===1)this.phase="COVERED_WAIT_READY";
    } else if(this.phase==="CANCELING")this.progress=Math.max(0,this.progress-clock*HOLD.contractionRate);
    if(this.phase==="GROWING" || this.phase==="CANCELING" || this.phase==="COVERED_WAIT_READY") {
      this.expansion+=(this.progress-this.expansion)*(1-Math.exp(-HOLD.expansionResponse*clock));
      if(this.progress===1 && this.expansion>.999)this.expansion=1;
      if(this.phase==="CANCELING" && this.progress===0 && this.expansion<.001)this.reset();
    }
    if(this.phase==="COVERED_WAIT_READY") {
      this.waiting+=clock;
      if(ready && (reduced || (this.expansion===1 && coverage>=HOLD.coverageThreshold))) {
        this.revealCenter={...this.center};this.revealProgress=0;this.waiting=0;this.phase="REVEALING";
      } else if(this.waiting>HOLD.readinessTimeout) {
        this.failure="Incoming frame was not ready; hold canceled. Use Retry frames or select again.";this.cancel();
      }
    } else if(this.phase==="REVEALING") {
      if(ready)this.revealProgress=clamp(this.revealProgress+clock/(reduced?.001:HOLD.revealDuration));
      else this.waiting+=clock;
      if(this.waiting>HOLD.readinessTimeout){this.failure="Incoming frame failed during reveal; outgoing scene retained.";this.phase="CANCELING";this.pressed=false;this.revealProgress=0;return false;}
      if(this.revealProgress===1) {
        this.cycles++;this.expansion=0;this.progress=0;
        this.phase=this.pressed?"SUCCESS_WAIT_RELEASE":"IDLE";return true;
      }
    }
    return false;
  }
  get countdown(): number|null {
    if(this.phase!=="GROWING")return null;
    return this.progress<HOLD.countdownThresholds[0]?3:this.progress<HOLD.countdownThresholds[1]?2:1;
  }
  get active(): boolean {return this.phase!=="IDLE";}
  get animating(): boolean {return this.active&&this.phase!=="SUCCESS_WAIT_RELEASE";}
  get preparing(): boolean {return this.phase==="GROWING" || this.phase==="COVERED_WAIT_READY" || this.phase==="REVEALING";}
}
