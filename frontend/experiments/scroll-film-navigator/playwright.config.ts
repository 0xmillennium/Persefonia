import { defineConfig } from "@playwright/test";
import { fileURLToPath } from "node:url";
export default defineConfig({
  testDir:"./tests",testMatch:["acceptance.spec.ts","visual.spec.ts","performance.spec.ts","resilience.spec.ts","network.spec.ts"],workers:1,retries:0,timeout:90_000,
  outputDir:"../../test-results/scroll-film-navigator/browser",
  reporter:[["list"],["json",{outputFile:fileURLToPath(new URL("../../test-results/scroll-film-navigator/browser-summary.json",import.meta.url))}]],
  use:{baseURL:"http://127.0.0.1:4180",viewport:{width:1200,height:800},screenshot:"only-on-failure",trace:"off",video:"off"},
  projects:[
    {name:"webkit",testIgnore:["performance.spec.ts"],use:{browserName:"webkit"}},
    {name:"webkit-touch",testIgnore:["performance.spec.ts"],use:{browserName:"webkit",viewport:{width:390,height:844},deviceScaleFactor:2,isMobile:true,hasTouch:true}},
    {name:"chromium",use:{browserName:"chromium",channel:"chromium",launchOptions:{args:["--disable-gpu","--disable-software-rasterizer"]}}},
    {name:"firefox",testIgnore:["performance.spec.ts"],use:{browserName:"firefox"}},
    {name:"chromium-touch",use:{browserName:"chromium",channel:"chromium",launchOptions:{args:["--disable-gpu","--disable-software-rasterizer"]},viewport:{width:390,height:844},deviceScaleFactor:2,isMobile:true,hasTouch:true}},
    {name:"chromium-tablet-touch",testIgnore:["performance.spec.ts"],use:{browserName:"chromium",channel:"chromium",launchOptions:{args:["--disable-gpu","--disable-software-rasterizer"]},viewport:{width:768,height:1024},deviceScaleFactor:2,isMobile:true,hasTouch:true}}
  ],
  webServer:{cwd:fileURLToPath(new URL("../..",import.meta.url)),command:"npm run film:dev",url:"http://127.0.0.1:4180",timeout:180_000,reuseExistingServer:false}
});
