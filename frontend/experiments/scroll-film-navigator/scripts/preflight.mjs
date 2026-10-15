import { readFile, readdir, stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { root, output, films, sourceSequence } from './assets.mjs';
import { hash } from './delivery.mjs';
import { preparation } from './media-config.mjs';

export async function sourceMetadata() {
  const result={};
  for(const film of films){
    const {folder,names:expected}=sourceSequence(film);
    const names=(await readdir(folder)).filter(n=>n.toLowerCase().endsWith('.png')).sort();
    if(JSON.stringify(names)!==JSON.stringify(expected))throw new Error(`${film}: canonical source inventory changed`);
    result[film]=await Promise.all(names.map(async name=>{const s=await stat(`${folder}/${name}`);return {name,bytes:s.size,mtimeMs:s.mtimeMs};}));
  }
  return result;
}
export async function sourceFingerprint() {
  // Small source files only; never hashes source images or delivery frames.
  const names=['index.html','vite.config.ts','tsconfig.json'];
  for(const folder of ['src','scripts'])for(const name of (await readdir(`${root}${folder}`)).sort())if(/\.(ts|css|mjs|py)$/.test(name))names.push(`${folder}/${name}`);
  return hash(Buffer.concat(await Promise.all(names.map(async name=>Buffer.concat([Buffer.from(name+'\0'),await readFile(`${root}${name}`)])))));
}
export async function preflight(directory=`${output}media`) {
  const raw=await readFile(`${directory}/manifest.json`,'utf8'),manifest=JSON.parse(raw);
  const {identity,entries,...contract}=manifest;
  if(!/^[a-f0-9]{64}$/.test(identity)||hash(JSON.stringify(contract))!==identity)throw new Error('Invalid media identity');
  for(const [key,value] of Object.entries(preparation))if(JSON.stringify(contract[key])!==JSON.stringify(value))throw new Error(`Incompatible preparation ${key}`);
  if(typeof contract.encoderVersion!=='string'||!/^[a-f0-9]{64}$/.test(contract.sourceHash))throw new Error('Incomplete preparation contract');
  if(await readFile(`${directory}/${identity}/manifest.json`,'utf8')!==raw)throw new Error('Active/published manifests disagree');
  for(const tier of preparation.tiers)for(const film of films){
    const records=entries[`${tier}/${film}`],folder=`${directory}/${identity}/${tier}/${film}`;
    if(!Array.isArray(records)||records.length!==192)throw new Error(`Incomplete ${tier}/${film}`);
    const names=await readdir(folder);
    if(names.length!==192)throw new Error(`Wrong output count: ${folder}`);
    await Promise.all(records.map(async(entry,i)=>{
      const name=`frame_${String(i+1).padStart(5,'0')}.webp`;
      if(entry.name!==name||entry.width!==tier||entry.height!==tier*9/16||!Number.isInteger(entry.bytes)||entry.bytes<=0)throw new Error(`Invalid delivery record ${name}`);
      const info=await stat(`${folder}/${name}`);if(!info.isFile()||info.size!==entry.bytes)throw new Error(`Missing/changed output ${folder}/${name}`);
    }));
  }
  return manifest;
}
export async function consumeMedia() {
  const manifest=await preflight();
  const receipt=JSON.parse(await readFile(`${output}media/validation-${manifest.identity}.json`,'utf8'));
  if(receipt.identity!==manifest.identity||receipt.manifestHash!==hash(await readFile(`${output}media/manifest.json`)))throw new Error('Missing matching integrity receipt');
  if(JSON.stringify(await sourceMetadata())!==JSON.stringify(receipt.sources))throw new Error('Source metadata changed since full validation');
  return manifest;
}
export async function verifyBuilt(active) {
  const directory=`${root}../../dist/scroll-film-navigator`,built=await preflight(`${directory}/media`);
  const receipt=JSON.parse(await readFile(`${directory}/build-receipt.json`,'utf8'));
  if(built.identity!==active.identity||receipt.sourceFingerprint!==await sourceFingerprint())throw new Error('Built artifact is stale; run film:build');
}
if(process.argv[1]===fileURLToPath(import.meta.url)){
  try{
    const active=await consumeMedia();
    if(process.argv.includes('--built'))await verifyBuilt(active);
    console.log(`PASS: cheap preflight ${active.identity}; configuration, inventory, sizes and source metadata only. No full hashing/decoding.`);
  }catch(error){console.error(`MEDIA PREFLIGHT FAILED: ${error.message}. Run film:prepare explicitly (film:build for stale build).`);process.exitCode=1;}
}
