import { describe,it,expect } from "vitest";
import { Hold,HOLD } from "../src/hold";
import { coverage } from "../src/material";
import { FilmTransport } from "../src/transport";
const advance=(h:Hold,seconds:number,ready=true,cover=1)=>{let commits=0;for(let t=0;t<seconds;t+=1/120)if(h.step(1/120,ready,cover))commits++;return commits;};
describe("Interruptible hold",()=>{
  it("Visible low callback rates preserve analytic expansion and retarget response",()=>{
    const slow=new Hold(),fast=new Hold();slow.select({x:200,y:200});fast.select({x:200,y:200});
    slow.step(.5,false,0);advance(fast,.5,false,0);expect(slow.expansion).toBeCloseTo(fast.expansion,4);expect(slow.expansion).toBeGreaterThan(.99);
    slow.release();slow.press({x:800,y:500},"mouse");slow.step(.5,false,0);expect(slow.center.x).toBeCloseTo(800+(200-800)*Math.exp(-HOLD.retargetDamping*.5),6);
  });
  it("Semantic repeated selection is idempotent; canceled selection resumes existing material",()=>{
    const h=new Hold();h.select({x:200,y:200});advance(h,.1,false,0);const expansion=h.expansion;
    h.select({x:800,y:500});expect(h.expansion).toBe(expansion);expect(h.center).toEqual({x:200,y:200});
    h.release();advance(h,.05,false,0);const residual=h.expansion,center={...h.center};
    h.select({x:800,y:500});expect(h.expansion).toBe(residual);expect(h.center).toEqual(center);expect(h.target).toEqual({x:800,y:500});expect(h.phase).toBe("COMMIT");
  });
  it("PH-01 initiates with continuous countdown",()=>{const h=new Hold();expect(h.press({x:200,y:200},"mouse")).toBe(true);expect(h.phase).toBe("HOLDING");expect(h.countdown).toBe(3);advance(h,1);expect(h.countdown).toBe(2);});
  it("PH-02 release visibly contracts toward idle without committing",()=>{const h=new Hold();h.press({x:300,y:200},"mouse");advance(h,1.5);const radius=h.expansion;h.release();expect(h.phase).toBe("CANCELING");expect(advance(h,.3)).toBe(0);expect(h.expansion).toBeLessThan(radius);advance(h,3);expect(h.phase).toBe("IDLE");expect(h.expansion).toBe(0);});
  it("PH-03/04/13 re-press preserves morphology, residual progress and countdown",()=>{const h=new Hold();h.press({x:100,y:100},"mouse");advance(h,2.4);h.release();advance(h,.12);const progress=h.progress,expansion=h.expansion;expect(h.press({x:900,y:500},"mouse")).toBe(true);expect(h.progress).toBe(progress);expect(h.expansion).toBe(expansion);expect(h.countdown).toBe(1);expect(h.center.x).toBe(100);h.step(.016,false,0);expect(h.center.x).toBeGreaterThan(100);expect(h.center.x).toBeLessThan(900);});
  it("PH-05/11 follows fine pointer offscreen without canceling and contracts there",()=>{const h=new Hold();h.press({x:10,y:10},"pen");h.move({x:-400,y:1200});advance(h,.5,false,0);expect(h.pressed).toBe(true);expect(h.center.x).toBeLessThan(-300);h.release();expect(h.target).toEqual({x:-400,y:1200});});
  it("PH-06 duration, coverage and readiness independently guard a single commit",()=>{const h=new Hold();h.press({x:100,y:100},"mouse");expect(advance(h,3.5,false,1)).toBe(0);expect(h.phase).toBe("COMMIT");expect(advance(h,.2,true,.9)).toBe(0);expect(h.step(.016,true,1)).toBe(true);expect(h.phase).toBe("GRACE");expect(advance(h,.2)).toBe(0);h.release();expect(h.phase).toBe("IDLE");});
  it("PH-07/08 long continuous hold cycles Robot → Tree → Robot",()=>{const h=new Hold(),t=new FilmTransport();h.press({x:100,y:100},"mouse");const order=[t.film];for(let i=0;i<950;i++){if(h.step(1/120,true,1)){t.switchFilm();order.push(t.film);}}expect(order).toEqual(["robotic-arm","tree","robotic-arm"]);expect(t.commits).toBe(2);});
  it("PH-09 touch movement cancels, never continuously retargets",()=>{const h=new Hold();h.press({x:100,y:100},"touch");h.move({x:105,y:105});expect(h.pressed).toBe(true);expect(h.target).toEqual({x:100,y:100});h.move({x:100+HOLD.touchSlop+1,y:100});expect(h.phase).toBe("CANCELING");expect(h.pressed).toBe(false);});
  it("PH-10 touch re-press retains residual field",()=>{const h=new Hold();h.press({x:10,y:10},"touch");advance(h,1);h.release();advance(h,.1);const p=h.progress;h.press({x:300,y:100},"touch");expect(h.progress).toBe(p);expect(h.target).toEqual({x:300,y:100});expect(h.center).toEqual({x:10,y:10});});
  it("PH-12 cancel and readiness timeout cannot leave stuck input",()=>{const h=new Hold();h.press({x:0,y:0},"mouse");advance(h,3);h.release();advance(h,4);expect(h.phase).toBe("IDLE");h.press({x:0,y:0},"mouse");advance(h,20,false);expect(h.phase).toBe("IDLE");});
  it("VC-01 mask reaches full viewport in portrait and offscreen geometry",()=>{for(const [width,height] of [[1200,800],[390,844],[800,1200]] as const)for(const center of [{x:10,y:10},{x:-400,y:1000},{x:width/2,y:height/2}]){expect(coverage(0,center,width!,height!,0)).toBe(0);expect(coverage(1,center,width!,height!,0)).toBe(1);expect(coverage(.4,center,width!,height!,0)).toBeLessThan(1);}});
  it("LC-07 reduced-motion holds retain readiness and duration gates",()=>{const h=new Hold();h.press({x:10,y:10},"mouse");expect(advance(h,2,false,0)).toBe(0);advance(h,1,false,0);expect(h.step(.016,true,0,true)).toBe(true);});
});
