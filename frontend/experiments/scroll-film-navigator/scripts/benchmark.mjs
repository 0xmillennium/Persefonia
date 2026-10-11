import { createHash } from 'node:crypto';
import { chromium,firefox,webkit } from 'playwright';
import { execFileSync } from 'node:child_process';
import { createServer } from 'node:http';
import { readFile,writeFile,mkdir,stat } from 'node:fs/promises';
import { createReadStream } from 'node:fs';
import { cpus,totalmem,release } from 'node:os';
import ts from 'typescript';
import { fileURLToPath } from 'node:url';
import { root,output } from './assets.mjs';
const candidates=`${output}remediation/candidates`,manifest=JSON.parse(await readFile(`${candidates}/manifest.json`,'utf8'));
const delivery=JSON.parse(await readFile(`${output}media/manifest.json`,'utf8'));
const imageRoot=`${output}media/${delivery.identity?`${delivery.identity}/`:''}`;
const destination=`${output}remediation/${process.env.FILM_BENCH_LABEL??"benchmark"}`;await mkdir(destination,{recursive:true});
const server=createServer(async(req,res)=>{
  try {
    if(req.url==='/'){res.setHeader('Content-Type','text/html');res.end('<!doctype html><title>Media candidate benchmark</title><canvas width="960" height="540"></canvas>');return;}
    if(req.url==='/transport.js'||req.url==='/loader.js'){res.setHeader('Content-Type','text/javascript');res.end(ts.transpileModule(req.url==='/transport.js'?execFileSync('git',['show','a650818:frontend/experiments/scroll-film-navigator/src/transport.ts'],{encoding:'utf8'}):await readFile(`${root}src/loader.ts`,'utf8'),{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText);return;}
    const image=/^\/image\/(robotic-arm|tree)\/(\d+)\.webp/.exec(req.url??''),movie=/^\/(robotic-arm|tree)\.ivf/.exec(req.url??'');
    const path=image?`${imageRoot}960/${image[1]}/frame_${String(Number(image[2])+1).padStart(5,'0')}.webp`:movie?`${candidates}/${movie[1]}.ivf`:null;
    if(!path){res.writeHead(404);res.end();return;}
    const size=(await stat(path)).size,range=/bytes=(\d+)-(\d+)/.exec(req.headers.range??'');
    res.setHeader('Content-Type',image?'image/webp':'application/octet-stream');res.setHeader('Cache-Control','public,max-age=31536000,immutable');
    if(range){const start=Number(range[1]),end=Math.min(size-1,Number(range[2]));res.writeHead(206,{'Content-Range':`bytes ${start}-${end}/${size}`,'Content-Length':end-start+1});createReadStream(path,{start,end}).pipe(res);}
    else{res.setHeader('Content-Length',size);createReadStream(path).pipe(res);}
  }catch(error){res.writeHead(500);res.end(String(error));}
});
await new Promise(r=>server.listen(4184,'127.0.0.1',r));
const sourceHashes={benchmark:createHash('sha256').update(await readFile(fileURLToPath(import.meta.url))).digest('hex'),baselineTransport:createHash('sha256').update(execFileSync('git',['show','a650818:frontend/experiments/scroll-film-navigator/src/transport.ts'])).digest('hex')};
const reports=[];
try {
 for(const engine of [process.env.FILM_BENCH_BROWSER??'chromium']) {
  const browser=await ({chromium,firefox,webkit}[engine]).launch(engine==='chromium'?{channel:'chromium'}:{});
  try {
   for(const film of (process.env.FILM_BENCH_FILMS??'robotic-arm,tree').split(','))for(const mode of (process.env.FILM_BENCH_MODES??'baseline-webp,directional-webp,intra-vp9').split(',')) {
    const context=await browser.newContext({viewport:{width:1000,height:620},recordVideo:{dir:destination,size:{width:1000,height:620}}}),page=await context.newPage();
    const errors=[];page.on('pageerror',e=>errors.push(String(e)));await page.goto('http://127.0.0.1:4184/');
    const result=await page.evaluate(async({mode,film,inventory})=>{
      const budget=mode==='directional2-webp'?{slots:2,cache:20,ahead:8,behind:4}:mode==='directional3-webp'?{slots:3,cache:24,ahead:10,behind:4}:{slots:4,cache:16,ahead:12,behind:3};
      const {FrameCache}=await import('/transport.js'),{loadBitmap}=await import('/loader.js');
      const canvas=document.querySelector('canvas'),ctx=canvas.getContext('2d',{alpha:false}),samples=[],decodeTimes=[],latencies=[];
      let target=0,displayed=-1,requests=0,bytes=0,failures=0,evictions=0,uploadMs=[],peak=0;
      const cache=new Map(),pending=new Map(),encoded=new Map(),encodedPending=new Map();let wanted=new Set(),direction=1,disposed=false;
      let decoder,serial=0;const decoded=new Map();
      const index=inventory.find(i=>i.film===film&&i.format==='VP9 all-intra IVF');
      if(mode==='intra-vp9') {
        if(!globalThis.VideoDecoder)return {unsupported:'VideoDecoder unavailable'};
        const config={codec:'vp09.00.10.08',codedWidth:960,codedHeight:540,optimizeForLatency:true};
        if(!(await VideoDecoder.isConfigSupported(config)).supported)return {unsupported:'VP9 config unsupported'};
        decoder=new VideoDecoder({output:frame=>{const p=decoded.get(frame.timestamp);if(!p){frame.close();return;}decoded.delete(frame.timestamp);createImageBitmap(frame).then(bitmap=>{frame.close();p.resolve(bitmap);},e=>{frame.close();p.reject(e);});},error:e=>{for(const p of decoded.values())p.reject(e);decoded.clear();}});decoder.configure(config);
      }
      const packet=async(frame)=>{
        const group=Math.floor(frame/12);let data=encoded.get(group);
        if(!data){let p=encodedPending.get(group);if(!p){const first=index.frames[group*12],last=index.frames[Math.min(191,group*12+11)];requests++;p=fetch(`/${film}.ivf`,{headers:{Range:`bytes=${first.offset}-${last.offset+last.length-1}`}}).then(async r=>{if(!r.ok)throw new Error('range fetch failed');const b=await r.arrayBuffer();bytes+=b.byteLength;encoded.set(group,b);while(encoded.size>8)encoded.delete(encoded.keys().next().value);return b;}).finally(()=>encodedPending.delete(group));encodedPending.set(group,p);}data=await p;}
        const offset=index.frames[frame].offset-index.frames[group*12].offset,length=index.frames[frame].length;
        return new Uint8Array(data,offset,length);
      };
      const load=async(frame,signal=new AbortController().signal)=>{
        const started=performance.now();let image;
        if(mode==='intra-vp9'){const data=await packet(frame),id=++serial;image=await new Promise((resolve,reject)=>{decoded.set(id,{resolve,reject});decoder.decode(new EncodedVideoChunk({type:'key',timestamp:id,data}));void decoder.flush().catch(reject);});}
        else {requests++;image=await loadBitmap(`/image/${film}/${frame}.webp`,signal);const entry=performance.getEntriesByName(location.origin+`/image/${film}/${frame}.webp`).at(-1);bytes+=entry?.transferSize??0;}
        decodeTimes.push(performance.now()-started);return image;
      };
      const baseline=mode==='baseline-webp'?new FrameCache(async(_film,frame,signal)=>load(frame,signal),8,2):null;
      const pump=()=>{
        if(disposed)return;
        for(const frame of wanted){if(pending.size>=budget.slots)break;if(cache.has(frame)||pending.has(frame))continue;
          const job=load(frame).then(image=>{if(disposed||!wanted.has(frame)){image.close();return;}cache.delete(frame);cache.set(frame,image);while(cache.size>budget.cache){const key=cache.keys().next().value;cache.get(key).close();cache.delete(key);evictions++;}}).catch(()=>failures++).finally(()=>{pending.delete(frame);pump();});pending.set(frame,job);peak=Math.max(peak,pending.size);
        }
      };
      const request=(frame,dir)=>{target=frame;direction=dir;
        if(baseline){baseline.request([{film,index:frame}]);return;}
        wanted=new Set([frame]);for(let i=1;i<=budget.ahead;i++)wanted.add(Math.max(0,Math.min(191,frame+direction*i)));for(let i=1;i<=budget.behind;i++)wanted.add(Math.max(0,Math.min(191,frame-direction*i)));pump();
      };
      const present=()=>{const image=baseline?.get(`${film}:${target}`)??cache.get(target);if(!image)return;const before=performance.now();ctx.drawImage(image,0,0,960,540);ctx.fillStyle='#000';ctx.fillRect(0,500,220,40);ctx.fillStyle='#fff';ctx.font='20px monospace';ctx.fillText(`${mode} ${target}`,10,530);uploadMs.push(performance.now()-before);displayed=target;};
      const wait=async frame=>{request(frame,1);const until=performance.now()+10000;while(!(baseline?.get(`${film}:${frame}`)??cache.get(frame))){if(performance.now()>until)throw new Error('candidate readiness timeout');await new Promise(r=>setTimeout(r,5));}present();};
      await wait(0);
      const workloads=[];
      const trajectory=async(name,duration,path)=>{
        const start=performance.now(),local=[],missed=[];let previousTarget=-1,requestedAt=start,targetReady=false;
        while(true){const time=await new Promise(r=>requestAnimationFrame(r)),fraction=Math.min(1,(time-start)/duration),p=path(fraction),frame=Math.round(p*191),dir=previousTarget>=0&&frame<previousTarget?-1:frame>previousTarget?1:direction;
          if(frame!==previousTarget){if(previousTarget>=0&&!targetReady)missed.push({frame:previousTarget,deadlineMs:performance.now()-requestedAt});request(frame,dir);previousTarget=frame;requestedAt=performance.now();targetReady=false;}present();
          if(displayed===frame&&!targetReady){latencies.push(performance.now()-requestedAt);targetReady=true;}
          local.push({t:performance.now()-start,desired:frame,displayed,ready:displayed===frame});if(fraction===1)break;
        }
        const distinct=new Set(local.map(s=>s.displayed)).size;let maxHold=0,runStart=local[0].t,last=local[0].displayed;
        for(const s of local){if(s.displayed!==last){maxHold=Math.max(maxHold,s.t-runStart);runStart=s.t;last=s.displayed;}}maxHold=Math.max(maxHold,local.at(-1).t-runStart);
        workloads.push({name,durationMs:duration,missedGenerations:missed,expectedSourceDwellMs:duration/192,distinct,readyFraction:local.filter(s=>s.ready).length/local.length,maxHeldMs:maxHold,samples:local});
      };
      for(const [name,duration,path] of [['cold-1x-forward',8000,t=>t],['warm-1x-reverse',8000,t=>1-t],['warm-2x-forward',4000,t=>t],['warm-2x-reverse',4000,t=>1-t],['alternating',4000,t=>(Math.sin(t*Math.PI*8)+1)/2]])await trajectory(name,duration,path);
      const jumps=[];for(let round=0;round<2;round++)for(const frame of [33,171,62,145,11,191,0]){const start=performance.now();await wait(frame);jumps.push({cacheContext:round===1?"repeated jump targets":"after traversals; HTTP cache warm, bounded decoded window",frame,ms:performance.now()-start});}
      const summary=a=>{a.sort((a,b)=>a-b);const at=p=>a[Math.min(a.length-1,Math.floor(a.length*p))]??0;return {count:a.length,p50:at(.5),p95:at(.95),p99:at(.99),max:at(1)};};
      const result={mode,film,budget,workloads,jumps,requests,bytes,failures,evictions:baseline?.evictions??evictions,decodedBytes:(baseline?.bytes??cache.size*960*540*4),peakPending:baseline?.peakPending??peak,readinessObservedMs:summary(latencies),fetchDecodeMs:summary(decodeTimes),canvasSubmissionMs:summary(uploadMs),webcodecs:!!globalThis.VideoDecoder};
      disposed=true;await Promise.allSettled([...pending.values()]);baseline?.dispose();for(const image of cache.values())image.close();decoder?.close();return result;
    },{mode,film,inventory:manifest.inventory});
    await page.screenshot({path:`${destination}/${engine}-${film}-${mode}.png`});const video=page.video();await context.close();await video.saveAs(`${destination}/${engine}-${film}-${mode}.webm`);await video.delete();reports.push({engine,browser:browser.version(),errors,...result});await writeFile(`${destination}/report.json`,JSON.stringify({sourceHashes,host:{cpu:cpus()[0]?.model,cpus:cpus().length,ram:totalmem(),os:release()},method:'960×540 canonical frames, 24fps source. Real-time progress trajectories; canvas identity is recorded alongside actual film pixels. One readiness observation per target generation; missed generations retained. Request counts are loader calls, ResourceTiming byte estimates include cached responses as reported. 25fps video cannot prove 48fps continuity. Callback/draw timing is not physical scanout. Local HTTP; cold decoded and then warm HTTP cache; bounded decoded window, no whole-film decode. VP9 packets requested in 12-frame indexed byte ranges. AVIF encoding unavailable in installed FFmpeg 4.4.2; not fabricated.',reports},null,2));console.log(`${engine} ${film} ${mode}: ${result.unsupported??result.workloads.map(w=>`${w.name} ${w.distinct} distinct / ${w.maxHeldMs.toFixed(1)}ms max hold`).join('; ')}`);
   }
  } finally{await browser.close();}
 }
} finally{server.close();}
