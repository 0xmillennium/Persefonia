import { test,expect } from "@playwright/test";
import { ready,state } from "./support";
import { mkdir,writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
test("Short integrated film, visible navigator, hold and reveal workload",async({page,browser},info)=>{
  await page.goto("/");await ready(page);const initial=await state(page);
  expect(initial.renderer.kind).toBe("webgl2");
  const session=await page.context().newCDPSession(page);await session.send("Performance.enable");
  const browserResources=[{phase:"initial",metrics:(await session.send("Performance.getMetrics")).metrics}];
  await page.evaluate(()=>window.__filmExperiment!.resetMetrics());
  // Synthetic fine pointer stresses the solver even in the touch context. Native
  // touch arbitration is qualified separately; this is not physical-phone proof.
  const result=await page.evaluate(async()=>{
    const intervals:number[]=[],presented:string[]=[],readiness:boolean[]=[],resources:object[]=[];let previous=0;
    for(let n=0;n<120;n++){
      const time=await new Promise<number>(resolve=>requestAnimationFrame(resolve));
      if(previous)intervals.push(time-previous);previous=time;
      window.scrollTo(0,(.5+.35*Math.sin(n/18))*(document.documentElement.scrollHeight-innerHeight));
      document.querySelector("#stage")!.dispatchEvent(new PointerEvent("pointermove",{bubbles:true,pointerType:"mouse",isPrimary:true,clientX:innerWidth*(.5+.25*Math.sin(n*.15)),clientY:innerHeight*(.4+.15*Math.cos(n*.2))}));
      const s=window.__filmExperiment!.state();if(s.drawn)presented.push(s.drawn);readiness.push(s.drawn===`${s.film}:${s.desired}`);
      if(n%20===19)resources.push({step:n+1,cache:s.cache,rendererBytes:s.renderer.bytes});
    }
    intervals.sort((a,b)=>a-b);const at=(p:number)=>intervals[Math.min(intervals.length-1,Math.floor(intervals.length*p))];
    return {rafSteps:120,intervalsMs:{p50:at(.5),p95:at(.95),max:at(1)},drawSubmitted:presented,readyFraction:readiness.filter(Boolean).length/readiness.length,resources};
  });
  const mixed=await state(page);expect(mixed.field.injections).toBeGreaterThan(initial.field.injections);
  browserResources.push({phase:"mixed",metrics:(await session.send("Performance.getMetrics")).metrics});
  // Actual wall-clock hold, never a forced simulation clock in performance work.
  const box=page.viewportSize()!;await page.mouse.move(box.width*.5,box.height*.4);await page.mouse.down();
  await expect.poll(async()=>(await state(page)).commits,{timeout:20_000}).toBe(1);await page.mouse.up();
  const ending=await state(page);expect(ending.renderFailure).toBeNull();expect(ending.stalePresentations).toBe(0);
  expect(ending.cache.size).toBeLessThanOrEqual(20);expect(ending.cache.peakPending).toBeLessThanOrEqual(2);
  await expect.poll(async()=>{const s=await state(page);return !s.scheduled&&s.cache.pending===0;},{timeout:10_000}).toBe(true);
  const settled=await state(page);await page.waitForTimeout(250);expect((await state(page)).renderer.draws).toBe(settled.renderer.draws);
  browserResources.push({phase:"settled",metrics:(await session.send("Performance.getMetrics")).metrics});
  const folder=fileURLToPath(new URL(`../../../test-results/scroll-film-navigator/finalization/performance/${info.project.name}/`,import.meta.url));await mkdir(folder,{recursive:true});await page.screenshot({path:`${folder}ending.png`});
  await page.evaluate(()=>window.__filmExperiment!.dispose());await expect.poll(async()=>(await state(page)).cache.pending).toBe(0);
  const disposed=await state(page);expect(disposed.cache.size).toBe(0);expect(disposed.gpu.bytes).toBe(0);expect(disposed.renderer.bytes).toBe(0);expect(disposed.scheduled).toBe(false);
  browserResources.push({phase:"disposed",metrics:(await session.send("Performance.getMetrics")).metrics});await session.detach();
  await writeFile(`${folder}smoke.json`,JSON.stringify({method:"120 native RAF steps of simultaneous scroll/pointer, followed by one wall-clock hold/reveal, idle settlement and disposal. Owned-resource samples and CDP heap/DOM metrics are distinct from whole-process RAM or GPU VRAM. Draw-submission identity and readiness are not display scanout. Backend is reported; touch viewport is not physical mobile evidence.",browser:browser.version(),initial,mixed,result,ending,settled,disposed,browserResources},null,2));
});
