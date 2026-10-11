import { readFile, mkdir, writeFile, stat, readdir } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
import { validate, root, output, films } from './assets.mjs';
// Derived files are disposable. Sources are never written or passed as output paths.
const source=await validate();
await mkdir(output,{recursive:true});
await writeFile(`${output}source-validation.json`,JSON.stringify(source,null,2));
const contract={sourceHash:createHash('sha256').update(JSON.stringify(source)).digest('hex'),tiers:[960,1600],quality:82,encoder:'ffmpeg/libwebp',filter:'lanczos',version:1};
const media=`${output}media`;
let existing;
try { existing=JSON.parse(await readFile(`${media}/manifest.json`,'utf8')); } catch {}
let valid=JSON.stringify(existing)===JSON.stringify(contract);
if(valid) for(const width of contract.tiers) for(const film of films) {
  try { const dir=`${media}/${width}/${film}`; const names=await readdir(dir); if(names.length!==192) valid=false; for(let i=1;i<=192;i++) if((await stat(`${dir}/frame_${String(i).padStart(5,'0')}.webp`)).size<100) valid=false; } catch { valid=false; }
}
if(valid) { console.log('Derived media up to date: source hashes and 384 frames per tier verified.'); process.exit(0); }
for(const width of contract.tiers) for(const film of films) {
  const destination=`${media}/${width}/${film}`; await mkdir(destination,{recursive:true});
  console.log(`Preparing ${film} ${width}px (192 one-to-one frames)…`);
  await new Promise((resolve,reject)=>{
    const child=spawn('ffmpeg',['-hide_banner','-loglevel','error','-y','-threads','2','-framerate','24','-start_number','1','-i',`${root}assets/frames/${film}/frame_%05d.png`,'-frames:v','192','-vf',`scale=${width}:-1:flags=lanczos`,'-c:v','libwebp','-quality','82','-threads','2',`${destination}/frame_%05d.webp`],{stdio:'inherit'});
    child.on('error',reject); child.on('exit',code=>code===0?resolve():reject(new Error(`ffmpeg exited ${code}`)));
  });
}
await writeFile(`${media}/manifest.json`,JSON.stringify(contract));
console.log('Prepared 768 delivery frames. Canonical PNGs unchanged.');
