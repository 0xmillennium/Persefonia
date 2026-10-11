import { test,expect } from "@playwright/test";
import { state,ready,progress,stroke,isTouch } from "./support";
test.beforeEach(async({page})=>{await page.goto("/");await ready(page);});
test.beforeEach(({page})=>{page.on("pageerror",error=>{throw error;});});
test("FT endpoints, bidirectional scrubbing, freeze, async reversals and cache bounds",async({page})=>{
  for(const p of [.2,.85,.35,1,0,.5])await progress(page,p);
  await expect.poll(async()=>{const s=await state(page);return s.cache.pending===0&&!s.scheduled;}).toBe(true);
  const before=await state(page);
  await page.evaluate(()=>new Promise<void>(resolve=>{let n=0;function f(){if(++n===20)resolve();else requestAnimationFrame(f);}requestAnimationFrame(f);}));
  expect((await state(page)).desired).toBe(before.desired);expect((await state(page)).drawn).toBe(before.drawn);expect((await state(page)).presented).toBe(before.presented);
  await page.evaluate(()=>{for(const p of [.2,.85,.35,.95,.1,.75,.35])window.scrollTo(0,p*(document.documentElement.scrollHeight-innerHeight));});
  await expect.poll(async()=>(await state(page)).drawn).toBe("robotic-arm:67");
  const s=await state(page);expect(s.cache.size).toBeLessThanOrEqual(20);expect(s.cache.peakPending).toBeLessThanOrEqual(2);expect(s.cache.bytes).toBeLessThanOrEqual(20*1600*900*4);expect(s.stalePresentations).toBe(0);
});
test("Accessible film selection preserves progress and excludes controls from holds",async({page})=>{
  await progress(page,.5);await page.getByRole("button",{name:"Tree",exact:true}).click();
  await expect.poll(async()=>(await state(page)).film).toBe("tree");expect((await state(page)).progress).toBeCloseTo(.5,2);expect((await state(page)).drawn).toBe("tree:96");
  await expect(page.getByRole("button",{name:"Tree",exact:true})).toHaveAttribute("aria-pressed","true");
  await page.locator("#progress").focus();await page.keyboard.press("Home");await expect.poll(async()=>(await state(page)).desired).toBe(0);
});
test("Repeated semantic selections preserve material, readiness feedback and the last requested scene",async({page})=>{
  await page.evaluate(()=>window.__filmExperiment!.freeze(true));
  await page.route("**/tree/*.webp",route=>route.abort());
  await page.getByRole("button",{name:"Tree",exact:true}).click();await page.evaluate(()=>window.__filmExperiment!.advance(.15));
  const first=(await state(page)).hold;
  await page.getByRole("button",{name:"Tree",exact:true}).click();expect((await state(page)).hold.expansion).toBe(first.expansion);expect((await state(page)).hold.center).toEqual(first.center);
  await expect(page.locator("#countdown")).toBeHidden();await expect(page.locator("#selection")).toContainText("Preparing Tree");
  await page.getByRole("button",{name:"Robotic arm",exact:true}).click();expect((await state(page)).hold.phase).toBe("CANCELING");
  await page.evaluate(()=>window.__filmExperiment!.advance(.05));const residual=(await state(page)).hold.expansion;
  await page.getByRole("button",{name:"Tree",exact:true}).click();expect((await state(page)).hold.expansion).toBe(residual);
  await page.unroute("**/tree/*.webp");await page.getByRole("button",{name:"Retry frames"}).click();
  await expect.poll(async()=>(await state(page)).next).toBe("tree:0");
  await page.evaluate(()=>window.__filmExperiment!.advance(1));expect((await state(page)).commits).toBe(1);
  expect((await state(page)).film).toBe("tree");expect((await state(page)).progress).toBe(0);
});
test("PH cancellation, residual countdown, retarget, exact single commit and long hold",async({page,browserName},info)=>{
  test.skip(isTouch(info.project.name),"Fine-pointer capture test; touch paths are qualified separately.");
  await page.evaluate(()=>window.__filmExperiment!.freeze(true));
  await page.mouse.move(350,300);await page.mouse.down();
  await page.evaluate(()=>window.__filmExperiment!.advance(2.25));expect((await state(page)).hold.progress).toBeGreaterThan(.72);
  await page.mouse.up();const residual=(await state(page)).hold.progress;const releasedAt=Date.now();
  await expect.poll(async()=>(await state(page)).hold.phase).toBe("CANCELING");
  await page.mouse.move(700,350);await page.mouse.down();const resumed=await state(page);
  expect(resumed.hold.progress).toBeGreaterThan(0);expect(resumed.hold.progress).toBeGreaterThanOrEqual(residual-(Date.now()-releasedAt)/1000*.72-.03);expect(resumed.hold.countdown).toBe(resumed.hold.progress<1/3?3:resumed.hold.progress<2/3?2:1);expect(resumed.hold.target.x).toBe(700);
  await page.mouse.move(-80,300);await page.evaluate(()=>window.__filmExperiment!.advance(.75));
  if(browserName==="chromium")await expect.poll(async()=>(await state(page)).hold.center.x,{intervals:[25,50]}).toBeLessThan(0);
  else await expect.poll(async()=>(await state(page)).hold.center.x,{intervals:[25,50]}).toBeLessThan(2); // Firefox/WebKit automation clamps the delivered coordinate to the viewport edge.
  expect((await state(page)).hold.pressed).toBe(true);
  await page.mouse.move(500,350);await page.evaluate(()=>window.__filmExperiment!.freeze(false));await expect.poll(async()=>(await state(page)).commits,{intervals:[25,50]}).toBe(1);expect((await state(page)).film).toBe("tree");
  await page.evaluate(async()=>{window.__filmExperiment!.freeze(true);await window.__filmExperiment!.advance(10);await window.__filmExperiment!.advance(5);});
  expect((await state(page)).commits).toBe(1);expect((await state(page)).hold.phase).toBe("SUCCESS_WAIT_RELEASE");
  await page.mouse.up();expect((await state(page)).hold.phase).toBe("IDLE");
  await page.evaluate(()=>window.__filmExperiment!.freeze(false));await page.mouse.down();
  await expect.poll(async()=>(await state(page)).commits,{timeout:6000}).toBe(2);await page.mouse.up();
});
test("LN core, slow/fast persistent material, localized deformation and complete dissipation",async({page},info)=>{
  test.skip(isTouch(info.project.name),"Mouse field metrics are covered on desktop; touch must not synthesize trails.");
  await progress(page,.5);await stroke(page,{x:200,y:350},{x:900,y:350},30,10);
  const s=await state(page);expect((await page.evaluate(()=>window.__filmExperiment!.fieldMetrics())).mass).toBeGreaterThan(0);expect(s.core.x).toBe("900px");expect(s.core.y).toBe("350px");expect(s.hold.phase).toBe("IDLE");expect(s.commits).toBe(0);
  const geometry=await page.evaluate(()=>window.__filmExperiment!.fieldMetrics());expect(geometry.area).toBeGreaterThan(0);
  await page.mouse.move(1000,500);const residual=await state(page);expect(residual.field.active).toBe(true);
  await expect.poll(async()=>(await state(page)).field.active,{timeout:7000}).toBe(false);expect((await state(page)).field.energy).toBe(0);expect((await state(page)).core.visible).toBe(true);
});
test("Touch starts immediately, threshold cancels, re-press reuses residue; native pan survives",async({page,browserName},info)=>{
  test.skip(!isTouch(info.project.name),"Requires touch-enabled context.");
  // Native touch injection in Chromium includes gesture arbitration, pointercancel and native scrolling.
  if(browserName==="chromium") {
    const session=await page.context().newCDPSession(page);
    await session.send("Input.dispatchTouchEvent",{type:"touchStart",touchPoints:[{x:195,y:340}]});
    await expect.poll(async()=>(await state(page)).hold.progress).toBeGreaterThan(.25);
    await session.send("Input.dispatchTouchEvent",{type:"touchEnd",touchPoints:[]});const residual=(await state(page)).hold.progress,releasedAt=Date.now();
    await session.send("Input.dispatchTouchEvent",{type:"touchStart",touchPoints:[{x:120,y:360}]});
    const resumed=await state(page);expect(resumed.hold.progress).toBeGreaterThan(0);expect(resumed.hold.progress).toBeGreaterThanOrEqual(residual-(Date.now()-releasedAt)/1000*.72-.03);expect(resumed.hold.target.x).toBe(120);
    for(let i=1;i<=6;i++)await session.send("Input.dispatchTouchEvent",{type:"touchMove",touchPoints:[{x:120,y:360-i*30}]});
    await session.send("Input.dispatchTouchEvent",{type:"touchEnd",touchPoints:[]});
    await expect.poll(async()=>page.evaluate(()=>scrollY)).toBeGreaterThan(0);expect((await state(page)).field.injections).toBe(0);expect((await state(page)).hold.pressed).toBe(false);await session.detach();
  } else {
    // WebKit's public API exposes tap, not a held native contact. This subset verifies modality;
    // real stationary-contact/pan qualification remains a physical-device gap in the README.
    await page.touchscreen.tap(195,340);expect((await state(page)).field.injections).toBe(0);expect((await state(page)).hold.pressed).toBe(false);
  }
});
test("LC resize, suspension, history and disposal",async({page},info)=>{
  await progress(page,.35);await page.getByRole("button",{name:"Tree",exact:true}).click();await expect.poll(async()=>(await state(page)).film).toBe("tree");
  if(!isTouch(info.project.name))await stroke(page,{x:200,y:250},{x:700,y:400},12);
  await page.setViewportSize({width:640,height:900});await expect.poll(async()=>(await state(page)).drawn).toBe("tree:67");expect((await state(page)).film).toBe("tree");
  await page.evaluate(()=>window.__filmExperiment!.suspend());expect((await state(page)).scheduled).toBe(false);
  await page.evaluate(()=>window.__filmExperiment!.resume());await expect.poll(async()=>(await state(page)).drawn).toBe("tree:67");
  // Native reload: Playwright 1.57 Firefox page.reload() produced navigation type
  // "navigate" and discarded entry state; location.reload() exercises browser reload.
  await Promise.all([page.waitForNavigation(),page.evaluate(()=>location.reload())]);await expect.poll(async()=>(await state(page)).film).toBe("tree");await expect.poll(async()=>(await state(page)).drawn).toBe("tree:67");expect((await state(page)).commits).toBe(0);
  await page.evaluate(()=>window.__filmExperiment!.dispose());const s=await state(page);expect(s.disposed).toBe(true);expect(s.cache.size).toBe(0);expect(s.scheduled).toBe(false);expect(s.gpu.bytes).toBe(0);
});
test("FT decode failure retains last valid frame, reports error and retries",async({page})=>{
  await page.route("**/frame_00192.webp*",route=>route.fulfill({status:200,contentType:"image/webp",body:"corrupt"}));
  await page.evaluate(()=>scrollTo(0,document.documentElement.scrollHeight-innerHeight));
  await expect(page.getByRole("button",{name:"Retry frames"})).toBeVisible();expect((await state(page)).cache.failures).toBeGreaterThan(0);expect((await state(page)).drawn).not.toBe("robotic-arm:191");
  const refreshed=page.waitForRequest(request=>request.url().includes("frame_00192.webp?retry=1"));
  await page.unroute("**/frame_00192.webp*");await page.getByRole("button",{name:"Retry frames"}).click();await expect.poll(async()=>(await state(page)).drawn).toBe("robotic-arm:191");await refreshed;
});
test("LC reduced motion preserves scroll and film access without fluid",async({page})=>{
  await page.emulateMedia({reducedMotion:"reduce"});await progress(page,.8);await page.mouse.move(500,300);expect((await state(page)).field.active).toBe(false);
  await page.getByRole("button",{name:"Tree",exact:true}).click();await expect.poll(async()=>(await state(page)).drawn).toBe("tree:153");expect((await state(page)).reduced).toBe(true);expect((await state(page)).progress).toBeCloseTo(.8,2);
});

 test("LC suspension ages persistent pigment by real elapsed time",async({page},info)=>{
  test.skip(isTouch(info.project.name),"Fine-pointer persistence study");
  await page.evaluate(()=>{window.__filmExperiment!.clearFluid();window.__filmExperiment!.freeze(true);});
  await stroke(page,{x:200,y:300},{x:850,y:400},12);
  await page.evaluate(()=>window.__filmExperiment!.advance(.02));
  const before=await page.evaluate(()=>window.__filmExperiment!.fieldMetrics());expect(before.mass).toBeGreaterThan(0);
  await page.evaluate(()=>window.__filmExperiment!.suspend());await page.waitForTimeout(150);await page.evaluate(()=>window.__filmExperiment!.resume());
  const short=await page.evaluate(()=>window.__filmExperiment!.fieldMetrics());expect(short.mass).toBeGreaterThan(0);expect(short.mass).toBeLessThan(before.mass);
  await page.evaluate(()=>window.__filmExperiment!.suspend());await page.waitForTimeout(4400);await page.evaluate(()=>window.__filmExperiment!.resume());
  expect((await state(page)).field.active).toBe(false);expect((await page.evaluate(()=>window.__filmExperiment!.fieldMetrics())).mass).toBe(0);
 });
