import { readFile, mkdir, writeFile, rename, rm, mkdtemp } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import { validate, root, output, films } from './assets.mjs';
import { hash, inventory, runFfmpeg } from './delivery.mjs';
const source=await validate();
await mkdir(output,{recursive:true});await writeFile(`${output}source-validation.json`,JSON.stringify(source,null,2));
const encoderVersion=execFileSync('ffmpeg',['-version'],{encoding:'utf8'}).split('\n')[0];
const contract={sourceHash:hash(JSON.stringify(source)),tiers:[960,1600],quality:82,encoder:'ffmpeg/libwebp',encoderVersion,filter:'lanczos',frames:192,version:3};
const identity=hash(JSON.stringify(contract)),media=`${output}media`,destination=`${media}/${identity}`,lock=`${output}prepare.lock`;
await mkdir(media,{recursive:true});
try{await mkdir(lock);}catch{throw new Error(`Preparation locked at ${lock}; verify no preparer is running before removing a stale lock`);}
let temporary;
try {
  await writeFile(`${lock}/owner.json`,JSON.stringify({pid:process.pid,started:new Date().toISOString()}));
  let existing;try{existing=JSON.parse(await readFile(`${media}/manifest.json`,'utf8'));}catch{}
  if(existing?.identity===identity){
    await inventory(destination,contract.tiers,films,existing.entries);
    console.log(`PASS: reused ${identity}; all 768 hashes, dimensions, correspondence indices and complete pixel decodes verified.`);
  }else{
    temporary=await mkdtemp(`${media}/.preparing-`);
    for(const width of contract.tiers)for(const film of films){
      const folder=`${temporary}/${width}/${film}`;await mkdir(folder,{recursive:true});console.log(`Preparing ${film} ${width}px, 192 original-time frames…`);
      await runFfmpeg(['-y','-threads','2','-framerate','24','-start_number','1','-i',`${root}assets/frames/${film}/frame_%05d.png`,'-frames:v','192','-vf',`scale=${width}:-1:flags=lanczos`,'-c:v','libwebp','-quality',String(contract.quality),'-threads','2',`${folder}/frame_%05d.webp`]);
    }
    const entries=await inventory(temporary,contract.tiers,films);
    const after=await validate();if(hash(JSON.stringify(after))!==contract.sourceHash)throw new Error('Canonical source identity changed during preparation');
    const manifest={...contract,identity,entries};await writeFile(`${temporary}/manifest.json`,JSON.stringify(manifest));
    try{await rename(temporary,destination);temporary=undefined;}catch(error){if(error.code!=='EEXIST'&&error.code!=='ENOTEMPTY')throw error;await inventory(destination,contract.tiers,films,entries);}
    const manifestTemp=`${media}/.manifest-${process.pid}.json`;await writeFile(manifestTemp,JSON.stringify(manifest));await rename(manifestTemp,`${media}/manifest.json`);
    console.log(`PASS: published ${identity}, 768 fully decoded delivery frames; canonical PNG hashes unchanged.`);
  }
}finally{if(temporary)await rm(temporary,{recursive:true,force:true});await rm(lock,{recursive:true,force:true});}
