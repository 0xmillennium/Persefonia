import { chromium } from 'playwright';
import { spawn, execFileSync } from 'node:child_process';
import { mkdir, writeFile, readFile, readdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
const cwd=fileURLToPath(new URL('../../../',import.meta.url)),experiment=fileURLToPath(new URL('../',import.meta.url));
const output=`${cwd}test-results/scroll-film-navigator/recordings/`;await mkdir(output,{recursive:true});
const sources=['index.html','vite.config.ts','tsconfig.json','scripts/record.mjs',...(await readdir(`${experiment}src/`)).filter(name=>/\.(ts|css)$/.test(name)).map(name=>`src/${name}`)];
async function fingerprints(){return Object.fromEntries(await Promise.all(sources.map(async name=>[name,createHash('sha256').update(await readFile(`${experiment}${name}`)).digest('hex')])));}
const before=await fingerprints();
let occupied=false;try{occupied=(await fetch('http://127.0.0.1:4182')).ok;}catch{}if(occupied)throw new Error('Recording port 4182 already occupied; refusing to reuse an unknown server');
const server=spawn(process.execPath,['node_modules/vite/bin/vite.js','--config','experiments/scroll-film-navigator/vite.config.ts','--port','4182'],{cwd,stdio:'pipe'});server.stderr.on('data',data=>process.stderr.write(data));
const url='http://127.0.0.1:4182';let browser;
try{
  const start=Date.now();while(true){try{if((await fetch(url)).ok)break;}catch{}if(Date.now()-start>15000)throw new Error('Recording server did not start');await new Promise(r=>setTimeout(r,100));}
  browser=await chromium.launch({channel:'chromium'});
  const desktop=await browser.newContext({viewport:{width:1200,height:800},recordVideo:{dir:output,size:{width:1200,height:800}}}),page=await desktop.newPage(),steps=[],origin=Date.now();
  const mark=async(name,shot=false)=>{const state=await page.evaluate(()=>window.__filmExperiment.state());steps.push({name,relativeMs:Date.now()-origin,time:new Date().toISOString(),state});if(shot)await page.screenshot({path:`${output}${name}.png`});};
  await page.goto(url);await page.waitForFunction(()=>window.__filmExperiment?.state().drawn==='robotic-arm:0');
  if((await page.evaluate(()=>window.__filmExperiment.state().renderer.kind))!=='webgl2')throw new Error('Primary GPU renderer required for material recordings');await mark('desktop-initial',true);
  await page.evaluate(()=>scrollTo(0,.5*(document.documentElement.scrollHeight-innerHeight)));await page.waitForFunction(()=>window.__filmExperiment.state().drawn==='robotic-arm:96');
  for(const [name,interval,curve]of [['slow-stroke',28,n=>[160+n*26,320]],['fast-stroke',4,n=>[160+n*26,390]],['circle',12,n=>[600+180*Math.cos(n*Math.PI/16),350+120*Math.sin(n*Math.PI/16)]],['cross-reverse',8,n=>[300+(n<16?n:32-n)*32,280+n*3]]]){
    await page.mouse.move(160,320);for(let n=0;n<33;n++){const[x,y]=curve(n);await page.mouse.move(x,y);await page.evaluate(ms=>new Promise(r=>setTimeout(r,ms)),interval);}await page.evaluate(()=>window.__filmExperiment.fieldMetrics());await mark(name,true);
  }
  await page.waitForFunction(()=>!window.__filmExperiment.state().field.active);
  await page.evaluate(()=>window.__filmExperiment.freeze(true));
  for(const [name,speed,circle]of [['controlled-slow',600,false],['controlled-fast',3000,false],['controlled-curl',900,true],['controlled-crossing',1200,true]]){
    await page.evaluate(async({speed,circle,name})=>{
      window.__filmExperiment.clearFluid();
      const crossing=name==='controlled-crossing',count=crossing?128:64,duration=crossing?3.2:circle?1.6:800/speed,time=performance.now();
      for(let n=0;n<=count;n++){const fraction=n/count,event=new PointerEvent('pointermove',{bubbles:true,pointerType:'mouse',isPrimary:true,clientX:crossing?600+250*Math.sin(fraction*Math.PI*4):circle?600+180*Math.cos(fraction*Math.PI*4):180+800*fraction,clientY:crossing?350+160*Math.sin(fraction*Math.PI*6):circle?350+130*Math.sin(fraction*Math.PI*4):350});Object.defineProperty(event,'timeStamp',{value:time+fraction*duration*1000});document.querySelector('#stage').dispatchEvent(event);if(n)await window.__filmExperiment.advance(duration/count);}
      window.__filmExperiment.fieldMetrics();
    },{speed,circle,name});await mark(name,true);
  }
  await page.evaluate(()=>window.__filmExperiment.advance(.25));await mark('controlled-crossing-advected',true);await page.evaluate(()=>window.__filmExperiment.advance(.55));await mark('controlled-crossing-residual-islands',true);
  await page.evaluate(()=>window.__filmExperiment.freeze(false));
  await page.waitForFunction(()=>!window.__filmExperiment.state().field.active);await mark('desktop-complete-decay',true);
  await page.mouse.move(350,300);await page.mouse.down();await page.waitForFunction(()=>window.__filmExperiment.state().hold.progress>.45);await mark('hold-growing-current',true);await page.mouse.up();await mark('hold-contracting',true);
  await page.mouse.move(700,350);await page.mouse.down();await mark('hold-residual-repress',true);await page.waitForFunction(()=>window.__filmExperiment.state().commits===1);await mark('hold-first-settled-tree',true);
  // Continuous automated contact observed in wall time, independent of the test clock.
  const duration=Date.now();await page.evaluate(ms=>new Promise(r=>setTimeout(r,ms)),15000);
  if((await page.evaluate(()=>window.__filmExperiment.state().commits))!==1)throw new Error('Continuous automated contact committed twice');await mark('hold-15s-no-repeat',true);await page.mouse.up();steps.push({name:'continuous-hold-observation',wallMs:Date.now()-duration});
  await page.mouse.down();await page.waitForFunction(()=>window.__filmExperiment.state().commits===2);await page.mouse.up();await mark('hold-distinct-second-press',true);
  // Real canonical media, intentionally delayed availability: full tinted A must wait without B.
  let permit;const gate=new Promise(r=>{permit=r;});await page.route('**/tree/*.webp*',async route=>{await gate;if(route.request().failure())return;try{await route.continue();}catch(error){if(!route.request().failure()&&!String(error).includes('Route is already handled'))throw error;}});
  await page.evaluate(()=>scrollTo(0,.75*(document.documentElement.scrollHeight-innerHeight)));await page.waitForFunction(()=>window.__filmExperiment.state().drawn==='robotic-arm:143');
  await page.mouse.move(420,320);await page.mouse.down();await page.waitForFunction(()=>{const s=window.__filmExperiment.state();return s.hold.phase==='COVERED_WAIT_READY'&&s.hold.coverage>=.999;});await page.evaluate(()=>window.__filmExperiment.freeze(true));await mark('hold-full-tinted-wait',true);await page.mouse.up();permit();
  await page.waitForFunction(()=>window.__filmExperiment.state().hold.phase==='REVEALING');await page.evaluate(()=>window.__filmExperiment.advance(.055));await mark('outward-reveal-first',true);await page.evaluate(()=>window.__filmExperiment.advance(.065));await mark('outward-reveal-middle',true);await page.evaluate(()=>window.__filmExperiment.advance(.08));await mark('outward-reveal-late',true);await page.evaluate(()=>window.__filmExperiment.advance(.2));await page.waitForFunction(()=>window.__filmExperiment.state().commits===3);await page.evaluate(()=>window.__filmExperiment.freeze(false));await mark('outward-reveal-clean-tree',true);await page.unrouteAll({behavior:'wait'});
  await page.emulateMedia({reducedMotion:'reduce'});await page.getByRole('button',{name:'Robotic arm',exact:true}).click();await page.waitForFunction(()=>window.__filmExperiment.state().film==='robotic-arm');await mark('reduced-motion-semantic',true);
  const video=page.video();await desktop.close();await video.saveAs(`${output}interaction.webm`);await video.delete();await writeFile(`${output}timeline.json`,JSON.stringify(steps,null,2));console.log('Recorded primary GPU desktop material and one continuous automated 15-second pointer contact.');
  const mobile=await browser.newContext({viewport:{width:390,height:844},deviceScaleFactor:2,isMobile:true,hasTouch:true,recordVideo:{dir:output,size:{width:390,height:844}}}),phone=await mobile.newPage(),touchSteps=[],mobileOrigin=Date.now();
  const mobileMark=async name=>{touchSteps.push({name,relativeMs:Date.now()-mobileOrigin,state:await phone.evaluate(()=>window.__filmExperiment.state())});await phone.screenshot({path:`${output}${name}.png`});};
  await phone.goto(url);await phone.waitForFunction(()=>window.__filmExperiment?.state().drawn==='robotic-arm:0');const session=await mobile.newCDPSession(phone);
  const down=async(x,y)=>session.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x,y}]}),up=async()=>session.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
  await mobileMark('touch-initial');await down(195,340);await phone.waitForFunction(()=>window.__filmExperiment.state().hold.progress>.4);await mobileMark('touch-growth');await up();await mobileMark('touch-contraction');await down(130,360);await mobileMark('touch-residual-repress');await phone.waitForFunction(()=>window.__filmExperiment.state().commits===1);await mobileMark('touch-first-settled');await up();
  await down(195,450);for(let n=1;n<=6;n++)await session.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:195,y:450-n*35}]});await up();await phone.waitForFunction(()=>scrollY>0);await mobileMark('touch-native-pan');await phone.waitForFunction(()=>window.__filmExperiment.state().hold.phase==='IDLE');
  await session.send('Input.synthesizePinchGesture',{x:195,y:420,scaleFactor:1.35,gestureSourceType:'touch'});const pinch=(await session.send('Page.getLayoutMetrics')).cssVisualViewport.scale;
  if(pinch<=1)throw new Error(`Native touch pinch did not zoom the visual viewport: ${pinch}`);await mobileMark('touch-native-pinch');touchSteps.at(-1).visualViewportScale=pinch;
  await session.send('Input.synthesizePinchGesture',{x:195,y:420,scaleFactor:1/pinch,gestureSourceType:'touch'});
  await phone.setViewportSize({width:844,height:390});await mobileMark('touch-landscape-cancellation');await down(420,150);await phone.waitForFunction(()=>window.__filmExperiment.state().commits===2);await up();await mobileMark('touch-landscape-distinct-press');
  await session.detach();const mobileVideo=phone.video();await mobile.close();await mobileVideo.saveAs(`${output}mobile-interaction.webm`);await mobileVideo.delete();await writeFile(`${output}mobile-timeline.json`,JSON.stringify(touchSteps,null,2));
  const after=await fingerprints();if(JSON.stringify(before)!==JSON.stringify(after))throw new Error('Source changed during recording; evidence needs re-recording');
  await writeFile(`${output}manifest.json`,JSON.stringify({method:'Real browser GPU API with canonical media. controlled-* scenes explicitly use timestamped synthetic input and the deterministic simulation clock to inspect morphology independently of slow-host packet cadence; delayed-media outward-reveal stills also use the deterministic clock for frame-by-frame region inspection; ordinary pointer/touch scenes and the continuous automated 15-second contact use wall time. Playwright WebM is 25fps, useful for morphology and semantics, insufficient alone to prove 48 source changes/s. CDP native touch emulation; no physical-device claim. Screenshots add overhead and are excluded from ordinary performance measurements. Landscape video padding reflects fixed recording dimensions.',browser:browser.version(),branch:execFileSync('git',['branch','--show-current'],{cwd,encoding:'utf8'}).trim(),sourceHashes:before,physicalHardware:false,desktop:steps[0].state.renderer.info,touch:touchSteps[0].state.renderer.info},null,2));console.log(`Artifacts saved in ${output}`);
}finally{await browser?.close();server.kill('SIGTERM');await new Promise(resolve=>{if(server.exitCode!==null)resolve();else{server.once('exit',resolve);setTimeout(resolve,2000);}});}
