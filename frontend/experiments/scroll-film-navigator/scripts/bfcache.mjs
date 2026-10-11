import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import { mkdir,writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
const cwd=fileURLToPath(new URL('../../../',import.meta.url)),output=`${cwd}test-results/scroll-film-navigator/bfcache/`,url='http://127.0.0.1:4187';await mkdir(output,{recursive:true});
let occupied=false;try{occupied=(await fetch(url)).ok;}catch{}if(occupied)throw new Error('BFCache port occupied');
const server=spawn(process.execPath,['node_modules/vite/bin/vite.js','preview','--config','experiments/scroll-film-navigator/vite.config.ts','--port','4187'],{cwd,stdio:'ignore'});let browser;
try{
 const deadline=Date.now()+15000;while(true){try{if((await fetch(url)).ok)break;}catch{}if(Date.now()>deadline)throw new Error('Built preview unavailable');await new Promise(r=>setTimeout(r,100));}
 browser=await chromium.launch({channel:'chromium',ignoreDefaultArgs:['--disable-back-forward-cache']});const page=await browser.newPage({viewport:{width:1200,height:800}}),session=await page.context().newCDPSession(page),notUsed=[];
 await session.send('Page.enable');session.on('Page.backForwardCacheNotUsed',event=>notUsed.push(event));
 await page.addInitScript(()=>window.addEventListener('pageshow',event=>sessionStorage.setItem('film-bfcache-event',JSON.stringify({url:location.href,persisted:event.persisted,time:performance.now()}))));
 await page.goto(url);await page.locator('#tree').click();await page.waitForFunction(()=>document.querySelector('#tree').getAttribute('aria-pressed')==='true');await page.evaluate(()=>scrollTo(0,.55*(document.documentElement.scrollHeight-innerHeight)));
 const before=await page.evaluate(()=>({y:scrollY,film:history.state?.scrollFilmNavigator?.film}));await page.mouse.move(500,300);await page.mouse.down();await page.locator('#countdown').waitFor({state:'visible'});
 await page.goto(`${url}/index.html?away=1`);await page.goBack();await page.waitForFunction(()=>document.querySelector('#tree').getAttribute('aria-pressed')==='true');await page.mouse.up();
 const after=await page.evaluate(()=>({y:scrollY,film:history.state?.scrollFilmNavigator?.film,countdownHidden:document.querySelector('#countdown').hidden,pageshow:JSON.parse(sessionStorage.getItem('film-bfcache-event')??'null'),hookPresent:'__filmExperiment' in window}));
 if(after.y!==before.y||after.film!=='tree'||!after.countdownHidden||after.hookPresent)throw new Error(`BFCache/history restoration contract failed: ${JSON.stringify({before,after})}`);
 await page.screenshot({path:`${output}restored.png`});await writeFile(`${output}report.json`,JSON.stringify({method:'Actual built artifact; Chromium default disable-back-forward-cache flag explicitly removed. Native navigation/back, recorded pageshow.persisted and CDP non-admission reasons. Transient hold is interrupted across navigation; native scroll and selected film must restore. No dev hooks or simulated pageshow event.',browser:browser.version(),before,after,notUsed,status:after.pageshow?.persisted?'PASS':'NOT VERIFIED'},null,2));console.log(after.pageshow?.persisted?'PASS: real BFCache admission and restoration':'NOT VERIFIED: native back restoration passed but browser did not admit BFCache',notUsed);
 await session.detach();
}finally{await browser?.close();server.kill('SIGTERM');}
