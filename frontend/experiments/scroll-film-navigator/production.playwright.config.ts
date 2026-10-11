import { defineConfig } from "@playwright/test";
import { fileURLToPath } from "node:url";
const launchOptions=process.env.FILM_SOFTWARE_GPU==="1"?{args:["--use-angle=swiftshader"]}:{};
export default defineConfig({
  testDir:"./tests",testMatch:"built.spec.ts",workers:1,retries:0,timeout:60_000,
  outputDir:"../../test-results/scroll-film-navigator/built",reporter:[["list"]],
  use:{baseURL:"http://127.0.0.1:4181",viewport:{width:1200,height:800},screenshot:"only-on-failure",trace:"off"},
  projects:[
    {name:"webkit-built",use:{browserName:"webkit"}},
    {name:"webkit-touch-built",use:{browserName:"webkit",viewport:{width:390,height:844},deviceScaleFactor:2,isMobile:true,hasTouch:true}},
    {name:"chromium-built",use:{browserName:"chromium",channel:"chromium",launchOptions}},
    {name:"firefox-built",use:{browserName:"firefox"}},
    {name:"chromium-touch-built",use:{browserName:"chromium",channel:"chromium",launchOptions,viewport:{width:390,height:844},deviceScaleFactor:2,isMobile:true,hasTouch:true}},
    {name:"chromium-tablet-touch-built",use:{browserName:"chromium",channel:"chromium",launchOptions,viewport:{width:768,height:1024},deviceScaleFactor:2,isMobile:true,hasTouch:true}}
  ],
  webServer:{cwd:fileURLToPath(new URL("../..",import.meta.url)),command:"npm run film:build && npx vite preview --config experiments/scroll-film-navigator/vite.config.ts",url:"http://127.0.0.1:4181",timeout:180_000,reuseExistingServer:false}
});
