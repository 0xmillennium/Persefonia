import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import { mkdir,writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { consumeMedia,sourceFingerprint } from './preflight.mjs';
import { output } from './assets.mjs';
await consumeMedia();
const label=process.env.FILM_REVIEW_LABEL??'review',renderer=process.env.FILM_REVIEW_RENDERER??'gpu';
if(!/^[a-zA-Z0-9-]+$/.test(label)||!['gpu','basic'].includes(renderer))throw new Error('Invalid review label or renderer');
const directory=`${output}finalization/${label}/`;await mkdir(directory,{recursive:true});
const before=await sourceFingerprint(),cwd=fileURLToPath(new URL('../../../',import.meta.url)),url='http://127.0.0.1:4192';
try{if((await fetch(url)).ok)throw new Error('Review port occupied');}catch(error){if(error.message==='Review port occupied')throw error;}
const server=spawn(process.execPath,['node_modules/vite/bin/vite.js','--config','experiments/scroll-film-navigator/vite.config.ts','--port','4192'],{cwd,stdio:'ignore'});
let browser,context;
try{
  const deadline=Date.now()+15000;while(true){try{if((await fetch(url)).ok)break;}catch{}if(Date.now()>deadline)throw new Error('Review server unavailable');await new Promise(r=>setTimeout(r,100));}
  browser=await chromium.launch({channel:'chromium',...(process.env.FILM_SOFTWARE_GPU==='1'?{args:['--use-angle=swiftshader']}:{})});context=await browser.newContext({viewport:{width:1200,height:800},reducedMotion:'no-preference',recordVideo:{dir:directory,size:{width:1200,height:800}}});
  const page=await context.newPage(),timeline=[],started=Date.now();
  const mark=async(name)=>{timeline.push({name,elapsedMs:Date.now()-started,state:await page.evaluate(()=>window.__filmExperiment.state())});await page.screenshot({path:`${directory}${name}.png`});};
  await page.goto(renderer==='basic'?`${url}/?renderer=basic`:url);await page.waitForFunction(()=>window.__filmExperiment?.state().drawn==='robotic-arm:0');
  if((await page.evaluate(()=>window.__filmExperiment.state().renderer.kind))!==(renderer==='basic'?'canvas2d':'webgl2'))throw new Error('Requested review material unavailable');
  await mark('01-dark-initial');await page.mouse.move(600,300);
  await page.mouse.wheel(0,120);await page.waitForFunction(()=>window.__filmExperiment.state().drawn==='robotic-arm:1');await mark('02-wheel-one-frame');
  await page.evaluate(()=>scrollTo(0,144/191*(document.documentElement.scrollHeight-innerHeight)));await page.waitForFunction(()=>window.__filmExperiment.state().drawn==='robotic-arm:144');
  for(const [name,interval,curve] of [
    ['03-slow',35,n=>[220+n*38,330+Math.sin(n*.3)*45]],
    ['04-fast',4,n=>[220+n*38,420-n*4]],
    ['05-curve-reverse',12,n=>[600+220*Math.sin(n*Math.PI/8),350+95*Math.cos(n*Math.PI/8)]],
    ['06-crossing',12,n=>[600+250*Math.sin(n*Math.PI/8),350+130*Math.sin(n*Math.PI/4)]]
  ]){for(let n=0;n<=16;n++){const [x,y]=curve(n);await page.mouse.move(x,y);await page.waitForTimeout(interval);}await mark(name);}
  await page.waitForFunction(()=>!window.__filmExperiment.state().field.active);await mark('07-decayed');
  await page.mouse.move(450,340);await page.mouse.down();await page.waitForTimeout(1000);await mark('08-early-growth');await page.mouse.up();await page.waitForTimeout(180);await mark('09-contracting');
  await page.mouse.move(650,320);await page.mouse.down();await mark('10-repress');
  await page.waitForFunction(()=>window.__filmExperiment.state().hold.phase==='REVEALING',{},{timeout:15000});await mark('11-outward-reveal');
  await page.waitForFunction(()=>window.__filmExperiment.state().commits===1);await mark('12-light-settled');await page.waitForTimeout(3200);await mark('13-long-press-single-commit');await page.mouse.up();
  for(let n=0;n<=20;n++){await page.mouse.move(220+n*32,350+65*Math.sin(n*.3));await page.waitForTimeout(24);}await mark('14-tree-navigator');
  await page.waitForFunction(()=>!window.__filmExperiment.state().field.active);await mark('15-tree-decayed');
  await page.emulateMedia({reducedMotion:'reduce'});await mark('16-reduced-motion');
  const video=page.video();await context.close();context=null;await video.saveAs(`${directory}navigator-hold.webm`);
  const after=await sourceFingerprint();if(before!==after)throw new Error('Review source changed during capture');
  await writeFile(`${directory}timeline.json`,JSON.stringify({status:'COMPLETE',browser:browser.version(),sourceFingerprint:before,method:'Actual browser, native wall-clock gestures and canonical active media. Playwright video cadence is about 25fps; software pacing/IPC changes achieved gesture speed. No controlled simulation, physical scanout or human artistic acceptance claim.',timeline},null,2));
  console.log(`Review captured: ${directory}navigator-hold.webm; ${timeline.length} identified snapshots`);
}finally{await context?.close();await browser?.close();server.kill('SIGTERM');}
