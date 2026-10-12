import { test,expect } from "@playwright/test";
import { ready,state,isTouch } from "./support";
import { difference } from "./pixels";
import { mkdir,writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
const artifacts=fileURLToPath(new URL("../../../test-results/scroll-film-navigator/finalization/",import.meta.url));
for(const basic of [false,true])test(`Rounded pointer clocks paint visible ${basic?"compatibility":"GPU"} pigment`,async({page},info)=>{
  test.skip(isTouch(info.project.name),"Fine-pointer regression; touch does not paint");
  await mkdir(`${artifacts}followup`,{recursive:true});await page.goto(basic?"/?renderer=basic":"/");await ready(page);
  expect((await state(page)).renderer.kind).toBe(basic?"canvas2d":"webgl2");
  await page.evaluate(async()=>{window.__filmExperiment!.freeze(true);window.__filmExperiment!.clearFluid();await window.__filmExperiment!.advance(0);});
  const clip={x:180,y:180,width:840,height:380},clean=await page.screenshot({clip});
  await page.evaluate(async()=>{
    for(let n=0;n<25;n++){
      const event=new PointerEvent("pointermove",{bubbles:true,pointerType:"mouse",clientX:220+n*24,clientY:330+Math.sin(n*.2)*55});
      Object.defineProperty(event,"timeStamp",{value:100});document.querySelector("#stage")!.dispatchEvent(event);
      await window.__filmExperiment!.advance(1/60);
    }
  });
  const painted=await page.screenshot({clip,path:`${artifacts}followup/${basic?"compatibility":"gpu"}-rounded.png`});
  expect((await state(page)).field.injections).toBe(24);expect(difference(clean,painted).changed).toBeGreaterThan(1000);
  await page.evaluate(()=>window.__filmExperiment!.advance(4.5));
  expect(difference(clean,await page.screenshot({clip})).changed).toBeLessThan(200);
  await page.emulateMedia({reducedMotion:"reduce"});
  // WebKit acknowledges emulation before dispatching the media-query change.
  // Frozen transport cannot age the field away: this waits for actual suppression.
  await expect.poll(async()=>{const s=await state(page);return {reduced:s.reduced,active:s.field.active,reason:s.navigator.reason};}).toEqual({reduced:true,active:false,reason:"reduced-motion"});
  const injections=(await state(page)).field.injections;await page.mouse.move(300,400);await page.mouse.move(500,400);
  expect((await state(page)).field.injections).toBe(injections);
  expect((await state(page)).field.active).toBe(false);expect((await state(page)).navigator.reason).toBe("reduced-motion");
});
test("Visible pigment, spatial residue and decay reach final pixels in both films",async({page},info)=>{
  test.skip(isTouch(info.project.name),"Fine-pointer material; touch must not inject");
  await mkdir(`${artifacts}navigator`,{recursive:true});await page.goto("/");await ready(page);
  expect((await state(page)).renderer.kind,JSON.stringify((await state(page)).renderer)).toBe("webgl2");
  await page.evaluate(()=>window.__filmExperiment!.freeze(true));
  const reports=[];
  for(const film of ["robotic-arm","tree"]){
    if(film==="tree"){
      await page.getByRole("button",{name:"Tree",exact:true}).click();
      await expect.poll(async()=>(await state(page)).next).toBe("tree:0");
      await page.evaluate(()=>window.__filmExperiment!.advance(1));
    }
    expect((await state(page)).film).toBe(film);await page.evaluate(()=>window.__filmExperiment!.clearFluid());
    const clip={x:180,y:180,width:840,height:380};
    const clean=await page.screenshot({clip});await page.mouse.move(220,330);
    for(let n=1;n<=24;n++){await page.mouse.move(220+n*28,330+Math.sin(n*.25)*60);await page.evaluate(()=>window.__filmExperiment!.advance(1/60));}
    const painted=await page.screenshot({path:`${artifacts}navigator/${film}-painted.png`,clip});
    const field=await page.evaluate(()=>window.__filmExperiment!.fieldMetrics());
    await info.attach(`${film}-state`,{body:JSON.stringify(await state(page),null,2),contentType:"application/json"});
    expect(field.mass).toBeGreaterThan(0);expect(difference(clean,painted).changed).toBeGreaterThan(1000);
    await page.evaluate(()=>window.__filmExperiment!.advance(.35));
    const residue=await page.screenshot({path:`${artifacts}navigator/${film}-residue.png`,clip});
    expect(difference(clean,residue).changed).toBeGreaterThan(500);expect(difference(painted,residue).changed).toBeGreaterThan(500);
    await page.evaluate(()=>window.__filmExperiment!.advance(4.5));
    const decayed=await page.screenshot({path:`${artifacts}navigator/${film}-decayed.png`,clip});
    expect((await state(page)).field.active).toBe(false);expect(difference(clean,decayed).changed).toBeLessThan(200);
    reports.push({film,field,painted:difference(clean,painted),residue:difference(clean,residue),decayed:difference(clean,decayed),state:await state(page)});
  }
  await writeFile(`${artifacts}navigator/pixels-${info.project.name}.json`,JSON.stringify({method:"Actual GPU and captured final pixels, controlled simulation steps; not wall-clock performance or human material approval.",reports},null,2));
});
test("Calibrated wheel and continuous native input select deterministic frames",async({page},info)=>{
  test.skip(isTouch(info.project.name),"Wheel desktop calibration; native touch coverage remains separate");
  await page.goto("/");await ready(page);await page.mouse.move(600,300);
  await page.mouse.wheel(0,120);await expect.poll(async()=>(await state(page)).drawn).toBe("robotic-arm:1");
  await page.mouse.wheel(0,240);await expect.poll(async()=>(await state(page)).drawn).toBe("robotic-arm:3");
  await page.mouse.wheel(0,-120);await expect.poll(async()=>(await state(page)).drawn).toBe("robotic-arm:2");
  const discrete=await page.evaluate(()=>scrollY);
  for(let n=0;n<6;n++)await page.mouse.wheel(0,5);
  await expect.poll(async()=>page.evaluate(()=>scrollY)).toBeGreaterThan(discrete+20);
  const continuous=await page.evaluate(()=>({y:scrollY,range:document.documentElement.scrollHeight-innerHeight}));
  await expect.poll(async()=>(await state(page)).desired).toBe(Math.round(continuous.y/continuous.range*191));
  await page.screenshot({path:`${artifacts}wheel-${info.project.name}.png`});
  await writeFile(`${artifacts}wheel-${info.project.name}.json`,JSON.stringify({method:"Native browser-injected 120/240/-120px wheel and 5px continuous packets; no physical detent claim",discrete,continuous,state:await state(page)},null,2));
});
test("Suspension invalidates pending movement while preserving existing pigment decay",async({page},info)=>{
  test.skip(isTouch(info.project.name),"Fine-pointer queue regression");
  await page.goto("/");await ready(page);await page.evaluate(()=>window.__filmExperiment!.freeze(true));
  const inject=()=>{const time=performance.now();for(let n=0;n<2;n++){const event=new PointerEvent("pointermove",{bubbles:true,pointerType:"mouse",clientX:300+n*120,clientY:340});Object.defineProperty(event,"timeStamp",{value:time+n*30});document.querySelector("#stage")!.dispatchEvent(event);}};
  // Consume real dye, then suspend briefly: existing material must remain.
  await page.evaluate(inject);await page.evaluate(()=>window.__filmExperiment!.advance(.02));
  const original=await page.evaluate(()=>window.__filmExperiment!.fieldMetrics());expect(original.mass).toBeGreaterThan(0);
  await page.evaluate(()=>window.__filmExperiment!.suspend());await page.waitForTimeout(150);await page.evaluate(()=>window.__filmExperiment!.resume());
  const retained=await page.evaluate(()=>window.__filmExperiment!.fieldMetrics());expect(retained.mass).toBeGreaterThan(0);expect(retained.mass).toBeLessThan(original.mass);
  // Queue and suspend within one browser task so RAF cannot consume the sample.
  await page.evaluate(()=>{window.__filmExperiment!.clearFluid();const time=performance.now();for(let n=0;n<2;n++){const event=new PointerEvent("pointermove",{bubbles:true,pointerType:"mouse",clientX:300+n*120,clientY:340});Object.defineProperty(event,"timeStamp",{value:time+n*30});document.querySelector("#stage")!.dispatchEvent(event);}window.__filmExperiment!.suspend();});
  await page.waitForTimeout(4400);await page.evaluate(()=>window.__filmExperiment!.resume());
  expect((await state(page)).field.active).toBe(false);expect((await page.evaluate(()=>window.__filmExperiment!.fieldMetrics())).mass).toBe(0);
});
test("Film theme settles at commit, survives cancellation and has readable surfaces",async({page},info)=>{
  await page.goto("/");await ready(page);await page.evaluate(()=>window.__filmExperiment!.freeze(true));
  const root=page.locator("html");await expect(root).toHaveAttribute("data-theme","dark");
  await page.mouse.move(350,300);await page.mouse.down();await page.evaluate(()=>window.__filmExperiment!.advance(.8));
  await expect(root).toHaveAttribute("data-theme","dark");await page.mouse.up();await page.evaluate(()=>window.__filmExperiment!.advance(.2));await expect(root).toHaveAttribute("data-theme","dark");
  await page.screenshot({path:`${artifacts}theme-dark-${info.project.name}.png`});
  await page.getByRole("button",{name:"Tree",exact:true}).click();await expect.poll(async()=>(await state(page)).next).toBe("tree:0");
  await expect(root).toHaveAttribute("data-theme","dark");await page.evaluate(()=>window.__filmExperiment!.advance(1));await expect(root).toHaveAttribute("data-theme","light");
  const contrast=await page.evaluate(()=>{
    const values=(s:string)=>s.match(/[\d.]+/g)!.map(Number);
    const luminance=(v:number[])=>v.slice(0,3).map(c=>c/255).map(c=>c<=.04045?c/12.92:((c+.055)/1.055)**2.4).reduce((n,c,i)=>n+c*[.2126,.7152,.0722][i]!,0);
    return ["header p","h1",".instructions",".controls"].map(selector=>{
      const style=getComputedStyle(document.querySelector(selector)!);const fg=values(style.color),bg=values(style.backgroundColor),alpha=bg[3]??1;
      const ratios=[0,255].map(back=>{const a=luminance(fg),b=luminance(bg.map(c=>c*alpha+back*(1-alpha)));return (Math.max(a,b)+.05)/(Math.min(a,b)+.05);});
      return {selector,minimum:Math.min(...ratios)};
    });
  });
  for(const entry of contrast)expect(entry.minimum).toBeGreaterThanOrEqual(4.5);
  await page.screenshot({path:`${artifacts}theme-light-${info.project.name}.png`});
  await page.evaluate(()=>window.__filmExperiment!.freeze(false));await page.reload();await expect(root).toHaveAttribute("data-theme","light");
});
