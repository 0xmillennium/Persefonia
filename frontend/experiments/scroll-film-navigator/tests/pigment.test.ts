import { describe,it,expect } from "vitest";
import { absorbedPigment,illuminatedPigment,pigmentBands } from "../src/pigment";
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
  it("illuminates dark and bright imagery without clipping channel detail",()=>{
    for(const source of [0,.04,.5,.9,1])for(const pigment of [0,.1,.5,.95,1])for(const absorption of [.35,.72,.65])for(const light of [.4,.65,.9]){
      const color=illuminatedPigment(source,pigment,absorption,light);
      expect(color).toBeGreaterThanOrEqual(0);expect(color).toBeLessThanOrEqual(1);
      expect(color).toBeGreaterThanOrEqual(absorbedPigment(source,pigment,absorption));
    }
    expect(illuminatedPigment(.9,.5,.72,.8)).toBeLessThan(illuminatedPigment(1,.5,.72,.8));
    expect(illuminatedPigment(0,.95,.72,.8)).toBeGreaterThan(.8);
  });
});
