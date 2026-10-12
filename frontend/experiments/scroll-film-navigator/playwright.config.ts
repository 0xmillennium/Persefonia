import { defineConfig } from "@playwright/test";
import { fileURLToPath } from "node:url";
const launchOptions=process.env.FILM_SOFTWARE_GPU==="1"?{args:["--use-angle=swiftshader"]}:{};
const label=process.env.FILM_REPORT_LABEL??"browser-summary";
if(!/^[a-zA-Z0-9-]+$/.test(label))throw new Error("Invalid film report label");
export default defineConfig({
  testDir:"./tests",testMatch:["acceptance.spec.ts","gpu.spec.ts","composition.spec.ts","visual.spec.ts","resilience.spec.ts","network.spec.ts","finalization.spec.ts","navigator.spec.ts","presentation.spec.ts"],workers:1,retries:0,timeout:90_000,
  outputDir:`../../test-results/scroll-film-navigator/${process.env.FILM_REPORT_LABEL??"browser"}`,
  reporter:[["list"],["json",{outputFile:fileURLToPath(new URL(`../../test-results/scroll-film-navigator/${label}.json`,import.meta.url))}]],
  use:{baseURL:"http://127.0.0.1:4180",viewport:{width:1200,height:800},screenshot:"only-on-failure",trace:"off",video:"off"},
  projects:[
    {name:"webkit",testIgnore:["performance.spec.ts"],use:{browserName:"webkit"}},
    {name:"webkit-touch",testIgnore:["performance.spec.ts"],use:{browserName:"webkit",viewport:{width:390,height:844},deviceScaleFactor:2,isMobile:true,hasTouch:true}},
    {name:"chromium",use:{browserName:"chromium",channel:"chromium",launchOptions}},
    {name:"firefox",testIgnore:["performance.spec.ts"],use:{browserName:"firefox"}},
    {name:"chromium-touch",use:{browserName:"chromium",channel:"chromium",launchOptions,viewport:{width:390,height:844},deviceScaleFactor:2,isMobile:true,hasTouch:true}},
    {name:"chromium-tablet-touch",testIgnore:["performance.spec.ts"],use:{browserName:"chromium",channel:"chromium",launchOptions,viewport:{width:768,height:1024},deviceScaleFactor:2,isMobile:true,hasTouch:true}}
  ],
  webServer:{cwd:fileURLToPath(new URL("../..",import.meta.url)),command:"npm run film:dev",url:"http://127.0.0.1:4180",timeout:30_000,reuseExistingServer:process.env.FILM_REUSE_SERVER==="1"}
});
