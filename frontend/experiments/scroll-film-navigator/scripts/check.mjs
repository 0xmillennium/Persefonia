import { spawn } from 'node:child_process';
import { consumeMedia,verifyBuilt,sourceFingerprint } from './preflight.mjs';
const before=await sourceFingerprint(),active=await consumeMedia();
async function run(script){
  console.log(`Functional gate: ${script}`);
  const child=spawn('npm',['run',script],{stdio:'inherit'});
  const code=await new Promise((resolve,reject)=>{child.on('error',reject);child.on('exit',code=>resolve(code??1));});
  if(code!==0){process.exitCode=code;throw new Error(`${script} failed; gate stopped, no automatic rerun`);}
}
await run('film:typecheck');await run('film:test');
let built=false;try{await verifyBuilt(active);built=true;}catch{}
if(!built)await run('film:build');else console.log('Functional gate: reusing matching built artifact');
await run('film:browser:functional');await run('film:browser:built');
await run('typecheck');await run('test:unit');
if(before!==await sourceFingerprint())throw new Error('Source changed during functional gate');
console.log('Functional gate complete. Media integrity is the retained explicit preparation receipt; performance/soak/recordings are separate.');
