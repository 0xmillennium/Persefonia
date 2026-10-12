import type { Scene } from "./renderer";
import type { FrameKey } from "./transport";
import { LiquidField } from "./field";
import { PIGMENT,absorbedPigment } from "./pigment";
import { HOLD_OPTICS } from "./hold-optics";
import { clamp } from "./transport";
const surface=()=>{const canvas=document.createElement("canvas"),context=canvas.getContext("2d");if(!context)throw new Error("Canvas unavailable");return {canvas,context};};
/** Basic compositor used on GPU failure. It preserves selection/hold semantics,
 * shares the navigator's pigment/refraction model on a bounded CPU field. */
export class CanvasRenderer {
  readonly kind="canvas2d";
  private context:CanvasRenderingContext2D;
  private slots=Array.from({length:2},()=>({...surface(),key:null as FrameKey|null,image:null as ImageBitmap|null,pixels:null as ImageData|null}));
  private layer=surface();private mask=surface();private pigment=surface();
  private pigmentPixels:ImageData|null=null;
  private maskPixels:ImageData|null=null;private maskRevision=-1;private maskChannel=-1;
  private gradients:Float32Array|null=null;
  readonly gpuSamples:number[]=[];
  width=1;height=1;uploads=0;draws=0;disposed=false;
  constructor(readonly canvas:HTMLCanvasElement) {const ctx=canvas.getContext("2d",{alpha:false});if(!ctx)throw new Error("Canvas unavailable");this.context=ctx;}
  resize(width:number,height:number):void {
    this.width=width;this.height=height;this.maskRevision=-1;
    const scale=Math.min(devicePixelRatio,1.5,1600/Math.max(width,height));
    this.canvas.width=Math.max(1,Math.round(width*scale));this.canvas.height=Math.max(1,Math.round(height*scale));
    this.layer.canvas.width=this.canvas.width;this.layer.canvas.height=this.canvas.height;
    this.pigment.canvas.width=this.canvas.width;this.pigment.canvas.height=this.canvas.height;this.pigmentPixels=null;
    for(const slot of this.slots){slot.canvas.width=this.canvas.width;slot.canvas.height=this.canvas.height;if(slot.image)this.project(slot);}
  }
  private project(slot:typeof this.slots[number]):void {
    slot.pixels=null;
    const image=slot.image!,w=slot.canvas.width,h=slot.canvas.height,ctx=slot.context;
    ctx.fillStyle="#0c0f0d";ctx.fillRect(0,0,w,h);
    const scale=this.width/this.height<.9?w/image.width:Math.max(w/image.width,h/image.height),iw=image.width*scale,ih=image.height*scale;
    ctx.drawImage(image,(w-iw)*(slot.key?.startsWith("robotic-arm:")?.56:.5),(h-ih)*.5,iw,ih);
  }
  upload(index:number,key:FrameKey,image:ImageBitmap):void {const slot=this.slots[index]!;if(slot.key===key)return;slot.key=key;slot.image=image;this.project(slot);this.uploads++;}
  swap():void {[this.slots[0],this.slots[1]]=[this.slots[1]!,this.slots[0]!];}
  draw(scene:Scene):void {
    if(this.disposed||!this.slots[0]!.image)return;
    const ctx=this.context,w=this.canvas.width,h=this.canvas.height;
    ctx.drawImage(this.slots[0]!.canvas,0,0);
    if(scene.hold.animating&&!scene.reduced) {
      const {material}=scene,channel=scene.hold.phase==="REVEALING"?1:0;
      if(!this.maskPixels||this.maskPixels.width!==material.width||this.maskPixels.height!==material.height){
        this.mask.canvas.width=material.width;this.mask.canvas.height=material.height;
        this.maskPixels=this.mask.context.createImageData(material.width,material.height);this.maskRevision=-1;
      }
      if(this.maskRevision!==material.revision||this.maskChannel!==channel){
        const data=this.maskPixels;
        for(let i=0;i<data.data.length;i+=4){data.data[i]=255;data.data[i+1]=255;data.data[i+2]=255;data.data[i+3]=material.pixels[i+channel]!;}
        this.mask.context.putImageData(data,0,0);this.maskRevision=material.revision;this.maskChannel=channel;
      }
      const layer=this.layer.context;layer.clearRect(0,0,w,h);layer.globalCompositeOperation="source-over";
      layer.drawImage(this.slots[0]!.canvas,0,0);layer.globalCompositeOperation="multiply";
      layer.fillStyle=`rgb(${HOLD_OPTICS.tintScale.map(value=>Math.round((1-HOLD_OPTICS.tintStrength+value*HOLD_OPTICS.tintStrength)*255)).join(" ")})`;layer.fillRect(0,0,w,h);
      layer.globalCompositeOperation="lighter";layer.fillStyle=`rgb(${HOLD_OPTICS.tintOffset.map(value=>Math.round(value*HOLD_OPTICS.tintStrength*255)).join(" ")})`;layer.fillRect(0,0,w,h);
      layer.globalCompositeOperation="source-over";
      if(channel===1){ctx.drawImage(this.layer.canvas,0,0);layer.clearRect(0,0,w,h);layer.drawImage(this.slots[1]!.canvas,0,0);}
      layer.globalCompositeOperation="destination-in";layer.drawImage(this.mask.canvas,0,0,w,h);layer.globalCompositeOperation="source-over";
      ctx.drawImage(this.layer.canvas,0,0);
      this.paintHold(scene);ctx.drawImage(this.pigment.canvas,0,0);
    }
    // Readback is cached for each resident frame. Preserve pigment over the hold
    // using the same film/tint/aperture composition as the GPU material.
    if(!scene.reduced&&scene.field instanceof LiquidField&&scene.field.active){
      const slot=this.slots[0]!;slot.pixels??=slot.context.getImageData(0,0,w,h);
      this.pigmentPixels??=this.pigment.context.createImageData(w,h);
      this.paint(scene.field,slot.pixels,scene);ctx.drawImage(this.pigment.canvas,0,0);
    }
    this.draws++;
  }
  private paintHold(scene:Scene):void {
    const {material,hold}=scene,fw=material.width,fh=material.height,w=this.canvas.width,h=this.canvas.height,m=material.pixels,revealing=hold.phase==="REVEALING",channel=revealing?1:0,rimChannel=revealing?2:3;
    const current=this.slots[0]!,incoming=this.slots[1]!;current.pixels??=current.context.getImageData(0,0,w,h);
    if(revealing)incoming.pixels??=incoming.context.getImageData(0,0,w,h);
    this.pigmentPixels??=this.pigment.context.createImageData(w,h);const output=this.pigmentPixels;
    this.pigment.context.clearRect(0,0,w,h);output.data.fill(0);
    const at=(x:number,y:number,c:number)=>m[(clamp(y,0,fh-1)*fw+clamp(x,0,fw-1))*4+c]!/255;
    const source=(data:Uint8ClampedArray,a:number,b:number,c:number,d:number,fx:number,fy:number,k:number)=>((data[a+k]!*(1-fx)+data[b+k]!*fx)*(1-fy)+(data[c+k]!*(1-fx)+data[d+k]!*fx)*fy)/255;
    // Visit only lattice cells touching the liquid boundary. Interiors use the
    // inexpensive cached Canvas masks above, retaining full-resolution film detail.
    for(let iy=-1;iy<fh;iy++)for(let ix=-1;ix<fw;ix++){
      const ra=at(ix,iy,rimChannel),rb=at(ix+1,iy,rimChannel),rc=at(ix,iy+1,rimChannel),rd=at(ix+1,iy+1,rimChannel);
      if(Math.max(ra,rb,rc,rd)<.004)continue;
      const left=Math.max(0,Math.ceil((ix+.5)*w/fw-.5)),right=Math.min(w,Math.ceil((ix+1.5)*w/fw-.5)),top=Math.max(0,Math.ceil((iy+.5)*h/fh-.5)),bottom=Math.min(h,Math.ceil((iy+1.5)*h/fh-.5));
      const gxA=at(ix+1,iy,channel)-at(ix-1,iy,channel),gxB=at(ix+2,iy,channel)-at(ix,iy,channel),gxC=at(ix+1,iy+1,channel)-at(ix-1,iy+1,channel),gxD=at(ix+2,iy+1,channel)-at(ix,iy+1,channel);
      const gyA=at(ix,iy+1,channel)-at(ix,iy-1,channel),gyB=at(ix+1,iy+1,channel)-at(ix+1,iy-1,channel),gyC=at(ix,iy+2,channel)-at(ix,iy,channel),gyD=at(ix+1,iy+2,channel)-at(ix+1,iy,channel);
      const ma=at(ix,iy,channel),mb=at(ix+1,iy,channel),mc=at(ix,iy+1,channel),md=at(ix+1,iy+1,channel);
      for(let y=top;y<bottom;y++)for(let x=left;x<right;x++){
        const fx=(x+.5)/w*fw-.5-ix,fy=(y+.5)/h*fh-.5-iy,a=(1-fx)*(1-fy),b=fx*(1-fy),c=(1-fx)*fy,d=fx*fy,index=(y*w+x)*4;
        const edge=ra*a+rb*b+rc*c+rd*d,gx=(gxA*a+gxB*b+gxC*c+gxD*d)*fw/this.width,gy=(gyA*a+gyB*b+gyC*c+gyD*d)*fh/this.height,normal=Math.max(Math.hypot(gx,gy),.00001);
        const px=clamp(x+gx/normal*HOLD_OPTICS.refraction*edge*w/this.width,0,w-1),py=clamp(y+gy/normal*HOLD_OPTICS.refraction*edge*h/this.height,0,h-1),sx=Math.floor(px),sy=Math.floor(py),tx=px-sx,ty=py-sy;
        const sa=(sy*w+sx)*4,sb=(sy*w+Math.min(sx+1,w-1))*4,sc=(Math.min(sy+1,h-1)*w+sx)*4,sd=(Math.min(sy+1,h-1)*w+Math.min(sx+1,w-1))*4,mask=ma*a+mb*b+mc*c+md*d;
        for(let k=0;k<3;k++){
          const original=source(current.pixels.data,sa,sb,sc,sd,tx,ty,k),tinted=original+(original*HOLD_OPTICS.tintScale[k]!+HOLD_OPTICS.tintOffset[k]!-original)*HOLD_OPTICS.tintStrength;
          const base=revealing?tinted+(source(incoming.pixels!.data,sa,sb,sc,sd,tx,ty,k)-tinted)*mask:original+(tinted-original)*mask;
          const color=revealing?HOLD_OPTICS.revealRimColor[k]!:HOLD_OPTICS.rimColor[k]!;
          output.data[index+k]=Math.round(clamp(base+(color-base)*edge*(revealing?.8:HOLD_OPTICS.rimStrength))*255);
        }
        output.data[index+3]=255;
      }
    }
    this.pigment.context.putImageData(output,0,0);
  }
  private paint(field:LiquidField,film:ImageData,scene:Scene):void {
    const fw=field.width,fh=field.height,dye=field.pigment,w=film.width,h=film.height,output=this.pigmentPixels!;
    this.gradients??=new Float32Array(fw*fh*2);if(this.gradients.length!==fw*fh*2)this.gradients=new Float32Array(fw*fh*2);
    const gradients=this.gradients;
    let minX=fw,maxX=-1,minY=fh,maxY=-1;
    for(let y=0;y<fh;y++)for(let x=0;x<fw;x++){
      const i=y*fw+x;gradients[i*2]=dye[(y*fw+Math.min(x+1,fw-1))*3]!-dye[(y*fw+Math.max(x-1,0))*3]!;
      gradients[i*2+1]=dye[(Math.min(y+1,fh-1)*fw+x)*3]!-dye[(Math.max(y-1,0)*fw+x)*3]!;
      if(dye[i*3]!>.003){minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
    }
    this.pigment.context.clearRect(0,0,w,h);if(maxX<0)return;
    const left=Math.max(0,Math.floor((minX-2)/fw*w)),right=Math.min(w,Math.ceil((maxX+3)/fw*w));
    const top=Math.max(0,Math.floor((minY-2)/fh*h)),bottom=Math.min(h,Math.ceil((maxY+3)/fh*h));
    const smooth=(low:number,high:number,value:number)=>{const t=clamp((value-low)/(high-low));return t*t*(3-2*t);};
    const absorption=[.35,.72,.65],dark=[.005,.25,.21],light=[.08,.85,.69],highlight=[.22,.76,.65];
    const held=scene.hold.animating&&!scene.reduced,revealing=scene.hold.phase==="REVEALING",m=scene.material,channel=revealing?1:0;
    if(held&&revealing){const slot=this.slots[1]!;slot.pixels??=slot.context.getImageData(0,0,w,h);}
    const mask=(x:number,y:number,c:number)=>{
      x=clamp(x,0,m.width-1);y=clamp(y,0,m.height-1);const ix=Math.floor(x),iy=Math.floor(y),fx=x-ix,fy=y-iy;
      const a=(iy*m.width+ix)*4+c,b=(iy*m.width+Math.min(ix+1,m.width-1))*4+c,cc=(Math.min(iy+1,m.height-1)*m.width+ix)*4+c,d=(Math.min(iy+1,m.height-1)*m.width+Math.min(ix+1,m.width-1))*4+c;
      return ((m.pixels[a]!*(1-fx)+m.pixels[b]!*fx)*(1-fy)+(m.pixels[cc]!*(1-fx)+m.pixels[d]!*fx)*fy)/255;
    };
    for(let y=top;y<bottom;y++){
      output.data.fill(0,(y*w+left)*4,(y*w+right)*4);
      const fy=clamp((y+.5)/h*fh-.5,0,fh-1),iy=Math.floor(fy),wy=fy-iy,rowA=iy*fw,rowB=Math.min(iy+1,fh-1)*fw;
      for(let x=left;x<right;x++){
        const fx=clamp((x+.5)/w*fw-.5,0,fw-1),ix=Math.floor(fx),wx=fx-ix,next=Math.min(ix+1,fw-1);
        const a=rowA+ix,b=rowA+next,c=rowB+ix,d=rowB+next,wa=(1-wx)*(1-wy),wb=wx*(1-wy),wc=(1-wx)*wy,wd=wx*wy;
        const densityValue=dye[a*3]!*wa+dye[b*3]!*wb+dye[c*3]!*wc+dye[d*3]!*wd;if(densityValue<=.003)continue;
        const gx=gradients[a*2]!*wa+gradients[b*2]!*wb+gradients[c*2]!*wc+gradients[d*2]!*wd,gy=gradients[a*2+1]!*wa+gradients[b*2+1]!*wb+gradients[c*2+1]!*wc+gradients[d*2+1]!*wd;
        const density=smooth(.015,.65,densityValue),marble=clamp((dye[a*3+1]!*wa+dye[b*3+1]!*wb+dye[c*3+1]!*wc+dye[d*3+1]!*wd)/(densityValue+.001)),vein=clamp((dye[a*3+2]!*wa+dye[b*3+2]!*wb+dye[c*3+2]!*wc+dye[d*3+2]!*wd)/(densityValue+.001)),shine=Math.min(Math.hypot(gx,gy)*.16,.12)*density,index=(y*w+x)*4;
        let dx=clamp(gx*PIGMENT.refraction,-PIGMENT.maxRefraction,PIGMENT.maxRefraction),dy=clamp(gy*PIGMENT.refraction,-PIGMENT.maxRefraction,PIGMENT.maxRefraction),coverage=0,edge=0;
        if(held){
          const mx=(x+.5)/w*m.width-.5,my=(y+.5)/h*m.height-.5;coverage=mask(mx,my,channel);edge=mask(mx,my,revealing?2:3);
          if(edge>.003){const hx=(mask(mx+1,my,channel)-mask(mx-1,my,channel))*m.width/this.width,hy=(mask(mx,my+1,channel)-mask(mx,my-1,channel))*m.height/this.height,length=Math.max(Math.hypot(hx,hy),.00001);dx+=hx/length*HOLD_OPTICS.refraction*edge;dy+=hy/length*HOLD_OPTICS.refraction*edge;}
        }
        const px=clamp(x+dx*w/this.width,0,w-1),py=clamp(y+dy*h/this.height,0,h-1),sx=Math.floor(px),sy=Math.floor(py),tx=px-sx,ty=py-sy;
        const sa=(sy*w+sx)*4,sb=(sy*w+Math.min(sx+1,w-1))*4,sc=(Math.min(sy+1,h-1)*w+sx)*4,sd=(Math.min(sy+1,h-1)*w+Math.min(sx+1,w-1))*4,ta=(1-tx)*(1-ty),tb=tx*(1-ty),tc=(1-tx)*ty,td=tx*ty;
        for(let k=0;k<3;k++){
          let color=(film.data[sa+k]!*ta+film.data[sb+k]!*tb+film.data[sc+k]!*tc+film.data[sd+k]!*td)/255;
          if(held){
            const tinted=color+(color*HOLD_OPTICS.tintScale[k]!+HOLD_OPTICS.tintOffset[k]!-color)*HOLD_OPTICS.tintStrength;
            if(revealing){const data=this.slots[1]!.pixels!.data,incoming=(data[sa+k]!*ta+data[sb+k]!*tb+data[sc+k]!*tc+data[sd+k]!*td)/255;color=tinted+(incoming-tinted)*coverage;}
            else color+=(tinted-color)*coverage;
            const rim=revealing?HOLD_OPTICS.revealRimColor[k]!:HOLD_OPTICS.rimColor[k]!;color+=(rim-color)*edge*(revealing?.8:HOLD_OPTICS.rimStrength);
          }
          const pigment=(dark[k]!+(light[k]!-dark[k]!)*marble)*(1-vein*.35),shaded=color+(absorbedPigment(color,pigment,absorption[k]!)-color)*density*PIGMENT.opacity;
          output.data[index+k]=Math.round(clamp(shaded+(highlight[k]!-shaded)*shine)*255);
        }
        output.data[index+3]=Math.round(smooth(.003,.015,densityValue)*255);
      }
    }
    this.pigment.context.putImageData(output,0,0,left,top,right-left,bottom-top);
  }
  get residentKeys():FrameKey[]{return this.slots.flatMap(s=>s.key?[s.key]:[]);}
  get bytes():number{return this.disposed?0:this.canvas.width*this.canvas.height*4*4+this.mask.canvas.width*this.mask.canvas.height*4+this.pigment.canvas.width*this.pigment.canvas.height*4+(this.pigmentPixels?.data.byteLength??0)+(this.maskPixels?.data.byteLength??0)+(this.gradients?.byteLength??0)+this.slots.reduce((bytes,slot)=>bytes+(slot.pixels?.data.byteLength??0),0);}
  get info():object{return {kind:this.kind,renderer:"Canvas 2D compatibility pigment and local refraction",buffer:[this.canvas.width,this.canvas.height],gpuTimer:false};}
  dispose():void {if(this.disposed)return;this.disposed=true;for(const s of this.slots){s.image=null;s.pixels=null;s.key=null;s.canvas.width=1;s.canvas.height=1;}this.layer.canvas.width=1;this.layer.canvas.height=1;this.mask.canvas.width=1;this.mask.canvas.height=1;this.pigment.canvas.width=1;this.pigment.canvas.height=1;this.pigmentPixels=null;this.maskPixels=null;this.gradients=null;this.canvas.width=1;this.canvas.height=1;}
}
