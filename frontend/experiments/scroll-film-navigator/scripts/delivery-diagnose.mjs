import { chromium } from 'playwright';
import { spawn,spawnSync } from 'node:child_process';
import { mkdir,writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { consumeMedia,sourceFingerprint } from './preflight.mjs';
import { output } from './assets.mjs';
import { analyzeDelivery } from './delivery-analysis.mjs';
const label=process.env.FILM_DELIVERY_LABEL,selected=(process.env.FILM_DELIVERY_CASES??'ready,plain,capture').split(',');
const directions=(process.env.FILM_DELIVERY_DIRECTIONS??'1,-1').split(',').map(Number);
if(directions.some(direction=>direction!==1&&direction!==-1))throw new Error('Directions must be 1 or -1');
if(!label||!/^[a-zA-Z0-9-]+$/.test(label)||selected.some(c=>!['ready','plain','capture','basic','legacy'].includes(c)))throw new Error('Explicit unique label and supported cases required');
const directory=`${output}delivery-continuation/${label}/`;await mkdir(directory,{recursive:false});
const media=await consumeMedia(),before=await sourceFingerprint();
const cwd=fileURLToPath(new URL('../../../',import.meta.url)),url='http://127.0.0.1:4194';
try{if((await fetch(url)).ok)throw new Error('Diagnostic port occupied');}catch(error){if(error.message==='Diagnostic port occupied')throw error;}
const server=spawn(process.execPath,['node_modules/vite/bin/vite.js','--config','experiments/scroll-film-navigator/vite.config.ts','--port','4194'],{cwd,stdio:'ignore'});
const launchFlags=process.env.FILM_SOFTWARE_GPU==='1'?['--use-angle=swiftshader']:[];
let browser;
const drive=async(page,duration,kind,direction,stage)=>page.evaluate(async({duration,kind,direction,stage})=>{
  const mark=window.__filmExperiment.markDelivery;
  const start=performance.now();mark(`${stage}-start`);
  await new Promise(resolve=>{
    const step=()=>{
      const elapsed=performance.now()-start,fraction=Math.min(1,elapsed/duration);
      const index=kind==='ready'?Math.round(4-4*Math.cos(elapsed/1000*24/8*Math.PI)):Math.round((direction===1?fraction:1-fraction)*191);
      const y=index/191*(document.documentElement.scrollHeight-innerHeight);
      mark('input',{index,y,elapsed,phase:stage});scrollTo(0,y);
      if(kind==='legacy'){
        const readStart=performance.now(),s=window.__filmExperiment.state();
        mark('legacy-read',{expected:index,observed:s.desired,readMs:performance.now()-readStart});
      }
      if(fraction===1){if(timer)clearInterval(timer);mark(`${stage}-end`);resolve();}
      else if(kind==='legacy')requestAnimationFrame(step);
    };
    const timer=kind==='legacy'?null:setInterval(step,16);
    if(kind==='legacy')requestAnimationFrame(step);else step();
  });
},{duration,kind,direction,stage});
try{
  const deadline=Date.now()+15000;while(true){try{if((await fetch(url)).ok)break;}catch{}if(Date.now()>deadline)throw new Error('Diagnostic server unavailable');await new Promise(r=>setTimeout(r,100));}
  browser=await chromium.launch({channel:'chromium',args:launchFlags});const runs=[];
  for(const kind of selected){
    const context=await browser.newContext({viewport:{width:1200,height:800},deviceScaleFactor:1,reducedMotion:'no-preference'});
    try{
      const page=await context.newPage(),errors=[];page.on('pageerror',error=>errors.push(String(error)));
      await page.goto(`${url}/?delivery=1&capture=1${kind==='basic'?'&renderer=basic':''}`);
      await page.waitForFunction(()=>window.__filmExperiment?.state().drawn==='robotic-arm:0');
      const startup=await page.evaluate(()=>window.__filmExperiment.state());await writeFile(`${directory}${kind}-startup.json`,JSON.stringify(startup,null,2));
      if(startup.renderer.kind!==(kind==='basic'?'canvas2d':'webgl2'))throw new Error(`${kind}: selected material unavailable`);
      // Same real publication. Control keeps only nine actual frames resident;
      // other cases warm HTTP/image caches, never the entire decoded sequence.
      if(kind==='ready')for(let index=1;index<=8;index++){
        await page.evaluate(index=>scrollTo(0,index/191*(document.documentElement.scrollHeight-innerHeight)),index);
        await page.waitForFunction(index=>window.__filmExperiment.state().drawn===`robotic-arm:${index}`,index);
      }else await drive(page,4000,'real',1,'warm');
      const session=await context.newCDPSession(page);
      for(const direction of (kind==='ready'?[1]:directions)){
        const begin=direction===1?0:191,name=`${kind}-${direction===1?'forward':'reverse'}`;
        await page.evaluate(index=>scrollTo(0,index/191*(document.documentElement.scrollHeight-innerHeight)),begin);
        await page.waitForFunction(index=>{const s=window.__filmExperiment.state();return s.drawn===`robotic-arm:${index}`&&s.cache.pending===0&&!s.scheduled;},begin,{timeout:10000});
        const initial=await page.evaluate(()=>({state:window.__filmExperiment.state(),time:performance.now(),origin:performance.timeOrigin,clockOffset:Date.now()-(performance.timeOrigin+performance.now())}));
        const frames=[],acks=[];let dropped=0;
        const listener=event=>{acks.push(session.send('Page.screencastFrameAck',{sessionId:event.sessionId}));if(frames.length<240)frames.push({data:event.data,time:event.metadata.timestamp*1000-initial.origin});else dropped++;};
        if(kind==='capture'||kind==='legacy'){session.on('Page.screencastFrame',listener);await session.send('Page.startScreencast',{format:'jpeg',quality:90,maxWidth:1200,maxHeight:800,everyNthFrame:1});}
        await drive(page,8000,kind==='ready'?'ready':kind==='legacy'?'legacy':'real',direction,'measure');
        const expected=kind==='ready'?null:direction===1?191:0;
        if(expected!==null)await page.waitForFunction(index=>window.__filmExperiment.state().drawn===`robotic-arm:${index}`,expected,{timeout:5000});
        await page.waitForTimeout(300);
        if(kind==='capture'||kind==='legacy'){await session.send('Page.stopScreencast');session.removeListener('Page.screencastFrame',listener);await Promise.all(acks);}
        const ending=await page.evaluate(()=>({state:window.__filmExperiment.state(),time:performance.now(),timeline:window.__filmExperiment.delivery()}));
        // Preserve the causal timeline even if post-workload pixel extraction fails.
        await writeFile(`${directory}${name}.json`,JSON.stringify({name,sourceFingerprint:before,initial,ending,captureTimes:frames.map(f=>f.time)},null,2)+'\n');
        if(ending.timeline.dropped)throw new Error('Observation ring overflowed; comparison incomplete');
        let captured=[];
        if(frames.length){
          const result=spawnSync('ffmpeg',['-v','error','-threads','2','-f','image2pipe','-framerate','24','-i','pipe:0','-vf','crop=120:16:0:784','-pix_fmt','gray','-vsync','0','-f','rawvideo','pipe:1'],{input:Buffer.concat(frames.map(f=>Buffer.from(f.data,'base64'))),maxBuffer:4*1024*1024});
          if(result.status!==0||result.stdout.length!==frames.length*1920)throw new Error(`Capture identity decode failed: ${result.stderr}`);
          captured=frames.map((f,n)=>{let code=0;for(let bit=0;bit<10;bit++)if(result.stdout[n*1920+8*120+bit*12+6]>128)code|=1<<bit;return {time:f.time,index:code&255,valid:!!(code&512)&&!(code&256)&&(code&255)<192};});
          for(const n of new Set([0,Math.floor(frames.length/2),frames.length-1]))await writeFile(`${directory}${name}-${n}.jpg`,Buffer.from(frames[n].data,'base64'));
        }
        const run={name,kind,direction,sourceFingerprint:before,initial,ending,captured,droppedCaptures:dropped,errors,conditions:{durationMs:8000,input:kind==='legacy'?'Legacy RAF-driven input plus immediate full state() read':'16ms timer, wall-clock trajectory, scroll events own target; no RAF input driver or per-step state serialization',window:kind==='ready'?[0,8]:[0,191],warming:kind==='ready'?'nine actual decoded frames':'4s full-range HTTP/image warming; only bounded decoded cache resident',viewport:[1200,800],identityShader:true,capture:kind==='capture'||kind==='legacy',mediaIdentity:media.identity}};
        run.analysis=analyzeDelivery(run);runs.push(run);await writeFile(`${directory}${name}.json`,JSON.stringify(run,null,2)+'\n');const {targetRecords,...compact}=run.analysis;console.log(JSON.stringify({name,...compact}));
      }
      await session.detach();
    }finally{await context.close();}
  }
  if(before!==await sourceFingerprint())throw new Error('Source changed during comparison');
  await writeFile(`${directory}summary.json`,JSON.stringify({sourceFingerprint:before,mediaIdentity:media.identity,browser:browser.version(),launchFlags,method:'Opt-in bounded development timeline, application performance.now clock. CDP TimeSinceEpoch timestamp converted by performance.timeOrigin; Date.now alignment offset recorded. Image-load and bitmap stages are wall-clock browser operations, not pure CPU/network times. Draw submission is not GPU completion or physical scanout. Ready control uses a nine-frame actual-media window, not full-film residency.',runs:runs.map(({name,conditions,analysis})=>({name,conditions,analysis}))},null,2)+'\n');
}finally{await browser?.close();server.kill('SIGTERM');}
