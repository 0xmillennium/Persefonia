import { test,expect } from "@playwright/test";
import { state,ready,isTouch } from "./support";
import { pixels } from "./pixels";
const sample=(png:Buffer,x:number,y:number)=>{const p=pixels(png),i=(Math.floor(y)*p.width+Math.floor(x))*p.channels;return Array.from(p.data.subarray(i,i+3));};
test("PH pixel ownership: current-only tint, full coverage, center-origin outward aperture and one commit",async({page},info)=>{
  test.skip(isTouch(info.project.name),"Desktop pixel fixture; separate touch interaction tests use canonical media.");
  // Small distinguishable films are isolated compositor fixtures, never delivery replacements.
  await page.route("**/*.webp*",async route=>{const blue=route.request().url().includes('/tree/');await route.fulfill({contentType:"image/svg+xml",body:`<svg xmlns="http://www.w3.org/2000/svg" width="960" height="540"><rect width="960" height="540" fill="${blue?'#1638df':'#df3016'}"/><text x="20" y="60" font-size="40" fill="white">${blue?'B':'A'}</text></svg>`});});
  await page.goto("/");await ready(page);expect((await state(page)).renderer.kind).toBe("webgl2");await page.evaluate(()=>window.__filmExperiment!.freeze(true));
  const advance=(seconds:number)=>page.evaluate(t=>window.__filmExperiment!.advance(t),seconds);
  await page.mouse.move(400,320);await page.mouse.down();await advance(1.1);
  const growth=await page.screenshot();const inside=sample(growth,450,360),outside=sample(growth,1100,550);
  expect(inside[0]).toBeGreaterThan(inside[2]!);expect(outside[0]).toBeGreaterThan(outside[2]!);expect(inside[1]).toBeGreaterThan(outside[1]!);expect((await state(page)).film).toBe('robotic-arm');
  // Hold readiness closed to capture completely tinted A before revealing B.
  await page.route("**/tree/*.webp*",route=>route.abort());
  // Already loaded matching B is intentionally displaced by native scroll to a new index.
  await page.evaluate(()=>scrollTo(0,(document.documentElement.scrollHeight-innerHeight)*.5));
  await advance(2.5);expect((await state(page)).hold.phase).toBe("COVERED_WAIT_READY");
  const covered=await page.screenshot();for(const [x,y] of [[10,180],[1180,180],[20,580],[1180,580]]){const c=sample(covered,x!,y!);expect(c[0]).toBeGreaterThan(c[2]!);expect(c[1]).toBeGreaterThan(60);}
  await page.mouse.up();await page.unroute("**/tree/*.webp*");await page.getByRole("button",{name:"Retry frames"}).click();
  await expect.poll(async()=>{await advance(.016);return (await state(page)).hold.phase;}).toBe("REVEALING");
  await advance(.03);const first=await page.screenshot(),center=sample(first,450,360),edge=sample(first,1150,550);
  expect(center[2]).toBeGreaterThan(center[0]!);expect(edge[0]).toBeGreaterThan(edge[2]!);
  const fixed=(await state(page)).hold.center;await page.mouse.move(950,400);await advance(.05);expect((await state(page)).hold.center).toEqual(fixed);
  await advance(.05);const middle=pixels(await page.screenshot());let rimPixels=0;for(let x=500;x<1150;x++){const i=(370*middle.width+x)*middle.channels;if(Math.min(middle.data[i]!,middle.data[i+1]!,middle.data[i+2]!)>150)rimPixels++;}expect(rimPixels).toBeGreaterThan(0);expect(rimPixels).toBeLessThan(80);
  await page.mouse.up();await advance(.4);expect((await state(page)).film).toBe("tree");expect((await state(page)).commits).toBe(1);
  const final=sample(await page.screenshot(),1150,550);expect(final[2]).toBeGreaterThan(final[0]!);
});
