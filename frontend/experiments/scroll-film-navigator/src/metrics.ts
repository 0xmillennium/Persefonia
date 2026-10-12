export class Samples {
  private buffer: Float64Array;
  private count=0;
  total=0;
  constructor(readonly capacity=4096) { this.buffer=new Float64Array(capacity); }
  add(value:number): void {if(!Number.isFinite(value))return;this.buffer[this.total%this.capacity]=value;this.total++;this.count=Math.min(this.count+1,this.capacity);}
  reset(): void {this.count=0;this.total=0;}
  summary(): {count:number;total:number;p50:number;p95:number;p99:number;max:number} {
    const values=Array.from(this.buffer.subarray(0,this.count)).sort((a,b)=>a-b);
    const at=(p:number)=>values[Math.min(values.length-1,Math.floor(values.length*p))]??0;
    return {count:this.count,total:this.total,p50:at(.5),p95:at(.95),p99:at(.99),max:at(1)};
  }
}
export interface DeliveryEvent { stage:string; time:number; [detail:string]:unknown }
/** Opt-in development observation. No logging, I/O or timers in the hot path. */
export class DeliveryTimeline {
  private events:(DeliveryEvent|undefined)[];
  private total=0;
  constructor(readonly capacity=8192){this.events=new Array(capacity);}
  record(event:Omit<DeliveryEvent,"time">&{time?:number}):void {
    this.events[this.total++%this.capacity]={...event,time:event.time??performance.now()} as DeliveryEvent;
  }
  snapshot():{total:number;dropped:number;events:DeliveryEvent[]} {
    const count=Math.min(this.total,this.capacity),start=Math.max(0,this.total-this.capacity);
    return {total:this.total,dropped:start,events:Array.from({length:count},(_,n)=>this.events[(start+n)%this.capacity]!)};
  }
}
export class Metrics {
  readonly input=new Samples();readonly decode=new Samples();readonly targetToDecode=new Samples();readonly upload=new Samples();
  readonly intervals=new Samples();readonly javascript=new Samples();readonly drawSubmission=new Samples();readonly targetToDraw=new Samples();readonly longTasks=new Samples();readonly resize=new Samples();
  frameDrops=0;
  private observer: PerformanceObserver|null=null;
  constructor() {
    if(typeof PerformanceObserver!=="undefined"&&PerformanceObserver.supportedEntryTypes.includes("longtask")) {
      this.observer=new PerformanceObserver(list=>{for(const entry of list.getEntries())this.longTasks.add(entry.duration);});this.observer.observe({type:"longtask",buffered:false});
    }
  }
  reset(): void {for(const s of [this.input,this.decode,this.targetToDecode,this.upload,this.intervals,this.javascript,this.drawSubmission,this.targetToDraw,this.longTasks,this.resize])s.reset();this.frameDrops=0;}
  report(): object {return {inputMs:this.input.summary(),fetchDecodeMs:this.decode.summary(),targetToDecodedMs:this.targetToDecode.summary(),textureUploadMs:this.upload.summary(),intervalMs:this.intervals.summary(),javascriptMs:this.javascript.summary(),drawSubmissionMs:this.drawSubmission.summary(),targetToDrawMs:this.targetToDraw.summary(),longTaskMs:this.longTasks.summary(),resizeMs:this.resize.summary(),frameDrops:this.frameDrops};}
  dispose(): void {this.observer?.disconnect();}
}
