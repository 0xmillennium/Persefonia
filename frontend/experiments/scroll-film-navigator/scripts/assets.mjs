import { readdir, readFile, mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import * as zlib from 'node:zlib';
import { sources } from './media-config.mjs';

export const root = fileURLToPath(new URL('../', import.meta.url));
export const output = fileURLToPath(new URL('../../../test-results/scroll-film-navigator/', import.meta.url));
export const films = ['robotic-arm', 'tree'];
export function sourceSequence(film, directory = `${root}assets/frames`) {
  const {directory:folder,digits}=sources[film];
  return {folder:`${directory}/${folder}`,digits,names:Array.from({length:192},(_,i)=>`frame_${String(i+1).padStart(digits,'0')}.png`)};
}
const signature = Buffer.from([137,80,78,71,13,10,26,10]);
const table = new Uint32Array(256);
for (let n=0;n<256;n++) { let c=n; for(let k=0;k<8;k++) c=(c&1)?0xedb88320^(c>>>1):c>>>1; table[n]=c>>>0; }
function crc(buffer) { if(zlib.crc32) return zlib.crc32(buffer); let c=0xffffffff; for(const b of buffer) c=table[(c^b)&255]^(c>>>8); return (c^0xffffffff)>>>0; }
export function inspectPng(buffer, label, expected = [3840,2160]) {
  const fail = reason => { throw new Error(`${label}: ${reason}`); };
  if (!buffer.subarray(0,8).equals(signature)) fail('invalid PNG signature');
  let offset=8, header=false, data=false, end=false;
  while(offset<buffer.length) {
    if(offset+12>buffer.length) fail('truncated chunk');
    const length=buffer.readUInt32BE(offset), next=offset+length+12;
    if(next>buffer.length) fail('truncated chunk payload');
    const type=buffer.toString('ascii',offset+4,offset+8);
    if(crc(buffer.subarray(offset+4,next-4))!==buffer.readUInt32BE(next-4)) fail(`${type} CRC mismatch`);
    if(!header && type!=='IHDR') fail('IHDR must be first');
    if(type==='IHDR') {
      if(header || length!==13) fail('invalid IHDR');
      const w=buffer.readUInt32BE(offset+8), h=buffer.readUInt32BE(offset+12);
      if(w!==expected[0] || h!==expected[1]) fail(`expected ${expected}, found ${w}x${h}`);
      if(buffer[offset+18]!==0 || buffer[offset+19]!==0 || buffer[offset+20]>1) fail('unsupported PNG header');
      header=true;
    }
    if(type==='IDAT') data=true;
    if(type==='IEND') { if(length || next!==buffer.length) fail('invalid IEND/trailing data'); end=true; }
    offset=next;
  }
  if(!header || !data || !end) fail('missing required PNG chunks');
  return {width:expected[0],height:expected[1],bytes:buffer.length,sha256:createHash('sha256').update(buffer).digest('hex')};
}
export async function validate(directory = `${root}assets/frames`) {
  const manifest={version:1,frames:192,width:3840,height:2160,films:{}};
  for(const film of films) {
    const {folder,names:expected}=sourceSequence(film,directory);
    const names=(await readdir(folder)).filter(n=>n.toLowerCase().endsWith('.png')).sort();
    if(names.length!==192 || names.some((n,i)=>n!==expected[i])) throw new Error(`${film}: expected exactly consecutive ${expected[0]}–${expected.at(-1)} in ${folder}; found ${names.length} PNGs`);
    manifest.films[film]={};
    for(const name of names) manifest.films[film][name]=inspectPng(await readFile(`${folder}/${name}`),`${film}/${name}`);
  }
  return manifest;
}
if(process.argv[1]===fileURLToPath(import.meta.url)) {
  try { const manifest=await validate(); await mkdir(output,{recursive:true}); await writeFile(`${output}source-validation.json`,JSON.stringify(manifest,null,2)); console.log('PASS: 384 PNGs; names, signatures, 3840×2160 dimensions, chunk structure, CRCs and source hashes validated. No pixel decoding.'); }
  catch(error) { console.error(`ASSET CONTRACT FAILED: ${error.message}`); process.exitCode=1; }
}
