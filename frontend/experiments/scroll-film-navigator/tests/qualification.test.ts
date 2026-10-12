import { it,expect } from "vitest";
import { summarize } from "../scripts/qualification.mjs";
const failure=(message:string)=>({suites:[{specs:[{title:"browser case",tests:[{status:"unexpected",results:[{errors:[{message}]}]}]}]}]});
const evidence={classification:"INCONCLUSIVE — LINUX VM WEBKIT",nativeLog:"stabilization/webkit-native.log"};
it("VM policy only waives explicit WebKit navigation crashes, never application assertions or other engines",()=>{
  const crash=failure("page.goto: Page crashed");
  expect(summarize(crash,1,{project:"webkit"}).blocking).toBe(true);
  expect(summarize(crash,1,{project:"webkit",policy:"vm-inconclusive"}).blocking).toBe(true);
  expect(summarize(crash,1,{project:"webkit",policy:"vm-inconclusive",evidence})).toMatchObject({blocking:false,status:"INCONCLUSIVE",passed:0,failed:1});
  expect(summarize(crash,1,{project:"chromium",policy:"vm-inconclusive",evidence}).blocking).toBe(true);
  expect(summarize(failure("Expected pixel ownership, received stale film"),1,{project:"webkit",policy:"vm-inconclusive",evidence}).blocking).toBe(true);
  expect(summarize(undefined,1,{project:"webkit",policy:"vm-inconclusive",evidence}).blocking).toBe(true);
  expect(summarize(undefined,0,{project:"firefox"}).blocking).toBe(true);
});
it("Each project's pass, skip and failure remain independently reportable",()=>{
  const report={suites:[{specs:[{title:"one",tests:[{status:"expected",results:[]},{status:"skipped",results:[],annotations:[{type:"skip"}]}]}]}]};
  expect(summarize(report,0,{project:"firefox"})).toMatchObject({status:"PASS",passed:1,skipped:1,failed:0,blocking:false});
  expect(summarize(failure("page.goto: Page crashed"),1,{project:"webkit"})).toMatchObject({status:"FAIL",blocking:true});
});
it("Native max-failure notice preserves the VM classification and distinguishes an unrun case",()=>{
  const report=failure("page.goto: Page crashed");
  Object.assign(report,{errors:[{message:"\u001b[31mTesting stopped early after 1 maximum allowed failures.\u001b[39m"}]});
  report.suites[0]!.specs.push({title:"unrun",tests:[{status:"skipped",results:[]}]});
  expect(summarize(report,1,{project:"webkit",policy:"vm-inconclusive",evidence})).toMatchObject({status:"INCONCLUSIVE",blocking:false,failed:1,skipped:0,notRun:1});
  expect(summarize(report,1,{project:"webkit"})).toMatchObject({status:"FAIL",blocking:true,notRun:1});
  Object.assign(report,{errors:[{message:"Worker failed for an unrelated reason"}]});
  expect(summarize(report,1,{project:"webkit",policy:"vm-inconclusive",evidence}).blocking).toBe(true);
  expect(summarize({suites:[{specs:[{title:"unrun",tests:[{status:"skipped",results:[]}]}]}]},0,{project:"firefox"})).toMatchObject({status:"PARTIAL",blocking:true,notRun:1});
});
