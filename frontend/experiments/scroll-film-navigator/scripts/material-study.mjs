import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import { mkdir,writeFile,readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
const cwd=fileURLToPath(new URL('../../../',import.meta.url)),output=`${cwd}test-results/scroll-film-navigator/material-study/`,url='http://127.0.0.1:4186';await mkdir(output,{recursive:true});
let occupied=false;try{occupied=(await fetch(url)).ok;}catch{}if(occupied)throw new Error('Material study port occupied');
const server=spawn(process.execPath,['node_modules/vite/bin/vite.js','--config','experiments/scroll-film-navigator/vite.config.ts','--port','4186'],{cwd,stdio:'ignore'});let browser;
try{
 const deadline=Date.now()+15000;while(true){try{if((await fetch(url)).ok)break;}catch{}if(Date.now()>deadline)throw new Error('Material study server unavailable');await new Promise(r=>setTimeout(r,100));}
 browser=await chromium.launch({channel:'chromium'});const studies=[];
 for(const refraction of [false,true]){
  const context=await browser.newContext({viewport:{width:1200,height:800}}),page=await context.newPage();
  await page.route('**/*.webp*',route=>route.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="960" height="540"><defs><pattern id="p" width="12" height="12" patternUnits="userSpaceOnUse"><rect width="12" height="12" fill="#ddd"/><path d="M0 0h6v6H0zM6 6h6v6H6z" fill="#222"/></pattern></defs><rect width="960" height="540" fill="url(#p)"/></svg>'}));
  if(!refraction)await page.route(/\/src\/material\.ts(?:\?|$)/,async route=>{const response=await route.fetch(),body=await response.text();if(!body.includes('gradient*22.0/viewport'))throw new Error('Refraction control substitution absent');await route.fulfill({response,body:body.replace('gradient*22.0/viewport','gradient*0.0/viewport')});});
  await page.goto(url);await page.waitForFunction(()=>window.__filmExperiment?.state().drawn==='robotic-arm:0');await page.evaluate(async()=>{window.__filmExperiment.freeze(true);window.__filmExperiment.clearFluid();const time=performance.now();for(let n=0;n<=32;n++){const event=new PointerEvent('pointermove',{bubbles:true,pointerType:'mouse',isPrimary:true,clientX:200+700*n/32,clientY:350});Object.defineProperty(event,'timeStamp',{value:time+n/32*700});document.querySelector('#stage').dispatchEvent(event);if(n)await window.__filmExperiment.advance(.7/32);}});
  const name=refraction?'refraction':'control';await page.screenshot({path:`${output}${name}.png`});studies.push({name,field:await page.evaluate(()=>window.__filmExperiment.fieldMetrics()),state:await page.evaluate(()=>window.__filmExperiment.state())});await context.close();
 }
 const ts=await readFile(`${cwd}experiments/scroll-film-navigator/tests/pixels.ts`,'utf8'),{transpileModule}=await import('typescript');const {difference}=await import(`data:text/javascript;base64,${Buffer.from(transpileModule(ts,{compilerOptions:{module:99,target:9}}).outputText).toString('base64')}`);
 const changed=difference(await readFile(`${output}control.png`),await readFile(`${output}refraction.png`));if(changed.changed<100)throw new Error('Actual refraction pixel difference absent');if(Math.abs(studies[0].field.mass/studies[1].field.mass-1)>.02)throw new Error('Control fluid states differ');
 await writeFile(`${output}report.json`,JSON.stringify({method:'Primary browser GPU compositor, synthetic checkerboard film only for deterministic isolation. Identical frozen-clock input and fluid state; control changes only navigator displacement multiplier to zero via a test route. Pigment remains enabled in both. Difference demonstrates actual localized source-image deformation, not morphology approval or performance.',browser:browser.version(),sourceHash:createHash('sha256').update(await readFile(`${cwd}experiments/scroll-film-navigator/src/material.ts`)).digest('hex'),difference:changed,studies},null,2));console.log('PASS: isolated actual refraction pixels',changed);
}finally{await browser?.close();server.kill('SIGTERM');}
