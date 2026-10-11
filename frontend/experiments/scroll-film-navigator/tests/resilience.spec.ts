import { test,expect } from "@playwright/test";
import { ready,state,progress,isTouch,stroke } from "./support";
test.beforeEach(async({page})=>{page.on("pageerror",error=>{throw error;});await page.goto("/");await ready(page);});

test("Responsive ratios retain usable targets, film position and bounded countdown labels",async({page})=>{
  await progress(page,.35);await page.evaluate(()=>window.__filmExperiment!.freeze(true));
  for(const size of [{width:320,height:740},{width:640,height:900},{width:768,height:1024},{width:1024,height:768},{width:844,height:390},{width:1000,height:650},{width:1200,height:800},{width:1440,height:900}]) {
    await page.setViewportSize(size);await expect.poll(async()=>(await state(page)).drawn).toBe("robotic-arm:67");
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

test("Mobile stationary hold, organic reveal and repeated cycle survive orientation changes",async({page,browserName},info)=>{
  test.skip(!isTouch(info.project.name),"Touch viewport case; desktop repeated holds are tested independently.");
  await progress(page,.35);
  const contact={x:195,y:340};
  const session=browserName==="chromium"?await page.context().newCDPSession(page):null;
  if(session)await session.send("Input.dispatchTouchEvent",{type:"touchStart",touchPoints:[contact]});
  else await page.evaluate(point=>document.querySelector("#stage")!.dispatchEvent(new PointerEvent("pointerdown",{bubbles:true,pointerType:"touch",pointerId:77,isPrimary:true,button:0,clientX:point.x,clientY:point.y})),contact);
  await expect.poll(async()=>(await state(page)).hold.progress,{intervals:[25,50]}).toBeGreaterThan(.35);
  await page.screenshot({path:`test-results/scroll-film-navigator/visual/${info.project.name}/mobile-hold-growth.png`});
  await page.setViewportSize({width:844,height:390});await expect.poll(async()=>(await state(page)).commits,{intervals:[25,50]}).toBe(1);
  expect((await state(page)).film).toBe("tree");expect((await state(page)).drawn).toBe("tree:67");
  await expect.poll(async()=>(await state(page)).commits,{timeout:10000,intervals:[25,50]}).toBe(2);
  if(session){await session.send("Input.dispatchTouchEvent",{type:"touchEnd",touchPoints:[]});await session.detach();}
  else await page.evaluate(()=>window.dispatchEvent(new PointerEvent("pointerup",{pointerType:"touch",pointerId:77})));
  const s=await state(page);expect(s.film).toBe("robotic-arm");expect(s.field.injections).toBe(0);expect(s.hold.pressed).toBe(false);expect(s.progress).toBeCloseTo(.35,2);expect(s.renderer.kind).toBe("canvas2d");
});

test("Optional WebGL failure/loss retains the full CPU interaction, including refraction",async({page})=>{
  await page.goto("/?renderer=webgl");await ready(page);
  const initial=await state(page);
  if(initial.renderer.kind==="webgl2"){
    const capable=await page.evaluate(()=>!!(document.querySelector("#film")as HTMLCanvasElement).getContext("webgl2")?.getExtension("WEBGL_lose_context"));
    test.skip(!capable,"Optional backend does not expose context-loss extension.");
    await page.evaluate(()=>window.__filmExperiment!.contextLoss());await expect.poll(async()=>(await state(page)).renderer.kind).toBe("canvas2d");
  }else expect(initial.renderer.acceleratedFailure).not.toBeNull();
  await progress(page,.5);await stroke(page,{x:120,y:200},{x:300,y:250},12);
  const fallback=await state(page);expect(fallback.renderFailure).toBeNull();expect(fallback.field.active).toBe(true);expect(fallback.gpu.bytes).toBe(0);expect(fallback.drawn).toBe("robotic-arm:96");
  if(initial.renderer.kind==="webgl2"){await page.evaluate(()=>window.__filmExperiment!.contextRestore());await expect.poll(async()=>(await state(page)).renderer.kind).toBe("webgl2");}
});
