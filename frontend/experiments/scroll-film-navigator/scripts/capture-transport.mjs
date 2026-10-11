import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import { mkdir,writeFile,readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
const cwd=fileURLToPath(new URL('../../../',import.meta.url)),output=`${cwd}test-results/scroll-film-navigator/transport-capture/`;await mkdir(output,{recursive:true});
let occupied=false;try{occupied=(await fetch('http://127.0.0.1:4183')).ok;}catch{}if(occupied)throw new Error('Capture port occupied; refusing to reuse an unknown server');
const server=spawn(process.execPath,['node_modules/vite/bin/vite.js','--config','experiments/scroll-film-navigator/vite.config.ts','--port','4183'],{cwd,stdio:'pipe'});server.stderr.on('data',b=>process.stderr.write(b));let browser;
async function decode(frames,height){
  if(!frames.length)return [];
  const ffmpeg=spawn('ffmpeg',['-hide_banner','-loglevel','error','-threads','2','-f','image2pipe','-vcodec','mjpeg','-i','pipe:0','-vf',`crop=120:16:0:${height-16}`,'-pix_fmt','gray','-f','rawvideo','pipe:1'],{stdio:['pipe','pipe','pipe']}),chunks=[];let errors='';ffmpeg.stdout.on('data',b=>chunks.push(b));ffmpeg.stderr.on('data',b=>errors+=b);
  const done=new Promise((resolve,reject)=>{ffmpeg.on('error',reject);ffmpeg.on('exit',code=>code===0?resolve():reject(new Error(errors)));});
  for(const frame of frames)if(!ffmpeg.stdin.write(Buffer.from(frame.data,'base64')))await new Promise(r=>ffmpeg.stdin.once('drain',r));ffmpeg.stdin.end();await done;const pixels=Buffer.concat(chunks);
  if(pixels.length!==frames.length*120*16)throw new Error(`Capture decode count mismatch: ${pixels.length}/${frames.length}`);
  return frames.map((frame,index)=>{let code=0;for(let bit=0;bit<10;bit++){const at=index*1920+8*120+bit*12+6;if(pixels[at]>128)code|=1<<bit;}return {timestamp:frame.timestamp,code,valid:!!(code&512)&&(code&255)<192,film:code&256?'tree':'robotic-arm',index:code&255};});
}
const reports=[];
try{
 const until=Date.now()+15000;while(true){try{if((await fetch('http://127.0.0.1:4183')).ok)break;}catch{}if(Date.now()>until)throw new Error('Capture server unavailable');await new Promise(r=>setTimeout(r,100));}
 browser=await chromium.launch({channel:'chromium'});
 for(const film of ['robotic-arm','tree'])for(const initialRate of [1,2]){
  const context=await browser.newContext({viewport:{width:1200,height:800}}),page=await context.newPage(),session=await context.newCDPSession(page);await session.send('Network.enable');let transferredBytes=0;session.on('Network.loadingFinished',e=>transferredBytes+=e.encodedDataLength);
  await page.goto('http://127.0.0.1:4183/?capture=1');await page.waitForFunction(()=>window.__filmExperiment?.state().drawn==='robotic-arm:0');if(film==='tree'){await page.locator('#tree').click();await page.waitForFunction(()=>window.__filmExperiment.state().film==='tree');}
  const environment=await page.evaluate(()=>({timeOrigin:performance.timeOrigin,renderer:window.__filmExperiment.state().renderer.info,tier:window.__filmExperiment.state().tier,dpr:devicePixelRatio}));if((await page.evaluate(()=>window.__filmExperiment.state().renderer.kind))!=='webgl2')throw new Error('Primary GPU unavailable');
  for(const [name,duration,direction,fluid,hold]of [[`cold-${initialRate}x-forward`,8000/initialRate,1,false,false],[`warm-${initialRate}x-reverse`,8000/initialRate,-1,false,false],['warm-2x-fluid-forward',4000,1,true,false],['warm-2x-hold-reverse',4000,-1,false,true]]){
   const frames=[];const listener=e=>{void session.send('Page.screencastFrameAck',{sessionId:e.sessionId});if(frames.length>=4000)throw new Error('Capture bound exceeded');frames.push({data:e.data,timestamp:e.metadata.timestamp*1000});};session.on('Page.screencastFrame',listener);await page.evaluate(()=>window.__filmExperiment.resetMetrics());
   await session.send('Page.startScreencast',{format:'jpeg',quality:85,maxWidth:1200,maxHeight:800,everyNthFrame:1});
   // Cancel/release before completion: paired preparation runs alongside native scrolling.
   if(hold){await page.mouse.move(600,320);await page.mouse.down();}
   const samples=await page.evaluate(async({duration,direction,fluid,hold})=>{const start=performance.now(),samples=[];let released=false;
    while(true){const time=await new Promise(r=>requestAnimationFrame(r)),fraction=Math.min(1,(time-start)/duration),p=direction===1?fraction:1-fraction;window.scrollTo(0,p*(document.documentElement.scrollHeight-innerHeight));if(fluid){const event=new PointerEvent('pointermove',{bubbles:true,pointerType:'mouse',isPrimary:true,clientX:600+300*Math.sin(fraction*Math.PI*12),clientY:350+120*Math.cos(fraction*Math.PI*9)});document.querySelector('#stage').dispatchEvent(event);}if(hold&&!released&&fraction>.3){window.dispatchEvent(new PointerEvent('pointerup',{pointerId:1}));released=true;}const s=window.__filmExperiment.state();samples.push({timestamp:performance.timeOrigin+performance.now(),desired:s.desired,submitted:s.drawn,pending:s.cache.pending,decoded:s.cache.size});if(fraction===1)break;
    }return samples;},{duration,direction,fluid,hold});
   if(hold)await page.mouse.up();await session.send('Page.stopScreencast');session.removeListener('Page.screencastFrame',listener);const snapshot=await page.evaluate(()=>window.__filmExperiment.state());
   const generations=samples.filter((s,i)=>!i||samples[i-1].desired!==s.desired);
   const identity=await decode(frames,800),valid=identity.filter(f=>f.valid),intervals=identity.slice(1).map((f,i)=>f.timestamp-identity[i].timestamp).sort((a,b)=>a-b);let last=null,runStart=valid[0]?.timestamp,maxHeld=0,changes=0;const latencies=[],seen=new Set();
   for(const captured of valid){const key=`${captured.film}:${captured.index}`;if(last!==key){if(last!==null)maxHeld=Math.max(maxHeld,captured.timestamp-runStart);runStart=captured.timestamp;last=key;changes++;}const target=generations.findLast(s=>s.timestamp<=captured.timestamp);if(target&&target.desired===captured.index&&!seen.has(target.desired)){seen.add(target.desired);latencies.push(captured.timestamp-target.timestamp);}}
   if(!valid.length)throw new Error('No valid source identity decoded from actual compositor pixels');
   maxHeld=Math.max(maxHeld,valid.at(-1).timestamp-runStart);
   const summary=values=>{values.sort((a,b)=>a-b);return {count:values.length,p50:values[Math.floor(values.length*.5)]??null,p95:values[Math.floor(values.length*.95)]??null,p99:values[Math.floor(values.length*.99)]??null,max:values.at(-1)??null};};
   const report={film,name,durationMs:duration,environment,captureFrames:identity.length,validIdentityFrames:valid.length,uniqueCapturedSourceFrames:new Set(valid.map(f=>`${f.film}:${f.index}`)).size,capturedChanges:changes,maxCapturedHeldMs:maxHeld,captureIntervalsMs:summary(intervals),matchedCaptureLatencyMs:summary(latencies),targetGenerations:generations.length,missedCapturedGenerations:generations.length-seen.size,samples,capturedIdentity:identity,state:snapshot,transferredBytes};reports.push(report);
   const captures=`${output}${film}-${initialRate}-${name}/`;await mkdir(captures,{recursive:true});for(let i=0;i<frames.length;i++)await writeFile(`${captures}${String(i).padStart(5,'0')}.jpg`,Buffer.from(frames[i].data,'base64'));await writeFile(`${captures}timestamps.json`,JSON.stringify(identity,null,2));
   await writeFile(`${output}${film}-${initialRate}-${name}.jpg`,Buffer.from(frames.at(-1)?.data??'','base64'));await writeFile(`${output}report.json`,JSON.stringify({method:'CDP compositor JPEG screencast, actual pixels from the primary GPU pass include a dev-only binary source-identity band. Capture cadence is measured; it is not display scanout and may undersample 24/48fps. Warm means preceding traversals in the same HTTP context, bounded 20-image window; cold means fresh context except the opening frame and its neighbors. State collection/capture adds overhead; independent uninstrumented performance suite reports normal costs. Capture matching latency is success-conditioned, not complete decoder latency. Native scroll geometry owns desired state. No physical-device claim.',sourceHash:createHash('sha256').update(await readFile(`${cwd}experiments/scroll-film-navigator/src/material.ts`)).digest('hex'),browser:browser.version(),reports},null,2));console.log(film,name,`${valid.length} captures, ${report.uniqueCapturedSourceFrames} unique source frames; capture p95 ${report.captureIntervalsMs.p95?.toFixed(1)}ms`);
  }
  await session.detach();await context.close();
 }
}finally{await browser?.close();server.kill('SIGTERM');}
