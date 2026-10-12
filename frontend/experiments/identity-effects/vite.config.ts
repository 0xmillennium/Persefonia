import { defineConfig } from "vite";
import { fileURLToPath } from "node:url";

export default defineConfig({
  root: fileURLToPath(new URL(".", import.meta.url)),
  publicDir: false,
  server: { host: "127.0.0.1", port: 4284, strictPort: true },
  preview: { host: "127.0.0.1", port: 4285, strictPort: true },
  build: { outDir: "../../dist/identity-effects", emptyOutDir: true, assetsInlineLimit: 0 }
});
