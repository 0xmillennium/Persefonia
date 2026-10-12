import { describe,it,expect } from "vitest";
import { Hold,HOLD } from "../src/hold";
import { HoldClock } from "../src/hold-clock";

describe("Hold event/frame clock ownership",()=>{
  it("assigns pre-release time to growth and pre-repress time to erosion across a delayed frame",()=>{
    const hold=new Hold(),clock=new HoldClock();clock.beforeEvent(hold,1000);hold.press({x:10,y:10},"touch");
    hold.step(clock.frame(1750),false,0);expect(hold.progress).toBeCloseTo(.25);
    clock.beforeEvent(hold,1840);hold.release();const released=hold.progress;
    expect(released).toBeCloseTo(.28);
    hold.step(clock.frame(1870),false,0);expect(hold.progress).toBeCloseTo(released-.03*HOLD.contractionRate);
    clock.beforeEvent(hold,1890);hold.press({x:200,y:100},"touch");
    expect(hold.progress).toBeCloseTo(released-.05*HOLD.contractionRate);
    hold.step(clock.frame(1940),false,0);expect(hold.progress).toBeCloseTo(released-.05*HOLD.contractionRate+.05/HOLD.duration);
  });
  it("does not rewind for an older RAF timestamp or count idle/suspended time",()=>{
    const hold=new Hold(),clock=new HoldClock();clock.beforeEvent(hold,9000);hold.press({x:10,y:10},"mouse");
    expect(clock.frame(8990)).toBe(0);expect(clock.frame(9100)).toBeCloseTo(.1);
    clock.reset();expect(clock.frame(60000)).toBe(0);
  });
  it("does not consume readiness or earned reveal time on ignored pointer events",()=>{
    const hold=new Hold(),clock=new HoldClock();clock.frame(1000);hold.select({x:10,y:10});
    clock.beforeEvent(hold,1100);expect(hold.step(clock.frame(1200),true,1,true)).toBe(false);
    clock.beforeEvent(hold,1250);expect(hold.step(clock.frame(1300),true,1,true)).toBe(true);expect(hold.cycles).toBe(1);
  });
});
