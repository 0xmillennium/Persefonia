import { test,expect } from "@playwright/test";
import { state,ready,progress,isTouch } from "./support";
import { difference } from "./pixels";
import { mkdir,writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
const artifacts=fileURLToPath(new URL("../../../test-results/scroll-film-navigator/visual/",import.meta.url));
test("Rendered film, organic reveal, contraction, navigator refraction and responsive diagnostics",async({page},info)=>{
  page.on("pageerror",error=>{throw error;});
  test.setTimeout(180000);await mkdir(`${artifacts}${info.project.name}`,{recursive:true});
  const shot=async(name:string)=>{const buffer=await page.screenshot({path:`${artifacts}${info.project.name}/${name}.png`});return buffer;};
  await page.goto("/");await ready(page);expect((await state(page)).renderFailure).toBeNull();
  await shot("01-initial");await progress(page,.5);await shot("02-midpoint");await progress(page,1);await shot("03-final");await progress(page,.35);await shot("04-reverse");
  const measurements:Record<string,unknown>={};
  if(!isTouch(info.project.name)) {
    await page.evaluate(()=>window.__filmExperiment!.freeze(true));
    const scripted=async(start:{x:number;y:number},end:{x:number;y:number},speed:number)=>page.evaluate(async({start,end,speed})=>{
      const distance=Math.hypot(end.x-start.x,end.y-start.y),steps=24;
      const time=performance.now();
      for(let n=0;n<=steps;n++) {
        const event=new PointerEvent("pointermove",{bubbles:true,pointerType:"mouse",clientX:start.x+(end.x-start.x)*n/steps,clientY:start.y+(end.y-start.y)*n/steps});
        Object.defineProperty(event,"timeStamp",{value:time+distance/speed*n/steps*1000});
        document.querySelector("#stage")!.dispatchEvent(event);
        if(n>0)await window.__filmExperiment!.advance(distance/speed/steps);
      }
    },{start,end,speed});
    const clean=await page.screenshot({clip:{x:180,y:180,width:840,height:380}});
    await scripted({x:200,y:350},{x:950,y:350},600);await shot("05-slow-liquid");
    const liquid=await page.screenshot({clip:{x:180,y:180,width:840,height:380}});measurements.deformation=difference(clean,liquid);
    expect(difference(clean,liquid).changed).toBeGreaterThan(100);
    measurements.slow=await page.evaluate(()=>window.__filmExperiment!.fieldMetrics());
    await page.evaluate(()=>{window.__filmExperiment!.clearFluid();});
    await scripted({x:180,y:470},{x:1000,y:280},3000);await shot("06-fast-liquid");
    measurements.fast=await page.evaluate(()=>window.__filmExperiment!.fieldMetrics());
    await page.evaluate(()=>window.__filmExperiment!.advance(.25));await shot("07-residual");
    await page.evaluate(()=>window.__filmExperiment!.advance(4.5));expect((await state(page)).field.active).toBe(false);await shot("08-dissipated");
    await page.evaluate(()=>window.__filmExperiment!.freeze(true));
    const advance=async(seconds:number)=>page.evaluate(t=>window.__filmExperiment!.advance(t),seconds);
    await page.mouse.move(500,350);await page.mouse.down();await advance(1.3);await shot("09-hold-growth");
    const expanded=(await state(page)).hold.expansion;await page.mouse.up();await advance(.22);expect((await state(page)).hold.expansion).toBeLessThan(expanded);await shot("10-contraction");
    const residue=(await state(page)).hold.progress;await page.mouse.move(650,300);await page.mouse.down();expect((await state(page)).hold.progress).toBe(residue);await advance(.2);await shot("11-repress");
    await advance(1.3);measurements.lateGrowth=await state(page);await shot("12-late-current-film-growth");for(let n=0;n<40&&(await state(page)).commits===0;n++)await advance(.05);
    await expect.poll(async()=>(await state(page)).commits).toBe(1);await shot("13-tree-selected");
    await advance(4);expect((await state(page)).commits).toBe(1);await shot("14-success-wait-release");await page.mouse.up();await page.evaluate(()=>window.__filmExperiment!.freeze(false));
    measurements.ending=await state(page);
  } else {
    await page.getByRole("button",{name:"Tree",exact:true}).click();await expect.poll(async()=>(await state(page)).film).toBe("tree");await shot("13-tree-selected");
  }
  await page.setViewportSize({width:390,height:844});await shot("15-portrait");await page.getByRole("button",{name:"Tree",exact:true}).click();await expect.poll(async()=>(await state(page)).film).toBe("tree");await shot("16-portrait-tree");
  await page.setViewportSize({width:844,height:390});await shot("17-landscape");
  await page.emulateMedia({reducedMotion:"reduce"});await shot("18-reduced-motion");
  await writeFile(`${artifacts}${info.project.name}/measurements.json`,JSON.stringify(measurements,null,2));
});
