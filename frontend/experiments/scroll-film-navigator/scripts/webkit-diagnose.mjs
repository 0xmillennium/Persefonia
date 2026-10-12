import { webkit } from 'playwright';
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { mkdir, writeFile, readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { sourceFingerprint, consumeMedia } from './preflight.mjs';

// Four layers, three independent launches each. No runtime workarounds or retries.
const output=fileURLToPath(new URL('../../../test-results/scroll-film-navigator/stabilization/',import.meta.url));
await mkdir(output,{recursive:true});
await consumeMedia();
const minimal=createServer((_req,res)=>{res.setHeader('Content-Type','text/html');res.end('<!doctype html><html lang="en"><title>WebKit isolation</title><button>Minimal page</button></html>');});
await new Promise(resolve=>minimal.listen(0,'127.0.0.1',resolve));
const cwd=fileURLToPath(new URL('../../../',import.meta.url));
const server=spawn(process.execPath,['node_modules/vite/bin/vite.js','--config','experiments/scroll-film-navigator/vite.config.ts','--port','4185'],{cwd,stdio:'inherit'});
const report={playwright:JSON.parse(await readFile(`${cwd}node_modules/playwright/package.json`,'utf8')).version,executable:webkit.executablePath(),sourceFingerprint:await sourceFingerprint(),results:[]};
try {
  const deadline=Date.now()+15_000;
  while(true){try{if((await fetch('http://127.0.0.1:4185')).ok)break;}catch{}if(Date.now()>deadline)throw new Error('Diagnostic server unavailable');await new Promise(resolve=>setTimeout(resolve,100));}
  for(const scenario of ['W1-empty','W2-webgl2','W3-basic','W4-normal'])for(let trial=1;trial<=3;trial++){
    const result={scenario,trial,status:'FAIL',pageCrash:false,initialized:false,errors:[]};
    let browser;
    try {
      browser=await webkit.launch({timeout:15_000});result.browser=browser.version();
      const page=await browser.newPage();page.setDefaultTimeout(10_000);
      page.on('crash',()=>{result.pageCrash=true;});page.on('pageerror',error=>result.errors.push(String(error)));
      await page.goto(scenario.startsWith('W1')||scenario.startsWith('W2')?`http://127.0.0.1:${minimal.address().port}`:`http://127.0.0.1:4185/${scenario==='W3-basic'?'?renderer=basic':''}`);
      if(scenario==='W1-empty'){await page.getByRole('button').click();result.initialized=true;}
      else if(scenario==='W2-webgl2'){
        result.graphics=await page.evaluate(()=>{
          const gl=document.createElement('canvas').getContext('webgl2');if(!gl)return {webgl2:false};
          const texture=gl.createTexture(),buffer=gl.createFramebuffer();gl.bindTexture(gl.TEXTURE_2D,texture);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,2,2,0,gl.RGBA,gl.UNSIGNED_BYTE,null);gl.bindFramebuffer(gl.FRAMEBUFFER,buffer);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,texture,0);gl.clearColor(0,1,0,1);gl.clear(gl.COLOR_BUFFER_BIT);
          const pixels=new Uint8Array(4);gl.readPixels(0,0,1,1,gl.RGBA,gl.UNSIGNED_BYTE,pixels);
          const ext=gl.getExtension('WEBGL_debug_renderer_info');const info={webgl2:true,status:gl.checkFramebufferStatus(gl.FRAMEBUFFER),error:gl.getError(),pixel:[...pixels],renderer:ext?gl.getParameter(ext.UNMASKED_RENDERER_WEBGL):gl.getParameter(gl.RENDERER)};gl.deleteFramebuffer(buffer);gl.deleteTexture(texture);return info;
        });result.initialized=true;
      }else{
        await page.waitForFunction(()=>window.__filmExperiment?.state().drawn==='robotic-arm:0');
        const state=await page.evaluate(()=>window.__filmExperiment.state());result.initialized=true;result.renderer=state.renderer;result.renderFailure=state.renderFailure;
      }
      result.status='PASS';
    }catch(error){result.error=String(error);}finally{await browser?.close().catch(()=>{});}
    report.results.push(result);console.log(JSON.stringify(result));await writeFile(`${output}webkit-diagnostic.json`,JSON.stringify(report,null,2));
  }
}finally{server.kill('SIGTERM');await new Promise(resolve=>minimal.close(resolve));}
