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
export class Metrics {
  readonly input=new Samples();readonly decode=new Samples();
  readonly intervals=new Samples();readonly javascript=new Samples();readonly drawSubmission=new Samples();readonly targetToDraw=new Samples();readonly longTasks=new Samples();readonly resize=new Samples();
  frameDrops=0;
  private observer: PerformanceObserver|null=null;
  constructor() {
    if(typeof PerformanceObserver!=="undefined"&&PerformanceObserver.supportedEntryTypes.includes("longtask")) {
      this.observer=new PerformanceObserver(list=>{for(const entry of list.getEntries())this.longTasks.add(entry.duration);});this.observer.observe({type:"longtask",buffered:false});
    }
  }
  reset(): void {for(const s of [this.input,this.decode,this.intervals,this.javascript,this.drawSubmission,this.targetToDraw,this.longTasks,this.resize])s.reset();this.frameDrops=0;}
  report(): object {return {inputMs:this.input.summary(),fetchDecodeMs:this.decode.summary(),intervalMs:this.intervals.summary(),javascriptMs:this.javascript.summary(),drawSubmissionMs:this.drawSubmission.summary(),targetToDrawMs:this.targetToDraw.summary(),longTaskMs:this.longTasks.summary(),resizeMs:this.resize.summary(),frameDrops:this.frameDrops};}
  dispose(): void {this.observer?.disconnect();}
}
