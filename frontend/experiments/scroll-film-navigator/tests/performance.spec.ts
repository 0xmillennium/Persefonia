import { test,expect } from "@playwright/test";
import { state,ready,progress } from "./support";
import { writeFile,mkdir } from "node:fs/promises";
import { cpus,totalmem,platform,release } from "node:os";
import { fileURLToPath } from "node:url";
const artifacts=fileURLToPath(new URL("../../../test-results/scroll-film-navigator/performance/",import.meta.url));
test("Measured isolated mixed workloads and bounded resource soak",async({page,browser},info)=>{
  const output=`${artifacts}${info.project.name}/`,viewport=page.viewportSize()!;
  test.setTimeout(300000);page.on("pageerror",error=>{throw error;});await mkdir(output,{recursive:true});await page.goto("/");await ready(page);await expect(page.locator("#debug")).toBeHidden();
  const session=await page.context().newCDPSession(page);await session.send("Performance.enable");
  const reports:Record<string,unknown>={},samples:unknown[]=[];
  const workload=async(name:string,frames:number,scroll:boolean,fluid:boolean)=>{
    const started=Date.now();await page.evaluate(()=>window.__filmExperiment!.resetMetrics());
    const callbackIntervals=await page.evaluate(async({frames,scroll,fluid})=>{
      const intervals:number[]=[];let previous=0;
      for(let n=0;n<frames;n++) {const time=await new Promise<number>(r=>requestAnimationFrame(r));if(previous)intervals.push(time-previous);previous=time;if(scroll)window.scrollTo(0,((Math.sin(n/(frames<100?5:36))+1)/2)*(document.documentElement.scrollHeight-innerHeight));if(fluid){const angle=n*.21;document.querySelector("#stage")!.dispatchEvent(new PointerEvent("pointermove",{bubbles:true,pointerType:"mouse",isPrimary:true,clientX:innerWidth*(.5+.3*Math.sin(angle)),clientY:innerHeight*(.45+.22*Math.cos(angle*1.3))}));}}
      intervals.sort((a,b)=>a-b);const at=(p:number)=>intervals[Math.min(intervals.length-1,Math.floor(intervals.length*p))]??0;
      return {count:intervals.length,p50:at(.5),p95:at(.95),p99:at(.99),max:at(1)};
    },{frames,scroll,fluid});
    await progress(page,.5);reports[name]={...await state(page),workloadCallbackIntervalsMs:callbackIntervals};samples.push({name,elapsedMs:Date.now()-started,...(await state(page)).cache,renderer:(await state(page)).renderer,browserMetrics:(await session.send("Performance.getMetrics")).metrics});
  };
  await workload("slow-scroll",180,true,false);await workload("fast-scroll",70,true,false);await workload("reverse-scrubbing",100,true,false);
  await workload("dense-navigator",180,false,true);await workload("simultaneous-scroll-fluid",180,true,true);
  await page.evaluate(()=>window.__filmExperiment!.resetMetrics());
  for(let n=0;n<4;n++){await page.getByRole("button",{name:n%2?"Robotic arm":"Tree",exact:true}).click();await expect.poll(async()=>(await state(page)).film).toBe(n%2?"robotic-arm":"tree");}reports["rapid-film-switches"]=await state(page);
  await page.evaluate(()=>window.__filmExperiment!.resetMetrics());
  for(let n=0;n<5;n++){await page.mouse.move(viewport.width*(.2+n*.12),viewport.height*.35);await page.mouse.down();await expect.poll(async()=>(await state(page)).hold.progress).toBeGreaterThan(.15);await page.mouse.up();}reports["cancel-repress"]=await state(page);
  await page.evaluate(()=>window.__filmExperiment!.resetMetrics());
  for(const size of [{width:1000,height:800},{width:390,height:844},{width:844,height:390},{width:1440,height:900},viewport])await page.setViewportSize(size);reports.resize=await state(page);
  await workload("soak-1800-raf-steps",1800,true,true);
  await page.evaluate(()=>window.__filmExperiment!.resetMetrics());const restorationStarted=Date.now();
  await page.evaluate(()=>window.__filmExperiment!.suspend());await page.evaluate(()=>window.__filmExperiment!.resume());await progress(page,.5);
  reports["suspension-restoration"]={...await state(page),restorationWallMs:Date.now()-restorationStarted};
  const s=await state(page);expect(s.cache.size).toBeLessThanOrEqual(8);expect(s.cache.peakPending).toBeLessThanOrEqual(2);expect(s.stalePresentations).toBe(0);expect(s.cache.failures).toBe(0);expect(s.renderFailure).toBeNull();
  // No synthetic 60Hz pass assertion. These are reported measurements on the observed backend.
  await writeFile(`${output}report.json`,JSON.stringify({environment:{cpu:cpus()[0]?.model,logicalCpus:cpus().length,ram:totalmem(),os:platform(),release:release(),node:process.version,browser:browser.version(),project:info.project.name,renderer:s.renderer.info,graphics:s.gpu.info,gpuDisabled:true,launchFlags:["--disable-gpu","--disable-software-rasterizer"],viewport:{...viewport,dpr:await page.evaluate(()=>devicePixelRatio)}},method:"Hidden diagnostics; bounded in-app JS/draw/active-RAF samples, independent workload callback intervals, CDP browser heap/DOM metrics. CPU target-to-draw is not photon latency. GPU acceleration disabled. Soak is 1800 requested RAF steps (actual duration recorded). A separate trace workload has instrumentation overhead and is excluded from ordinary measurements.",reports,resourceSamples:samples},null,2));
  // Separate diagnostic trace: screenshot events plus raster/compositor timeline. This
  // measures browser work, not display scanout or a physical 60Hz qualification.
  await session.send("Tracing.start",{categories:"devtools.timeline,disabled-by-default-devtools.timeline,disabled-by-default-devtools.timeline.frame,disabled-by-default-devtools.screenshot,blink.user_timing",transferMode:"ReturnAsStream"});
  await workload("trace-only",90,true,true);
  const completed=new Promise<{stream?:string}>(resolve=>session.once("Tracing.tracingComplete",resolve));
  await session.send("Tracing.end");const {stream}=await completed;
  if(!stream)throw new Error("Browser trace stream unavailable");
  let trace="";
  while(true){const chunk=await session.send("IO.read",{handle:stream});trace+=chunk.data;if(chunk.eof)break;}
  await session.send("IO.close",{handle:stream});await writeFile(`${output}browser-trace.json`,trace);
  const events=JSON.parse(trace).traceEvents as {name:string;dur?:number}[];
  const summary=Object.fromEntries(["DrawFrame","BeginFrame","RasterTask","CompositeLayers","Paint","Screenshot"].map(name=>{const selected=events.filter(e=>e.name===name),durations=selected.flatMap(e=>e.dur!==undefined?[e.dur/1000]:[]).sort((a,b)=>a-b);return [name,{events:selected.length,durationMs:durations.reduce((a,b)=>a+b,0),p95Ms:durations[Math.min(durations.length-1,Math.floor(durations.length*.95))]??null}];}));
  await writeFile(`${output}trace-summary.json`,JSON.stringify({method:"Separate 90-RAF-step traced workload. Event counts/durations are browser trace observations, not actual photon latency.",summary},null,2));await session.detach();
});
