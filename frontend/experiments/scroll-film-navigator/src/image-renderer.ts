import type { FrameKey } from "./transport";
import type { Scene } from "./renderer";
/** Semantic plain-image fallback when neither graphics context initializes.
 * Native image decode owns acknowledgment; failed/stale loads retain the last valid image. */
export class ImageRenderer {
  readonly kind="image";
  readonly gpuSamples:number[]=[];
  private keys:(FrameKey|null)[]=[null,null];
  private requested:FrameKey|null=null;
  private generation=0;private attempt=0;private pendingImage:HTMLImageElement|null=null;
  failure:string|null=null;
  presentedKey:FrameKey|null=null;
  uploads=0;draws=0;disposed=false;
  constructor(private layer:HTMLElement,private version:string,private tier:number,private changed:()=>void){layer.hidden=false;}
  resize(_width:number,_height:number):void {}
  upload(slot:number,key:FrameKey,_image:ImageBitmap):void{if(this.keys[slot]!==key){this.keys[slot]=key;this.uploads++;}}
  swap():void{[this.keys[0],this.keys[1]]=[this.keys[1]!,this.keys[0]!];}
  draw(_scene:Scene):void{
    const key=this.keys[0];if(this.disposed||!key||key===this.requested)return;
    this.requested=key;this.failure=null;const generation=++this.generation,[film,index]=key.split(":"),image=new Image();
    this.pendingImage?.removeAttribute("src");
    this.pendingImage=image;image.id="poster";image.width=1600;image.height=900;image.alt=`${film==="tree"?"Tree":"Robotic arm"}, film frame ${Number(index)+1} of 192`;
    image.src=`/media/${this.version}/${this.tier}/${film}/frame_${String(Number(index)+1).padStart(5,"0")}.webp${this.attempt?`?plainRetry=${this.attempt}`:""}`;
    void image.decode().then(()=>{if(this.disposed||generation!==this.generation)return;this.pendingImage=null;this.layer.replaceChildren(image);this.layer.hidden=false;this.presentedKey=key;this.draws++;this.changed();}).catch(()=>{if(!this.disposed&&generation===this.generation){this.pendingImage=null;this.failure=`Plain image decode failed for ${key}. Last valid frame retained.`;this.changed();}});
  }
  retry():void{this.attempt++;this.requested=null;this.failure=null;}
  suspend():void{this.generation++;this.pendingImage?.removeAttribute("src");this.pendingImage=null;this.requested=null;}
  get bytes():number{const image=this.layer.querySelector("img"),pixels=image?image.naturalWidth*image.naturalHeight:0;return this.disposed?0:(pixels+(this.pendingImage?.naturalWidth??0)*(this.pendingImage?.naturalHeight??0))*4;}
  get residentKeys():FrameKey[]{return this.keys.filter((key):key is FrameKey=>key!==null);}
  get info():object{return {kind:this.kind,renderer:"Semantic image fallback; GPU fluid and mask effects unavailable",gpuTimer:false};}
  dispose():void{if(this.disposed)return;this.disposed=true;this.suspend();this.keys=[null,null];this.presentedKey=null;}
}
