import { webkit, chromium, firefox } from 'playwright';
import { writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
const output=fileURLToPath(new URL('../../../test-results/scroll-film-navigator/remediation/browser-preflight/',import.meta.url));await mkdir(output,{recursive:true});
const results=[],app=process.env.FILM_PREFLIGHT_APP==='1',churn=process.env.FILM_PREFLIGHT_CHURN==='1',reused=new Map();
let server;if(app){const cwd=fileURLToPath(new URL('../../../',import.meta.url));server=spawn(process.execPath,['node_modules/vite/bin/vite.js','--config','experiments/scroll-film-navigator/vite.config.ts','--port','4185'],{cwd,stdio:'ignore'});const deadline=Date.now()+15000;while(true){try{if((await fetch('http://127.0.0.1:4185')).ok)break;}catch{}if(Date.now()>deadline)throw new Error('App preflight server unavailable');await new Promise(r=>setTimeout(r,100));}}
try{

for(const mode of (app?['minimal','app-webgl','app-basic','app-nojs']:['minimal']))for(const [name,type] of [['webkit',webkit],['chromium',chromium],['firefox',firefox]])for(let trial=0;trial<(name==='webkit'?(churn?15:5):1);trial++){
  let browser,page;const reuseKey=`${mode}:${name}`;const result={name,trial,mode,reusedBrowser:churn,method:'Fresh browser, inline minimal semantic page; no experiment assets, JS module or shader'};
  try{browser=reused.get(reuseKey)??await type.launch(name==='chromium'?{channel:'chromium'}:{ });if(churn)reused.set(reuseKey,browser);result.version=browser.version();page=await browser.newPage({javaScriptEnabled:mode!=='app-nojs'});if(mode==='minimal')await page.setContent('<!doctype html><html><body><button>Minimal native page</button></body></html>');if(mode==='minimal'){await page.getByRole('button').click();result.text=await page.getByRole('button').textContent();}else{await page.goto(`http://127.0.0.1:4185/${mode==='app-basic'?'?renderer=basic':''}`);if(mode==='app-nojs')await page.locator('#poster').waitFor();else await page.waitForFunction(()=>window.__filmExperiment?.state().drawn==='robotic-arm:0');result.text=await page.title();}result.graphics=await page.evaluate(()=>{const canvas=document.createElement('canvas'),gl=canvas.getContext('webgl2');if(!gl)return {webgl2:false};const ext=gl.getExtension('WEBGL_debug_renderer_info');return {webgl2:true,floatFramebuffer:!!gl.getExtension('EXT_color_buffer_float'),vendor:ext?gl.getParameter(ext.UNMASKED_VENDOR_WEBGL):gl.getParameter(gl.VENDOR),renderer:ext?gl.getParameter(ext.UNMASKED_RENDERER_WEBGL):gl.getParameter(gl.RENDERER)};});result.status='PASS';}
  catch(error){result.status='FAIL';result.error=String(error);}finally{await page?.close().catch(()=>{});if(!churn||trial===(name==='webkit'?14:0)){await browser?.close();reused.delete(reuseKey);}}
  results.push(result);console.log(mode,name,trial,result.status,result.graphics??result.error);await writeFile(`${output}${churn?'context-churn':app?'app-investigation':'report'}.json`,JSON.stringify(results,null,2));
}

}finally{for(const browser of reused.values())await browser.close();server?.kill("SIGTERM");}
