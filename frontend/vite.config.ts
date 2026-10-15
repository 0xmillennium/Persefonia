import { defineConfig } from "vite";
import { homeMedia } from "./scripts/home-media-plugin";

export default defineConfig({
  base: "/assets/",
  plugins: [homeMedia()],
  server: {
    host: "127.0.0.1"
  },
  build: {
    manifest: true,
    outDir: "../app/build/generated/vite/static/assets",
    emptyOutDir: true,
    assetsDir: ".",
    // The diagram renderer (mermaid) is only fetched on the dedicated
    // mermaid-loader entry, so its weight never reaches the main page bundle.
    // Mermaid ships its core as a single pre-bundled ESM module (~1.26 MB
    // source, ~599 kB minified) that the bundler cannot split further at module
    // boundaries; the per-package grouping below splits every other heavy vendor
    // (katex, cytoscape, d3) into separate cacheable chunks, leaving only that
    // one irreducible module above 500 kB. The warning limit is raised just
    // past that measured floor so a real future regression still surfaces.
    chunkSizeWarningLimit: 650,
    rollupOptions: {
      input: {
        main: "src/main.ts",
        home: "src/home.ts",
        "mermaid-loader": "src/mermaid-loader.ts"
      },
      output: {
        codeSplitting: {
          groups: [
            {
              // Keep the shared Vite dynamic-import helper independent of
              // diagram vendors; otherwise homepage globe imports pull Mermaid in.
              name(id: string) {
                return id.includes("vite/preload-helper") ? "runtime-preload" : null;
              },
              priority: 100,
              minSize: 0
            },
            {
              name(id: string) {
                const match = id.match(
                  /node_modules\/(?:\.pnpm\/)?(@[^/]+\/[^/]+|[^/]+)/
                );
                if (!match) {
                  return null;
                }
                // Pixi's module graph contains cycles; let automatic chunking
                // keep its initialization order intact.
                if (match[1] === "pixi.js") {
                  return null;
                }
                return `vendor-${match[1].replace("@", "").replace("/", "-")}`;
              },
              minSize: 20_000,
              maxSize: 400_000
            }
          ]
        }
      }
    }
  }
});
