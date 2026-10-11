import { clamp } from "./transport";
export type HoldPhase = "IDLE" | "HOLDING" | "CANCELING" | "COMMIT" | "GRACE";
export type Modality = "mouse" | "pen" | "touch" | "keyboard";
export interface Point { x: number; y: number }
export const HOLD = {
  duration: 2.8, countdownThresholds: [1/3, 2/3] as const,
  expansionResponse: 10, contractionRate: 0.72, retargetDamping: 9,
  gracePeriod: 0.55, coverageThreshold: 0.997, touchSlop: 12,
  readinessTimeout: 12
};
export class Hold {
  phase: HoldPhase = "IDLE";
  progress = 0;
  expansion = 0;
  pressed = false;
  modality: Modality = "mouse";
  center: Point = {x:0,y:0};
  target: Point = {x:0,y:0};
  origin: Point = {x:0,y:0};
  private grace = 0;
  private waiting = 0;
  cycles = 0;
  press(point: Point, modality: Modality): boolean {
    if(this.pressed || this.phase==="COMMIT" || this.phase==="GRACE") return false;
    this.pressed=true; this.modality=modality; this.origin={...point}; this.target={...point};
    if(this.phase==="IDLE") this.center={...point};
    this.phase="HOLDING"; this.waiting=0; return true;
  }
  select(point: Point): void {
    if(this.modality==="keyboard" && this.phase==="COMMIT") return;
    if(!this.active || this.phase==="GRACE") {this.reset();this.center={...point};}
    this.pressed=false;this.waiting=0;this.modality="keyboard";this.target={...point};
    this.progress=1; this.phase="COMMIT";
  }
  move(point: Point): void {
    if(!this.pressed) return;
    if(this.modality==="touch") {
      if(Math.hypot(point.x-this.origin.x,point.y-this.origin.y)>HOLD.touchSlop) this.release();
    } else this.target={...point};
  }
  release(): void {
    this.pressed=false;
    if(this.phase==="HOLDING" || this.phase==="COMMIT") this.phase="CANCELING";
    else if(this.phase==="GRACE") this.reset();
  }
  reset(): void { this.phase="IDLE"; this.pressed=false; this.progress=0; this.expansion=0; this.grace=0; this.waiting=0; }
  /** A commit is returned only after duration, actual visual coverage and matching frame readiness. */
  step(seconds: number, ready: boolean, coverage: number, reduced = false): boolean {
    const clock=Number.isFinite(seconds)?Math.max(0,seconds):0;
    // These first-order responses are analytic and stable for the actual elapsed
    // time. Capping their clock made growth/retargeting depend on the RAF rate.
    // Visibility suspension resets the gesture instead of integrating hidden time.
    const follow=1-Math.exp(-HOLD.retargetDamping*clock);
    this.center.x+=(this.target.x-this.center.x)*follow;
    this.center.y+=(this.target.y-this.center.y)*follow;
    if(this.phase==="HOLDING") {
      this.progress=clamp(this.progress+clock/HOLD.duration);
      if(this.progress===1) this.phase="COMMIT";
    } else if(this.phase==="CANCELING") {
      this.progress=Math.max(0,this.progress-clock*HOLD.contractionRate);
    } else if(this.phase==="GRACE") {
      this.grace+=clock;
      if(this.grace>=HOLD.gracePeriod) {
        if(this.pressed) { this.phase="HOLDING"; this.progress=0; this.expansion=0; this.grace=0; }
        else this.reset();
      }
    }
    if(this.phase==="HOLDING" || this.phase==="CANCELING" || this.phase==="COMMIT") {
      this.expansion+=(this.progress-this.expansion)*(1-Math.exp(-HOLD.expansionResponse*clock));
      if(this.progress===1 && this.expansion>0.999) this.expansion=1;
      if(this.phase==="CANCELING" && this.progress===0 && this.expansion<0.001) this.reset();
    }
    if(this.phase==="COMMIT") {
      this.waiting+=clock;
      if(ready && (reduced || coverage>=HOLD.coverageThreshold)) {
        this.phase="GRACE"; this.progress=1; this.expansion=1; this.grace=0; this.cycles++; return true;
      }
      if(this.waiting>HOLD.readinessTimeout) this.release();
    }
    return false;
  }
  get countdown(): number | null {
    if(this.phase!=="HOLDING" && this.phase!=="COMMIT") return null;
    return this.progress<HOLD.countdownThresholds[0]?3:this.progress<HOLD.countdownThresholds[1]?2:1;
  }
  get active(): boolean { return this.phase!=="IDLE"; }
}
