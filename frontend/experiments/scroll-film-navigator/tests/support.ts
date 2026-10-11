import { expect,type Page } from "@playwright/test";
export interface State {
  viewport:{width:number;height:number;dpr:number};
  film:string; progress:number; desired:number; presented:string|null; drawn:string|null; commits:number; next:string|null;
  hold:{phase:string;progress:number;expansion:number;center:{x:number;y:number};target:{x:number;y:number};countdown:number|null;pressed:boolean;cycles:number;coverage:number};
  field:{active:boolean;energy:number;peak:number;injections:number};
  cache:{size:number;limit:number;pending:number;queued:number;bytes:number;peakPending:number;failures:number;staleCompletions:number;evictions:number;error:string|null};
  core:{x:string;y:string;visible:boolean}; reduced:boolean; renderFailure:string|null; reveal:boolean;suspended:boolean;disposed:boolean;scheduled:boolean;stalePresentations:number;
  metrics:Record<string,unknown>;gpu:{info:unknown;bytes:number;uploads:number;draws:number;samples:number[]};
  renderer:{kind:string|null;bytes:number;acceleratedFailure:string|null;info:unknown};
}
declare global {interface Window {__filmExperiment?:{state():State;freeze(frozen:boolean):void;advance(seconds:number):Promise<void>;resetMetrics():void;clearFluid():void;fieldMetrics():{mass:number;area:number;varianceX:number;varianceY:number;components:number};suspend():void;resume():void;dispose():void;contextLoss():void;contextRestore():void}}}
export const state=(page:Page)=>page.evaluate(()=>window.__filmExperiment!.state());
export async function ready(page:Page):Promise<void> {await expect.poll(async()=>(await state(page)).drawn).toBe("robotic-arm:0");}
export async function progress(page:Page,p:number):Promise<void> {
  await page.evaluate(value=>window.scrollTo({top:value*(document.documentElement.scrollHeight-innerHeight),behavior:"instant"}),p);
  const index=Math.round(p*191);
  await expect.poll(async()=>{const s=await state(page);return s.drawn;}).toBe(`${(await state(page)).film}:${index}`);
}
export async function stroke(page:Page,start:{x:number;y:number},end:{x:number;y:number},steps=24,interval=8):Promise<void> {
  await page.mouse.move(start.x,start.y);
  // Frame-clock spacing is part of a controlled input workload, never a readiness substitute.
  for(let n=1;n<=steps;n++) {await page.mouse.move(start.x+(end.x-start.x)*n/steps,start.y+(end.y-start.y)*n/steps);await page.evaluate(ms=>new Promise(resolve=>setTimeout(resolve,ms)),interval);}
}
export const isTouch=(name:string)=>name.includes("touch");
