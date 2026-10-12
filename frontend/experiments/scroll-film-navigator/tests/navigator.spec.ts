import { test,expect } from "@playwright/test";
import { ready,state,progress,isTouch } from "./support";
import { difference } from "./pixels";
import { mkdir,writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
const output=fileURLToPath(new URL("../../../test-results/scroll-film-navigator/navigator-refinement/",import.meta.url));
test.use({video:{mode:"on",size:{width:1200,height:800}}});
for(const basic of [false,true])test(`Navigator ${basic?"compatibility":"GPU"} material preserves speed, spatial transport and decay on both films`,async({page},info)=>{
  test.skip(isTouch(info.project.name),"Fine-pointer material; touch must not paint");
  await mkdir(output,{recursive:true});await page.goto(basic?"/?renderer=basic":"/");await ready(page);await progress(page,144/191);
  expect((await state(page)).renderer.kind).toBe(basic?"canvas2d":"webgl2");
  await page.evaluate(()=>window.__filmExperiment!.freeze(true));
  const label=basic?"compatibility":"gpu",reports=[];
  const gesture=async(points:{x:number;y:number}[],speed:number)=>page.evaluate(async({points,speed})=>{
    const start=performance.now();let time=start;
    for(let n=0;n<points.length;n++){
      const p=points[n]!,previous=points[n-1];const elapsed=previous?Math.hypot(p.x-previous.x,p.y-previous.y)/speed:0;
      time+=elapsed*1000;const event=new PointerEvent("pointermove",{bubbles:true,pointerType:"mouse",clientX:p.x,clientY:p.y});
      Object.defineProperty(event,"timeStamp",{value:time});document.querySelector("#stage")!.dispatchEvent(event);
      await window.__filmExperiment!.advance(elapsed);
    }
  },{points,speed});
  const clip={x:120,y:130,width:960,height:480};
  const shot=async(name:string)=>page.screenshot({clip,path:`${output}${label}-${name}.png`});
  for(const film of ["robotic-arm","tree"]){
    if(film==="tree"){
      await page.getByRole("button",{name:"Tree",exact:true}).click();await expect.poll(async()=>(await state(page)).next).toBe("tree:144");
      await page.evaluate(()=>window.__filmExperiment!.advance(1));await expect.poll(async()=>(await state(page)).drawn).toBe("tree:144");
    }
    await page.evaluate(()=>window.__filmExperiment!.clearFluid());const clean=await shot(`${film}-clean`);
    const line=Array.from({length:17},(_,n)=>({x:300+n*30,y:350}));
    await gesture(line,600);const slow=await page.evaluate(()=>window.__filmExperiment!.fieldMetrics());await shot(`${film}-slow`);
    await page.evaluate(()=>window.__filmExperiment!.clearFluid());await gesture(line,3000);
    const fast=await page.evaluate(()=>window.__filmExperiment!.fieldMetrics());await shot(`${film}-fast`);
    expect(slow.varianceY).toBeGreaterThan(fast.varianceY*1.3);
    await page.evaluate(()=>window.__filmExperiment!.clearFluid());
    const curve=Array.from({length:33},(_,n)=>({x:600+200*Math.sin(n*Math.PI/16),y:350+115*Math.sin(n*Math.PI/8)}));
    await gesture(curve,1400);const painted=await shot(`${film}-crossing`),initial=await page.evaluate(()=>window.__filmExperiment!.fieldMetrics()),injections=(await state(page)).field.injections;
    expect(difference(clean,painted).changed).toBeGreaterThan(1000);
    await page.evaluate(async()=>{for(let n=0;n<12;n++)await window.__filmExperiment!.advance(1/30);});
    const moved=await shot(`${film}-post-stop`),advected=await page.evaluate(()=>window.__filmExperiment!.fieldMetrics());
    expect(advected.mass).toBeGreaterThan(0);expect(advected.mass).toBeLessThan(initial.mass);
    expect(Math.abs(initial.varianceX-advected.varianceX)+Math.abs(initial.varianceY-advected.varianceY)).toBeGreaterThan(1);
    expect(difference(painted,moved).changed).toBeGreaterThan(500);expect((await state(page)).field.injections).toBe(injections);
    expect((await state(page)).core).toMatchObject({visible:true,x:"600px"});
    await page.evaluate(()=>window.__filmExperiment!.advance(4.5));const decayed=await shot(`${film}-decayed`);
    expect(difference(clean,decayed).changed).toBeLessThan(200);expect((await state(page)).field.active).toBe(false);expect((await state(page)).scheduled).toBe(false);
    reports.push({film,slow,fast,initial,advected,painted:difference(clean,painted),postStop:difference(painted,moved),decayed:difference(clean,decayed),state:await state(page)});
  }
  await writeFile(`${output}${label}-measurements.json`,JSON.stringify({method:"Canonical current media, controlled timestamped gestures and actual rendered pixels. Twelve post-stop transport steps distinguish spatial evolution from scalar fading. Video shows the controlled sequence; no wall-clock performance or human artistic approval claim.",reports},null,2));
  await info.attach(`${label}-navigator`,{body:JSON.stringify(reports),contentType:"application/json"});
});
