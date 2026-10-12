import { defineConfig } from "vite";
import { fileURLToPath } from "node:url";

export default defineConfig({
  root: fileURLToPath(new URL(".", import.meta.url)),
  publicDir: false,
  server: { host: "127.0.0.1", port: 4280, strictPort: true },
  preview: { host: "127.0.0.1", port: 4281, strictPort: true },
  build: { outDir: "../../dist/location-earth-globe", emptyOutDir: true, assetsInlineLimit: 0 }
});
