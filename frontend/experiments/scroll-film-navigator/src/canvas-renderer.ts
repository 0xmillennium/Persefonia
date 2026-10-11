import { boundaryNoise, radiusFor } from "./material";
import type { Scene } from "./renderer";
import type { FrameKey } from "./transport";

const smooth=(low:number,high:number,value:number)=>{const t=Math.max(0,Math.min(1,(value-low)/(high-low)));return t*t*(3-2*t);};
const tint=[.16,.17,.145] as const,shine=[.12,.14,.125] as const;
interface Slot {canvas:HTMLCanvasElement;context:CanvasRenderingContext2D;key:FrameKey|null;image:ImageBitmap|null;pixels:ImageData|null}
function surface(): {canvas:HTMLCanvasElement;context:CanvasRenderingContext2D} {
  const canvas=document.createElement("canvas"),context=canvas.getContext("2d",{alpha:false,willReadFrequently:true});
  if(!context)throw new Error("Canvas 2D unavailable");return {canvas,context};
}
/** Full CPU-capable compositor. Only moving material pixels are resampled; static film
 * and the organic two-film mask use native 2D blits/paths. No GPU or WebGL required. */
export class CanvasRenderer {
  readonly kind="canvas2d";
  private context:CanvasRenderingContext2D;
  private slots:Slot[]=Array.from({length:2},()=>({...surface(),key:null,image:null,pixels:null}));
  private material=document.createElement("canvas");
  private materialContext:CanvasRenderingContext2D;
  private materialPixels:ImageData;
  private gradients=new Float32Array(0);
  private boundary=surface();
  private dirty:{x:number;y:number;width:number;height:number}|null=null;
  readonly gpuSamples:number[]=[];
  width=1;height=1;uploads=0;draws=0;disposed=false;
  constructor(readonly canvas:HTMLCanvasElement) {
    const context=canvas.getContext("2d",{alpha:false,willReadFrequently:true});
    const materialContext=this.material.getContext("2d",{willReadFrequently:true});
    if(!context||!materialContext)throw new Error("Canvas 2D allocation failed");
    this.context=context;this.materialContext=materialContext;this.materialPixels=materialContext.createImageData(1,1);
  }
  resize(width:number,height:number):void {
    this.width=width;this.height=height;
    // Film stays crisp at a bounded 1280px long edge; refracting material has its own
    // 720px long-edge budget. Resampling never processes a full 4K frame per RAF.
    const scale=Math.min(devicePixelRatio,1.5,1280/Math.max(width,height));
    this.canvas.width=Math.max(1,Math.round(width*scale));this.canvas.height=Math.max(1,Math.round(height*scale));
    const materialScale=Math.min(1,720/Math.max(width,height));
    this.material.width=Math.max(1,Math.round(width*materialScale));this.material.height=Math.max(1,Math.round(height*materialScale));
    this.materialPixels=this.materialContext.createImageData(this.material.width,this.material.height);this.dirty=null;
    for(const slot of this.slots){slot.canvas.width=this.canvas.width;slot.canvas.height=this.canvas.height;if(slot.image)this.project(slot);}
  }
  private project(slot:Slot):void {
    const image=slot.image!;const ctx=slot.context,w=slot.canvas.width,h=slot.canvas.height;
    ctx.fillStyle="#0c0f0d";ctx.fillRect(0,0,w,h);
    const portrait=this.width/this.height<.9;
    const scale=portrait?w/image.width:Math.max(w/image.width,h/image.height),iw=image.width*scale,ih=image.height*scale;
    if(portrait){
      const margin=Math.max(0,(h-ih)*.5);
      // Extend only the source's boundary colors. Stretching the whole source into
      // the backdrop would create a second, distorted subject in portrait.
      // Collapse each boundary row horizontally before extending its color.
      // This avoids turning source grass detail into vertical portrait stripes.
      const edge=this.boundary;edge.canvas.width=1;edge.canvas.height=2;edge.context.imageSmoothingQuality="high";
      edge.context.drawImage(image,0,0,image.width,1,0,0,1,1);
      edge.context.drawImage(image,0,image.height-1,image.width,1,0,1,1,1);
      const colors=edge.context.getImageData(0,0,1,2).data;
      for(let n=0;n<2;n++){const i=n*4;ctx.fillStyle=`rgb(${Math.round(colors[i]!*.15)},${Math.round(colors[i+1]!*.15)},${Math.round(colors[i+2]!*.15)})`;ctx.fillRect(0,n?h-margin:0,w,margin);}
    }
    ctx.drawImage(image,(w-iw)*(slot.key?.startsWith("robotic-arm:")?.56:.5),(h-ih)*.5,iw,ih);
    slot.pixels=ctx.getImageData(0,0,w,h);
  }
  upload(index:number,key:FrameKey,image:ImageBitmap):void {
    const slot=this.slots[index]!;if(slot.key===key)return;
    slot.key=key;slot.image=image;this.project(slot);this.uploads++;
  }
  swap():void {[this.slots[0],this.slots[1]]=[this.slots[1]!,this.slots[0]!];}
  private contour(scene:Scene):Path2D|null {
    const radius=radiusFor(scene.hold.expansion,scene.hold.center,this.width,this.height);if(radius<0)return null;
    const path=new Path2D(),diagonal=Math.hypot(this.width,this.height),{center}=scene.hold;
    // Find the same irregular SDF boundary used by coverage and the optional shader.
    for(let n=0;n<=160;n++){
      const angle=n/160*Math.PI*2,dx=Math.cos(angle),dy=Math.sin(angle);
      let low=Math.max(0,radius-diagonal*.086),high=radius+diagonal*.086;
      for(let i=0;i<12;i++){const r=(low+high)*.5;if(r+boundaryNoise(center.x+dx*r,center.y+dy*r,center,scene.time)*diagonal<radius)low=r;else high=r;}
      const r=(low+high)*.5,x=(center.x+dx*r)*this.canvas.width/this.width,y=(center.y+dy*r)*this.canvas.height/this.height;
      if(n===0)path.moveTo(x,y);else path.lineTo(x,y);
    }
    path.closePath();return path;
  }
  private refract(scene:Scene):void {
    const {field}=scene,pixels=field.pixels,fw=field.width,fh=field.height;
    const mw=this.material.width,mh=this.material.height,out=this.materialPixels.data;
    if(this.dirty){const d=this.dirty;this.materialContext.clearRect(d.x,d.y,d.width,d.height);for(let y=d.y;y<d.y+d.height;y++)out.fill(0,(y*mw+d.x)*4,(y*mw+d.x+d.width)*4);}
    let minX=fw,minY=fh,maxX=-1,maxY=-1;
    if(this.gradients.length!==fw*fh*2)this.gradients=new Float32Array(fw*fh*2);
    const gradients=this.gradients;
    for(let y=0;y<fh;y++)for(let x=0;x<fw;x++){
      const i=y*fw+x;
      gradients[i*2]=(pixels[(y*fw+Math.min(fw-1,x+1))*4+2]!-pixels[(y*fw+Math.max(0,x-1))*4+2]!)/255;
      gradients[i*2+1]=(pixels[(Math.min(fh-1,y+1)*fw+x)*4+2]!-pixels[(Math.max(0,y-1)*fw+x)*4+2]!)/255;
      if(pixels[i*4+2]!>25){minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);}
    }
    if(maxX<0){this.materialContext.clearRect(0,0,mw,mh);this.dirty=null;return;}
    const x0=Math.max(0,Math.floor((minX-2)/fw*mw)),y0=Math.max(0,Math.floor((minY-2)/fh*mh));
    const x1=Math.min(mw,Math.ceil((maxX+3)/fw*mw)),y1=Math.min(mh,Math.ceil((maxY+3)/fh*mh));
    const first=this.slots[0]!.pixels!,second=this.slots[1]!.pixels;
    const pw=first.width,ph=first.height,a=first.data,b=second?.data;
    const diagonal=Math.hypot(this.width,this.height),radius=radiusFor(scene.hold.expansion,scene.hold.center,this.width,this.height);
    for(let y=y0;y<y1;y++)for(let x=x0;x<x1;x++){
      const gx=Math.max(0,Math.min(fw-1,(x+.5)/mw*fw-.5)),gy=Math.max(0,Math.min(fh-1,(y+.5)/mh*fh-.5));
      const ix=Math.floor(gx),iy=Math.floor(gy),fx=gx-ix,fy=gy-iy;
      const j=(iy*fw+ix)*4,k=(iy*fw+Math.min(ix+1,fw-1))*4,l=(Math.min(iy+1,fh-1)*fw+ix)*4,m=(Math.min(iy+1,fh-1)*fw+Math.min(ix+1,fw-1))*4;
      // Bilinear density cannot exceed its four cell samples. Skip the entire
      // transparent cell span without changing any visible material pixel.
      if(Math.max(pixels[j+2]!,pixels[k+2]!,pixels[l+2]!,pixels[m+2]!)<=.12*255){
        x=Math.min(x1-1,Math.ceil((ix+1.5)*mw/fw-.5)-1);continue;
      }
      const densityValue=((pixels[j+2]!*(1-fx)+pixels[k+2]!*fx)*(1-fy)+(pixels[l+2]!*(1-fx)+pixels[m+2]!*fx)*fy)/255;
      const density=smooth(.12,.18,densityValue);if(density===0)continue;
      const velocityX=((pixels[j]!*(1-fx)+pixels[k]!*fx)*(1-fy)+(pixels[l]!*(1-fx)+pixels[m]!*fx)*fy)/255;
      const velocityY=((pixels[j+1]!*(1-fx)+pixels[k+1]!*fx)*(1-fy)+(pixels[l+1]!*(1-fx)+pixels[m+1]!*fx)*fy)/255;
      // Interpolate normals as well as density/velocity: nearest-cell normals
      // produced visible blockwise displacement of fine film detail.
      const gj=j/2,gk=k/2,gl=l/2,gm=m/2;
      const gradX=(gradients[gj]!*(1-fx)+gradients[gk]!*fx)*(1-fy)+(gradients[gl]!*(1-fx)+gradients[gm]!*fx)*fy;
      const gradY=(gradients[gj+1]!*(1-fx)+gradients[gk+1]!*fx)*(1-fy)+(gradients[gl+1]!*(1-fx)+gradients[gm+1]!*fx)*fy;
      const dx=gradX*25+(velocityX-.5)*density*18,dy=gradY*25+(velocityY-.5)*density*18;
      const cssX=(x+.5)/mw*this.width+dx,cssY=(y+.5)/mh*this.height+dy;
      const sx=Math.max(0,Math.min(pw-1,cssX/this.width*pw-.5)),sy=Math.max(0,Math.min(ph-1,cssY/this.height*ph-.5));
      const px=Math.floor(sx),py=Math.floor(sy),tx=sx-px,ty=sy-py;
      const p=(py*pw+px)*4,q=(py*pw+Math.min(px+1,pw-1))*4,r=(Math.min(py+1,ph-1)*pw+px)*4,s=(Math.min(py+1,ph-1)*pw+Math.min(px+1,pw-1))*4;
      let mask=0;
      if(scene.reveal&&b&&radius>=0){const distance=Math.hypot(cssX-scene.hold.center.x,cssY-scene.hold.center.y)+boundaryNoise(cssX,cssY,scene.hold.center,scene.time)*diagonal-radius;mask=1-smooth(-4,4,distance);}
      const output=(y*mw+x)*4,shading=density*.46,highlight=Math.hypot(gradX,gradY)*density*.8;
      for(let c=0;c<3;c++){
        let color=(a[p+c]!*(1-tx)+a[q+c]!*tx)*(1-ty)+(a[r+c]!*(1-tx)+a[s+c]!*tx)*ty;
        if(mask>0&&b){const incoming=(b[p+c]!*(1-tx)+b[q+c]!*tx)*(1-ty)+(b[r+c]!*(1-tx)+b[s+c]!*tx)*ty;color+=(incoming-color)*mask;}
        out[output+c]=Math.round(Math.max(0,Math.min(255,color*(1-.22*shading)+tint[c]!*255*shading+shine[c]!*255*highlight)));
      }
      out[output+3]=Math.round(density*255);
    }
    this.dirty={x:x0,y:y0,width:x1-x0,height:y1-y0};
    // The bounded overlay is reused; transparency removes old material completely.
    this.materialContext.putImageData(this.materialPixels,0,0,x0,y0,x1-x0,y1-y0);
  }
  draw(scene:Scene):void {
    if(this.disposed||!this.slots[0]!.pixels)return;
    const ctx=this.context;ctx.drawImage(this.slots[0]!.canvas,0,0);
    const path=scene.reduced?null:this.contour(scene);
    if(path){
      ctx.save();if(scene.reveal&&this.slots[1]!.pixels){ctx.clip(path);ctx.drawImage(this.slots[1]!.canvas,0,0);}else if(scene.hold.phase!=="GRACE"){ctx.fillStyle="rgba(65,70,57,.06)";ctx.fill(path);}ctx.restore();
      if(scene.hold.phase!=="GRACE"){
        const cx=scene.hold.center.x*this.canvas.width/this.width,cy=scene.hold.center.y*this.canvas.height/this.height;
        const radius=radiusFor(scene.hold.expansion,scene.hold.center,this.width,this.height),band=Math.min(.08,7/Math.max(1,radius));
        const transform=(scale:number)=>new DOMMatrix().translate(cx,cy).scale(scale).translate(-cx,-cy);
        const ring=new Path2D();ring.addPath(path,transform(1+band));ring.addPath(path,transform(1-band));
        // Reproject the matching pair inside a narrow contour band: spatial rim
        // refraction without a full-screen per-pixel noise pass.
        ctx.save();ctx.clip(ring,"evenodd");ctx.translate(cx,cy);ctx.scale(1+band*.6,1+band*.6);ctx.translate(-cx,-cy);
        ctx.drawImage(this.slots[0]!.canvas,0,0);if(scene.reveal&&this.slots[1]!.pixels){ctx.clip(path);ctx.drawImage(this.slots[1]!.canvas,0,0);}ctx.restore();
        ctx.strokeStyle="rgba(220,230,210,.14)";ctx.lineWidth=2;ctx.stroke(path);
      }
    }
    if(fieldActive(scene)){this.refract(scene);ctx.drawImage(this.material,0,0,this.canvas.width,this.canvas.height);}
    this.draws++;
  }
  get residentKeys():FrameKey[]{return this.slots.flatMap(slot=>slot.key?[slot.key]:[]);}
  get bytes():number{return this.canvas.width*this.canvas.height*4+this.slots.reduce((n,s)=>n+s.canvas.width*s.canvas.height*4+(s.pixels?.data.byteLength??0),0)+this.material.width*this.material.height*4+this.materialPixels.data.byteLength+this.gradients.byteLength+this.boundary.canvas.width*this.boundary.canvas.height*4;}
  get info():object{return {kind:this.kind,renderer:"Canvas 2D; CPU material compositor; no WebGL dependency",buffer:[this.canvas.width,this.canvas.height],materialBuffer:[this.material.width,this.material.height],gpuTimer:false};}
  dispose():void {if(this.disposed)return;this.disposed=true;for(const slot of this.slots){slot.image=null;slot.pixels=null;slot.key=null;slot.canvas.width=1;slot.canvas.height=1;}this.material.width=1;this.material.height=1;this.materialPixels=this.materialContext.createImageData(1,1);this.gradients=new Float32Array(0);this.boundary.canvas.width=1;this.boundary.canvas.height=1;this.dirty=null;this.canvas.width=1;this.canvas.height=1;}
}
function fieldActive(scene:Scene):boolean{return scene.field.active&&!scene.reduced;}
