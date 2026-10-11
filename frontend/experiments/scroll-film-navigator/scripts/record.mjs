import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
const cwd=fileURLToPath(new URL('../../../',import.meta.url));
const output=fileURLToPath(new URL('../../../test-results/scroll-film-navigator/recordings/',import.meta.url));
await mkdir(output,{recursive:true});
const server=spawn(process.execPath,['node_modules/vite/bin/vite.js','--config','experiments/scroll-film-navigator/vite.config.ts','--port','4182'],{cwd,stdio:'pipe'});
server.stderr.on('data',data=>process.stderr.write(data));
const url='http://127.0.0.1:4182';
let browser;
try {
  const start=Date.now();
  while(true) {try{if((await fetch(url)).ok)break;}catch{}if(Date.now()-start>15000)throw new Error('Recording server did not start');await new Promise(r=>setTimeout(r,100));}
  browser=await chromium.launch({channel:'chromium',args:['--disable-gpu','--disable-software-rasterizer']});
  const context=await browser.newContext({viewport:{width:1200,height:800},recordVideo:{dir:output,size:{width:1200,height:800}}});
  const page=await context.newPage();await page.goto(url);await page.waitForFunction(()=>window.__filmExperiment?.state().drawn==='robotic-arm:0');
  const steps=[];
  const mark=async(name)=>steps.push({name,time:Date.now(),state:await page.evaluate(()=>window.__filmExperiment.state())});
  await mark('initial');
  for(const p of [.5,1,.35]){await page.evaluate(p=>scrollTo(0,p*(document.documentElement.scrollHeight-innerHeight)),p);await page.waitForFunction(p=>window.__filmExperiment.state().drawn===`robotic-arm:${Math.round(p*191)}`,p);await mark(`scroll-${p}`);}
  await page.mouse.move(200,350);
  for(let n=1;n<=24;n++){await page.mouse.move(200+n*30,350+Math.sin(n*.3)*50);await page.evaluate(()=>new Promise(r=>requestAnimationFrame(r)));}
  await mark('navigator');await page.waitForFunction(()=>!window.__filmExperiment.state().field.active);await mark('dissipated');
  await page.mouse.move(450,350);await page.mouse.down();await page.waitForFunction(()=>window.__filmExperiment.state().hold.progress>.45);await page.mouse.up();await mark('early-release');
  await page.mouse.move(650,320);await page.mouse.down();await mark('repress');await page.waitForFunction(()=>window.__filmExperiment.state().commits===1);await mark('first-commit');await page.waitForFunction(()=>window.__filmExperiment.state().commits===2);await page.mouse.up();await mark('continuous-second-commit');
  await page.setViewportSize({width:390,height:844});await page.getByRole('button',{name:'Tree',exact:true}).click();await page.waitForFunction(()=>window.__filmExperiment.state().film==='tree');await mark('portrait');
  await page.emulateMedia({reducedMotion:'reduce'});await page.evaluate(()=>scrollTo(0,(document.documentElement.scrollHeight-innerHeight)*.8));await page.waitForFunction(()=>window.__filmExperiment.state().drawn==='tree:153');await mark('reduced-motion');
  const video=page.video();await context.close();await video.saveAs(`${output}interaction.webm`);await video.delete();
  await writeFile(`${output}timeline.json`,JSON.stringify(steps,null,2));console.log(`Recorded real browser interaction: ${output}interaction.webm`);
  const mobile=await browser.newContext({viewport:{width:390,height:844},deviceScaleFactor:2,isMobile:true,hasTouch:true,recordVideo:{dir:output,size:{width:390,height:844}}});
  const phone=await mobile.newPage();await phone.goto(url);await phone.waitForFunction(()=>window.__filmExperiment?.state().drawn==='robotic-arm:0');
  const touch=await mobile.newCDPSession(phone),mobileSteps=[];
  const mobileMark=async(name)=>mobileSteps.push({name,time:Date.now(),state:await phone.evaluate(()=>window.__filmExperiment.state())});
  await mobileMark('portrait-initial');await touch.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:195,y:340}]});
  await phone.waitForFunction(()=>window.__filmExperiment.state().hold.progress>.45);await mobileMark('touch-growth');
  await touch.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await mobileMark('touch-early-release');
  await touch.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:150,y:360}]});await mobileMark('touch-repress');
  await phone.waitForFunction(()=>window.__filmExperiment.state().commits===1);await mobileMark('touch-first-commit');
  await phone.setViewportSize({width:844,height:390});await mobileMark('held-landscape');
  await phone.waitForFunction(()=>window.__filmExperiment.state().commits===2);await touch.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await mobileMark('touch-second-commit');
  await phone.setViewportSize({width:390,height:844});
  await touch.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:195,y:450}]});
  for(let n=1;n<=6;n++)await touch.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:195,y:450-n*35}]});
  await touch.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await phone.waitForFunction(()=>scrollY>0);await mobileMark('native-pan-cancels-hold');
  await touch.detach();const mobileVideo=phone.video();await mobile.close();await mobileVideo.saveAs(`${output}mobile-interaction.webm`);await mobileVideo.delete();
  await writeFile(`${output}mobile-timeline.json`,JSON.stringify(mobileSteps,null,2));console.log(`Recorded native touch: ${output}mobile-interaction.webm`);
} finally {await browser?.close();server.kill('SIGTERM');}
