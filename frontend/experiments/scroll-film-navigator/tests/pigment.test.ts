import { describe,it,expect } from "vitest";
import { absorbedPigment,pigmentBands } from "../src/pigment";
describe("Pigment material",()=>{
  it("absorbs a pale film instead of adding clipped fluorescent light",()=>{
    for(const source of [0,.04,.5,1])for(const pigment of [.005,.08,.25,.85])for(const absorption of [.35,.72,.65]){
      const target=absorbedPigment(source,pigment,absorption);expect(target).toBeGreaterThanOrEqual(0);expect(target).toBeLessThan(1);
    }
    expect(absorbedPigment(1,.85,.72)).toBeLessThan(1);
    expect(absorbedPigment(0,.85,.72)).toBeGreaterThan(.5);
  });
  it("seeds spatial color variation along a stroke rather than identical transverse stripes",()=>{
    const a=pigmentBands(200,350,0),b=pigmentBands(600,350,0);expect(a).not.toEqual(b);
    for(const value of [...a,...b]){expect(value).toBeGreaterThanOrEqual(0);expect(value).toBeLessThanOrEqual(1);}
  });
});
