import { it,expect } from "vitest";
import { WheelCalibration } from "../src/wheel";
import { frameAt } from "../src/transport";
const packet=(deltaY:number,deltaMode=0,timeStamp=0)=>({deltaY,deltaMode,timeStamp,deltaX:0,ctrlKey:false,shiftKey:false});
it("Calibrates delta magnitude rather than counting events, conserving fractional native position",()=>{
  const wheel=new WheelCalibration();let y=0;
  for(let n=1;n<=191;n++){y=wheel.target(packet(120,0,n*10),y,4200)!;expect(frameAt(y/4200)).toBe(n);}
  expect(y).toBe(4200);
  for(let n=190;n>=0;n--){y=wheel.target(packet(-120,0,4000+(191-n)*10),y,4200)!;expect(frameAt(y/4200)).toBe(n);}
  expect(y).toBe(0);expect(wheel.target(packet(240),0,4200)).toBe(44);
  wheel.reset();expect(wheel.target(packet(3,1),0,4200)).toBe(22);
  wheel.reset();let fractional=0;for(let n=0;n<3;n++)fractional=wheel.target(packet(1,1,n*10),fractional,4200)!;expect(fractional).toBe(22);
});
it("Preserves continuous bursts, page scrolling, horizontal gestures and zoom",()=>{
  const wheel=new WheelCalibration();expect(wheel.target(packet(.7),100,4200)).toBeNull();expect(wheel.target(packet(120,0,20),100,4200)).toBeNull();
  expect(wheel.target(packet(120,0,200),100,4200)).toBe(122);
  for(const p of [packet(1,2),{...packet(120),ctrlKey:true},{...packet(120),shiftKey:true},{...packet(120),deltaX:2}])expect(wheel.target(p,100,4200)).toBeNull();
});
