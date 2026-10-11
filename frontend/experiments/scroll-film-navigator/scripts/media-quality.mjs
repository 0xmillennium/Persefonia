import { spawnSync } from 'node:child_process';
import { readFile,writeFile } from 'node:fs/promises';
import { root,output } from './assets.mjs';
const delivery=JSON.parse(await readFile(`${output}media/manifest.json`,'utf8')),reports=[];
for(const film of ['robotic-arm','tree'])for(const [probe,index]of [[1,49],[2,97],[3,145]])for(const format of ['WebP','VP9']){
 const source=`${root}assets/frames/${film}/frame_${String(index).padStart(5,'0')}.png`,candidate=format==='WebP'?`${output}media/${delivery.identity}/960/${film}/frame_${String(index).padStart(5,'0')}.webp`:`${output}remediation/candidates/${film}-vp9-${String(probe).padStart(2,'0')}.png`;
 const command=['-hide_banner','-loglevel','info','-threads','2','-i',source,'-i',candidate,'-filter_complex_threads','1','-lavfi','[0:v]scale=960:540:flags=lanczos,format=yuv420p[reference];[1:v]format=yuv420p[candidate];[reference][candidate]psnr','-frames:v','1','-f','null','-'];
 const result=spawnSync('ffmpeg',command,{encoding:'utf8'});if(result.status!==0)throw new Error(result.stderr);
 const average=/average:([\d.]+)/.exec(result.stderr)?.[1];if(!average)throw new Error('PSNR measurement absent');
 reports.push({film,index,format,averagePsnr:Number(average),stderr:result.stderr});
}
await writeFile(`${output}remediation/candidates/quality.json`,JSON.stringify({method:'Three original-time probes per film, candidate decode vs canonical PNG scaled with Lanczos to 960×540, both converted to yuv420p for PSNR. PSNR is a technical quality indicator, not human material approval or full-sequence fidelity proof.',reports},null,2));
console.log(reports.map(r=>`${r.film} ${r.index} ${r.format}: ${r.averagePsnr.toFixed(2)}dB`).join('\n'));
