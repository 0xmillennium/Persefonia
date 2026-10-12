import { spawn } from 'node:child_process';
import { mkdir,readFile,writeFile } from 'node:fs/promises';
import { output } from './assets.mjs';
import { projects,summarize } from './qualification.mjs';
import { consumeMedia,verifyBuilt,sourceFingerprint } from './preflight.mjs';
const before=await sourceFingerprint(),active=await consumeMedia();
const directory=`${output}stabilization/gate/`;await mkdir(directory,{recursive:true});
const policy=process.env.FILM_WEBKIT_POLICY??'strict';
if(!['strict','vm-inconclusive'].includes(policy))throw new Error('Unknown FILM_WEBKIT_POLICY');
const evidence=policy==='vm-inconclusive'?JSON.parse(await readFile(process.env.FILM_WEBKIT_EVIDENCE,'utf8')):null;
if(evidence&&evidence.classification!=='INCONCLUSIVE — LINUX VM WEBKIT')throw new Error('Explicit VM crash classification required');
const report={sourceFingerprint:before,mediaIdentity:active.identity,policy,evidence,checks:[],projects:[]};
async function run(script,args=[],env={}){
  console.log(`Functional gate: ${script}`);
  const start=Date.now(),child=spawn('npm',['run',script,...(args.length?['--',...args]:[])],{stdio:'inherit',env:{...process.env,...env}});
  const code=await new Promise((resolve,reject)=>{child.on('error',reject);child.on('exit',code=>resolve(code??1));});
  report.checks.push({script,args,code,durationMs:Date.now()-start});await persist();return code;
}
async function persist(){await writeFile(`${directory}summary.json`,JSON.stringify(report,null,2));}
async function required(script){if(await run(script)!==0)throw new Error(`${script} failed; prerequisite gate stopped, no automatic rerun`);}
await required('film:typecheck');await required('film:test');
let built=false;try{await verifyBuilt(active);built=true;}catch{}
if(!built)await required('film:build');else console.log('Functional gate: reusing matching built artifact');
// A failed project cannot conceal the other engines or built-artifact results.
for(const built of [false,true])for(const name of projects){
  const project=name+(built?'-built':''),label=`gate-${project}`;
  const code=await run(built?'film:browser:built':'film:browser:functional',[`--project=${project}`,`--max-failures=${name.startsWith('webkit')?1:0}`],{FILM_REPORT_LABEL:label,DEBUG:name.startsWith('webkit')?'pw:browser':process.env.DEBUG??''});
  let result;try{result=JSON.parse(await readFile(`${output}${label}.json`,'utf8'));}catch{}
  report.projects.push(summarize(result,code,{project,policy,evidence}));await persist();
}
for(const script of ['typecheck','test:unit'])await run(script);
if(before!==await sourceFingerprint())throw new Error('Source changed during functional gate');
const failed=report.checks.some(check=>!check.script.startsWith('film:browser')&&check.code!==0)||report.projects.some(project=>project.blocking);
report.status=failed?'FAIL':report.projects.some(project=>project.vmException)?'PASS WITH VM WEBKIT LIMITATION':'PASS';await persist();
console.table(report.projects.map(({project,status,passed,skipped,failed,vmException})=>({project,status,passed,skipped,failed,vmException})));
console.log(`Functional gate: ${report.status}. Retained media integrity; performance/soak/recordings remain separate.`);process.exitCode=failed?1:0;
