import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import { mkdir,writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { consumeMedia,sourceFingerprint } from './preflight.mjs';
import { runFfmpeg } from './delivery.mjs';
import { output } from './assets.mjs';
await consumeMedia();const before=await sourceFingerprint();
const label=process.env.FILM_TRANSPORT_LABEL??`transport-${Date.now()}`;
if(!/^[a-zA-Z0-9-]+$/.test(label))throw new Error('Invalid transport evidence label');
const directory=`${output}finalization/${label}/`;await mkdir(directory,{recursive:false});
const cwd=fileURLToPath(new URL('../../../',import.meta.url)),url='http://127.0.0.1:4193';
try{if((await fetch(url)).ok)throw new Error('Transport port occupied');}catch(error){if(error.message==='Transport port occupied')throw error;}
const server=spawn(process.execPath,['node_modules/vite/bin/vite.js','--config','experiments/scroll-film-navigator/vite.config.ts','--port','4193'],{cwd,stdio:'ignore'});let browser;
const summary=values=>{values.sort((a,b)=>a-b);const at=p=>values[Math.min(values.length-1,Math.floor(values.length*p))]??null;return {count:values.length,p50:at(.5),p95:at(.95),max:at(1)};};
try{
  const deadline=Date.now()+15000;while(true){try{if((await fetch(url)).ok)break;}catch{}if(Date.now()>deadline)throw new Error('Transport server unavailable');await new Promise(r=>setTimeout(r,100));}
  const launchFlags=process.env.FILM_SOFTWARE_GPU==='1'?['--use-angle=swiftshader']:[];
  browser=await chromium.launch({channel:'chromium',args:launchFlags});const page=await browser.newPage({viewport:{width:1200,height:800}}),session=await page.context().newCDPSession(page);
  await page.goto(`${url}/?capture=1`);await page.waitForFunction(()=>window.__filmExperiment?.state().drawn==='robotic-arm:0');
  const environment=await page.evaluate(()=>window.__filmExperiment.state());await writeFile(`${directory}startup.json`,JSON.stringify(environment,null,2));if(environment.renderer.kind!=='webgl2')throw new Error(`Capture requires primary renderer: ${JSON.stringify(environment.renderer)}`);
  const traverse=async(duration,direction,alternating=false)=>page.evaluate(async({duration,direction,alternating})=>{
    const start=performance.now(),samples=[];let n=0;
    while(true){await new Promise(r=>requestAnimationFrame(r));const fraction=Math.min(1,(performance.now()-start)/duration);
      const progress=alternating?(n++%2?.7:.3):direction===1?fraction:1-fraction;
      scrollTo(0,progress*(document.documentElement.scrollHeight-innerHeight));const s=window.__filmExperiment.state();
      samples.push({time:performance.timeOrigin+performance.now(),desired:s.desired,submitted:s.drawn,pending:s.cache.pending});if(fraction===1)break;
    }return samples;
  },{duration,direction,alternating});
  await traverse(4000,1);const reports=[];
  const selected=process.env.FILM_TRANSPORT_CASES?.split(',');
  for(const [name,duration,direction,alternating] of [['warm-1x-reverse',8000,-1,false],['warm-1x-forward',8000,1,false],['warm-2x-reverse',4000,-1,false],['warm-2x-forward',4000,1,false],['alternating-targets',1500,1,true]].filter(([name])=>!selected||selected.includes(name))){
    const folder=`${directory}${name}/`;await mkdir(folder,{recursive:true});const frames=[],acks=[];
    const listener=event=>{acks.push(session.send('Page.screencastFrameAck',{sessionId:event.sessionId}));frames.push({data:event.data,time:event.metadata.timestamp*1000});};
    session.on('Page.screencastFrame',listener);await session.send('Page.startScreencast',{format:'jpeg',quality:90,maxWidth:1200,maxHeight:800,everyNthFrame:1});
    const samples=await traverse(duration,direction,alternating);await session.send('Page.stopScreencast');session.removeListener('Page.screencastFrame',listener);await Promise.all(acks);
    for(let n=0;n<frames.length;n++)await writeFile(`${folder}${String(n).padStart(5,'0')}.jpg`,Buffer.from(frames[n].data,'base64'));
    // Decode the source identity stamped into the actual compositor pixels.
    await runFfmpeg(['-y','-threads','2','-framerate','24','-i',`${folder}%05d.jpg`,'-frames:v',String(frames.length),'-vf','crop=120:16:0:784','-pix_fmt','gray','-f','rawvideo',`${folder}identity.raw`]);
    const {readFile}=await import('node:fs/promises'),raw=await readFile(`${folder}identity.raw`);
    if(raw.length!==frames.length*1920)throw new Error('Capture identity count mismatch');
    const captured=frames.map((f,n)=>{let code=0;for(let bit=0;bit<10;bit++)if(raw[n*1920+8*120+bit*12+6]>128)code|=1<<bit;return {time:f.time,index:code&255,valid:!!(code&512)&&!(code&256)&&(code&255)<192};});
    const valid=captured.filter(f=>f.valid);if(!valid.length)throw new Error('No valid captured identities');
    let key=valid[0].index,start=valid[0].time,maxHeld=0;for(const f of valid)if(f.index!==key){maxHeld=Math.max(maxHeld,f.time-start);start=f.time;key=f.index;}maxHeld=Math.max(maxHeld,valid.at(-1).time-start);
    const report={name,durationMs:duration,samples,captured,uniqueCapturedFrames:new Set(valid.map(f=>f.index)).size,maxCapturedHeldMs:maxHeld,captureIntervalsMs:summary(captured.slice(1).map((f,n)=>f.time-captured[n].time)),state:await page.evaluate(()=>window.__filmExperiment.state())};
    reports.push(report);await writeFile(`${folder}report.json`,JSON.stringify(report,null,2));
    console.log(JSON.stringify({name,captures:frames.length,unique:report.uniqueCapturedFrames,maxHeld,intervals:report.captureIntervalsMs}));
  }
  if(before!==await sourceFingerprint())throw new Error('Source changed during transport capture');
  await writeFile(`${directory}report.json`,JSON.stringify({method:'One robotic-arm HTTP context, initial 2x warm traversal, native RAF scroll mapping. CDP JPEG compositor pixels carry source identity. A bounded decoded window is warm progressively, not a fully resident film. Capture overhead/cadence may undersample 24/48fps and does not prove display scanout. No fluid or physical-device qualification in this transport-only study.',sourceFingerprint:before,browser:browser.version(),launchFlags,environment,reports},null,2));
  await session.detach();
}finally{await browser?.close();server.kill('SIGTERM');}
