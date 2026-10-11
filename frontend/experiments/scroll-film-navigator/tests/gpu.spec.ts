import { test,expect } from "@playwright/test";
import { ready,state,isTouch } from "./support";
import { mkdir,writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
test("LN GPU-owned field preserves spatial residue and dose across input packet rates",async({page},info)=>{
  test.skip(isTouch(info.project.name),"Fine-pointer solver study; native touch does not inject pigment");
  await page.goto("/");await ready(page);expect((await state(page)).renderer.kind).toBe("webgl2");
  const stroke=async(count:number)=>page.evaluate(async count=>{
    window.__filmExperiment!.clearFluid();window.__filmExperiment!.freeze(true);
    const time=performance.now();
    for(let n=0;n<=count;n++){const event=new PointerEvent("pointermove",{bubbles:true,pointerType:"mouse",isPrimary:true,clientX:200+700*n/count,clientY:340});Object.defineProperty(event,"timeStamp",{value:time+n/count*700});document.querySelector("#stage")!.dispatchEvent(event);}
    await window.__filmExperiment!.advance(.02);return window.__filmExperiment!.fieldMetrics();
  },count);
  const coarse=await stroke(8),dense=await stroke(32);expect(coarse.mass).toBeGreaterThan(0);expect(dense.mass/coarse.mass).toBeGreaterThan(.75);expect(dense.mass/coarse.mass).toBeLessThan(1.25);
  const initial=await page.screenshot();await page.evaluate(()=>window.__filmExperiment!.advance(.3));const advected=await page.evaluate(()=>window.__filmExperiment!.fieldMetrics());expect(advected.mass).toBeGreaterThan(0);expect(advected.mass).toBeLessThan(dense.mass);expect(Math.abs(advected.varianceX-dense.varianceX)).toBeGreaterThan(.01);
  await page.evaluate(()=>window.__filmExperiment!.advance(4.5));expect((await state(page)).field.active).toBe(false);expect((await state(page)).scheduled).toBe(false);
  // A preceding long frame interval must expire old material, not fresh queued input.
  // Inject and advance in one task: a zero-delta inspection RAF must not consume
  // the queued input before the deliberately delayed step is exercised.
  await page.evaluate(async()=>{window.__filmExperiment!.clearFluid();const time=performance.now();for(let n=0;n<2;n++){const event=new PointerEvent("pointermove",{bubbles:true,pointerType:"mouse",isPrimary:true,clientX:300+n*100,clientY:340});Object.defineProperty(event,"timeStamp",{value:time+n*100});document.querySelector("#stage")!.dispatchEvent(event);}await window.__filmExperiment!.advance(4.5);});
  expect((await state(page)).field.active).toBe(true);
  const freshAfterStall=await page.evaluate(()=>window.__filmExperiment!.fieldMetrics());expect(freshAfterStall.mass).toBeGreaterThan(0);
  await page.evaluate(()=>window.__filmExperiment!.advance(4.5));expect((await state(page)).field.active).toBe(false);
  const output=fileURLToPath(new URL(`../../../test-results/scroll-film-navigator/gpu/${info.project.name}/`,import.meta.url));await mkdir(output,{recursive:true});await writeFile(`${output}packet-residue.png`,initial);await writeFile(`${output}measurements.json`,JSON.stringify({method:"Actual primary GPU targets, explicit inspection readback outside normal render loop; synthetic timestamped controlled input. Dose/transport/decay relationships, not human morphology approval or physical performance.",coarse,dense,advected,freshAfterStall,state:await state(page)},null,2));
});
