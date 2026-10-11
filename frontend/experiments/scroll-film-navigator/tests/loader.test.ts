import { afterEach,beforeEach,expect,it,vi } from "vitest";
import { loadBitmap } from "../src/loader";

class Source {
  onload:(()=>void)|null=null;
  onerror:(()=>void)|null=null;
  src="";
  decoding="";
  removeAttribute(name:string):void {if(name==="src")this.src="";}
}
let images:Source[]=[];
beforeEach(()=>{
  images=[];
  vi.stubGlobal("Image",class extends Source {constructor(){super();images.push(this);}});
});
afterEach(()=>vi.unstubAllGlobals());

it("Does not start a source load after prior cancellation",async()=>{
  const controller=new AbortController();controller.abort();
  await expect(loadBitmap("/frame.webp",controller.signal)).rejects.toMatchObject({name:"AbortError"});expect(images).toHaveLength(0);
});
it("Cancels the native source transfer and removes handlers",async()=>{
  const controller=new AbortController(),loaded=loadBitmap("/frame.webp",controller.signal);
  const rejection=expect(loaded).rejects.toMatchObject({name:"AbortError"});controller.abort();await rejection;
  expect(images[0]!.src).toBe("");expect(images[0]!.onload).toBeNull();expect(images[0]!.onerror).toBeNull();
});
it("Uses the loaded image rather than a teardown-sensitive blob URL",async()=>{
  const bitmap={close:vi.fn()},decode=vi.fn().mockResolvedValue(bitmap);vi.stubGlobal("createImageBitmap",decode);
  const loaded=loadBitmap("/frame.webp",new AbortController().signal),image=images[0]!;image.onload!();
  await expect(loaded).resolves.toBe(bitmap);expect(decode).toHaveBeenCalledWith(image);expect(image.src).toBe("");
});
it("Keeps an aborted decode pending until its bitmap can be closed by the cache",async()=>{
  let finish!:(bitmap:unknown)=>void;
  vi.stubGlobal("createImageBitmap",()=>new Promise(resolve=>{finish=resolve;}));
  const controller=new AbortController(),loaded=loadBitmap("/frame.webp",controller.signal),image=images[0]!;
  image.onload!();await Promise.resolve();controller.abort();expect(image.src).toBe("/frame.webp");
  const bitmap={close:vi.fn()};finish(bitmap);await expect(loaded).resolves.toBe(bitmap);expect(image.src).toBe("");
});
it("Rejects invalid images and synchronous bitmap allocation failures with cleanup",async()=>{
  const failed=loadBitmap("/broken.webp",new AbortController().signal);images[0]!.onerror!();await expect(failed).rejects.toThrow("Image unavailable or invalid");expect(images[0]!.src).toBe("");
  vi.stubGlobal("createImageBitmap",()=>{throw new Error("Allocation failed");});
  const failedDecode=loadBitmap("/frame.webp",new AbortController().signal);images[1]!.onload!();await expect(failedDecode).rejects.toThrow("Allocation failed");expect(images[1]!.src).toBe("");
});
