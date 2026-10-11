import { test,expect } from "@playwright/test";
import { ready,state,progress } from "./support";
import { mkdir,writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

test("Constrained connection retains the complete renderer and last valid film during preparation",async({page,browserName},info)=>{
  test.skip(browserName!=="chromium","CDP bandwidth shaping is Chromium-specific; other engines retain functional coverage.");
  test.setTimeout(90000);
  const session=await page.context().newCDPSession(page);
  await session.send("Network.enable");await session.send("Network.setCacheDisabled",{cacheDisabled:true});
  const connection={offline:false,latency:150,downloadThroughput:125000,uploadThroughput:62500};
  await session.send("Network.emulateNetworkConditions",connection);
  let transferredBytes=0,completedRequests=0;
  session.on("Network.loadingFinished",event=>{transferredBytes+=event.encodedDataLength;completedRequests++;});
  const started=Date.now();await page.goto("/");await expect.poll(async()=>(await state(page)).drawn,{timeout:30000}).toBe("robotic-arm:0");
  const initialReadyMs=Date.now()-started;await ready(page);expect((await state(page)).renderer.kind).toBe("canvas2d");
  await progress(page,.5);const previous=await state(page),switchStarted=Date.now();
  await page.getByRole("button",{name:"Tree",exact:true}).click();
  const preparing=await state(page);expect(preparing.film).toBe("robotic-arm");expect(preparing.drawn).toBe(previous.drawn);expect(preparing.commits).toBe(0);
  await expect.poll(async()=>(await state(page)).drawn,{timeout:30000}).toBe("tree:96");
  const switchReadyMs=Date.now()-switchStarted;expect((await state(page)).commits).toBe(1);expect((await state(page)).progress).toBeCloseTo(.5,2);
  // A fine pointer remains fully capable on all viewport sizes; native touch
  // arbitration and stationary holds are qualified in the modality tests.
  await page.evaluate(()=>{
    const stage=document.querySelector("#stage")!;
    const time=performance.now();
    for(let n=0;n<12;n++){
      const event=new PointerEvent("pointermove",{bubbles:true,pointerType:"mouse",isPrimary:true,clientX:innerWidth*(.2+n*.04),clientY:innerHeight*.4});
      Object.defineProperty(event,"timeStamp",{value:time+n*8});stage.dispatchEvent(event);
    }
  });
  const complete=await state(page);expect(complete.field.active).toBe(true);expect(complete.reduced).toBe(false);expect(complete.renderFailure).toBeNull();expect(complete.cache.peakPending).toBeLessThanOrEqual(2);expect(complete.stalePresentations).toBe(0);
  const output=fileURLToPath(new URL(`../../../test-results/scroll-film-navigator/network/${info.project.name}/`,import.meta.url));
  await mkdir(output,{recursive:true});await writeFile(`${output}report.json`,JSON.stringify({method:"Cold-cache Chromium CDP shaping, 1 Mbps downstream, 150 ms added request latency; dev-module transfer included. Last valid image retained, full material/organic switching enabled. No simulation of data-plan billing or physical radio variability.",connection,viewport:page.viewportSize(),initialReadyMs,switchReadyMs,transferredBytes,completedRequests,state:complete},null,2));
  await session.detach();
});
