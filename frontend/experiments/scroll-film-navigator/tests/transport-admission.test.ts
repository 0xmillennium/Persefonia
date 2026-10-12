import { it,expect,vi } from "vitest";
import { FrameCache,keyFor,FilmTransport,type Decoded,type Film,type PreparationEvent } from "../src/transport";
const flush=async()=>{for(let n=0;n<16;n++)await Promise.resolve();};
const image=():Decoded=>({width:1600,height:900,close:vi.fn()});
function deferred(){
  const calls:{key:string;signal:AbortSignal;finish:(value:Decoded)=>void;finished:boolean}[]=[];
  const events:PreparationEvent[]=[];
  const cache=new FrameCache<Decoded>((film,index,signal)=>new Promise(resolve=>calls.push({key:keyFor(film,index),signal,finish:resolve,finished:false})),20,2,()=>{},event=>events.push(event));
  const finish=(key:string)=>{const r=calls.find(r=>r.key===key&&!r.finished);if(!r)throw new Error(`No pending ${key}`);r.finished=true;const value=image();r.finish(value);return value;};
  const cleanup=async()=>{cache.dispose();for(const r of calls)if(!r.finished)finish(r.key);await flush();};
  return {cache,calls,events,finish,cleanup};
}
it("T1 reserves current admission in 100→103 while retaining productive speculative ownership",async()=>{
  const h=deferred();
  try{
    h.cache.request([{film:"tree",index:100}]);h.finish("tree:100");await flush();
    h.cache.request([{film:"tree",index:100}],["tree:100"],{direction:1,speed:48});await flush();
    expect(h.cache.decodingKeys).toEqual(["tree:101"]);
    h.cache.request([{film:"tree",index:103}],["tree:100"],{direction:1,speed:48});
    expect(h.cache.decodingKeys).toEqual(["tree:101","tree:103"]);
    expect(h.calls.find(r=>r.key==="tree:101")!.signal.aborted).toBe(false);
    expect(h.calls.some(r=>r.key==="tree:102")).toBe(false);
    const current=h.finish("tree:103");await flush();expect(h.cache.get("tree:103")).toBe(current);
  }finally{await h.cleanup();}
});
it("T2 adjacent traversal promotes an admitted neighbor instead of canceling and reloading it",async()=>{
  const h=deferred();
  try{
    h.cache.request([{film:"tree",index:100}]);h.finish("tree:100");await flush();
    h.cache.request([{film:"tree",index:101}],["tree:100"],{direction:1,speed:24});
    const predicted=h.calls.find(r=>r.key==="tree:101")!;
    expect(predicted.signal.aborted).toBe(false);expect(h.calls.filter(r=>r.key==="tree:101")).toHaveLength(1);
    h.finish("tree:101");await flush();expect(h.cache.get("tree:101")).toBeDefined();
    expect(h.calls.some(r=>r.key==="tree:102")).toBe(true);
    h.cache.request([{film:"tree",index:102}],["tree:101"],{direction:1,speed:24});h.finish("tree:102");await flush();
    expect(h.cache.get("tree:102")).toBeDefined();expect(h.events.filter(e=>e.stage==="cancel")).toHaveLength(0);
  }finally{await h.cleanup();}
});
it("T3/4 abort retains non-abortable slots; first actual settlement admits the latest reversed/jumped target",async()=>{
  const h=deferred(),transport=new FilmTransport();
  try{
    h.cache.request([{film:"tree",index:10}]);
    expect(h.cache.pending).toBe(2);
    h.cache.request([{film:"tree",index:150}],[],{direction:1,speed:48});
    h.cache.request([{film:"tree",index:140}],[],{direction:-1,speed:48});
    expect(h.calls).toHaveLength(2);expect(h.cache.pending).toBe(2);expect(h.calls.every(r=>r.signal.aborted)).toBe(true);
    const late=h.finish("tree:11");await flush();expect(late.close).toHaveBeenCalledOnce();
    expect(h.calls[2]!.key).toBe("tree:140");expect(h.cache.pending).toBe(2);expect(h.cache.get("tree:11")).toBeUndefined();
    expect(h.calls.some(r=>r.key==="tree:150")).toBe(false);
    h.finish("tree:10");await flush();h.finish("tree:140");await flush();expect(h.cache.get("tree:140")).toBeDefined();
    transport.restore("tree");transport.setProgress(140/191);expect(transport.present("tree:11")).toBe(false);expect(transport.present("tree:140")).toBe(true);
    expect(h.cache.peakPending).toBe(2);
  }finally{await h.cleanup();}
});
it("T5 paired targets have admission fairness even while an older primary request owns a slot",async()=>{
  const h=deferred();
  const pair=(index:number)=>[{film:"robotic-arm" as Film,index},{film:"tree" as Film,index}];
  try{
    h.cache.request(pair(100),[],{direction:1,speed:24});
    expect(h.cache.decodingKeys).toEqual(["robotic-arm:100","tree:100"]);
    h.cache.request(pair(101),[],{direction:1,speed:24});h.finish("robotic-arm:100");await flush();
    expect(h.cache.decodingKeys).toEqual(["tree:100","robotic-arm:101"]);
    h.cache.request(pair(102),[],{direction:1,speed:24});h.finish("tree:100");await flush();
    // Two primary requests must not monopolize the pair's two slots.
    expect(h.cache.decodingKeys).toEqual(["robotic-arm:101","tree:102"]);
    h.finish("robotic-arm:101");await flush();expect(h.cache.decodingKeys).toContain("robotic-arm:102");
    h.finish("tree:102");h.finish("robotic-arm:102");await flush();
    expect(h.cache.get("tree:102")).toBeDefined();expect(h.cache.get("robotic-arm:102")).toBeDefined();
  }finally{await h.cleanup();}
});
it("A retained current-owned operation cannot displace the productive prediction lane",async()=>{
  const h=deferred();
  try{
    h.cache.request([{film:"tree",index:10}],[],{direction:1,speed:24});
    h.cache.request([{film:"tree",index:12}],[],{direction:1,speed:24});
    h.finish("tree:11");await flush();
    expect(h.cache.decodingKeys).toEqual(["tree:10","tree:13"]);
    expect(h.calls.find(r=>r.key==="tree:10")!.signal.aborted).toBe(false);
    h.finish("tree:10");await flush();
    expect(h.cache.decodingKeys).toEqual(["tree:13","tree:12"]);
    expect(h.calls.find(r=>r.key==="tree:13")!.signal.aborted).toBe(false);
    h.finish("tree:12");await flush();expect(h.cache.get("tree:12")).toBeDefined();
  }finally{await h.cleanup();}
});
it.each([110,230])("T6/7 %dms non-abortable loading retains capacity bounds and prepares a stationary target within two service times",async(delay)=>{
  vi.useFakeTimers({toFake:["setTimeout","clearTimeout","performance"]});
  const events:PreparationEvent[]=[],images:Decoded[]=[],selectionTimes=new Map<number,number>();let target=0;
  const cache=new FrameCache<Decoded>((_film,_index,_signal)=>new Promise(resolve=>setTimeout(()=>{const value=image();images.push(value);resolve(value);},delay)),20,2,()=>{cache.request([{film:"tree",index:target}],[],{direction:1,speed:24});},event=>events.push(event));
  try{
    let misses=0,maxMissing=0,ready=0;
    for(let index=0;index<96;index++){
      target=index;
      selectionTimes.set(index,performance.now());
      cache.request([{film:"tree",index}],[],{direction:1,speed:24});await vi.advanceTimersByTimeAsync(1000/24);
      if(cache.get(keyFor("tree",index))){ready++;misses=0;}else maxMissing=Math.max(maxMissing,++misses);
      expect(cache.entries.size).toBeLessThanOrEqual(20);expect(cache.pending).toBeLessThanOrEqual(2);
    }
    expect(ready).toBeGreaterThan(0);
    // A stopped target needs at most residual slot occupancy + its own service;
    // this bound comes from the mocked loader, not a VM frame-rate SLA.
    cache.request([{film:"tree",index:95}],[],{direction:1,speed:24});await vi.advanceTimersByTimeAsync(2*delay);
    expect(cache.get("tree:95")).toBeDefined();
    const lanes=new Map<string,string>();
    for(const event of events){if(event.stage==="admitted"){
      lanes.set(event.key,event.lane!);
      expect([...lanes.values()].filter(lane=>lane==="speculative").length).toBeLessThanOrEqual(1);
      if(event.current)expect(event.time-selectionTimes.get(Number(event.key.split(":")[1]))!).toBeLessThanOrEqual(delay+1);
    }if(event.stage==="settled")lanes.delete(event.key);}
    const admittedKeys=new Set(events.filter(e=>e.stage==="admitted").map(e=>e.key));
    console.log(JSON.stringify({delay,ready,maxConsecutiveUnavailable:maxMissing,neverAdmittedTargets:Array.from({length:96},(_,index)=>keyFor("tree",index)).filter(key=>!admittedKeys.has(key)).length,admissions:events.filter(e=>e.stage==="admitted").length,cancellations:events.filter(e=>e.stage==="cancel").length,obsolete:events.filter(e=>e.stage==="obsolete").length}));
    cache.suspend();await vi.advanceTimersByTimeAsync(2*delay);expect(cache.pending).toBe(0);expect(cache.queued).toBe(0);
  }finally{cache.dispose();await vi.runAllTimersAsync();expect(images.every(value=>vi.mocked(value.close).mock.calls.length===1)).toBe(true);vi.useRealTimers();}
});
