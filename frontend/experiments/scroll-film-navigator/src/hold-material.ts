import { clamp } from "./transport";
import type { Hold, Point } from "./hold";
import { radiusFor } from "./material";
const smooth=(a:number,b:number,x:number)=>{const t=clamp((x-a)/(b-a));return t*t*(3-2*t);};
function terrain(x:number,y:number):number {
  // Stationary material structure. It is injected once, then carried by the stored field.
  return Math.sin(x*.018+Math.sin(y*.021))* .55+Math.sin(y*.029-x*.011)*.3+Math.sin(x*.047+y*.033)*.15;
}
/** Persistent signed-distance lattice. Translation advects the existing field; growth
 * dilates it and cancellation erodes it. Re-press never re-seeds the lattice. */
export class HoldMaterial {
  readonly width:number;readonly height:number;readonly pixels:Uint8Array;
  readonly distances:Float32Array;
  private scratch:Float32Array;private scratchStructure:Float32Array;
  private revealTerrain:Float32Array;
  private structure:Float32Array;
  private previousRadius=0;
  private previousCenter:Point={x:0,y:0};
  private initialized=false;
  private revealing=false;
  coverage=0;area=0;revision=0;generations=0;
  constructor(readonly stageWidth:number,readonly stageHeight:number,resolution=192) {
    this.width=resolution;this.height=Math.min(256,Math.max(48,Math.round(resolution*stageHeight/stageWidth)));
    const n=this.width*this.height;this.distances=new Float32Array(n);this.scratch=new Float32Array(n);this.scratchStructure=new Float32Array(n);this.revealTerrain=new Float32Array(n);this.structure=new Float32Array(n);
    for(let y=0;y<this.height;y++)for(let x=0;x<this.width;x++)this.structure[y*this.width+x]=terrain((x+.5)/this.width*stageWidth,(y+.5)/this.height*stageHeight);
    this.pixels=new Uint8Array(n*4);this.clear();
  }
  private sample(buffer:Float32Array,x:number,y:number,extrapolate=true):number {
    const cx=clamp(x,0,this.width-1),cy=clamp(y,0,this.height-1),ix=Math.floor(cx),iy=Math.floor(cy),fx=cx-ix,fy=cy-iy;
    const at=(px:number,py:number)=>buffer[Math.min(this.height-1,py)*this.width+Math.min(this.width-1,px)]!;
    const inside=(at(ix,iy)*(1-fx)+at(ix+1,iy)*fx)*(1-fy)+(at(ix,iy+1)*(1-fx)+at(ix+1,iy+1)*fx)*fy;
    // Linear extrapolation preserves distance gradients when the effective center exits the viewport.
    const slopeX=at(cx<=0?1:this.width-1,iy)-at(cx<=0?0:this.width-2,iy);
    const slopeY=at(ix,cy<=0?1:this.height-1)-at(ix,cy<=0?0:this.height-2);
    return extrapolate?inside+(x-cx)*slopeX+(y-cy)*slopeY:inside;
  }
  step(hold:Hold):void {
    if(!hold.animating){if(this.initialized)this.clear();return;}
    const w=this.stageWidth,h=this.stageHeight,diagonal=Math.hypot(w,h);
    if(!this.initialized) {
      this.generations++;this.initialized=true;this.previousCenter={...hold.center};this.previousRadius=0;
      for(let y=0;y<this.height;y++)for(let x=0;x<this.width;x++) {
        const px=(x+.5)/this.width*w,py=(y+.5)/this.height*h;
        this.distances[y*this.width+x]=Math.hypot(px-hold.center.x,py-hold.center.y)+this.structure[y*this.width+x]!*diagonal*.045;
      }
    }
    if(hold.phase!=="REVEALING") {
      this.revealing=false;
      const radius=Math.max(0,radiusFor(Math.pow(hold.expansion,1.6),hold.center,w,h)),delta=radius-this.previousRadius;
      const dx=(hold.center.x-this.previousCenter.x)/w*this.width,dy=(hold.center.y-this.previousCenter.y)/h*this.height;
      for(let y=0;y<this.height;y++)for(let x=0;x<this.width;x++) {
        const i=y*this.width+x,structure=dx===0&&dy===0?this.structure[i]!:this.sample(this.structure,x-dx,y-dy,false);
        this.scratchStructure[i]=structure;
        const d=(dx===0&&dy===0?this.distances[i]!:this.sample(this.distances,x-dx,y-dy))-delta*(1+structure*.12);
        this.scratch[y*this.width+x]=d;
      }
      this.distances.set(this.scratch);this.structure.set(this.scratchStructure);
      if(hold.expansion===1){let edge=-Infinity;for(const distance of this.distances)edge=Math.max(edge,distance);const correction=edge+Math.max(3,w/this.width*.7)*2;if(correction>0)for(let i=0;i<this.distances.length;i++)this.distances[i]=this.distances[i]!-correction;}
      this.previousCenter={...hold.center};this.previousRadius=radius;
    } else if(!this.revealing) {
      this.revealing=true;
      const center=hold.revealCenter;
      for(let y=0;y<this.height;y++)for(let x=0;x<this.width;x++) {
        const px=(x+.5)/this.width*w,py=(y+.5)/this.height*h,d=Math.hypot(px-center.x,py-center.y);
        // Freeze this aperture's spatial topology and origin at reveal initiation.
        this.revealTerrain[y*this.width+x]=d+this.structure[y*this.width+x]!*Math.min(d*.2,diagonal*.045);
      }
    }
    const softness=Math.max(3,w/this.width*.7),radius=hold.phase==="REVEALING"?radiusFor(hold.revealProgress,hold.revealCenter,w,h):-1;
    let covered=0,area=0;
    for(let i=0;i<this.distances.length;i++) {
      const mask=hold.expansion<=0?0:1-smooth(-softness,softness,this.distances[i]!);
      const d=this.revealTerrain[i]!-radius,reveal=radius<0?0:1-smooth(-softness,softness,d);
      const rim=radius<0?0:Math.exp(-Math.abs(d)/(softness*1.8));
      const j=i*4;this.pixels[j]=Math.round(mask*255);this.pixels[j+1]=Math.round(reveal*255);this.pixels[j+2]=Math.round(rim*255);this.pixels[j+3]=255;
      if(mask>.995)covered++;if(mask>.5)area++;
    }
    this.coverage=covered/this.distances.length;this.area=area;this.revision++;
  }
  clear():void {this.pixels.fill(0);this.distances.fill(1e6);this.previousRadius=0;this.initialized=false;this.revealing=false;this.coverage=0;this.area=0;this.revision++;}
  get bytes():number {return this.distances.byteLength+this.scratch.byteLength+this.scratchStructure.byteLength+this.revealTerrain.byteLength+this.structure.byteLength+this.pixels.byteLength;}
}
