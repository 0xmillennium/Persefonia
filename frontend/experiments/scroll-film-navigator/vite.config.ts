import { defineConfig } from "vite";
import { fileURLToPath } from "node:url";
import { createReadStream } from "node:fs";
import { stat, cp } from "node:fs/promises";
const media = fileURLToPath(new URL("../../test-results/scroll-film-navigator/media/", import.meta.url));
const root = fileURLToPath(new URL(".", import.meta.url));
export default defineConfig({
  root, publicDir: false,
  server: { host: "127.0.0.1", port: 4180, strictPort: true },
  preview: { host: "127.0.0.1", port: 4181, strictPort: true },
  build: { outDir: "../../dist/scroll-film-navigator", emptyOutDir: true },
  plugins: [{ name: "experiment-media",
    configureServer(server) { server.middlewares.use(async (req,res,next) => {
      const match = /^\/media\/(960|1600)\/(robotic-arm|tree)\/frame_(\d{5})\.webp(?:\?.*)?$/.exec(req.url ?? "");
      if(!match) { next(); return; }
      const path = `${media}${match[1]}/${match[2]}/frame_${match[3]}.webp`;
      try { const info=await stat(path); res.setHeader("Content-Type","image/webp"); res.setHeader("Content-Length",info.size); res.setHeader("Cache-Control","no-cache"); createReadStream(path).pipe(res); }
      catch { res.statusCode=404; res.end("Missing delivery frame; run npm run film:prepare"); }
    }); },
    async closeBundle() { await cp(media,`${root}../../dist/scroll-film-navigator/media`,{recursive:true}); }
  }]
});
