import { test,expect } from "@playwright/test";
import { ready,state,progress,isTouch,stroke } from "./support";
test.beforeEach(async({page})=>{page.on("pageerror",error=>{throw error;});await page.goto("/");await ready(page);});

test("Responsive ratios retain usable targets, film position and bounded countdown labels",async({page})=>{
  await progress(page,.35);await page.evaluate(()=>window.__filmExperiment!.freeze(true));
  for(const size of [{width:320,height:740},{width:640,height:900},{width:768,height:1024},{width:1024,height:768},{width:844,height:390},{width:1000,height:650},{width:1200,height:800},{width:1440,height:900}]) {
    await page.setViewportSize(size);
    // A pre-resize frame key can already match. Wait for the native resize handler
    // to adopt this geometry and cancel the prior gesture before pressing again.
    await expect.poll(async()=>{const s=await state(page);return {width:s.viewport.width,height:s.viewport.height,phase:s.hold.phase};}).toEqual({...size,phase:"IDLE"});
    await expect.poll(async()=>(await state(page)).drawn).toBe("robotic-arm:67");
    const geometry=await page.evaluate(()=>({
      coarse:matchMedia("(any-pointer: coarse)").matches,
      controls:[...document.querySelectorAll<HTMLElement>("button:not([hidden]),input")].map(el=>{const box=el.getBoundingClientRect();return {tag:el.tagName,left:box.left,right:box.right,top:box.top,bottom:box.bottom,height:box.height};}),
      header:document.querySelector("header")!.getBoundingClientRect().bottom,footer:document.querySelector("footer")!.getBoundingClientRect().top
    }));
    expect(geometry.header).toBeLessThan(geometry.footer);
    for(const box of geometry.controls){expect(box.left).toBeGreaterThanOrEqual(0);expect(box.right).toBeLessThanOrEqual(size.width);expect(box.top).toBeGreaterThanOrEqual(0);expect(box.bottom).toBeLessThanOrEqual(size.height);if(geometry.coarse||box.tag==="INPUT")expect(box.height).toBeGreaterThanOrEqual(44);}
    await page.mouse.move(1,100);await page.mouse.down();await expect(page.locator("#countdown")).toBeVisible();
    const label=await page.locator("#countdown").boundingBox();expect(label).not.toBeNull();expect(label!.x).toBeGreaterThanOrEqual(0);expect(label!.x+label!.width).toBeLessThanOrEqual(size.width);expect(label!.y).toBeGreaterThanOrEqual(geometry.header);expect(label!.y+label!.height).toBeLessThanOrEqual(geometry.footer);
    await page.mouse.up();expect((await state(page)).hold.pressed).toBe(false);
  }
});

test("Incoming failure cannot reveal an unprepared film; retry prepares the latest matching pair",async({page})=>{
  await page.route("**/tree/*.webp",route=>route.fulfill({status:404,body:"deliberately missing"}));
  await page.getByRole("button",{name:"Tree",exact:true}).click();await expect(page.getByRole("button",{name:"Retry frames"})).toBeVisible();
  await progress(page,.5);const waiting=await state(page);expect(waiting.film).toBe("robotic-arm");expect(waiting.commits).toBe(0);expect(waiting.reveal).toBe(false);expect(waiting.next).toBeNull();expect(waiting.drawn).toBe("robotic-arm:96");
  await page.unroute("**/tree/*.webp");await page.getByRole("button",{name:"Retry frames"}).click();await expect.poll(async()=>(await state(page)).drawn).toBe("tree:96");expect((await state(page)).progress).toBeCloseTo(.5,2);expect((await state(page)).commits).toBe(1);
});

test("Native keyboard/wheel and pointer cancel, blur, lost capture remain responsive",async({page},info)=>{
  test.skip(isTouch(info.project.name),"Fine-pointer/native wheel case; mobile native pan is tested separately.");
  await page.keyboard.press("End");await expect.poll(async()=>(await state(page)).drawn).toBe("robotic-arm:191");
  await page.mouse.move(300,300);await page.mouse.wheel(0,-600);await expect.poll(async()=>(await state(page)).desired).toBeLessThan(191);
  for(const interruption of ["cancel","blur","capture"]){
    await page.evaluate(()=>{const stage=document.querySelector<HTMLElement>("#stage")!;stage.addEventListener("pointerdown",event=>{stage.dataset.contact=String((event as PointerEvent).pointerId);},{once:true});});
    await page.mouse.down();await expect.poll(async()=>(await state(page)).hold.progress,{intervals:[25,50]}).toBeGreaterThan(.12);
    await page.evaluate(kind=>{const stage=document.querySelector<HTMLElement>("#stage")!,id=Number(stage.dataset.contact);if(kind==="blur")window.dispatchEvent(new Event("blur"));else if(kind==="capture")stage.releasePointerCapture(id);else stage.dispatchEvent(new PointerEvent("pointercancel",{bubbles:true,pointerId:id}));},interruption);
    if(interruption==="capture")await page.mouse.move(305,305);
    await expect.poll(async()=>(await state(page)).hold.pressed).toBe(false);await page.mouse.up();await expect.poll(async()=>(await state(page)).hold.phase).toBe("IDLE");expect((await state(page)).commits).toBe(0);
  }
});

test("Touch orientation cancels transient growth; a distinct contact earns one transition",async({page,browserName},info)=>{
  test.skip(!isTouch(info.project.name),"Touch-only orientation case");
  test.skip(browserName!=="chromium","Native held touch injection is available through Chromium CDP; physical Safari remains unverified");
  await progress(page,.35);await page.evaluate(()=>window.__filmExperiment!.freeze(true));
  const session=await page.context().newCDPSession(page);
  const begin=async(x:number,y:number)=>session.send("Input.dispatchTouchEvent",{type:"touchStart",touchPoints:[{x,y}]});
  const end=async()=>session.send("Input.dispatchTouchEvent",{type:"touchEnd",touchPoints:[]});
  await begin(195,340);await page.evaluate(()=>window.__filmExperiment!.advance(1.2));
  expect((await state(page)).hold.progress).toBeGreaterThan(.35);
  await page.setViewportSize({width:844,height:390});expect((await state(page)).hold.phase).toBe("IDLE");expect((await state(page)).commits).toBe(0);await end();
  await begin(420,150);await expect.poll(async()=>(await state(page)).next).toBe("tree:67");
  await page.evaluate(()=>window.__filmExperiment!.advance(3.8));expect((await state(page)).commits).toBe(1);
  await page.evaluate(async()=>{await window.__filmExperiment!.advance(10);await window.__filmExperiment!.advance(5);});expect((await state(page)).commits).toBe(1);await end();
  await begin(420,150);await expect.poll(async()=>(await state(page)).next).toBe("robotic-arm:67");await page.evaluate(()=>window.__filmExperiment!.advance(3.8));expect((await state(page)).commits).toBe(2);await end();
  const s=await state(page);expect(s.film).toBe("robotic-arm");expect(s.field.injections).toBe(0);expect(s.hold.pressed).toBe(false);expect(s.progress).toBeCloseTo(.35,2);await session.detach();
});

test("GPU failure/loss selects explicit basic film mode and restoration reconstructs GPU ownership",async({page})=>{
  await page.goto("/?renderer=webgl");await ready(page);
  const initial=await state(page);
  if(initial.renderer.kind==="webgl2"){
    const capable=await page.evaluate(()=>!!(document.querySelector("#film")as HTMLCanvasElement).getContext("webgl2")?.getExtension("WEBGL_lose_context"));
    test.skip(!capable,"Optional backend does not expose context-loss extension.");
    await page.evaluate(()=>window.__filmExperiment!.contextLoss());await expect.poll(async()=>(await state(page)).renderer.kind).toBe("canvas2d");
  }else expect(initial.renderer.acceleratedFailure).not.toBeNull();
  await progress(page,.5);await stroke(page,{x:120,y:200},{x:300,y:250},12);
  const fallback=await state(page);expect(fallback.renderFailure).toBeNull();expect(fallback.field.active).toBe(false);await expect(page.locator("#quality")).toBeVisible();expect(fallback.gpu.bytes).toBe(0);expect(fallback.drawn).toBe("robotic-arm:96");
  if(initial.renderer.kind==="webgl2"){await page.evaluate(()=>window.__filmExperiment!.contextRestore());await expect.poll(async()=>(await state(page)).renderer.kind).toBe("webgl2");}
});

test("LC resize during an earned reveal explicitly cancels to the outgoing scene",async({page})=>{
  await progress(page,.35);await page.evaluate(()=>window.__filmExperiment!.freeze(true));
  await page.getByRole("button",{name:"Tree",exact:true}).click();await expect.poll(async()=>(await state(page)).next).toBe("tree:67");
  for(let n=0;n<40&&(await state(page)).hold.phase!=="REVEALING";n++)await page.evaluate(()=>window.__filmExperiment!.advance(.025));expect((await state(page)).hold.phase).toBe("REVEALING");
  await page.setViewportSize({width:900,height:700});await expect.poll(async()=>(await state(page)).hold.phase).toBe("IDLE");await expect.poll(async()=>(await state(page)).drawn).toBe("robotic-arm:67");
  expect((await state(page)).hold.phase).toBe("IDLE");expect((await state(page)).commits).toBe(0);expect((await state(page)).progress).toBeCloseTo(.35,2);
});
test("LC native DPR and page-scale changes preserve mapping and reset transient growth",async({page,browserName},info)=>{
  test.skip(browserName!=="chromium","Native DPR override uses Chromium CDP; fixed DPR contexts cover the other engines");
  await progress(page,.5);await page.evaluate(()=>window.__filmExperiment!.freeze(true));
  const session=await page.context().newCDPSession(page),size=page.viewportSize()!;
  await page.getByRole("button",{name:"Tree",exact:true}).click();
  await session.send("Emulation.setDeviceMetricsOverride",{width:size.width,height:size.height,deviceScaleFactor:3,mobile:isTouch(info.project.name)});
  await expect.poll(async()=>(await state(page)).viewport.dpr).toBe(3);await expect.poll(async()=>(await state(page)).hold.phase).toBe("IDLE");
  await session.send("Emulation.setPageScaleFactor",{pageScaleFactor:1.5});await expect.poll(async()=>(await state(page)).drawn).toBe("robotic-arm:96");expect((await state(page)).progress).toBeCloseTo(.5,2);
  await session.send("Emulation.setPageScaleFactor",{pageScaleFactor:1});await session.send("Emulation.clearDeviceMetricsOverride");await session.detach();
});

test("LC switching from a fine pointer to stationary touch removes the hover core",async({page,browserName},info)=>{
  test.skip(browserName!=="chromium"||!isTouch(info.project.name),"Native modality transition requires a touch-enabled CDP context");
  await page.mouse.move(150,250);await expect(page.locator("#core")).toBeVisible();const injections=(await state(page)).field.injections;
  const session=await page.context().newCDPSession(page);await session.send("Input.dispatchTouchEvent",{type:"touchStart",touchPoints:[{x:195,y:340}]});
  await expect(page.locator("#core")).toBeHidden();expect((await state(page)).field.injections).toBe(injections);await session.send("Input.dispatchTouchEvent",{type:"touchEnd",touchPoints:[]});
  await page.mouse.move(155,255);await expect(page.locator("#core")).toBeVisible();await page.setViewportSize({width:844,height:390});await expect(page.locator("#core")).toBeHidden();await session.detach();
});
