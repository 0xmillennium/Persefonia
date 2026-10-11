import { describe,it,expect,vi } from "vitest";
import { frameAt,scrollAt,FilmTransport,FrameCache,keyFor,type Decoded,type Film } from "../src/transport";
interface Image extends Decoded { id:string; close: ReturnType<typeof vi.fn<() => void>> }
const image=(id:string):Image=>({id,width:1600,height:900,close:vi.fn()});
const flush=async()=>{for(let i=0;i<8;i++)await Promise.resolve();};
describe("Frame transport",()=>{
  it("FT-02 endpoints and finite clamping",()=>{expect(frameAt(0)).toBe(0);expect(frameAt(1)).toBe(191);expect(frameAt(-2)).toBe(0);expect(frameAt(2)).toBe(191);expect(frameAt(NaN)).toBe(0);expect(scrollAt(100,0)).toBe(0);});
  it("FT-03/04 deterministic forward and reverse mapping",()=>{const path=Array.from({length:192},(_,i)=>i/191);expect(path.map(frameAt)).toEqual(Array.from({length:192},(_,i)=>i));expect(path.reverse().map(frameAt)).toEqual(Array.from({length:192},(_,i)=>191-i));for(let n=0;n<100;n++)expect(frameAt(.35)).toBe(67);});
  it("FT-05 freezes and FT-07 switching preserves normalized position",()=>{const t=new FilmTransport();t.setProgress(.35);const version=t.version;for(let i=0;i<100;i++)t.setProgress(.35);expect(t.version).toBe(version);expect(t.desired).toBe(67);t.switchFilm();expect(t.film).toBe("tree");expect(t.progress).toBe(.35);expect(t.desired).toBe(67);});
  it("FT-06/10 rejects obsolete async presentations",()=>{const t=new FilmTransport();for(const p of [.2,.85,.35])t.setProgress(p);expect(t.present(keyFor(t.film,162))).toBe(false);expect(t.present(keyFor(t.film,38))).toBe(false);expect(t.present(keyFor(t.film,67))).toBe(true);expect(t.presented).toBe("robotic-arm:67");expect(t.stalePresentations).toBe(2);});
  it("FT-06 closes superseded decodes regardless of completion order",async()=>{
    const completions=new Map<string,(value:Image)=>void>();
    const cache=new FrameCache<Image>((film,index)=>new Promise(resolve=>completions.set(keyFor(film,index),resolve)),4,2);
    cache.request([{film:"robotic-arm",index:38}]);cache.request([{film:"robotic-arm",index:162}]);cache.request([{film:"robotic-arm",index:67}]);
    const stale=image("38");completions.get("robotic-arm:38")!(stale);completions.get("robotic-arm:39")!(image("39"));await flush();
    expect(stale.close).toHaveBeenCalledOnce();expect(completions.has("robotic-arm:162")).toBe(false);
    completions.get("robotic-arm:67")!(image("67"));completions.get("robotic-arm:68")!(image("68"));await flush();
    expect(cache.get("robotic-arm:67")?.id).toBe("67");expect(cache.pending).toBe(0);expect(cache.staleCompletions).toBe(2);cache.dispose();
  });
  it("FT-08 bounds cache, decode work, queues and closes evictions",async()=>{
    const images:Image[]=[];
    const cache=new FrameCache<Image>(async(film,index)=>{const value=image(keyFor(film,index));images.push(value);return value;},4,2);
    for(let i=0;i<192;i++) {cache.request([{film:i%2?"tree":"robotic-arm",index:i}]);await flush();expect(cache.entries.size).toBeLessThanOrEqual(4);expect(cache.queued).toBeLessThanOrEqual(2);}
    expect(cache.bytes).toBeLessThanOrEqual(4*1600*900*4);expect(cache.peakPending).toBe(2);expect(cache.evictions).toBeGreaterThan(100);
    cache.dispose();expect(images.every(i=>i.close.mock.calls.length===1)).toBe(true);
  });
  it("FT-09 explicit failed-frame fallback and manual retry",async()=>{
    let fail=true;const cache=new FrameCache<Image>(async(_film,index)=>{if(fail&&index===50)throw new Error("decode failed");return image(String(index));});
    cache.request([{film:"tree",index:50}]);await flush();expect(cache.error).toContain("tree:50");expect(cache.failures).toBe(1);cache.request([{film:"tree",index:50}]);await flush();expect(cache.failures).toBe(1);
    fail=false;cache.retry();cache.request([{film:"tree",index:50}]);await flush();expect(cache.get("tree:50")).toBeDefined();cache.dispose();
  });
  it("FT-09 explicit retry refreshes only failed delivery identities",async()=>{
    const calls:{film:Film;index:number;attempt:number}[]=[];
    const cache=new FrameCache<Image>(async(film,index,_signal,attempt)=>{
      calls.push({film,index,attempt});if(film==="tree"&&index===50&&attempt===0)throw new Error("cached corrupt decode");return image(keyFor(film,index));
    });
    cache.request([{film:"tree",index:50}]);await flush();expect(cache.failures).toBe(1);
    cache.retry();cache.request([{film:"tree",index:50}]);await flush();expect(cache.get("tree:50")).toBeDefined();
    cache.request([{film:"robotic-arm",index:50}]);await flush();
    expect(calls.filter(c=>c.film==="tree"&&c.index===50).map(c=>c.attempt)).toEqual([0,1]);
    expect(calls.filter(c=>c.film==="robotic-arm"||c.index===51).every(c=>c.attempt===0)).toBe(true);cache.dispose();
  });
  it("FT-08 repeated requests retain the same intentional neighbor instead of aborting it",async()=>{
    const signals:AbortSignal[]=[],resolves:((value:Image)=>void)[]=[];
    const cache=new FrameCache<Image>((_film,_index,signal)=>{signals.push(signal);return new Promise(resolve=>resolves.push(resolve));},4,2);
    cache.request([{film:"tree",index:20}]);cache.request([{film:"tree",index:20}]);expect(signals).toHaveLength(2);expect(signals.every(signal=>!signal.aborted)).toBe(true);
    resolves[0]!(image("20"));resolves[1]!(image("21"));await flush();expect(cache.get("tree:21")).toBeDefined();expect(cache.staleCompletions).toBe(0);cache.dispose();
  });
  it("LC-02 suspension aborts pending work without restarting it; the next request resumes",async()=>{
    let delayed=true;const signals:AbortSignal[]=[],resolves:((value:Image)=>void)[]=[];const changed=vi.fn();
    const cache=new FrameCache<Image>((film,index,signal)=>{signals.push(signal);return delayed?new Promise(resolve=>resolves.push(resolve)):Promise.resolve(image(keyFor(film,index)));},4,2,changed);
    cache.request([{film:"tree",index:0}]);cache.suspend();expect(signals.every(signal=>signal.aborted)).toBe(true);
    delayed=false;const late=[image("late-0"),image("late-1")];resolves.forEach((resolve,index)=>resolve(late[index]!));await flush();
    expect(cache.pending).toBe(0);expect(cache.queued).toBe(0);expect(cache.entries.size).toBe(0);expect(signals).toHaveLength(2);expect(changed).not.toHaveBeenCalled();expect(late.every(value=>value.close.mock.calls.length===1)).toBe(true);
    cache.request([{film:"tree",index:20}]);await flush();expect(cache.get("tree:20")).toBeDefined();expect(cache.failures).toBe(0);cache.dispose();
  });
  it("LC-05 disposal closes late completed resources",async()=>{let resolve!:(image:Image)=>void;const cache=new FrameCache<Image>((_:Film)=>new Promise(r=>{resolve=r;}),4,1);cache.request([{film:"tree",index:0}]);cache.dispose();const late=image("late");resolve(late);await flush();expect(late.close).toHaveBeenCalledOnce();expect(cache.entries.size).toBe(0);});
});

it("FT-08/09 preparation follows direction and scales its bounded window with speed",async()=>{
  const calls:number[]=[];const cache=new FrameCache<Image>(async(_film,index)=>{calls.push(index);return image(String(index));},20,2);
  cache.request([{film:"tree",index:100}],[],{direction:-1,speed:48});await flush();await flush();
  expect(calls[0]).toBe(100);expect(calls[1]).toBe(99);expect(calls).toContain(92);expect(calls).not.toContain(108);
  expect(cache.entries.size).toBeLessThanOrEqual(20);expect(cache.peakPending).toBe(2);cache.dispose();
});
