export const FILMS = ["robotic-arm", "tree"] as const;
export type Film = typeof FILMS[number];
export const FRAME_COUNT = 192;
export const clamp = (value: number, min = 0, max = 1) => Math.min(max, Math.max(min, value));
export function frameAt(progress: number): number { return Math.round(clamp(Number.isFinite(progress) ? progress : 0) * (FRAME_COUNT - 1)); }
export function scrollAt(y: number, range: number): number { return clamp(range > 0 ? y / range : 0); }
export function otherFilm(film: Film): Film { return film === FILMS[0] ? FILMS[1] : FILMS[0]; }
export type FrameKey = `${Film}:${number}`;
export const keyFor = (film: Film, index: number): FrameKey => `${film}:${index}`;
export interface PreparationHint { direction: 1|-1; speed: number }
export interface Decoded { width: number; height: number; close(): void }
export interface Loader<T extends Decoded> { (film: Film, index: number, signal: AbortSignal, attempt: number): Promise<T> }
interface Request { key: FrameKey; film: Film; index: number; priority: number; attempt: number; controller: AbortController }

/** Bounded decoded LRU and bounded latest-target work queue. Renderer owns two presentation slots separately. */
export class FrameCache<T extends Decoded> {
  readonly entries = new Map<FrameKey, T>();
  private queue: Request[] = [];
  private inFlight = new Map<FrameKey, Request>();
  private pinned = new Set<FrameKey>();
  private failed = new Set<FrameKey>();
  private retryAttempts = new Map<FrameKey, number>(); // At most the 384 canonical keys.
  private disposed = false;
  private suspended = false;
  private targets: readonly {film: Film; index: number}[] = [];
  private direction:1|-1=1;
  private readinessSeconds=0;
  hits = 0; misses = 0; evictions = 0; staleCompletions = 0; failures = 0;
  peakPending = 0;
  error: string | null = null;
  constructor(private loader: Loader<T>, readonly limit = 8, readonly concurrency = 2, private changed: () => void = () => {}) {
    if(limit < 4 || concurrency < 1 || concurrency > limit) throw new Error("Invalid cache budget");
  }
  get(key: FrameKey): T | undefined {
    const image = this.entries.get(key);
    if(image) { this.entries.delete(key); this.entries.set(key,image); }
    return image;
  }
  request(targets: readonly {film: Film; index: number}[], presented: readonly FrameKey[] = [], hint?: PreparationHint): void {
    if(this.disposed) return;
    this.suspended=false;
    if(targets.length>2 || presented.length>2) throw new Error("At most two film targets/residents are supported");
    const previousTargets=this.targets;this.targets=targets;
    this.pinned = new Set([...presented, ...targets.map(t=>keyFor(t.film,t.index))]);
    const work=targets.map((target,priority)=>({...target,priority}));
    // Prepare the neighbor that can finish before scroll reaches it. Always
    // prioritize the exact current target; this changes decode order only.
    const lead=hint?Math.min(10,Math.max(1,Math.ceil(Math.abs(hint.speed)*this.readinessSeconds))):1;
    const ahead=hint?Math.min(10,Math.max(2,Math.ceil(Math.abs(hint.speed)*.18),lead+1)):1;
    const behind=hint?Math.min(4,ahead):0,direction=hint?.direction??1;
    for(let distance=1;distance<=Math.max(ahead,behind);distance++)for(const [filmOrder,target] of targets.entries()) {
      if(distance<=ahead)work.push({film:target.film,index:clamp(target.index+distance*direction,0,191),priority:10+Math.abs(distance-lead)*2+filmOrder});
      if(distance<=behind)work.push({film:target.film,index:clamp(target.index-distance*direction,0,191),priority:40+distance*2+filmOrder});
    }
    const unique=new Map<FrameKey,typeof work[number]>();
    for(const target of work.sort((a,b)=>a.priority-b.priority))if(!unique.has(keyFor(target.film,target.index)))unique.set(keyFor(target.film,target.index),target);
    // Include two resident presentation images in the ownership budget, including during reveals.
    const available=Math.max(targets.length,this.limit-presented.filter(key=>!unique.has(key)).length);
    const prepared=[...unique.values()].slice(0,available),wanted=new Set(prepared.map(t=>keyFor(t.film,t.index)));
    this.queue=this.queue.filter(r=>wanted.has(r.key));
    for(const r of this.inFlight.values())if(!wanted.has(r.key)&&!this.pinned.has(r.key))r.controller.abort();
    // A wanted speculative neighbor can still block a new film/reversal/jump.
    // Ordinary traversal must keep useful ahead work: repeatedly preempting it
    // when decode lags the scroll clock can starve every forthcoming frame.
    // Retain bounded decode ownership until abort/decode settlement; preempt only
    // enough unpinned transfers to admit missing current targets, never residents.
    const urgent=targets.some(t=>{const previous=previousTargets.find(p=>p.film===t.film);return !previous||Math.abs(t.index-previous.index)>Math.max(2,ahead)||(t.index-previous.index)*this.direction<0;});
    this.direction=hint?.direction??this.direction;
    const missing=urgent?targets.filter(t=>{const key=keyFor(t.film,t.index);return !this.entries.has(key)&&!this.inFlight.has(key)&&!this.failed.has(key);}).length:0;
    const settling=[...this.inFlight.values()].filter(r=>r.controller.signal.aborted).length;
    let needed=Math.max(0,missing-(this.concurrency-this.inFlight.size+settling));
    const speculative=[...this.inFlight.values()].filter(r=>!this.pinned.has(r.key)&&!r.controller.signal.aborted).sort((a,b)=>b.priority-a.priority);
    for(const r of speculative){if(needed--<=0)break;r.controller.abort();}
    for(const target of prepared)this.enqueue(target.film,target.index,target.priority);
    this.queue.sort((a,b)=>a.priority-b.priority);
    this.evict(); this.pump();
  }
  private enqueue(film: Film, index: number, priority: number): void {
    const key=keyFor(film,index);
    if(this.entries.has(key)) { this.hits++; return; }
    const queued=this.queue.find(r=>r.key===key);if(queued){queued.priority=priority;return;}
    if(this.failed.has(key) || this.inFlight.has(key)) return;
    this.misses++; this.queue.push({key,film,index,priority,attempt:this.retryAttempts.get(key)??0,controller:new AbortController()});
  }
  private pump(): void {
    while(!this.disposed && !this.suspended && this.inFlight.size<this.concurrency && this.queue.length) {
      const r=this.queue.shift()!; this.inFlight.set(r.key,r);
      const started=performance.now();
      this.peakPending=Math.max(this.peakPending,this.inFlight.size);
      void this.loader(r.film,r.index,r.controller.signal,r.attempt).then(image=>{
        if(this.disposed || r.controller.signal.aborted) { image.close(); this.staleCompletions++; return; }
        const elapsed=Math.max(0,(performance.now()-started)/1000);
        this.readinessSeconds=this.readinessSeconds?this.readinessSeconds*.75+elapsed*.25:elapsed;
        this.entries.set(r.key,image); this.evict();
      }).catch((error: unknown)=>{
        if(!r.controller.signal.aborted && !this.disposed) {
          this.failures++; this.error=`${r.key}: ${String(error)}`; this.failed.add(r.key);
          // At most 384 possible keys. Explicit retry, rather than hot-looping broken frames.
        }
      }).finally(()=>{
        this.inFlight.delete(r.key);
        if(!this.disposed && !this.suspended) {
          for(const [priority,target] of this.targets.entries()) this.enqueue(target.film,target.index,priority);
          this.queue.sort((a,b)=>a.priority-b.priority);
          this.pump(); this.changed();
        }
      });
    }
  }
  private evict(): void {
    for(const [key,image] of this.entries) {
      if(this.entries.size<=this.limit) break;
      if(!this.pinned.has(key)) { this.entries.delete(key); image.close(); this.evictions++; }
    }
  }
  suspend(): void { this.suspended=true; this.queue=[]; for(const r of this.inFlight.values())r.controller.abort(); }
  retry(): void {
    // Native image caches can retain a corrupt decode by URL even after the source
    // recovers. Only explicit retries of failed keys get a fresh delivery identity.
    for(const key of this.failed)this.retryAttempts.set(key,(this.retryAttempts.get(key)??0)+1);
    this.failed.clear(); this.error=null;
  }
  get pending(): number { return this.inFlight.size; }
  get queued(): number { return this.queue.length; }
  get scheduledKeys(): FrameKey[] { return [...this.inFlight.keys(),...this.queue.map(r=>r.key)]; }
  get decodingKeys(): FrameKey[] { return [...this.inFlight.keys()]; }
  get bytes(): number { let n=0; for(const image of this.entries.values()) n+=image.width*image.height*4; return n; }
  dispose(): void { this.disposed=true; for(const r of this.inFlight.values()) r.controller.abort(); this.queue=[]; this.targets=[]; for(const image of this.entries.values()) image.close(); this.entries.clear(); this.pinned.clear(); this.failed.clear(); this.retryAttempts.clear(); }
}

/** Async availability does not own desired state. Presentation can only acknowledge current target. */
export class FilmTransport {
  film: Film = FILMS[0];
  progress = 0;
  desired = 0;
  presented: FrameKey | null = null;
  version = 0;
  stalePresentations = 0;
  commits = 0;
  setProgress(progress: number): void {
    this.progress=clamp(progress);
    const frame=frameAt(this.progress);
    if(frame!==this.desired) { this.desired=frame; this.version++; }
  }
  present(key: FrameKey): boolean {
    if(key!==keyFor(this.film,this.desired)) { this.stalePresentations++; return false; }
    this.presented=key; return true;
  }
  switchFilm(): void { this.film=otherFilm(this.film); this.version++; this.commits++; }
  restore(film: Film): void { this.film=film; this.version++; this.presented=null; }
}

/** Native geometry selects frames; this estimator only chooses preparation order. */
export class TraversalEstimator {
  direction:1|-1=1;
  speed=0;
  private index=0;
  private time=0;
  observe(index:number,time:number):void {
    const delta=index-this.index,elapsed=(time-this.time)/1000;
    if(delta && this.time && elapsed>0){this.direction=delta>0?1:-1;this.speed=Math.min(192,Math.abs(delta)/Math.max(elapsed,.016));}
    this.index=index;this.time=time;
  }
  get hint():PreparationHint{return {direction:this.direction,speed:this.speed};}
}
