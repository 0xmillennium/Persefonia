import { readFile, readdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { spawn } from 'node:child_process';
export const hash = bytes => createHash('sha256').update(bytes).digest('hex');
export function webpDimensions(b) {
  if(b.length<30 || b.toString('ascii',0,4)!=='RIFF' || b.toString('ascii',8,12)!=='WEBP' || b.readUInt32LE(4)+8!==b.length)throw new Error('Invalid/truncated WebP container');
  for(let at=12;at+8<=b.length;) {
    const tag=b.toString('ascii',at,at+4),size=b.readUInt32LE(at+4),p=at+8;
    if(p+size>b.length)throw new Error('Truncated WebP chunk');
    if(tag==='VP8X' && size>=10)return [b.readUIntLE(p+4,3)+1,b.readUIntLE(p+7,3)+1];
    if(tag==='VP8 ' && size>=10 && b[p+3]===0x9d && b[p+4]===1 && b[p+5]===0x2a)return [b.readUInt16LE(p+6)&0x3fff,b.readUInt16LE(p+8)&0x3fff];
    if(tag==='VP8L' && size>=5 && b[p]===0x2f){const bits=b.readUInt32LE(p+1);return [(bits&0x3fff)+1,((bits>>>14)&0x3fff)+1];}
    at=p+size+(size&1);
  }
  throw new Error('Missing WebP image header');
}
export async function runFfmpeg(args) {
  return new Promise((resolve,reject)=>{
    const child=spawn('ffmpeg',['-hide_banner','-loglevel','error','-xerror',...args],{stdio:['ignore','pipe','pipe']});let output='',errors='';
    child.stdout.on('data',b=>{output+=b;});child.stderr.on('data',b=>{errors+=b;});child.on('error',reject);
    child.on('exit',code=>code===0?resolve(output):reject(new Error(`ffmpeg ${code}: ${errors}`)));
  });
}
export async function inventory(directory,tiers,films,expected) {
  const entries={};
  for(const width of tiers)for(const film of films){
    const folder=`${directory}/${width}/${film}`,names=(await readdir(folder)).sort();
    const canonical=Array.from({length:192},(_,i)=>`frame_${String(i+1).padStart(5,'0')}.webp`);
    if(JSON.stringify(names)!==JSON.stringify(canonical))throw new Error(`${folder}: incorrect delivery frame inventory`);
    entries[`${width}/${film}`]=[];
    for(const name of names){const bytes=await readFile(`${folder}/${name}`),[w,h]=webpDimensions(bytes);if(w!==width||h!==width*9/16)throw new Error(`${folder}/${name}: wrong dimensions`);entries[`${width}/${film}`].push({name,width:w,height:h,bytes:bytes.length,sha256:hash(bytes)});}
    const decoded=await runFfmpeg(['-threads','2','-framerate','24','-start_number','1','-i',`${folder}/frame_%05d.webp`,'-frames:v','192','-progress','pipe:1','-f','null','-']);
    if(!/^frame=192$/m.test(decoded))throw new Error(`${folder}: full delivery decode did not produce 192 frames`);
  }
  if(expected && JSON.stringify(entries)!==JSON.stringify(expected))throw new Error('Delivery bytes/hash inventory changed');
  return entries;
}
