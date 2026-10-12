import { defineConfig } from "vite";
import { fileURLToPath } from "node:url";
import { createReadStream } from "node:fs";
import { stat, cp, readFile, writeFile } from "node:fs/promises";
import { sourceFingerprint } from "./scripts/preflight.mjs";
const media=fileURLToPath(new URL("../../test-results/scroll-film-navigator/media/",import.meta.url));
const root=fileURLToPath(new URL(".",import.meta.url));
async function manifest():Promise<{identity:string}> {const value=JSON.parse(await readFile(`${media}manifest.json`,"utf8"));if(!/^[a-f0-9]{64}$/.test(value.identity))throw new Error("Run film:prepare: versioned media required");return value;}
export default defineConfig({
  root,publicDir:false,
  server:{host:"127.0.0.1",port:4180,strictPort:true},preview:{host:"127.0.0.1",port:4181,strictPort:true},
  build:{outDir:"../../dist/scroll-film-navigator",emptyOutDir:true},
  plugins:[{name:"experiment-media",
    async transformIndexHtml(html){return html.replaceAll("__MEDIA_VERSION__",(await manifest()).identity);},
    configureServer(server){server.middlewares.use(async(req,res,next)=>{
      const match=/^\/media\/([a-f0-9]{64})\/(960|1600)\/(robotic-arm|tree)\/frame_(\d{5})\.webp(?:\?.*)?$/.exec(req.url??"");
      if(!match){next();return;}
      const index=Number(match[4]);if(index<1||index>192){res.statusCode=404;res.end();return;}
      const path=`${media}${match[1]}/${match[2]}/${match[3]}/frame_${match[4]}.webp`;
      try{const info=await stat(path);res.setHeader("Content-Type","image/webp");res.setHeader("Content-Length",info.size);res.setHeader("Cache-Control","public, max-age=31536000, immutable");createReadStream(path).pipe(res);}catch{res.statusCode=404;res.end("Missing versioned delivery frame; run film:prepare");}
    });}
  },{name:"experiment-build-receipt",apply:"build",
    async writeBundle(){const active=await manifest();const destination=`${root}../../dist/scroll-film-navigator/media`;await cp(`${media}${active.identity}`,`${destination}/${active.identity}`,{recursive:true});await cp(`${media}manifest.json`,`${destination}/manifest.json`);await writeFile(`${root}../../dist/scroll-film-navigator/build-receipt.json`,JSON.stringify({sourceFingerprint:await sourceFingerprint()}));}
  }]
});
