import type { Modality, Point } from "./hold";
export interface MotionSample extends Point { vx: number; vy: number; speed: number; distance: number }
/** Event timestamps, not event counts, define speed. Duplicate/coincident observations are ignored. */
export class PointerObserver {
  private last: (Point & {time:number}) | null = null;
  modality: Modality = "mouse";
  observe(x: number,y: number,time: number): MotionSample | null {
    if(!Number.isFinite(x+y+time)) return null;
    const old=this.last;
    if(old && time<=old.time) return null;
    this.last={x,y,time};
    if(!old || time-old.time>750) return null;
    const distance=Math.hypot(x-old.x,y-old.y);
    if(distance<0.1) return null;
    const dt=Math.max((time-old.time)/1000,0.001);
    const speed=Math.min(distance/dt,4500);
    return {x,y,vx:(x-old.x)/distance*speed,vy:(y-old.y)/distance*speed,speed,distance};
  }
  reset(): void { this.last=null; }
}
export function eligible(button: number, primary: boolean, excluded: boolean, point: Point, width: number, height: number): boolean {
  return button===0 && primary && !excluded && point.x>=0 && point.y>=0 && point.x<=width && point.y<=height;
}
