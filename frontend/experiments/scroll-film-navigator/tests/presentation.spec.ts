import { test,expect } from "@playwright/test";
import { ready,state,isTouch } from "./support";
import { pixels } from "./pixels";
import { mkdir,writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

// Readable frame identities in compositor pixels, independent of delivery/decode cost.
const color=(index:number)=>[40+index,60,180];
const image=(index:number)=>`<svg xmlns="http://www.w3.org/2000/svg" width="960" height="540"><rect width="960" height="540" fill="rgb(${color(index).join(',')})"/></svg>`;
async function displayed(page:import("@playwright/test").Page,index:number):Promise<void>{
  const p=pixels(await page.screenshot({clip:{x:40,y:240,width:16,height:16}}));
  for(let channel=0;channel<3;channel++)expect(Math.abs(p.data[channel]!-color(index)[channel]!)).toBeLessThanOrEqual(2);
}
test("Native scroll presents ready frames forward/reverse and stops without a playback clock",async({page},info)=>{
  test.skip(isTouch(info.project.name),"Deterministic compositor fixture; native touch remains separately covered");
  await page.route("**/*.webp*",route=>{const index=Number(/frame_(\d+)/.exec(route.request().url())![1])-1;return route.fulfill({contentType:"image/svg+xml",body:image(index)});});
  await page.goto("/");await ready(page);const samples=[];
  for(const index of [1,2,3,4,5,6,5,4,3,2,1,0]){
    const start=Date.now();await page.evaluate(index=>scrollTo(0,index/191*(document.documentElement.scrollHeight-innerHeight)),index);
    await expect.poll(async()=>(await state(page)).drawn).toBe(`robotic-arm:${index}`);await displayed(page,index);
    samples.push({target:index,displayed:index,readinessMs:Date.now()-start});
  }
  // Several scroll updates in one task: the latest native position owns the next draw.
  await page.evaluate(()=>{for(const index of [20,40,10])scrollTo(0,index/191*(document.documentElement.scrollHeight-innerHeight));});
  await expect.poll(async()=>(await state(page)).drawn).toBe("robotic-arm:10");await displayed(page,10);
  await expect.poll(async()=>{const s=await state(page);return !s.scheduled&&s.cache.pending===0;}).toBe(true);
  const stop=await state(page);await page.waitForTimeout(250);const after=await state(page);
  expect(after.desired).toBe(stop.desired);expect(after.drawn).toBe(stop.drawn);expect(after.scheduled).toBe(false);expect(after.stalePresentations).toBe(0);await displayed(page,10);
  const directory=fileURLToPath(new URL("../../../test-results/scroll-film-navigator/stabilization/",import.meta.url));await mkdir(directory,{recursive:true});await writeFile(`${directory}presentation-${info.project.name}.json`,JSON.stringify({method:"Native scroll geometry; small ready SVG frame fixtures served as image responses; actual screenshot color identities. Readiness includes polling/screenshot IPC, not decoder-only latency or physical scanout.",samples,stop,after},null,2));
});

test("Delayed target retains the valid image and redraws on decode without further scroll",async({page},info)=>{
  test.skip(isTouch(info.project.name),"Desktop compositor readiness fixture");
  let release!:()=>void;const delayed=new Promise<void>(resolve=>{release=resolve;});let requested=false;
  await page.route("**/*.webp*",async route=>{const index=Number(/frame_(\d+)/.exec(route.request().url())![1])-1;if(index===96){requested=true;await delayed;}await route.fulfill({contentType:"image/svg+xml",body:image(index)});});
  await page.goto("/");await ready(page);
  await page.evaluate(()=>scrollTo(0,.5*(document.documentElement.scrollHeight-innerHeight)));
  await expect.poll(()=>requested).toBe(true);await expect.poll(async()=>(await state(page)).desired).toBe(96);
  expect((await state(page)).drawn).toBe("robotic-arm:0");await displayed(page,0);
  release();await expect.poll(async()=>(await state(page)).drawn).toBe("robotic-arm:96");await displayed(page,96);expect((await state(page)).stalePresentations).toBe(0);
});
