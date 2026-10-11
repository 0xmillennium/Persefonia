import { clamp } from "./transport";
import type { MotionSample } from "./input";
export const FIELD = { width: 144, maxHeight: 192, dissipation: 2.3, velocityDrag: 3.5, viscosity: 0.09, cutoff: 0.012, lifetime: 4.2 };
/** Bounded semi-Lagrangian viscous field, in CSS pixels/second. No DOM or renderer dependencies. */
export class LiquidField {
  readonly width: number;
  readonly height: number;
  readonly pixels: Uint8Array;
  private density: Float32Array;
  private vx: Float32Array;
  private vy: Float32Array;
  private nextD: Float32Array;
  private nextX: Float32Array;
  private nextY: Float32Array;
  private age=FIELD.lifetime;
  energy=0;
  peak=0;
  active=false;
  injections=0;
  constructor(readonly stageWidth: number, readonly stageHeight: number, width=FIELD.width) {
    this.width=width; this.height=Math.min(FIELD.maxHeight,Math.max(48,Math.round(width*stageHeight/stageWidth)));
    const n=this.width*this.height;
    this.density=new Float32Array(n); this.vx=new Float32Array(n); this.vy=new Float32Array(n);
    this.nextD=new Float32Array(n); this.nextX=new Float32Array(n); this.nextY=new Float32Array(n); this.pixels=new Uint8Array(n*4);
    this.encode();
  }
  inject(sample: MotionSample): void {
    if(!Number.isFinite(sample.x+sample.y+sample.speed+sample.vx+sample.vy+sample.distance) || sample.speed<=0 || sample.distance<=0) return;
    this.age=0; this.active=true; this.injections++;
    const speed=clamp(sample.speed/1800), radius=40-28*speed, length=radius+speed*65;
    const dirX=sample.vx/sample.speed, dirY=sample.vy/sample.speed;
    // Resample the actual segment; at most 32 splats. Distance-weighted dosing removes event-count dependence.
    const count=Math.min(32,Math.max(1,Math.ceil(sample.distance/Math.max(radius*.65,4))));
    const dose=Math.min(sample.distance/count/radius,2)*0.8;
    for(let s=0;s<count;s++) {
      const back=sample.distance*(s+.5)/count;
      const cx=sample.x-dirX*back, cy=sample.y-dirY*back;
      const extent=length*2.1;
      const minX=Math.max(0,Math.floor((cx-extent)/this.stageWidth*this.width));
      const maxX=Math.min(this.width-1,Math.ceil((cx+extent)/this.stageWidth*this.width));
      const minY=Math.max(0,Math.floor((cy-extent)/this.stageHeight*this.height));
      const maxY=Math.min(this.height-1,Math.ceil((cy+extent)/this.stageHeight*this.height));
      for(let y=minY;y<=maxY;y++) for(let x=minX;x<=maxX;x++) {
        const dx=(x+.5)/this.width*this.stageWidth-cx, dy=(y+.5)/this.height*this.stageHeight-cy;
        const along=dx*dirX+dy*dirY, across=-dx*dirY+dy*dirX;
        const weight=Math.exp(-1.8*(along*along/(length*length)+across*across/(radius*radius)));
        if(weight<.015) continue;
        const i=y*this.width+x;
        this.density[i]=Math.min(2.5,this.density[i]!+weight*dose);
        // Damped directional transport plus paired shear; neighboring wakes merge as one scalar material.
        const curl=Math.sin(along/radius*2.2)*across/radius;
        this.vx[i]=clamp(this.vx[i]!+weight*(sample.vx*.07-dirY*curl*45)*dose,-260,260);
        this.vy[i]=clamp(this.vy[i]!+weight*(sample.vy*.07+dirX*curl*45)*dose,-260,260);
      }
    }
  }
  private sample(buffer: Float32Array,x: number,y: number): number {
    x=clamp(x,0,this.width-1); y=clamp(y,0,this.height-1);
    const ix=Math.floor(x), iy=Math.floor(y), fx=x-ix, fy=y-iy;
    const a=iy*this.width+ix, b=iy*this.width+Math.min(ix+1,this.width-1);
    const c=Math.min(iy+1,this.height-1)*this.width+ix, d=Math.min(iy+1,this.height-1)*this.width+Math.min(ix+1,this.width-1);
    return (buffer[a]!*(1-fx)+buffer[b]!*fx)*(1-fy)+(buffer[c]!*(1-fx)+buffer[d]!*fx)*fy;
  }
  step(elapsed: number): void {
    if(!this.active) return;
    this.age+=elapsed;
    if(this.age>FIELD.lifetime) { this.clear(); return; }
    const dt=clamp(elapsed,0,1/30), decay=Math.exp(-FIELD.dissipation*elapsed), drag=Math.exp(-FIELD.velocityDrag*elapsed);
    let energy=0, peak=0;
    for(let y=0;y<this.height;y++) for(let x=0;x<this.width;x++) {
      const i=y*this.width+x;
      const sx=x-this.vx[i]!*dt*this.width/this.stageWidth, sy=y-this.vy[i]!*dt*this.height/this.stageHeight;
      let d=this.sample(this.density,sx,sy)*decay;
      if(d<FIELD.cutoff) d=0;
      const left=y*this.width+Math.max(0,x-1), right=y*this.width+Math.min(this.width-1,x+1);
      const top=Math.max(0,y-1)*this.width+x, bottom=Math.min(this.height-1,y+1)*this.width+x;
      const diffuse=FIELD.viscosity*dt*60;
      this.nextD[i]=d;
      this.nextX[i]=(this.sample(this.vx,sx,sy)*(1-diffuse)+(this.vx[left]!+this.vx[right]!+this.vx[top]!+this.vx[bottom]!)*diffuse*.25)*drag;
      this.nextY[i]=(this.sample(this.vy,sx,sy)*(1-diffuse)+(this.vy[left]!+this.vy[right]!+this.vy[top]!+this.vy[bottom]!)*diffuse*.25)*drag;
      energy+=d; peak=Math.max(peak,d);
    }
    [this.density,this.nextD]=[this.nextD,this.density]; [this.vx,this.nextX]=[this.nextX,this.vx]; [this.vy,this.nextY]=[this.nextY,this.vy];
    this.energy=energy; this.peak=peak; this.active=peak>FIELD.cutoff;
    this.encode();
  }
  private encode(): void {
    for(let i=0;i<this.density.length;i++) {
      const j=i*4;
      this.pixels[j]=Math.round(clamp(.5+this.vx[i]!/520)*255);
      this.pixels[j+1]=Math.round(clamp(.5+this.vy[i]!/520)*255);
      this.pixels[j+2]=Math.round(clamp(this.density[i]!)*255); this.pixels[j+3]=255;
    }
  }
  clear(): void { this.density.fill(0); this.vx.fill(0); this.vy.fill(0); this.energy=0; this.peak=0; this.active=false; this.age=FIELD.lifetime; this.encode(); }
  metrics(): {mass:number; area:number; varianceX:number; varianceY:number; components:number} {
    let mass=0, mx=0, my=0, area=0;
    for(let y=0;y<this.height;y++) for(let x=0;x<this.width;x++) { const d=this.density[y*this.width+x]!; mass+=d; mx+=x*d; my+=y*d; if(d>.1) area++; }
    mx/=mass||1; my/=mass||1;
    let varianceX=0,varianceY=0,components=0;
    const seen=new Uint8Array(this.density.length), stack:number[]=[];
    for(let y=0;y<this.height;y++) for(let x=0;x<this.width;x++) {
      const i=y*this.width+x,d=this.density[i]!;
      varianceX+=(x-mx)**2*d; varianceY+=(y-my)**2*d;
      if(d<=.1 || seen[i]) continue;
      components++; stack.push(i); seen[i]=1;
      while(stack.length) { const j=stack.pop()!; for(const k of [j-1,j+1,j-this.width,j+this.width]) if(k>=0&&k<seen.length&&Math.abs(k%this.width-j%this.width)<=1&&!seen[k]&&this.density[k]!>.1) {seen[k]=1;stack.push(k);} }
    }
    return {mass,area,varianceX:varianceX/(mass||1),varianceY:varianceY/(mass||1),components};
  }
  get bytes(): number { return this.density.byteLength*6+this.pixels.byteLength; }
}
