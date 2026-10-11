import { mkdir, readFile, writeFile, stat } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
import { root, output, films } from './assets.mjs';
const delivery=JSON.parse(await readFile(`${output}media/manifest.json`,'utf8'));
const imageRoot=`${output}media/${delivery.identity?`${delivery.identity}/`:''}`;
const destination=`${output}remediation/candidates`;
await mkdir(destination,{recursive:true});
const run=(args)=>new Promise((resolve,reject)=>{const child=spawn('ffmpeg',['-hide_banner','-loglevel','error','-threads','2',...args],{stdio:'inherit'});child.on('error',reject);child.on('exit',code=>code===0?resolve():reject(new Error(`ffmpeg ${code}`)));});
const inventory=[];
for(const film of films) {
  const path=`${destination}/${film}.ivf`,started=performance.now();
  await run(['-y','-framerate','24','-start_number','1','-i',`${root}assets/frames/${film}/frame_%05d.png`,'-frames:v','192','-vf','scale=960:540:flags=lanczos','-c:v','libvpx-vp9','-g','1','-b:v','0','-crf','25','-deadline','good','-cpu-used','3','-row-mt','1','-threads','2','-pix_fmt','yuv420p','-f','ivf',path]);
  const data=await readFile(path),frames=[];
  if(data.toString('ascii',0,4)!=='DKIF')throw new Error('Invalid IVF');
  for(let offset=32,index=0;offset<data.length;index++) {const length=data.readUInt32LE(offset);frames.push({index,offset:offset+12,length,timestamp:Number(data.readBigUInt64LE(offset+4))});offset+=12+length;}
  if(frames.length!==192)throw new Error('Invalid frame count');
  inventory.push({film,format:'VP9 all-intra IVF',width:960,height:540,bytes:data.length,prepareMs:performance.now()-started,sha256:createHash('sha256').update(data).digest('hex'),frames});
  await run(['-i',path,'-f','null','-']);
  // A browser-independent sample of seekable delivery quality and encoded frame identity.
  await run(['-y','-i',path,'-vf','select=eq(n\\,48)+eq(n\\,96)+eq(n\\,144)','-vsync','0',`${destination}/${film}-vp9-%02d.png`]);
  // Offline optical-flow study. Keep these disposable study frames separate from delivery.
  // Original frames remain exact inputs; interpolated samples are never creative authority.
  for(const start of [49,97]) {
    const prefix=`${destination}/${film}-${start}`,source=`${root}assets/frames/${film}/frame_%05d.png`;
    const before=performance.now();
    await run(['-y','-framerate','24','-start_number',String(start),'-i',source,'-frames:v','18','-vf','scale=640:360:flags=lanczos,minterpolate=fps=48:mi_mode=mci:mc_mode=aobmc:me_mode=bidir:vsbmc=1','-threads','2',`${prefix}-interpolated-%02d.png`]);
    await run(['-y','-framerate','24','-start_number',String(start),'-i',source,'-frames:v','10','-vf','scale=640:360:flags=lanczos','-threads','2',`${prefix}-source-%02d.png`]);
    inventory.push({film,format:'FFmpeg minterpolate MCI study',start,outputCount:18,width:640,height:360,prepareMs:performance.now()-before});
  }
  const webps=await Promise.all(Array.from({length:192},(_,i)=>stat(`${imageRoot}960/${film}/frame_${String(i+1).padStart(5,'0')}.webp`)));
  inventory.push({film,format:'WebP q82 sequence',width:960,height:540,bytes:webps.reduce((n,s)=>n+s.size,0)});
}
await writeFile(`${destination}/manifest.json`,JSON.stringify({version:1,source:'Canonical PNGs, unchanged',inventory},null,2));
console.log('Candidate data prepared and VP9 decoded for validation.');
