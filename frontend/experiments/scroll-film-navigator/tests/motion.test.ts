import { describe,it,expect } from "vitest";
import { queueMotion,resampleMotion,motionSplat,PointerObserver,type MotionSample } from "../src/input";
const segment=(x:number,distance:number,direction=1):MotionSample=>({x,y:0,distance,vx:direction*100,vy:0,speed:100});
describe("Bounded ordered motion geometry",()=>{
  it("accumulates tiny coalesced movement instead of erasing slow strokes",()=>{
    const distance=(packets:number)=>{const observer=new PointerObserver();let total=0;observer.observe(0,0,0);for(let n=1;n<=packets;n++)total+=observer.observe(n*10/packets,0,n*200/packets)?.distance??0;return total;};
    expect(distance(200)).toBeGreaterThan(9.8);expect(distance(50)).toBeGreaterThan(9.8);
    expect(Math.abs(distance(200)-distance(50))).toBeLessThan(.11);
  });
  it("retains monotonic timestamp and inactivity rules while accumulating input",()=>{
    const observer=new PointerObserver();observer.observe(0,0,0);observer.observe(.05,0,1);
    expect(observer.observe(10,0,1)).toBeNull();expect(observer.observe(10,0,.5)).toBeNull();
    expect(observer.observe(.2,0,2)?.distance).toBeCloseTo(.2);
    expect(observer.observe(10,0,1000)).toBeNull();expect(observer.observe(10.2,0,1002)?.distance).toBeCloseTo(.2);
  });
  it("conserves submitted dose across long single and multiple batches",()=>{
    const sample=(distance:number):MotionSample=>({x:distance,y:0,vx:4500,vy:0,speed:4500,distance});
    const dose=(distance:number)=>resampleMotion([sample(distance)]).reduce((n,s)=>n+motionSplat(s).dose,0);
    expect(resampleMotion([sample(960)])).toHaveLength(32);
    expect(dose(960)).toBeCloseTo(dose(320)*3);
  });
  it("keeps a reversal beyond the former twelve-packet limit on its actual path",()=>{
    const queue:MotionSample[]=[];
    for(let n=1;n<=11;n++)queueMotion(queue,segment(100*n/11,100/11));
    queueMotion(queue,segment(120,20));queueMotion(queue,segment(100,20,-1));
    expect(queue).toHaveLength(13);expect(queue.at(-2)?.x).toBe(120);
    const deposits=resampleMotion(queue);expect(deposits.every(p=>p.x>=0&&p.x<=120)).toBe(true);
    expect(deposits.some(p=>p.vx<0)).toBe(true);expect(deposits.reduce((n,p)=>n+p.distance,0)).toBeCloseTo(140);
  });
  it("resamples the same arc length independently of packet count",()=>{
    const path=(count:number)=>Array.from({length:count},(_,i)=>segment((i+1)*200/count,200/count));
    const coarse=resampleMotion(path(8)),dense=resampleMotion(path(32));
    expect(coarse).toHaveLength(dense.length);
    for(let i=0;i<coarse.length;i++){expect(coarse[i]!.x).toBeCloseTo(dense[i]!.x);expect(coarse[i]!.distance).toBe(dense[i]!.distance);}
  });
  it("retains complete forward-collinear length within the pending budget",()=>{
    const queue:MotionSample[]=[];let dropped=0;
    for(let n=1;n<=128;n++)if(queueMotion(queue,segment(n,1)))dropped++;
    expect(queue.length).toBeLessThanOrEqual(32);expect(dropped).toBe(0);
    expect(queue.reduce((n,p)=>n+p.distance,0)).toBe(128);expect(resampleMotion(queue).at(-1)!.x).toBeLessThan(128);
  });
  it("ages out old non-collinear segments without extending the final tangent",()=>{
    const queue:MotionSample[]=[];let dropped=0;
    for(let n=0;n<48;n++)if(queueMotion(queue,segment(n%2?100:120,20,n%2?-1:1)))dropped++;
    expect(queue).toHaveLength(32);expect(dropped).toBe(16);
    expect(resampleMotion(queue).every(p=>p.x>=100&&p.x<=120)).toBe(true);
  });
});
