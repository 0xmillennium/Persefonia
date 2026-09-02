import { defineConfig } from "vite";

export default defineConfig({
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
        "mermaid-loader": "src/mermaid-loader.ts"
      },
      output: {
        codeSplitting: {
          groups: [
            {
              name(id: string) {
                const match = id.match(
                  /node_modules\/(?:\.pnpm\/)?(@[^/]+\/[^/]+|[^/]+)/
                );
                if (!match) {
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
