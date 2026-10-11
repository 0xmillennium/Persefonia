import type { Scene } from "./renderer";
import type { FrameKey } from "./transport";
const surface=()=>{const canvas=document.createElement("canvas"),context=canvas.getContext("2d");if(!context)throw new Error("Canvas unavailable");return {canvas,context};};
/** Basic compositor used on GPU failure. It preserves selection/hold semantics,
 * but deliberately makes no claim of GPU liquid/refraction fidelity. */
export class CanvasRenderer {
  readonly kind="canvas2d";
  private context:CanvasRenderingContext2D;
  private slots=Array.from({length:2},()=>({...surface(),key:null as FrameKey|null,image:null as ImageBitmap|null}));
  private layer=surface();private mask=surface();
  readonly gpuSamples:number[]=[];
  width=1;height=1;uploads=0;draws=0;disposed=false;
  constructor(readonly canvas:HTMLCanvasElement) {const ctx=canvas.getContext("2d",{alpha:false});if(!ctx)throw new Error("Canvas unavailable");this.context=ctx;}
  resize(width:number,height:number):void {
    this.width=width;this.height=height;
    const scale=Math.min(devicePixelRatio,1.5,1600/Math.max(width,height));
    this.canvas.width=Math.max(1,Math.round(width*scale));this.canvas.height=Math.max(1,Math.round(height*scale));
    this.layer.canvas.width=this.canvas.width;this.layer.canvas.height=this.canvas.height;
    for(const slot of this.slots){slot.canvas.width=this.canvas.width;slot.canvas.height=this.canvas.height;if(slot.image)this.project(slot);}
  }
  private project(slot:typeof this.slots[number]):void {
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
      const {material}=scene;this.mask.canvas.width=material.width;this.mask.canvas.height=material.height;
      const data=this.mask.context.createImageData(material.width,material.height),channel=scene.hold.phase==="REVEALING"?1:0;
      for(let i=0;i<data.data.length;i+=4){data.data[i]=255;data.data[i+1]=255;data.data[i+2]=255;data.data[i+3]=material.pixels[i+channel]!;}
      this.mask.context.putImageData(data,0,0);
      const layer=this.layer.context;layer.clearRect(0,0,w,h);layer.globalCompositeOperation="source-over";
      if(scene.hold.phase==="REVEALING") {
        ctx.fillStyle="rgba(8,145,127,.32)";ctx.fillRect(0,0,w,h);
        layer.drawImage(this.slots[1]!.canvas,0,0);
      } else {layer.fillStyle="rgba(8,145,127,.32)";layer.fillRect(0,0,w,h);}
      layer.globalCompositeOperation="destination-in";layer.drawImage(this.mask.canvas,0,0,w,h);layer.globalCompositeOperation="source-over";
      ctx.drawImage(this.layer.canvas,0,0);
    }
    this.draws++;
  }
  get residentKeys():FrameKey[]{return this.slots.flatMap(s=>s.key?[s.key]:[]);}
  get bytes():number{return this.disposed?0:this.canvas.width*this.canvas.height*4*4+this.mask.canvas.width*this.mask.canvas.height*4;}
  get info():object{return {kind:this.kind,renderer:"Basic Canvas 2D fallback; GPU liquid/refraction unavailable",buffer:[this.canvas.width,this.canvas.height],gpuTimer:false};}
  dispose():void {if(this.disposed)return;this.disposed=true;for(const s of this.slots){s.image=null;s.key=null;s.canvas.width=1;s.canvas.height=1;}this.layer.canvas.width=1;this.layer.canvas.height=1;this.mask.canvas.width=1;this.mask.canvas.height=1;this.canvas.width=1;this.canvas.height=1;}
}
