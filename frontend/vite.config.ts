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
    rollupOptions: {
      input: {
        main: "src/main.ts",
        "mermaid-loader": "src/mermaid-loader.ts"
      }
    }
  }
});
