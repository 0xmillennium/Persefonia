import { spawn, execFileSync } from 'node:child_process';
import { appendFile, mkdir } from 'node:fs/promises';
import { output } from './assets.mjs';
import { sourceFingerprint } from './preflight.mjs';
const [purpose,command,...args]=process.argv.slice(2);
if(!purpose||!command)throw new Error('Usage: execute.mjs purpose command [args]');
const start=new Date(),revision=execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim();
const fingerprint=await sourceFingerprint();
await mkdir(`${output}finalization`,{recursive:true});
const child=spawn(command,args,{stdio:'inherit',detached:process.platform!=='win32'});
for(const signal of ['SIGINT','SIGTERM'])process.on(signal,()=>{
  if(!child.pid)return;
  try{if(process.platform==='win32')child.kill(signal);else process.kill(-child.pid,signal);}catch{}
});
const result=await new Promise(resolve=>{child.on('error',error=>resolve({code:1,error:String(error)}));child.on('exit',(code,signal)=>resolve({code:code??1,signal}));});
await appendFile(`${output}finalization/execution.jsonl`,JSON.stringify({purpose,command:[command,...args],start:start.toISOString(),end:new Date().toISOString(),durationMs:Date.now()-start.getTime(),revision,sourceFingerprint:fingerprint,evidenceRoot:output,...result})+'\n');
process.exitCode=result.code;
